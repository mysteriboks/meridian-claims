package com.meridian.claims.service;

import com.meridian.claims.controller.SubmitClaimRequest;
import com.meridian.claims.dao.AdjudicationResultsDAO;
import com.meridian.claims.dao.ClaimAccumulatorContributionDAO;
import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimDiagnosisDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.ClaimNoteDAO;
import com.meridian.claims.dao.InfoRequestDAO;
import com.meridian.claims.service.EobService;
import com.meridian.claims.service.PaymentService;
import com.meridian.claims.dao.MemberCoverageDAO;
import com.meridian.claims.dao.MemberDAO;
import com.meridian.claims.dao.PlanDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.InfoRequest;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.ClaimNumberGenerator;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClaimServiceTest {

    private ClaimService claimService;
    private ClaimDAO claimDAO;
    private ClaimLineItemDAO lineItemDAO;
    private ClaimDiagnosisDAO diagnosisDAO;
    private ClaimAuditDAO claimAuditDAO;
    private ClaimAccumulatorContributionDAO contributionDAO;
    private AdjudicationResultsDAO adjResultsDAO;
    private MemberDAO memberDAO;
    private ProviderDAO providerDAO;
    private PlanDAO planDAO;
    private MemberCoverageDAO memberCoverageDAO;
    private AdjudicationService adjudicationService;
    private VoidReversalService voidReversalService;
    private AuditService auditService;
    private InfoRequestDAO infoRequestDAO;
    private ClaimNoteDAO claimNoteDAO;
    private PhiAccessLogService phiAccessLogService;
    private EobService eobService;
    private PaymentService paymentService;

    @Before
    public void setUp() {
        claimService = new ClaimService();
        claimDAO = Mockito.mock(ClaimDAO.class);
        lineItemDAO = Mockito.mock(ClaimLineItemDAO.class);
        diagnosisDAO = Mockito.mock(ClaimDiagnosisDAO.class);
        claimAuditDAO = Mockito.mock(ClaimAuditDAO.class);
        contributionDAO = Mockito.mock(ClaimAccumulatorContributionDAO.class);
        adjResultsDAO = Mockito.mock(AdjudicationResultsDAO.class);
        memberDAO = Mockito.mock(MemberDAO.class);
        providerDAO = Mockito.mock(ProviderDAO.class);
        planDAO = Mockito.mock(PlanDAO.class);
        memberCoverageDAO = Mockito.mock(MemberCoverageDAO.class);
        adjudicationService = Mockito.mock(AdjudicationService.class);
        voidReversalService = Mockito.mock(VoidReversalService.class);
        auditService = Mockito.mock(AuditService.class);
        infoRequestDAO = Mockito.mock(InfoRequestDAO.class);
        claimNoteDAO = Mockito.mock(ClaimNoteDAO.class);
        phiAccessLogService = Mockito.mock(PhiAccessLogService.class);
        eobService = Mockito.mock(EobService.class);
        paymentService = Mockito.mock(PaymentService.class);

        ReflectionTestUtils.setField(claimService, "claimDAO", claimDAO);
        ReflectionTestUtils.setField(claimService, "lineItemDAO", lineItemDAO);
        ReflectionTestUtils.setField(claimService, "diagnosisDAO", diagnosisDAO);
        ReflectionTestUtils.setField(claimService, "claimAuditDAO", claimAuditDAO);
        ReflectionTestUtils.setField(claimService, "contributionDAO", contributionDAO);
        ReflectionTestUtils.setField(claimService, "adjudicationResultsDAO", adjResultsDAO);
        ReflectionTestUtils.setField(claimService, "memberDAO", memberDAO);
        ReflectionTestUtils.setField(claimService, "providerDAO", providerDAO);
        ReflectionTestUtils.setField(claimService, "planDAO", planDAO);
        ReflectionTestUtils.setField(claimService, "memberCoverageDAO", memberCoverageDAO);
        ReflectionTestUtils.setField(claimService, "adjudicationService", adjudicationService);
        ReflectionTestUtils.setField(claimService, "voidReversalService", voidReversalService);
        ReflectionTestUtils.setField(claimService, "auditService", auditService);
        ReflectionTestUtils.setField(claimService, "infoRequestDAO", infoRequestDAO);
        ReflectionTestUtils.setField(claimService, "claimNoteDAO", claimNoteDAO);
        ReflectionTestUtils.setField(claimService, "phiAccessLogService", phiAccessLogService);
        ReflectionTestUtils.setField(claimService, "eobService", eobService);
        ReflectionTestUtils.setField(claimService, "paymentService", paymentService);
    }

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    // -------------------------------------------------------------------------
    // State-machine guard
    // -------------------------------------------------------------------------

    @Test
    public void assertLegalTransition_pass() {
        claimService.assertLegalTransition("SUBMITTED", "APPROVED");
        claimService.assertLegalTransition("SUBMITTED", "DENIED");
        claimService.assertLegalTransition("PAID", "VOIDED");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_illegalThrows() {
        claimService.assertLegalTransition("DENIED", "PAID");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_unknownSourceThrows() {
        claimService.assertLegalTransition("BOGUS", "APPROVED");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_terminalStateThrows() {
        claimService.assertLegalTransition("VOIDED", "SUBMITTED");
    }

    @Test
    public void assertLegalTransition_specTransitions_allLegal() {
        // Every transition in PHASES.md "Claim Status State Machine" must be legal.
        claimService.assertLegalTransition("SUBMITTED", "IN_REVIEW");
        claimService.assertLegalTransition("IN_REVIEW", "APPROVED");
        claimService.assertLegalTransition("IN_REVIEW", "DENIED");
        claimService.assertLegalTransition("IN_REVIEW", "PENDING_INFO");
        claimService.assertLegalTransition("PENDING_INFO", "IN_REVIEW");
        claimService.assertLegalTransition("PENDING_INFO", "ABANDONED");
        claimService.assertLegalTransition("DENIED", "IN_REVIEW");
        claimService.assertLegalTransition("APPROVED", "PENDING_PAYMENT");
        claimService.assertLegalTransition("PENDING_PAYMENT", "IN_BATCH");
        claimService.assertLegalTransition("IN_BATCH", "PAID");
        claimService.assertLegalTransition("IN_BATCH", "PENDING_PAYMENT");
        claimService.assertLegalTransition("PAID", "REPLACED");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_approvedToDenied_removed() {
        // Not in the spec — must now be rejected.
        claimService.assertLegalTransition("APPROVED", "DENIED");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_pendingPaymentToPaid_removed() {
        // Direct PENDING_PAYMENT → PAID is not in the spec (must go via IN_BATCH).
        claimService.assertLegalTransition("PENDING_PAYMENT", "PAID");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_inReviewToAbandoned_removed() {
        claimService.assertLegalTransition("IN_REVIEW", "ABANDONED");
    }

    @Test(expected = ServiceException.class)
    public void assertLegalTransition_pendingInfoToDenied_removed() {
        claimService.assertLegalTransition("PENDING_INFO", "DENIED");
    }

    // -------------------------------------------------------------------------
    // VOID claim submit
    // -------------------------------------------------------------------------

    @Test
    public void submit_void_reversesAccumulatorAndVoidsOriginal() {
        SubmitClaimRequest req = buildVoidRequest();

        Claim original = new Claim();
        original.setId(50);
        original.setMemberId(10);
        original.setPlanId(1);
        original.setStatus(ClaimStatus.PAID);
        original.setVersion(3);

        Member member = new Member();
        member.setId(10);

        Provider provider = new Provider();
        provider.setId(5);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));

        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Mockito.when(providerDAO.findById(5)).thenReturn(provider);
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);
        Mockito.when(claimDAO.findById(50)).thenReturn(original);

        claimService.submit(req, 1);

        Mockito.verify(voidReversalService).reverse(50, 10, 1, date(2026, 1, 1));
        Mockito.verify(claimDAO).updateStatus(50, "VOIDED", 3);
    }

    // -------------------------------------------------------------------------
    // Re-adjudication
    // -------------------------------------------------------------------------

    @Test(expected = ServiceException.class)
    public void reAdjudicate_rejectedForApprovedClaim() {
        Claim claim = new Claim();
        claim.setId(1);
        claim.setStatus(ClaimStatus.APPROVED);
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);

        claimService.reAdjudicate(1, 99);
    }

    @Test
    public void reAdjudicate_appendsNextRunId_andRetainsHistory() {
        Claim claim = new Claim();
        claim.setId(1);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setPlanId(1);
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.IN_REVIEW);  // IN_REVIEW → APPROVED is a legal transition
        claim.setVersion(2);
        claim.setDateOfService(date(2026, 2, 15));
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);
        Mockito.when(memberDAO.findById(10)).thenReturn(new Member());
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));

        // Run 1 already exists.
        Mockito.when(adjResultsDAO.maxRunId(1)).thenReturn(1);
        Mockito.when(contributionDAO.findByClaimId(1)).thenReturn(null);
        Mockito.when(adjudicationService.adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(ClaimStatus.APPROVED);

        claimService.reAdjudicate(1, 99);

        // Next run is 2, and prior results are NOT deleted (history retained).
        Mockito.verify(adjudicationService).adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(2));
        Mockito.verify(adjResultsDAO, Mockito.never()).deleteByClaimId(Mockito.anyInt());
    }

    @Test
    public void reAdjudicate_reversesPriorContributionExactlyOnce() {
        Claim claim = new Claim();
        claim.setId(1);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setPlanId(1);
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.IN_REVIEW);
        claim.setVersion(2);
        claim.setDateOfService(date(2026, 2, 15));
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);
        Mockito.when(memberDAO.findById(10)).thenReturn(new Member());
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));

        Mockito.when(adjResultsDAO.maxRunId(1)).thenReturn(1);

        com.meridian.claims.model.ClaimAccumulatorContribution prior =
            new com.meridian.claims.model.ClaimAccumulatorContribution();
        prior.setClaimId(1);
        prior.setReversed(false);
        Mockito.when(contributionDAO.findByClaimId(1)).thenReturn(prior);

        Mockito.when(adjudicationService.adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(ClaimStatus.APPROVED);

        claimService.reAdjudicate(1, 99);

        Mockito.verify(voidReversalService, Mockito.times(1))
            .reverse(1, 10, 1, date(2026, 1, 1));
    }

    // -------------------------------------------------------------------------
    // DENIED re-adjudication → routes to IN_REVIEW (regression for state-machine fix)
    // -------------------------------------------------------------------------

    @Test
    public void reAdjudicate_deniedClaim_engineApproves_resultsInInReview() {
        // Even when the engine returns APPROVED, a DENIED source must land in IN_REVIEW.
        Claim claim = new Claim();
        claim.setId(1);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setPlanId(1);
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.DENIED);
        claim.setVersion(1);
        claim.setDateOfService(date(2026, 2, 15));
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);
        Mockito.when(memberDAO.findById(10)).thenReturn(new Member());
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));

        Mockito.when(adjResultsDAO.maxRunId(1)).thenReturn(0);
        Mockito.when(contributionDAO.findByClaimId(1)).thenReturn(null);

        // Engine says APPROVED — but DENIED may only go to IN_REVIEW per spec.
        Mockito.when(adjudicationService.adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(ClaimStatus.APPROVED);

        // Must not throw; status must be coerced to IN_REVIEW.
        Claim result = claimService.reAdjudicate(1, 99);
        Mockito.verify(claimDAO).updateStatus(1, ClaimStatus.IN_REVIEW.name(), 1);
    }

    @Test
    public void reAdjudicate_deniedClaim_engineDenies_resultsInInReview() {
        // When engine returns DENIED, DENIED source must still land in IN_REVIEW.
        Claim claim = new Claim();
        claim.setId(2);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setPlanId(1);
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.DENIED);
        claim.setVersion(1);
        claim.setDateOfService(date(2026, 2, 15));
        Mockito.when(claimDAO.findById(2)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);
        Mockito.when(memberDAO.findById(10)).thenReturn(new Member());
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));

        Mockito.when(adjResultsDAO.maxRunId(2)).thenReturn(0);
        Mockito.when(contributionDAO.findByClaimId(2)).thenReturn(null);

        Mockito.when(adjudicationService.adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(ClaimStatus.DENIED);

        claimService.reAdjudicate(2, 99);
        Mockito.verify(claimDAO).updateStatus(2, ClaimStatus.IN_REVIEW.name(), 1);
    }

    @Test(expected = ServiceException.class)
    public void reAdjudicate_nullPlan_throwsServiceException() {
        // Reloaded plan being null must yield a clear ServiceException, not NPE.
        Claim claim = new Claim();
        claim.setId(3);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setPlanId(99);  // plan that doesn't exist
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.IN_REVIEW);
        claim.setVersion(1);
        claim.setDateOfService(date(2026, 2, 15));
        Mockito.when(claimDAO.findById(3)).thenReturn(claim);

        Mockito.when(adjResultsDAO.maxRunId(3)).thenReturn(0);
        Mockito.when(contributionDAO.findByClaimId(3)).thenReturn(null);
        Mockito.when(memberDAO.findById(10)).thenReturn(new Member());
        Mockito.when(providerDAO.findById(5)).thenReturn(new Provider());
        Mockito.when(planDAO.findById(99)).thenReturn(null);  // plan deleted

        claimService.reAdjudicate(3, 99);
    }

    // -------------------------------------------------------------------------
    // Claim number format and collision retry
    // -------------------------------------------------------------------------

    @Test
    public void claimNumberGenerator_formatMatchesCLM_YYYYMMDD_NNNNNN() {
        String number = ClaimNumberGenerator.generate(date(2026, 6, 29));
        assertTrue("Format must match CLM-YYYYMMDD-NNNNNN",
            number.matches("CLM-\\d{8}-\\d{6}"));
    }

    @Test
    public void submit_claimNumberCollision_retriesAndSucceeds() {
        // First insert throws DuplicateKeyException (collision); second succeeds.
        SubmitClaimRequest req = new SubmitClaimRequest();
        req.setClaimType(ClaimType.ORIGINAL);
        req.setMemberId(10);
        req.setProviderId(5);
        req.setCoverageOrder("PRIMARY");
        req.setDateOfService(date(2026, 2, 15));

        SubmitClaimRequest.LineItemRow li = new SubmitClaimRequest.LineItemRow();
        li.setProcedureCode("99213");
        li.setBilledAmount(Money.of("100.00"));
        req.setLineItems(Arrays.asList(li));

        SubmitClaimRequest.DiagnosisRow dx = new SubmitClaimRequest.DiagnosisRow();
        dx.setDiagnosisCode("J06.9");
        dx.setDiagnosisType("PRIMARY");
        req.setDiagnoses(Arrays.asList(dx));

        Member member = new Member();
        member.setId(10);
        Provider provider = new Provider();
        provider.setId(5);
        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));

        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Mockito.when(providerDAO.findById(5)).thenReturn(provider);
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);

        // First call throws (collision), second call succeeds (returns void).
        Mockito.doThrow(new DuplicateKeyException("duplicate claim_number"))
            .doNothing()
            .when(claimDAO).insert(Mockito.any(Claim.class));

        Mockito.when(adjudicationService.adjudicate(
            Mockito.any(), Mockito.anyList(), Mockito.anyList(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt()))
            .thenReturn(ClaimStatus.APPROVED);

        // Should NOT throw — the retry must succeed.
        claimService.submit(req, 1);

        // insert was called twice (first attempt + retry).
        Mockito.verify(claimDAO, Mockito.times(2)).insert(Mockito.any(Claim.class));
    }

    // -------------------------------------------------------------------------
    // submit() — guard conditions (MED-9)
    // -------------------------------------------------------------------------

    @Test(expected = ServiceException.class)
    public void submit_secondary_missingCobPrimaryPaid_throws() {
        // SECONDARY claim without cobPrimaryPaid must be rejected before any DAO calls.
        SubmitClaimRequest req = new SubmitClaimRequest();
        req.setClaimType(ClaimType.ORIGINAL);
        req.setMemberId(10);
        req.setProviderId(5);
        req.setCoverageOrder("SECONDARY");
        req.setCobPrimaryPaid(null);
        req.setDateOfService(date(2026, 2, 15));

        SubmitClaimRequest.LineItemRow li = new SubmitClaimRequest.LineItemRow();
        li.setProcedureCode("99213");
        li.setBilledAmount(Money.of("100.00"));
        req.setLineItems(Arrays.asList(li));

        SubmitClaimRequest.DiagnosisRow dx = new SubmitClaimRequest.DiagnosisRow();
        dx.setDiagnosisCode("J06.9");
        dx.setDiagnosisType("PRIMARY");
        req.setDiagnoses(Arrays.asList(dx));

        claimService.submit(req, 1);
    }

    @Test(expected = ServiceException.class)
    public void submit_corrected_originalNotPaid_throws() {
        // CORRECTED claim whose original is not PAID must be rejected.
        SubmitClaimRequest req = new SubmitClaimRequest();
        req.setClaimType(ClaimType.CORRECTED);
        req.setMemberId(10);
        req.setProviderId(5);
        req.setCoverageOrder("PRIMARY");
        req.setOriginalClaimId(50);
        req.setDateOfService(date(2026, 2, 15));

        SubmitClaimRequest.LineItemRow li = new SubmitClaimRequest.LineItemRow();
        li.setProcedureCode("99213");
        li.setBilledAmount(Money.of("100.00"));
        req.setLineItems(Arrays.asList(li));

        SubmitClaimRequest.DiagnosisRow dx = new SubmitClaimRequest.DiagnosisRow();
        dx.setDiagnosisCode("J06.9");
        dx.setDiagnosisType("PRIMARY");
        req.setDiagnoses(Arrays.asList(dx));

        Member member = new Member();
        member.setId(10);
        Provider provider = new Provider();
        provider.setId(5);
        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));

        MemberCoverage cov = new MemberCoverage();
        cov.setPlanId(1);
        cov.setCoverageOrder(CoverageOrder.PRIMARY);
        cov.setEffectiveDate(date(2026, 1, 1));

        Mockito.when(memberDAO.findById(10)).thenReturn(member);
        Mockito.when(providerDAO.findById(5)).thenReturn(provider);
        Mockito.when(memberCoverageDAO.findActiveByMemberId(10)).thenReturn(Arrays.asList(cov));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);

        Claim original = new Claim();
        original.setId(50);
        original.setStatus(ClaimStatus.IN_REVIEW);
        Mockito.when(claimDAO.findById(50)).thenReturn(original);

        claimService.submit(req, 1);
    }

    // -------------------------------------------------------------------------
    // Phase 5 — approve / deny / requestInfo / resubmit / assignClaim / addNote
    // -------------------------------------------------------------------------

    @Test
    public void approve_happyPath_updatesStatusAndAudits() {
        Claim claim = inReviewClaim(10);
        Mockito.when(claimDAO.findById(10)).thenReturn(claim);

        claimService.approve(10, 99, "Looks good");

        Mockito.verify(claimDAO).updateStatus(10, "APPROVED", 1);
        Mockito.verify(claimAuditDAO).insert(Mockito.any());
        Mockito.verify(phiAccessLogService).record(claim.getMemberId(), 10, "APPROVED");
    }

    @Test(expected = ServiceException.class)
    public void approve_emptyNotes_throws() {
        Mockito.when(claimDAO.findById(10)).thenReturn(inReviewClaim(10));
        claimService.approve(10, 99, "");
    }

    @Test(expected = ServiceException.class)
    public void approve_illegalTransition_throws() {
        Claim claim = inReviewClaim(10);
        claim.setStatus(ClaimStatus.PAID);
        Mockito.when(claimDAO.findById(10)).thenReturn(claim);
        claimService.approve(10, 99, "notes");
    }

    @Test
    public void deny_happyPath_updatesStatusAndAudits() {
        Claim claim = inReviewClaim(20);
        Mockito.when(claimDAO.findById(20)).thenReturn(claim);

        claimService.deny(20, 99, "NOT_COVERED", "Service not covered");

        Mockito.verify(claimDAO).update(Mockito.any(Claim.class));
        Mockito.verify(phiAccessLogService).record(claim.getMemberId(), 20, "DENIED");
    }

    @Test(expected = ServiceException.class)
    public void deny_noReasonCode_throws() {
        Mockito.when(claimDAO.findById(20)).thenReturn(inReviewClaim(20));
        claimService.deny(20, 99, null, "notes");
    }

    @Test
    public void deny_reversesAccumulatorContribution_whenPresent() {
        // A claim routed to IN_REVIEW during adjudication has already contributed to the member's
        // deductible/OOP accumulators. Denying it must reverse that contribution.
        Claim claim = inReviewClaim(20);
        Mockito.when(claimDAO.findById(20)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);

        com.meridian.claims.model.ClaimAccumulatorContribution prior =
            new com.meridian.claims.model.ClaimAccumulatorContribution();
        prior.setClaimId(20);
        prior.setReversed(false);
        Mockito.when(contributionDAO.findByClaimId(20)).thenReturn(prior);

        claimService.deny(20, 99, "NOT_COVERED", "Service not covered");

        Mockito.verify(voidReversalService).reverse(20, 10, 1, date(2026, 1, 1));
        Mockito.verify(claimDAO).update(Mockito.any(Claim.class));
    }

    @Test
    public void deny_doesNotReverse_whenNoContributionOrAlreadyReversed() {
        Claim claim = inReviewClaim(20);
        Mockito.when(claimDAO.findById(20)).thenReturn(claim);
        com.meridian.claims.model.ClaimAccumulatorContribution reversed =
            new com.meridian.claims.model.ClaimAccumulatorContribution();
        reversed.setClaimId(20);
        reversed.setReversed(true);
        Mockito.when(contributionDAO.findByClaimId(20)).thenReturn(reversed);

        claimService.deny(20, 99, "NOT_COVERED", "Service not covered");

        Mockito.verify(voidReversalService, Mockito.never())
            .reverse(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Date.class));
    }

    @Test
    public void markAbandoned_reversesContributionAndTransitions() {
        Claim claim = new Claim();
        claim.setId(40);
        claim.setMemberId(10);
        claim.setPlanId(1);
        claim.setStatus(ClaimStatus.PENDING_INFO);
        claim.setVersion(3);
        Mockito.when(claimDAO.findById(40)).thenReturn(claim);

        Plan plan = new Plan();
        plan.setId(1);
        plan.setBenefitYearStart(date(2026, 1, 1));
        Mockito.when(planDAO.findById(1)).thenReturn(plan);

        com.meridian.claims.model.ClaimAccumulatorContribution prior =
            new com.meridian.claims.model.ClaimAccumulatorContribution();
        prior.setClaimId(40);
        prior.setReversed(false);
        Mockito.when(contributionDAO.findByClaimId(40)).thenReturn(prior);

        claimService.markAbandoned(40, "Stale: PENDING_INFO past info_request due_date");

        Mockito.verify(voidReversalService).reverse(40, 10, 1, date(2026, 1, 1));
        Mockito.verify(claimDAO).updateStatus(40, "ABANDONED", 3);
        Mockito.verify(claimAuditDAO).insert(Mockito.any(com.meridian.claims.model.ClaimAuditEntry.class));
    }

    @Test(expected = ServiceException.class)
    public void markAbandoned_illegalFromTerminalState_throws() {
        Claim claim = new Claim();
        claim.setId(41);
        claim.setStatus(ClaimStatus.PAID);   // PAID → ABANDONED is not a legal transition
        claim.setVersion(1);
        Mockito.when(claimDAO.findById(41)).thenReturn(claim);
        claimService.markAbandoned(41, "Stale");
    }

    @Test
    public void requestInfo_happyPath_insertsInfoRequestAndTransitions() {
        Claim claim = inReviewClaim(30);
        Mockito.when(claimDAO.findById(30)).thenReturn(claim);

        claimService.requestInfo(30, 99, "MEMBER", date(2026, 7, 30), "Need EOB from prior plan");

        Mockito.verify(infoRequestDAO).insert(Mockito.any(InfoRequest.class));
        Mockito.verify(claimDAO).updateStatus(30, "PENDING_INFO", 1);
    }

    @Test(expected = ServiceException.class)
    public void requestInfo_emptyNotes_throws() {
        Mockito.when(claimDAO.findById(30)).thenReturn(inReviewClaim(30));
        claimService.requestInfo(30, 99, "MEMBER", date(2026, 7, 30), "");
    }

    @Test
    public void resubmit_noOpenInfoRequest_transitionsToInReview() {
        Claim claim = new Claim();
        claim.setId(40);
        claim.setMemberId(10);
        claim.setStatus(ClaimStatus.PENDING_INFO);
        claim.setVersion(2);
        Mockito.when(claimDAO.findById(40)).thenReturn(claim);
        Mockito.when(infoRequestDAO.findOpenByClaimId(40)).thenReturn(null); // no open request

        claimService.resubmit(40, 99);

        Mockito.verify(claimDAO).updateStatus(40, "IN_REVIEW", 2);
    }

    @Test(expected = ServiceException.class)
    public void resubmit_openInfoRequest_throws() {
        Claim claim = new Claim();
        claim.setId(40);
        claim.setStatus(ClaimStatus.PENDING_INFO);
        claim.setVersion(1);
        Mockito.when(claimDAO.findById(40)).thenReturn(claim);
        InfoRequest open = new InfoRequest();
        open.setId(5);
        open.setStatus("OPEN");
        Mockito.when(infoRequestDAO.findOpenByClaimId(40)).thenReturn(open);

        claimService.resubmit(40, 99);
    }

    @Test
    public void assignClaim_setsAssigneeAndAudits() {
        Claim claim = inReviewClaim(50);
        Mockito.when(claimDAO.findById(50)).thenReturn(claim);

        claimService.assignClaim(50, 7, 1);

        Mockito.verify(claimDAO).updateAssignment(50, 7, 1);
        Mockito.verify(claimAuditDAO).insert(Mockito.any());
    }

    @Test
    public void addNote_insertsNote() {
        Claim claim = inReviewClaim(60);
        Mockito.when(claimDAO.findById(60)).thenReturn(claim);

        claimService.addNote(60, 99, "Reviewed with supervisor");

        Mockito.verify(claimNoteDAO).insert(Mockito.any());
    }

    @Test(expected = ServiceException.class)
    public void addNote_emptyText_throws() {
        Mockito.when(claimDAO.findById(60)).thenReturn(inReviewClaim(60));
        claimService.addNote(60, 99, "  ");
    }

    private Claim inReviewClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setMemberId(10);
        c.setProviderId(5);
        c.setPlanId(1);
        c.setStatus(ClaimStatus.IN_REVIEW);
        c.setVersion(1);
        return c;
    }

    // -------------------------------------------------------------------------
    // canViewClaim — HIPAA minimum-necessary scoping
    // -------------------------------------------------------------------------

    @Test
    public void canViewClaim_reviewerSeesAnyClam() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        User reviewer = userWithRole(UserRole.REVIEWER, 1);
        assertTrue(claimService.canViewClaim(claim, reviewer));
    }

    @Test
    public void canViewClaim_adminSeesAnyClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        User admin = userWithRole(UserRole.ADMIN, 1);
        assertTrue(claimService.canViewClaim(claim, admin));
    }

    @Test
    public void canViewClaim_staffSeesOwnSubmittedClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(7);
        User staff = userWithRole(UserRole.STAFF, 7);
        assertTrue(claimService.canViewClaim(claim, staff));
    }

    @Test
    public void canViewClaim_staffSeesAssignedClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        claim.setAssignedToUserId(7);
        User staff = userWithRole(UserRole.STAFF, 7);
        assertTrue(claimService.canViewClaim(claim, staff));
    }

    @Test
    public void canViewClaim_staffCannotSeeOthersClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        claim.setAssignedToUserId(88);
        User staff = userWithRole(UserRole.STAFF, 7);
        assertFalse(claimService.canViewClaim(claim, staff));
    }

    @Test
    public void canViewClaim_financeCannotSeeUnrelatedClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        User finance = userWithRole(UserRole.FINANCE, 7);
        assertFalse(claimService.canViewClaim(claim, finance));
    }

    @Test
    public void canViewClaim_analystCannotSeeUnrelatedClaim() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(99);
        User analyst = userWithRole(UserRole.ANALYST, 7);
        assertFalse(claimService.canViewClaim(claim, analyst));
    }

    @Test
    public void canViewClaim_nullUserReturnsFalse() {
        Claim claim = new Claim();
        claim.setCreatedByUserId(1);
        assertFalse(claimService.canViewClaim(claim, null));
    }

    private static User userWithRole(UserRole role, int id) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private SubmitClaimRequest buildVoidRequest() {
        SubmitClaimRequest req = new SubmitClaimRequest();
        req.setClaimType(ClaimType.VOID);
        req.setMemberId(10);
        req.setProviderId(5);
        req.setOriginalClaimId(50);
        req.setDateOfService(date(2026, 2, 15));
        req.setCoverageOrder("PRIMARY");

        SubmitClaimRequest.LineItemRow li = new SubmitClaimRequest.LineItemRow();
        li.setProcedureCode("99213");
        li.setBilledAmount(Money.of("200.00"));
        req.setLineItems(Arrays.asList(li));

        SubmitClaimRequest.DiagnosisRow dx = new SubmitClaimRequest.DiagnosisRow();
        dx.setDiagnosisCode("J06.9");
        dx.setDiagnosisType("PRIMARY");
        req.setDiagnoses(Arrays.asList(dx));

        return req;
    }
}
