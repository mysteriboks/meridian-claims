package com.meridian.claims.service;

import com.meridian.claims.dao.EnrollmentBatchDAO;
import com.meridian.claims.intake.EnrollmentRecord;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.intake.X12Edi834Parser;
import com.meridian.claims.model.EnrollmentBatch;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import com.meridian.claims.util.DateUtil;
import com.meridian.claims.util.LedgerUtil;
import com.meridian.claims.util.LogMaskUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Orchestrates inbound X12 834 (Benefit Enrollment and Maintenance) file
 * processing (Phase 17) — file-level idempotency (SHA-256 in
 * {@code enrollment_batches}, mirroring {@link IntakeService}) plus
 * per-record fault isolation, one {@link EnrollmentRecord} at a time.
 *
 * Maintenance type handling is intentionally simple, matching this
 * codebase's structural-simplification precedent for X12 parsers:
 * <ul>
 *   <li><b>021 (Add)</b> — creates the member if not already known, then adds
 *       a PRIMARY coverage record if the file carries a resolvable plan and
 *       effective date.</li>
 *   <li><b>024 (Termination)</b> — sets a termination date on the member's
 *       open-ended coverage record (matching the file's plan, if given).</li>
 *   <li><b>001 (Change)</b> — updates member demographics only. Coverage
 *       changes in practice arrive as a paired 024 (end old) + 021 (start
 *       new), so 001 deliberately does not touch coverage records here.</li>
 * </ul>
 * Anything that can't be resolved (unknown member for a term/change, no plan
 * match, missing required dates) is quarantined individually — the rest of
 * the file still processes.
 */
@Service
public class EnrollmentIntakeService {

    private static final Logger LOG = Logger.getLogger(EnrollmentIntakeService.class);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED  = "COMPLETED";
    private static final String STATUS_FAILED     = "FAILED";

    private static final int MAX_ERROR_MESSAGE_LEN = 2000;

    private final EnrollmentBatchDAO batchDAO;
    private final X12Edi834Parser parser;
    private final MemberService memberService;
    private final PlanService planService;
    private final AuditService auditService;

    @Autowired
    public EnrollmentIntakeService(EnrollmentBatchDAO batchDAO, X12Edi834Parser parser,
                                    MemberService memberService, PlanService planService,
                                    AuditService auditService) {
        this.batchDAO = batchDAO;
        this.parser = parser;
        this.memberService = memberService;
        this.planService = planService;
        this.auditService = auditService;
    }

    /** Return the most recent enrollment batch ledger rows, newest first. */
    public List<EnrollmentBatch> findRecentBatches(int limit) {
        return batchDAO.findRecent(limit);
    }

    /**
     * Processes one inbound 834 file: file-level idempotency check, parse,
     * per-record dispatch, ledger update. Mirrors {@link IntakeService#processFile}'s
     * shape closely enough to reuse its {@link IntakeService.IntakeSummary} return
     * type rather than defining a second near-identical DTO.
     */
    public IntakeService.IntakeSummary processFile(String fileName, String fileContent) {
        String fileHash = LedgerUtil.sha256(fileContent);

        EnrollmentBatch existing = batchDAO.findByFileHash(fileHash);
        if (existing != null && !STATUS_PROCESSING.equals(existing.getStatus())) {
            LOG.info("EnrollmentIntakeService: file already processed (hash=" + fileHash
                + " status=" + existing.getStatus() + ") — skipping fileName=" + fileName);
            return IntakeService.IntakeSummary.duplicate(fileName, existing.getSucceeded(), existing.getQuarantined());
        }

        int batchId;
        if (existing != null) {
            batchId = existing.getId();
            LOG.warn("EnrollmentIntakeService: reclaiming stale PROCESSING batch id=" + batchId
                + " for fileName=" + fileName);
        } else {
            EnrollmentBatch batch = new EnrollmentBatch();
            batch.setFileName(fileName);
            batch.setFileHash(fileHash);
            batch.setStatus(STATUS_PROCESSING);
            batchId = batchDAO.insert(batch);
        }

        List<EnrollmentRecord> records;
        try {
            records = parser.parse(fileContent);
        } catch (IntakeParseException e) {
            String msg = LogMaskUtil.maskPhi("File-level parse failure: " + e.getMessage());
            LOG.error("EnrollmentIntakeService: " + msg + " fileName=" + fileName);
            batchDAO.updateCompletion(batchId, STATUS_FAILED, 0, 0, 0, msg);
            auditService.record("ENROLLMENT_FILE_FAILED", "EnrollmentBatch", (long) batchId, msg);
            return IntakeService.IntakeSummary.fileLevelFailure(fileName, msg);
        }

        int succeeded = 0;
        int quarantined = 0;
        List<String> quarantineReasons = new ArrayList<String>();

        for (int i = 0; i < records.size(); i++) {
            EnrollmentRecord rec = records.get(i);
            String outcome = apply(rec);
            if (outcome == null) {
                succeeded++;
            } else {
                quarantined++;
                String reason = LogMaskUtil.maskPhi("Record " + (i + 1) + ": " + outcome);
                quarantineReasons.add(reason);
                LOG.warn("EnrollmentIntakeService: quarantined " + reason + " fileName=" + fileName);
                auditService.record("ENROLLMENT_RECORD_QUARANTINED", "EnrollmentBatch", (long) batchId, reason);
            }
        }

        int total = records.size();
        boolean nothingSucceeded = succeeded == 0 && total > 0;
        String finalStatus = nothingSucceeded ? STATUS_FAILED : STATUS_COMPLETED;
        batchDAO.updateCompletion(batchId, finalStatus, total, succeeded, quarantined,
            LedgerUtil.joinReasons(quarantineReasons, MAX_ERROR_MESSAGE_LEN));

        LOG.info("EnrollmentIntakeService: fileName=" + fileName + " batchId=" + batchId
            + " total=" + total + " succeeded=" + succeeded + " quarantined=" + quarantined
            + " status=" + finalStatus);

        return new IntakeService.IntakeSummary(fileName, false, finalStatus.equals(STATUS_FAILED) && nothingSucceeded,
            succeeded, quarantined, null);
    }

    /** Applies one enrollment record. Returns null on success, or a quarantine reason on failure. */
    private String apply(EnrollmentRecord rec) {
        if (rec.getMemberNumber() == null || rec.getMemberNumber().trim().isEmpty()) {
            return "member number is missing (no NM1*IL segment)";
        }
        String maintenanceType = rec.getMaintenanceTypeCode();
        if (maintenanceType == null) {
            return "maintenance type code is missing (no INS segment)";
        }

        Plan plan = null;
        if (rec.getPlanName() != null && !rec.getPlanName().trim().isEmpty()) {
            plan = planService.findByPlanName(rec.getPlanName().trim());
            if (plan == null) {
                return "plan not found: " + rec.getPlanName();
            }
        }

        try {
            if (EnrollmentRecord.MAINTENANCE_ADD.equals(maintenanceType)) {
                return applyAdd(rec, plan);
            } else if (EnrollmentRecord.MAINTENANCE_TERM.equals(maintenanceType)) {
                return applyTermination(rec, plan);
            } else if (EnrollmentRecord.MAINTENANCE_CHANGE.equals(maintenanceType)) {
                return applyChange(rec);
            }
            return "unrecognized maintenance type code: " + maintenanceType;
        } catch (ServiceException e) {
            return e.getMessage();
        }
    }

    private String applyAdd(EnrollmentRecord rec, Plan plan) {
        Member member = memberService.findByMemberNumber(rec.getMemberNumber().trim());
        if (member != null) {
            return "member already enrolled: " + rec.getMemberNumber();
        }
        if (rec.getFirstName() == null || rec.getFirstName().trim().isEmpty()) {
            return "first name is missing (no NM1*IL segment)";
        }
        if (rec.getLastName() == null || rec.getLastName().trim().isEmpty()) {
            return "last name is missing (no NM1*IL segment)";
        }
        Date dob = DateUtil.parseFlexible(rec.getDobString());
        if (dob == null) {
            return "date of birth is missing or invalid (no DMG segment)";
        }
        Member created = memberService.createMember(rec.getMemberNumber().trim(), rec.getFirstName().trim(),
            rec.getLastName().trim(), dob, null, null, null);

        if (plan != null) {
            Date effectiveDate = DateUtil.parseFlexible(rec.getEffectiveDateString());
            if (effectiveDate == null) {
                return "coverage effective date is missing or invalid (no DTP*348 segment)";
            }
            memberService.addCoverage(created.getId(), plan.getId(), "PRIMARY",
                effectiveDate, DateUtil.parseFlexible(rec.getTerminationDateString()));
        }
        return null;
    }

    private String applyTermination(EnrollmentRecord rec, Plan plan) {
        Member member = memberService.findByMemberNumber(rec.getMemberNumber().trim());
        if (member == null) {
            return "member not found for termination: " + rec.getMemberNumber();
        }
        Date terminationDate = DateUtil.parseFlexible(rec.getTerminationDateString());
        if (terminationDate == null) {
            return "coverage termination date is missing or invalid (no DTP*349 segment)";
        }

        MemberCoverage target = null;
        for (MemberCoverage c : memberService.getCoverageRecords(member.getId())) {
            if (c.getTerminationDate() != null) {
                continue;
            }
            if (plan != null && c.getPlanId() != plan.getId()) {
                continue;
            }
            target = c;
            break;
        }
        if (target == null) {
            return "no active coverage found to terminate for member: " + rec.getMemberNumber();
        }
        memberService.updateCoverage(target.getId(), target.getPlanId(), target.getCoverageOrder().name(),
            target.getEffectiveDate(), terminationDate);
        return null;
    }

    private String applyChange(EnrollmentRecord rec) {
        Member member = memberService.findByMemberNumber(rec.getMemberNumber().trim());
        if (member == null) {
            return "member not found for change: " + rec.getMemberNumber();
        }
        String firstName = rec.getFirstName() != null && !rec.getFirstName().trim().isEmpty()
            ? rec.getFirstName().trim() : member.getFirstName();
        String lastName = rec.getLastName() != null && !rec.getLastName().trim().isEmpty()
            ? rec.getLastName().trim() : member.getLastName();
        Date dob = DateUtil.parseFlexible(rec.getDobString());
        memberService.updateMember(member.getId(), firstName, lastName,
            dob != null ? dob : member.getDob(), member.getAddress(), member.getPhone(), member.getEmail(), null);
        return null;
    }
}
