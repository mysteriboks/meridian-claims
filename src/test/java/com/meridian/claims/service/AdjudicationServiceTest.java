package com.meridian.claims.service;

import com.meridian.claims.dao.AdjudicationResultsDAO;
import com.meridian.claims.dao.ClaimAccumulatorContributionDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.dao.FeeScheduleRateDAO;
import com.meridian.claims.dao.LookupDAO;
import com.meridian.claims.dao.PlanCoverageRuleDAO;
import com.meridian.claims.dao.PriorAuthorizationDAO;
import com.meridian.claims.dao.ReferralDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimDiagnosis;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for AdjudicationService.determineDisposition() — the three routing
 * branches tested via full adjudicate() invocations with all rules enabled.
 *
 * All DAOs are mocked. The rule chain is driven to completion (no hard-rule failure)
 * so that determineDisposition() is the sole determinant of the returned ClaimStatus.
 */
public class AdjudicationServiceTest {

    private AdjudicationService adjudicationService;

    // DAOs wired into AdjudicationService
    private ClaimDAO claimDAO;
    private ClaimLineItemDAO claimLineItemDAO;
    private FeeScheduleRateDAO feeScheduleRateDAO;
    private PlanCoverageRuleDAO planCoverageRuleDAO;
    private PriorAuthorizationDAO priorAuthDAO;
    private ReferralDAO referralDAO;
    private DeductibleAccumulatorDAO accumulatorDAO;
    private ClaimAccumulatorContributionDAO contributionDAO;
    private AdjudicationResultsDAO adjudicationResultsDAO;
    private LookupDAO lookupDAO;

    // Shared test fixtures
    private static final String PROCEDURE_CODE = "99213";
    private static final String SERVICE_TYPE    = "OFFICE_VISIT";
    private static final int    RUN_ID          = 1;

    @Before
    public void setUp() {
        adjudicationService = new AdjudicationService();

        claimDAO              = Mockito.mock(ClaimDAO.class);
        claimLineItemDAO      = Mockito.mock(ClaimLineItemDAO.class);
        feeScheduleRateDAO    = Mockito.mock(FeeScheduleRateDAO.class);
        planCoverageRuleDAO   = Mockito.mock(PlanCoverageRuleDAO.class);
        priorAuthDAO          = Mockito.mock(PriorAuthorizationDAO.class);
        referralDAO           = Mockito.mock(ReferralDAO.class);
        accumulatorDAO        = Mockito.mock(DeductibleAccumulatorDAO.class);
        contributionDAO       = Mockito.mock(ClaimAccumulatorContributionDAO.class);
        adjudicationResultsDAO = Mockito.mock(AdjudicationResultsDAO.class);
        lookupDAO             = Mockito.mock(LookupDAO.class);

        ReflectionTestUtils.setField(adjudicationService, "claimDAO",              claimDAO);
        ReflectionTestUtils.setField(adjudicationService, "claimLineItemDAO",      claimLineItemDAO);
        ReflectionTestUtils.setField(adjudicationService, "feeScheduleRateDAO",    feeScheduleRateDAO);
        ReflectionTestUtils.setField(adjudicationService, "planCoverageRuleDAO",   planCoverageRuleDAO);
        ReflectionTestUtils.setField(adjudicationService, "priorAuthDAO",          priorAuthDAO);
        ReflectionTestUtils.setField(adjudicationService, "referralDAO",           referralDAO);
        ReflectionTestUtils.setField(adjudicationService, "accumulatorDAO",        accumulatorDAO);
        ReflectionTestUtils.setField(adjudicationService, "contributionDAO",       contributionDAO);
        ReflectionTestUtils.setField(adjudicationService, "adjudicationResultsDAO", adjudicationResultsDAO);
        ReflectionTestUtils.setField(adjudicationService, "lookupDAO",             lookupDAO);
        ReflectionTestUtils.setField(adjudicationService, "autoApproveThresholdStr", "500.00");
    }

    // -------------------------------------------------------------------------
    // Branch 1: CORRECTED claim type → IN_REVIEW
    // -------------------------------------------------------------------------

    @Test
    public void correctedClaimType_routesToInReview() {
        Claim claim = buildClaim(ClaimType.CORRECTED, "PRIMARY");
        List<ClaimLineItem> lineItems = buildLineItems(Money.of("300.00"));
        wireCommonMocks(claim, lineItems, Money.of("300.00"));

        ClaimStatus result = adjudicationService.adjudicate(
            claim, lineItems, diagnoses(), plan(), member(), provider(),
            activeCoverage(claim.getDateOfService()), RUN_ID);

        assertEquals("CORRECTED claim must route to IN_REVIEW", ClaimStatus.IN_REVIEW, result);
    }

    // -------------------------------------------------------------------------
    // Branch 2: SECONDARY coverage order → IN_REVIEW
    // -------------------------------------------------------------------------

    @Test
    public void secondaryCoverageOrder_routesToInReview() {
        Claim claim = buildClaim(ClaimType.ORIGINAL, "SECONDARY");
        claim.setCobPrimaryPaid(Money.of("50.00"));
        List<ClaimLineItem> lineItems = buildLineItems(Money.of("300.00"));
        wireCommonMocks(claim, lineItems, Money.of("300.00"));

        ClaimStatus result = adjudicationService.adjudicate(
            claim, lineItems, diagnoses(), plan(), member(), provider(),
            activeCoverage(claim.getDateOfService()), RUN_ID);

        assertEquals("SECONDARY coverage must route to IN_REVIEW", ClaimStatus.IN_REVIEW, result);
    }

    // -------------------------------------------------------------------------
    // Branch 3a: totalPlanPaid below threshold → APPROVED
    // -------------------------------------------------------------------------

    @Test
    public void totalPaidBelowThreshold_routesToApproved() {
        // threshold = 500.00; billed/allowed = 400.00; plan pays 80% = 320.00 < 500
        Claim claim = buildClaim(ClaimType.ORIGINAL, "PRIMARY");
        List<ClaimLineItem> lineItems = buildLineItems(Money.of("400.00"));
        // fee schedule returns 400 → allowed = 400
        wireCommonMocks(claim, lineItems, Money.of("400.00"));

        ClaimStatus result = adjudicationService.adjudicate(
            claim, lineItems, diagnoses(), plan(), member(), provider(),
            activeCoverage(claim.getDateOfService()), RUN_ID);

        assertEquals("totalPlanPaid $320 < $500 threshold must route to APPROVED",
            ClaimStatus.APPROVED, result);
    }

    // -------------------------------------------------------------------------
    // Branch 3b: totalPlanPaid above threshold → IN_REVIEW
    // -------------------------------------------------------------------------

    @Test
    public void totalPaidAboveThreshold_routesToInReview() {
        // threshold = 500.00; billed/allowed = 750.00; plan pays 80% = 600.00 > 500
        Claim claim = buildClaim(ClaimType.ORIGINAL, "PRIMARY");
        List<ClaimLineItem> lineItems = buildLineItems(Money.of("750.00"));
        wireCommonMocks(claim, lineItems, Money.of("750.00"));

        ClaimStatus result = adjudicationService.adjudicate(
            claim, lineItems, diagnoses(), plan(), member(), provider(),
            activeCoverage(claim.getDateOfService()), RUN_ID);

        assertEquals("totalPlanPaid $600 > $500 threshold must route to IN_REVIEW",
            ClaimStatus.IN_REVIEW, result);
    }

    // -------------------------------------------------------------------------
    // Fixture builders
    // -------------------------------------------------------------------------

    private Claim buildClaim(ClaimType claimType, String coverageOrder) {
        Claim c = new Claim();
        c.setId(1);
        c.setMemberId(10);
        c.setProviderId(5);
        c.setPlanId(1);
        c.setClaimType(claimType);
        c.setCoverageOrder(coverageOrder);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 2, 20));
        return c;
    }

    /**
     * Builds a single line item with the given billed amount.
     * allowedAmount, rateSource, and planPaidAmount are intentionally left null
     * here — the rule chain sets them during adjudication.
     */
    private List<ClaimLineItem> buildLineItems(BigDecimal billedAmount) {
        ClaimLineItem item = new ClaimLineItem();
        item.setId(1);
        item.setClaimId(1);
        item.setProcedureCode(PROCEDURE_CODE);
        item.setBilledAmount(billedAmount);
        return Arrays.asList(item);
    }

    private List<ClaimDiagnosis> diagnoses() {
        return Collections.emptyList();
    }

    /**
     * PPO plan: no deductible, generous OOP cap, 80% in-network coverage,
     * 365-day timely filing limit — all designed so the rule chain passes cleanly.
     */
    private Plan plan() {
        Plan p = new Plan();
        p.setId(1);
        p.setPlanType(PlanType.PPO);               // not HMO → ReferralRule skips
        p.setDeductibleAmount(Money.ZERO);
        p.setOopMax(Money.of("99999.00"));
        p.setCopayAmount(Money.ZERO);
        p.setCoveragePctInNetwork(new BigDecimal("80"));
        p.setCoveragePctOutNetwork(new BigDecimal("60"));
        p.setBenefitYearStart(date(2026, 1, 1));
        p.setTimelyFilingDays(365);
        return p;
    }

    private Member member() {
        Member m = new Member();
        m.setId(10);
        return m;
    }

    private Provider provider() {
        Provider p = new Provider();
        p.setId(5);
        p.setNetworkStatus(NetworkStatus.IN_NETWORK);
        return p;
    }

    private MemberCoverage activeCoverage(Date dos) {
        MemberCoverage cov = new MemberCoverage();
        cov.setMemberId(10);
        cov.setPlanId(1);
        cov.setEffectiveDate(date(2026, 1, 1));
        cov.setTerminationDate(date(2026, 12, 31));
        return cov;
    }

    /**
     * Wires all DAO mocks so every rule in the 13-rule chain completes without
     * a hard-failure (DENIED outcome).  The fee schedule rate is set equal to
     * the billed amount so allowed == billed and no NO_RATE rerouting occurs.
     */
    private void wireCommonMocks(Claim claim, List<ClaimLineItem> lineItems,
                                 BigDecimal feeScheduleRate) {
        // LookupDAO — CoverageRule needs a ProcedureCode with serviceType
        ProcedureCode pc = new ProcedureCode();
        pc.setCode(PROCEDURE_CODE);
        pc.setServiceType(SERVICE_TYPE);
        Mockito.when(lookupDAO.findAllProcedureCodes()).thenReturn(Arrays.asList(pc));

        // PlanCoverageRuleDAO — CoverageRule needs a PlanCoverageRule for SERVICE_TYPE
        PlanCoverageRule pcr = new PlanCoverageRule();
        pcr.setServiceType(SERVICE_TYPE);
        pcr.setCoveragePct(new BigDecimal("80"));
        pcr.setRequiresPriorAuth(false);
        pcr.setRequiresReferral(false);
        Mockito.when(planCoverageRuleDAO.findByPlanId(1)).thenReturn(Arrays.asList(pcr));

        // ClaimDAO — DuplicateRule (only for ORIGINAL; CORRECTED skips the check)
        Mockito.when(claimDAO.findByMemberAndDOS(
            Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Date.class)))
            .thenReturn(null);

        // FeeScheduleRateDAO — FeeScheduleRule; returns feeScheduleRate so rateSource = PLAN_WIDE
        Mockito.when(feeScheduleRateDAO.resolveAllowedAmount(
            Mockito.anyInt(), Mockito.any(), Mockito.anyString(), Mockito.any(Date.class)))
            .thenReturn(feeScheduleRate);

        // DeductibleAccumulatorDAO — DeductibleRule acquires the locked row
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setDeductibleAccumulated(Money.ZERO);
        acc.setOopAccumulated(Money.ZERO);
        Mockito.when(accumulatorDAO.findOrCreateForUpdate(
            Mockito.anyInt(), Mockito.anyInt(), Mockito.any(Date.class)))
            .thenReturn(acc);

        // The finalization step persists accumulator changes and inserts a contribution row.
        // No stubbing needed — void methods are no-ops on mocks by default.
    }

    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
}
