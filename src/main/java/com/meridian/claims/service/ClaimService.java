package com.meridian.claims.service;

import com.meridian.claims.dao.AdjudicationResultsDAO;
import com.meridian.claims.dao.ClaimAccumulatorContributionDAO;
import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimDiagnosisDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.ClaimNoteDAO;
import com.meridian.claims.dao.InfoRequestDAO;
import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.dao.PlanDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.AdjudicationResult;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimAccumulatorContribution;
import com.meridian.claims.model.ClaimAuditEntry;
import com.meridian.claims.model.ClaimDiagnosis;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimNote;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.InfoRequest;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.util.ClaimNumberGenerator;
import com.meridian.claims.util.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ClaimService {

    // Legal state-machine transitions: from → set of allowed tos.
    // Authoritative source: PHASES.md → "Claim Status State Machine". This map must match
    // that table exactly (CLAUDE.md: code and docs must never disagree).
    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = new HashMap<String, Set<String>>();
    static {
        LEGAL_TRANSITIONS.put("SUBMITTED",       setOf("DENIED", "APPROVED", "IN_REVIEW"));
        LEGAL_TRANSITIONS.put("IN_REVIEW",        setOf("APPROVED", "DENIED", "PENDING_INFO"));
        LEGAL_TRANSITIONS.put("PENDING_INFO",     setOf("IN_REVIEW", "ABANDONED"));
        LEGAL_TRANSITIONS.put("DENIED",           setOf("IN_REVIEW"));
        LEGAL_TRANSITIONS.put("APPROVED",         setOf("PENDING_PAYMENT"));
        LEGAL_TRANSITIONS.put("PENDING_PAYMENT",  setOf("IN_BATCH"));
        LEGAL_TRANSITIONS.put("IN_BATCH",         setOf("PAID", "PENDING_PAYMENT"));
        LEGAL_TRANSITIONS.put("PAID",             setOf("VOIDED", "REPLACED"));
        // Terminal states — no outbound transitions
        LEGAL_TRANSITIONS.put("VOIDED",           Collections.<String>emptySet());
        LEGAL_TRANSITIONS.put("REPLACED",         Collections.<String>emptySet());
        LEGAL_TRANSITIONS.put("ABANDONED",        Collections.<String>emptySet());
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO lineItemDAO;
    @Autowired private ClaimDiagnosisDAO diagnosisDAO;
    @Autowired private ClaimAuditDAO claimAuditDAO;
    @Autowired private ClaimAccumulatorContributionDAO contributionDAO;
    @Autowired private AdjudicationResultsDAO adjudicationResultsDAO;
    @Autowired private MemberDAO memberDAO;
    @Autowired private ProviderDAO providerDAO;
    @Autowired private PlanDAO planDAO;
    @Autowired private MemberCoverageDAO memberCoverageDAO;
    @Autowired private AdjudicationService adjudicationService;
    @Autowired private VoidReversalService voidReversalService;
    @Autowired private AuditService auditService;
    @Autowired private InfoRequestDAO infoRequestDAO;
    @Autowired private ClaimNoteDAO claimNoteDAO;
    @Autowired private PhiAccessLogService phiAccessLogService;
    @Autowired private EobService eobService;
    @Autowired private PaymentService paymentService;

    @Transactional
    public Claim submit(SubmitClaimRequest req, int createdByUserId) {
        // 1. Basic validation
        if (req.getLineItems() == null || req.getLineItems().isEmpty()) {
            throw new ServiceException("Claim must have at least one line item");
        }
        if (req.getDiagnoses() == null || req.getDiagnoses().isEmpty()) {
            throw new ServiceException("Claim must have at least one diagnosis");
        }
        boolean hasPrimary = false;
        for (SubmitClaimRequest.DiagnosisRow d : req.getDiagnoses()) {
            if ("PRIMARY".equals(d.getDiagnosisType())) {
                hasPrimary = true;
                break;
            }
        }
        if (!hasPrimary) {
            throw new ServiceException("Claim must have a PRIMARY diagnosis");
        }
        for (SubmitClaimRequest.LineItemRow li : req.getLineItems()) {
            if (li.getBilledAmount() == null || li.getBilledAmount().signum() <= 0) {
                throw new ServiceException("Each line item must have a positive billed amount");
            }
        }
        if ("SECONDARY".equals(req.getCoverageOrder()) && req.getCobPrimaryPaid() == null) {
            throw new ServiceException("cob_primary_paid is required for SECONDARY claims");
        }

        // 2. Load domain entities
        Member member = memberDAO.findById(req.getMemberId());
        if (member == null) {
            throw new ServiceException("Member not found: id=" + req.getMemberId());
        }
        Provider provider = providerDAO.findById(req.getProviderId());
        if (provider == null) {
            throw new ServiceException("Provider not found: id=" + req.getProviderId());
        }

        // 3. Resolve active coverage for DOS
        MemberCoverage activeCoverage = resolveActiveCoverage(
            req.getMemberId(), req.getCoverageOrder(), req.getDateOfService());
        if (activeCoverage == null) {
            throw new ServiceException("No active " + req.getCoverageOrder() +
                " coverage found for member id=" + req.getMemberId() + " on " + req.getDateOfService());
        }

        // 4. Snapshot plan
        Plan plan = planDAO.findById(activeCoverage.getPlanId());
        if (plan == null) {
            throw new ServiceException("Plan not found: id=" + activeCoverage.getPlanId());
        }

        // 5. For CORRECTED/VOID: verify original claim
        if (req.getClaimType() == ClaimType.CORRECTED || req.getClaimType() == ClaimType.VOID) {
            if (req.getOriginalClaimId() == null) {
                throw new ServiceException("original_claim_id is required for " + req.getClaimType() + " claims");
            }
            Claim original = claimDAO.findById(req.getOriginalClaimId());
            if (original == null) {
                throw new ServiceException("Original claim not found: id=" + req.getOriginalClaimId());
            }
            if (original.getStatus() != ClaimStatus.PAID) {
                throw new ServiceException("Original claim must be in PAID status to submit a " +
                    req.getClaimType() + " claim; current status=" + original.getStatus());
            }
        }

        // 6. Build and insert claim
        Date today = new Date();
        Claim claim = new Claim();
        claim.setClaimNumber(ClaimNumberGenerator.generate(today));
        claim.setMemberId(req.getMemberId());
        claim.setProviderId(req.getProviderId());
        claim.setClaimType(req.getClaimType());
        claim.setOriginalClaimId(req.getOriginalClaimId());
        claim.setDateOfService(req.getDateOfService());
        claim.setSubmissionDate(today);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setPlanId(activeCoverage.getPlanId());
        claim.setCoverageOrder(req.getCoverageOrder());
        claim.setPriorAuthNumber(req.getPriorAuthNumber());
        claim.setReferralNumber(req.getReferralNumber());
        claim.setCobPrimaryPaid(req.getCobPrimaryPaid());
        claim.setAccidentIndicator(req.isAccidentIndicator());
        claim.setAccidentType(req.getAccidentType());
        claim.setAccidentDate(req.getAccidentDate());
        claim.setNotes(req.getNotes());
        claim.setExternalReference(req.getExternalReference());
        claim.setCreatedByUserId(createdByUserId);
        // Retry on claim_number collision (same-millisecond submissions).
        // ClaimNumberGenerator uses a per-process counter to make collisions rare;
        // three attempts covers any residual race.
        boolean inserted = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                claim.setClaimNumber(ClaimNumberGenerator.generate(today));
            }
            try {
                claimDAO.insert(claim);
                inserted = true;
                break;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new ServiceException("Could not generate a unique claim number after 3 attempts; please resubmit");
                }
            }
        }
        if (!inserted) {
            throw new ServiceException("Claim insert failed unexpectedly");
        }

        // 7. Insert line items
        List<ClaimLineItem> lineItems = buildLineItems(claim.getId(), req.getLineItems());
        lineItemDAO.insertBatch(lineItems);

        // 8. Insert diagnoses
        List<ClaimDiagnosis> diagnoses = buildDiagnoses(claim.getId(), req.getDiagnoses());
        diagnosisDAO.insertBatch(diagnoses);

        // 9. Branch on claim type
        if (req.getClaimType() == ClaimType.VOID) {
            // VOID bypasses the adjudication engine entirely
            Claim original = claimDAO.findById(req.getOriginalClaimId());
            voidReversalService.reverse(
                req.getOriginalClaimId(),
                original.getMemberId(),
                original.getPlanId(),
                plan.getBenefitYearStart());
            // The ORIGINAL claim's PAID → VOIDED transition is a legal state-machine move.
            assertLegalTransition(original.getStatus().name(), "VOIDED");
            claimDAO.updateStatus(original.getId(), ClaimStatus.VOIDED.name(), original.getVersion());
            // The NEW VOID claim record is a reversal instruction, created already-terminal (VOIDED).
            // It is NOT routed through assertLegalTransition for its own status (SUBMITTED → VOIDED is
            // not a state-machine transition; see PHASES.md "Claim Status State Machine" note).
            claimDAO.updateStatus(claim.getId(), ClaimStatus.VOIDED.name(), claim.getVersion());
            writeClaimAudit(claim.getId(), "VOID_SUBMITTED", ClaimStatus.SUBMITTED.name(),
                ClaimStatus.VOIDED.name(), createdByUserId, "VOID claim processed");
            auditService.record("VOID_SUBMITTED", "CLAIM", (long) claim.getId(),
                "VOID of claim id=" + req.getOriginalClaimId());
            claim.setStatus(ClaimStatus.VOIDED);
            return claim;
        }

        // ORIGINAL or CORRECTED — run adjudication.
        // Note: CORRECTED claim approval does not yet reverse the original claim's
        // accumulators or transition the original to REPLACED; deferred to a future phase.
        ClaimStatus disposition = adjudicationService.adjudicate(
            claim, lineItems, diagnoses, plan, member, provider, activeCoverage,
            1 /* initial run_id */);

        assertLegalTransition(ClaimStatus.SUBMITTED.name(), disposition.name());
        claimDAO.updateStatus(claim.getId(), disposition.name(), claim.getVersion());
        claim.setStatus(disposition);

        writeClaimAudit(claim.getId(), "CLAIM_SUBMITTED", ClaimStatus.SUBMITTED.name(),
            disposition.name(), createdByUserId, null);
        auditService.record("CLAIM_SUBMITTED", "CLAIM", (long) claim.getId(),
            "Claim submitted; disposition=" + disposition);

        return claim;
    }

    @Transactional
    public Claim reAdjudicate(int claimId, int userId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null) {
            throw new ServiceException("Claim not found: id=" + claimId);
        }
        if (claim.getStatus() != ClaimStatus.DENIED && claim.getStatus() != ClaimStatus.IN_REVIEW) {
            throw new ServiceException("Re-adjudication is only allowed for DENIED or IN_REVIEW claims; " +
                "current status=" + claim.getStatus());
        }

        // Determine next run_id BEFORE touching prior results — each re-adjudication appends
        // a new run rather than overwriting history (the schema and view JSP support multiple runs).
        int runId = adjudicationResultsDAO.maxRunId(claimId) + 1;

        // Reverse prior accumulator contribution if present and not already reversed
        reverseContributionIfPresent(claim);

        // Reload all needed entities (validate each — matches the null-guards in submit())
        Member member = memberDAO.findById(claim.getMemberId());
        if (member == null) {
            throw new ServiceException("Member not found for re-adjudication: id=" + claim.getMemberId());
        }
        Provider provider = providerDAO.findById(claim.getProviderId());
        if (provider == null) {
            throw new ServiceException("Provider not found for re-adjudication: id=" + claim.getProviderId());
        }
        Plan plan = planDAO.findById(claim.getPlanId());
        if (plan == null) {
            throw new ServiceException("Plan not found for re-adjudication: id=" + claim.getPlanId());
        }
        MemberCoverage activeCoverage = resolveActiveCoverage(
            claim.getMemberId(), claim.getCoverageOrder(), claim.getDateOfService());
        if (activeCoverage == null) {
            throw new ServiceException("No active " + claim.getCoverageOrder() +
                " coverage found for re-adjudication of claim id=" + claimId);
        }
        List<ClaimLineItem> lineItems = lineItemDAO.findByClaimId(claimId);
        List<ClaimDiagnosis> diagnoses = diagnosisDAO.findByClaimId(claimId);

        ClaimStatus disposition = adjudicationService.adjudicate(
            claim, lineItems, diagnoses, plan, member, provider, activeCoverage, runId);

        // Per PHASES.md state machine, DENIED may only transition to IN_REVIEW. The engine
        // can return any disposition (APPROVED/DENIED/IN_REVIEW), so coerce it here so a
        // reviewer makes the final call rather than crashing the re-adjudication.
        if (claim.getStatus() == ClaimStatus.DENIED) {
            disposition = ClaimStatus.IN_REVIEW;
        }

        assertLegalTransition(claim.getStatus().name(), disposition.name());
        claimDAO.updateStatus(claimId, disposition.name(), claim.getVersion());
        claim.setStatus(disposition);

        writeClaimAudit(claimId, "RE_ADJUDICATED", null, disposition.name(), userId, null);
        auditService.record("RE_ADJUDICATED", "CLAIM", (long) claimId, "Re-adjudicated; disposition=" + disposition);

        return claimDAO.findById(claimId);
    }

    // -------------------------------------------------------------------------
    // Phase 5 — Reviewer workflow
    // -------------------------------------------------------------------------

    @Transactional
    public void assignClaim(int claimId, Integer reviewerId, int actingUserId) {
        Claim claim = requireClaim(claimId);
        claimDAO.updateAssignment(claimId, reviewerId, claim.getVersion());
        String detail = reviewerId == null ? "unassigned" : "assigned to user id=" + reviewerId;
        writeClaimAudit(claimId, "CLAIM_ASSIGNED", null, null, actingUserId, detail);
        auditService.record("CLAIM_ASSIGNED", "CLAIM", (long) claimId, detail);
    }

    @Transactional
    public void approve(int claimId, int userId, String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            throw new ServiceException("Approval notes are required");
        }
        Claim claim = requireClaim(claimId);
        assertLegalTransition(claim.getStatus().name(), ClaimStatus.APPROVED.name());
        claimDAO.updateStatus(claimId, ClaimStatus.APPROVED.name(), claim.getVersion());
        writeClaimAudit(claimId, "APPROVED", claim.getStatus().name(), ClaimStatus.APPROVED.name(), userId, notes);
        auditService.record("APPROVED", "CLAIM", (long) claimId, notes);
        phiAccessLogService.record(claim.getMemberId(), claimId, "APPROVED");
        eobService.generate(claimId);
        paymentService.generatePayment(claimId);
    }

    @Transactional
    public void deny(int claimId, int userId, String denialReasonCode, String notes) {
        if (denialReasonCode == null || denialReasonCode.trim().isEmpty()) {
            throw new ServiceException("Denial reason code is required");
        }
        if (notes == null || notes.trim().isEmpty()) {
            throw new ServiceException("Denial notes are required");
        }
        Claim claim = requireClaim(claimId);
        assertLegalTransition(claim.getStatus().name(), ClaimStatus.DENIED.name());
        // A claim routed to IN_REVIEW during adjudication already contributed to the member's
        // deductible/OOP accumulators. Denying it means the member paid nothing, so reverse that
        // contribution — otherwise the accumulators stay inflated and the plan over-pays on the
        // member's later claims. Mirrors the reversal on VOID and re-adjudication.
        reverseContributionIfPresent(claim);
        // Update status + denial reason in one shot. The claim's own notes are preserved; the
        // denial notes live in the audit trail and EOB, not on top of the submission notes.
        Claim updated = new Claim();
        updated.setId(claimId);
        updated.setStatus(ClaimStatus.DENIED);
        updated.setDenialReasonCode(denialReasonCode);
        updated.setNotes(claim.getNotes());
        updated.setAssignedToUserId(claim.getAssignedToUserId());
        updated.setVersion(claim.getVersion());
        claimDAO.update(updated);
        writeClaimAudit(claimId, "DENIED", claim.getStatus().name(), ClaimStatus.DENIED.name(), userId, notes);
        auditService.record("DENIED", "CLAIM", (long) claimId, "reason=" + denialReasonCode + " " + notes);
        phiAccessLogService.record(claim.getMemberId(), claimId, "DENIED");
        eobService.generate(claimId);
    }

    @Transactional
    public InfoRequest requestInfo(int claimId, int userId, String requestedFrom,
                                   Date dueDate, String requestNotes) {
        if (requestedFrom == null || requestedFrom.trim().isEmpty()) {
            throw new ServiceException("requestedFrom is required (MEMBER/PROVIDER/BOTH)");
        }
        if (dueDate == null) {
            throw new ServiceException("Due date is required for info request");
        }
        if (requestNotes == null || requestNotes.trim().isEmpty()) {
            throw new ServiceException("Request notes are required — free-text only is not sufficient");
        }
        Claim claim = requireClaim(claimId);
        assertLegalTransition(claim.getStatus().name(), ClaimStatus.PENDING_INFO.name());

        InfoRequest ir = new InfoRequest();
        ir.setClaimId(claimId);
        ir.setRequestedFrom(requestedFrom);
        ir.setRequestedByUserId(userId);
        ir.setDueDate(dueDate);
        ir.setRequestNotes(requestNotes);
        infoRequestDAO.insert(ir);

        claimDAO.updateStatus(claimId, ClaimStatus.PENDING_INFO.name(), claim.getVersion());
        writeClaimAudit(claimId, "INFO_REQUESTED", claim.getStatus().name(),
            ClaimStatus.PENDING_INFO.name(), userId, requestNotes);
        auditService.record("INFO_REQUESTED", "CLAIM", (long) claimId,
            "from=" + requestedFrom + " due=" + dueDate);
        phiAccessLogService.record(claim.getMemberId(), claimId, "INFO_REQUESTED");
        return ir;
    }

    @Transactional
    public void respondInfo(int infoRequestId, int userId, String responseNotes) {
        if (responseNotes == null || responseNotes.trim().isEmpty()) {
            throw new ServiceException("Response notes are required");
        }
        InfoRequest ir = infoRequestDAO.findById(infoRequestId);
        if (ir == null) {
            throw new ServiceException("Info request not found: id=" + infoRequestId);
        }
        infoRequestDAO.markResponded(infoRequestId, responseNotes);
        writeClaimAudit(ir.getClaimId(), "INFO_RESPONDED", null, null, userId, responseNotes);
        auditService.record("INFO_RESPONDED", "CLAIM", (long) ir.getClaimId(), responseNotes);
    }

    @Transactional
    public void resubmit(int claimId, int userId) {
        Claim claim = requireClaim(claimId);
        assertLegalTransition(claim.getStatus().name(), ClaimStatus.IN_REVIEW.name());
        InfoRequest open = infoRequestDAO.findOpenByClaimId(claimId);
        if (open != null) {
            throw new ServiceException(
                "Cannot resubmit: open info request (id=" + open.getId() + ") must be RESPONDED or WAIVED first");
        }
        claimDAO.updateStatus(claimId, ClaimStatus.IN_REVIEW.name(), claim.getVersion());
        writeClaimAudit(claimId, "RESUBMITTED", ClaimStatus.PENDING_INFO.name(),
            ClaimStatus.IN_REVIEW.name(), userId, null);
        auditService.record("RESUBMITTED", "CLAIM", (long) claimId, "moved to IN_REVIEW");
    }

    @Transactional
    public ClaimNote addNote(int claimId, int userId, String noteText) {
        if (noteText == null || noteText.trim().isEmpty()) {
            throw new ServiceException("Note text is required");
        }
        requireClaim(claimId);
        ClaimNote note = new ClaimNote();
        note.setClaimId(claimId);
        note.setAuthorUserId(userId);
        note.setNote(noteText.trim());
        claimNoteDAO.insert(note);
        return note;
    }

    /**
     * System-initiated abandonment of a stale PENDING_INFO claim (StaleClaimJob).
     * Reverses any accumulator contribution the claim made during adjudication — an abandoned
     * claim pays nothing, so it must not leave the member's deductible/OOP inflated — then
     * transitions the claim to ABANDONED and audits it. No acting user: this is a system action.
     */
    @Transactional
    public void markAbandoned(int claimId, String reason) {
        Claim claim = requireClaim(claimId);
        assertLegalTransition(claim.getStatus().name(), ClaimStatus.ABANDONED.name());
        reverseContributionIfPresent(claim);
        claimDAO.updateStatus(claimId, ClaimStatus.ABANDONED.name(), claim.getVersion());
        ClaimAuditEntry audit = new ClaimAuditEntry();
        audit.setClaimId(claimId);
        audit.setEventType("ABANDONED");
        audit.setOldStatus(claim.getStatus().name());
        audit.setNewStatus(ClaimStatus.ABANDONED.name());
        audit.setNotes(reason);
        claimAuditDAO.insert(audit);
        auditService.record("ABANDONED", "CLAIM", (long) claimId, reason);
    }

    public List<InfoRequest> findInfoRequests(int claimId) {
        return infoRequestDAO.findByClaimId(claimId);
    }

    public List<ClaimNote> findNotes(int claimId) {
        return claimNoteDAO.findByClaimId(claimId);
    }

    public Page<Claim> searchWorklist(String query, String status, Integer assignedToUserId,
                                      boolean unassignedOnly, boolean slaBreachedOnly,
                                      Integer scopeToUserId, int page, int size) {
        return claimDAO.searchWorklist(query, status, assignedToUserId,
            unassignedOnly, slaBreachedOnly, scopeToUserId, page, size);
    }

    /**
     * Returns true if the given user is allowed to view a specific claim.
     * REVIEWER and ADMIN see any claim. STAFF / FINANCE / ANALYST see only
     * claims they submitted or are assigned to (HIPAA minimum-necessary access).
     */
    public boolean canViewClaim(Claim claim, User user) {
        if (user == null) return false;
        UserRole role = user.getRole();
        if (role == UserRole.REVIEWER || role == UserRole.ADMIN) {
            return true;
        }
        int uid = user.getId();
        return (claim.getCreatedByUserId() != null && claim.getCreatedByUserId() == uid)
                || (claim.getAssignedToUserId() != null && claim.getAssignedToUserId() == uid);
    }

    // -------------------------------------------------------------------------

    public Claim findById(int id) {
        return claimDAO.findById(id);
    }

    public Page<Claim> search(String query, String status, int page, int size) {
        return claimDAO.search(query, status, page, size);
    }

    public List<ClaimLineItem> findLineItems(int claimId) {
        return lineItemDAO.findByClaimId(claimId);
    }

    public List<ClaimDiagnosis> findDiagnoses(int claimId) {
        return diagnosisDAO.findByClaimId(claimId);
    }

    public List<AdjudicationResult> findAdjudicationResults(int claimId) {
        return adjudicationResultsDAO.findByClaimId(claimId);
    }

    public List<ClaimAuditEntry> findAuditTrail(int claimId) {
        return claimAuditDAO.findByClaimId(claimId);
    }

    // -------------------------------------------------------------------------

    private Claim requireClaim(int claimId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null) {
            throw new ServiceException("Claim not found: id=" + claimId);
        }
        return claim;
    }

    /**
     * Reverses the claim's deductible/OOP accumulator contribution if one exists and has not
     * already been reversed. A no-op for claims that never reached the soft-rule stage (hard
     * denials never write a contribution). Shared by deny(), reAdjudicate() and markAbandoned()
     * so every exit that leaves a claim unpaid restores the member's accumulators.
     */
    private void reverseContributionIfPresent(Claim claim) {
        ClaimAccumulatorContribution prior = contributionDAO.findByClaimId(claim.getId());
        if (prior != null && !prior.isReversed()) {
            Plan plan = planDAO.findById(claim.getPlanId());
            if (plan == null) {
                throw new ServiceException("Plan not found for accumulator reversal: id=" + claim.getPlanId());
            }
            voidReversalService.reverse(claim.getId(), claim.getMemberId(), claim.getPlanId(),
                plan.getBenefitYearStart());
        }
    }

    void assertLegalTransition(String fromStatus, String toStatus) {
        Set<String> allowed = LEGAL_TRANSITIONS.get(fromStatus);
        if (allowed == null) {
            throw new ServiceException("Unknown source status: " + fromStatus);
        }
        if (!allowed.contains(toStatus)) {
            throw new ServiceException("Illegal claim status transition: " + fromStatus + " → " + toStatus);
        }
    }

    private MemberCoverage resolveActiveCoverage(int memberId, String coverageOrder, Date dos) {
        List<MemberCoverage> coverages = memberCoverageDAO.findActiveByMemberId(memberId);
        for (MemberCoverage cov : coverages) {
            if (coverageOrder.equals(cov.getCoverageOrder().name()) && cov.isActiveOn(dos)) {
                return cov;
            }
        }
        return null;
    }

    private List<ClaimLineItem> buildLineItems(int claimId, List<SubmitClaimRequest.LineItemRow> rows) {
        java.util.List<ClaimLineItem> items = new java.util.ArrayList<ClaimLineItem>();
        for (SubmitClaimRequest.LineItemRow row : rows) {
            ClaimLineItem li = new ClaimLineItem();
            li.setClaimId(claimId);
            li.setProcedureCode(row.getProcedureCode());
            li.setDescription(row.getDescription());
            li.setBilledAmount(row.getBilledAmount());
            items.add(li);
        }
        return items;
    }

    private List<ClaimDiagnosis> buildDiagnoses(int claimId, List<SubmitClaimRequest.DiagnosisRow> rows) {
        java.util.List<ClaimDiagnosis> result = new java.util.ArrayList<ClaimDiagnosis>();
        for (int i = 0; i < rows.size(); i++) {
            SubmitClaimRequest.DiagnosisRow row = rows.get(i);
            ClaimDiagnosis d = new ClaimDiagnosis();
            d.setClaimId(claimId);
            d.setDiagnosisCode(row.getDiagnosisCode());
            d.setSequenceNumber(i + 1);
            d.setDiagnosisType(row.getDiagnosisType() != null ? row.getDiagnosisType() : "SECONDARY");
            result.add(d);
        }
        return result;
    }

    private void writeClaimAudit(int claimId, String eventType, String oldStatus, String newStatus,
                                  int userId, String notes) {
        ClaimAuditEntry entry = new ClaimAuditEntry();
        entry.setClaimId(claimId);
        entry.setEventType(eventType);
        entry.setOldStatus(oldStatus);
        entry.setNewStatus(newStatus);
        entry.setChangedByUserId(userId);
        entry.setNotes(notes);
        claimAuditDAO.insert(entry);
    }
}
