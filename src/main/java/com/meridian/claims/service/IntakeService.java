package com.meridian.claims.service;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.dao.ClaimIntakeBatchDAO;
import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.intake.ClaimFileParseResult;
import com.meridian.claims.intake.ClaimFileParser;
import com.meridian.claims.intake.IntakeParseException;
import com.meridian.claims.model.ClaimIntakeBatch;
import com.meridian.claims.model.User;
import com.meridian.claims.util.LogMaskUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates inbound claim file processing for the batch intake pipeline.
 *
 * Responsibilities:
 *   1. File-level idempotency — SHA-256 hash keyed in claim_intake_batches;
 *      re-submitting the same file is a no-op.
 *   2. Parsing via the injected ClaimFileParser (FHIR R4 in Phase 9).
 *   3. Per-record fault isolation — one bad record is quarantined; the rest proceed.
 *   4. Submitting clean records through the existing ClaimService.submit pipeline.
 *   5. Updating the ledger row to COMPLETED / FAILED with counts.
 */
@Service
public class IntakeService {

    private static final Logger LOG = Logger.getLogger(IntakeService.class);

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED  = "COMPLETED";
    private static final String STATUS_FAILED     = "FAILED";

    /** Cap on the error_message ledger field (TEXT column; this is a sanity bound). */
    private static final int MAX_ERROR_MESSAGE_LEN = 2000;

    private final ClaimIntakeBatchDAO batchDAO;
    private final ClaimFileParser claimFileParser;
    private final ClaimService claimService;
    private final AuditService auditService;
    private final UserDAO userDAO;

    @Value("${claims.intake.system-user-id:0}")
    private int configuredSystemUserId;

    private volatile int resolvedSystemUserId = -1;

    @Autowired
    public IntakeService(ClaimIntakeBatchDAO batchDAO,
                         ClaimFileParser claimFileParser,
                         ClaimService claimService,
                         AuditService auditService,
                         UserDAO userDAO) {
        this.batchDAO = batchDAO;
        this.claimFileParser = claimFileParser;
        this.claimService = claimService;
        this.auditService = auditService;
        this.userDAO = userDAO;
    }

    /**
     * Return the most recent intake batch ledger rows, newest first.
     * Used by IntakeBatchController to populate the admin batch list.
     *
     * @param limit maximum number of rows to return (caller is responsible for clamping to a sane range)
     */
    public List<ClaimIntakeBatch> findRecentBatches(int limit) {
        return batchDAO.findRecent(limit);
    }

    /**
     * Process a single inbound claim file using the injected default parser.
     * Delegates to {@link #processFile(String, String, ClaimFileParser)}.
     *
     * @param fileName    the original file name (for logging and the ledger row)
     * @param fileContent full UTF-8 text content of the file
     * @return a summary of the processing run
     */
    public IntakeSummary processFile(String fileName, String fileContent) {
        return processFile(fileName, fileContent, claimFileParser);
    }

    /**
     * Process a single inbound claim file using the supplied parser.
     * The poller calls this overload to pass the format-specific parser
     * (FHIR or X12 EDI 837) selected per file extension.
     *
     * @param fileName    the original file name (for logging and the ledger row)
     * @param fileContent full UTF-8 text content of the file
     * @param parser      the parser to use for this file
     * @return a summary of the processing run
     */
    public IntakeSummary processFile(String fileName, String fileContent, ClaimFileParser parser) {
        String fileHash = sha256(fileContent);

        // Idempotency check — only a terminal (COMPLETED/FAILED) row is a true no-op.
        // A stale PROCESSING row means a prior run died mid-file; re-process it.
        ClaimIntakeBatch existing = batchDAO.findByFileHash(fileHash);
        if (existing != null && !STATUS_PROCESSING.equals(existing.getStatus())) {
            LOG.info("IntakeService: file already processed (hash=" + fileHash
                + " status=" + existing.getStatus() + ") — skipping fileName=" + fileName);
            return IntakeSummary.duplicate(fileName, existing.getSucceeded(), existing.getQuarantined());
        }

        // Insert (or reuse) the ledger row.
        int batchId;
        if (existing != null) {
            batchId = existing.getId();
            LOG.warn("IntakeService: reclaiming stale PROCESSING batch id=" + batchId
                + " for fileName=" + fileName);
        } else {
            ClaimIntakeBatch batch = new ClaimIntakeBatch();
            batch.setFileName(fileName);
            batch.setFileHash(fileHash);
            batch.setStatus(STATUS_PROCESSING);
            batchId = batchDAO.insert(batch);
        }

        int systemUserId = resolveSystemUserId();

        // Parse. A file-level failure (bad JSON / wrong resource type) marks the whole
        // batch FAILED and signals the caller to route the file to rejected/.
        ClaimFileParseResult parseResult;
        try {
            parseResult = parser.parse(fileContent);
        } catch (IntakeParseException e) {
            String msg = mask("File-level parse failure: " + e.getMessage());
            LOG.error("IntakeService: " + msg + " fileName=" + fileName);
            batchDAO.updateCompletion(batchId, STATUS_FAILED, 0, 0, 0, msg);
            auditService.record("INTAKE_FILE_FAILED", "ClaimIntakeBatch", (long) batchId, msg);
            return IntakeSummary.fileLevelFailure(fileName, msg);
        }

        int succeeded = 0;
        int quarantined = 0;
        List<String> quarantineReasons = new ArrayList<String>();

        // Per-record parse failures (bad data within an otherwise valid file) are
        // quarantined individually — they do not abort the file.
        for (ClaimFileParseResult.RecordError pe : parseResult.getRecordErrors()) {
            quarantined++;
            recordQuarantine(batchId, fileName, "Record " + pe.getRecordIndex() + ": " + pe.getReason(),
                quarantineReasons);
        }

        // Submit each successfully-parsed claim; a submission failure quarantines
        // that record without affecting the others.
        List<SubmitClaimRequest> claims = parseResult.getClaims();
        for (int i = 0; i < claims.size(); i++) {
            try {
                claimService.submit(claims.get(i), systemUserId);
                succeeded++;
            } catch (Exception e) {
                quarantined++;
                recordQuarantine(batchId, fileName, "Claim " + (i + 1) + ": " + e.getMessage(),
                    quarantineReasons);
            }
        }

        int total = parseResult.totalRecords();
        // The whole file "failed" (route to rejected/) when nothing was accepted.
        boolean nothingSucceeded = succeeded == 0 && total > 0;
        String finalStatus = nothingSucceeded ? STATUS_FAILED : STATUS_COMPLETED;
        batchDAO.updateCompletion(batchId, finalStatus, total, succeeded, quarantined,
            joinReasons(quarantineReasons));

        LOG.info("IntakeService: fileName=" + fileName + " batchId=" + batchId
            + " total=" + total + " succeeded=" + succeeded
            + " quarantined=" + quarantined + " status=" + finalStatus);

        return new IntakeSummary(fileName, false, finalStatus.equals(STATUS_FAILED) && nothingSucceeded,
            succeeded, quarantined, null);
    }

    private void recordQuarantine(int batchId, String fileName, String rawReason,
                                  List<String> reasons) {
        String reason = mask(rawReason);
        reasons.add(reason);
        LOG.warn("IntakeService: quarantined " + reason + " fileName=" + fileName);
        auditService.record("INTAKE_RECORD_QUARANTINED", "ClaimIntakeBatch", (long) batchId, reason);
    }

    /** Mask PHI (DOBs and member numbers) in operator-facing strings before they hit logs/audit. */
    private String mask(String message) {
        return LogMaskUtil.maskMemberNumber(LogMaskUtil.maskDobsInMessage(message));
    }

    private String joinReasons(List<String> reasons) {
        if (reasons.isEmpty()) return null;
        String joined = String.join("; ", reasons);
        return joined.length() <= MAX_ERROR_MESSAGE_LEN
            ? joined : joined.substring(0, MAX_ERROR_MESSAGE_LEN);
    }

    /** Resolve the system user id to attribute batch-submitted claims. */
    private int resolveSystemUserId() {
        if (resolvedSystemUserId > 0) return resolvedSystemUserId;
        if (configuredSystemUserId > 0) {
            resolvedSystemUserId = configuredSystemUserId;
            return resolvedSystemUserId;
        }
        // Fall back to looking up by username
        User system = userDAO.findByUsername("system");
        if (system != null) {
            resolvedSystemUserId = system.getId();
            LOG.info("IntakeService: resolved system user id=" + resolvedSystemUserId);
            return resolvedSystemUserId;
        }
        LOG.warn("IntakeService: 'system' user not found — claims will have no creator");
        return 0;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Summary returned by processFile(). */
    public static class IntakeSummary {
        private final String fileName;
        private final boolean duplicate;
        private final boolean reject;
        private final int succeeded;
        private final int quarantined;
        private final String errorMessage;

        public IntakeSummary(String fileName, boolean duplicate, boolean reject,
                             int succeeded, int quarantined, String errorMessage) {
            this.fileName = fileName;
            this.duplicate = duplicate;
            this.reject = reject;
            this.succeeded = succeeded;
            this.quarantined = quarantined;
            this.errorMessage = errorMessage;
        }

        /** Already-processed file — caller archives it (no re-processing). */
        public static IntakeSummary duplicate(String fileName, int succeeded, int quarantined) {
            return new IntakeSummary(fileName, true, false, succeeded, quarantined, null);
        }

        /** File could not be parsed at all — caller routes it to rejected/. */
        public static IntakeSummary fileLevelFailure(String fileName, String errorMessage) {
            return new IntakeSummary(fileName, false, true, 0, 0, errorMessage);
        }

        public String getFileName() { return fileName; }
        public boolean isDuplicate() { return duplicate; }
        /** True if the file should be moved to rejected/ rather than archive/. */
        public boolean shouldReject() { return reject; }
        public int getSucceeded() { return succeeded; }
        public int getQuarantined() { return quarantined; }
        public String getErrorMessage() { return errorMessage; }
    }
}
