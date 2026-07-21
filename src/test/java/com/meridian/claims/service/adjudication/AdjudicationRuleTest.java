package com.meridian.claims.service.adjudication;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.FeeScheduleRateDAO;
import com.meridian.claims.dao.PlanCoverageRuleDAO;
import com.meridian.claims.dao.PriorAuthorizationDAO;
import com.meridian.claims.dao.ReferralDAO;
import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.model.Claim;
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
import com.meridian.claims.model.RateSource;
import com.meridian.claims.model.Referral;
import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for each adjudication rule — pass, fail, and boundary cases.
 */
public class AdjudicationRuleTest {

    private Claim claim;
    private Plan plan;
    private Member member;
    private Provider provider;
    private MemberCoverage activeCoverage;
    private ClaimDAO claimDAO;
    private FeeScheduleRateDAO feeScheduleRateDAO;
    private PlanCoverageRuleDAO planCoverageRuleDAO;
    private PriorAuthorizationDAO priorAuthDAO;
    private ReferralDAO referralDAO;

    @Before
    public void setUp() {
        claimDAO = Mockito.mock(ClaimDAO.class);
        feeScheduleRateDAO = Mockito.mock(FeeScheduleRateDAO.class);
        planCoverageRuleDAO = Mockito.mock(PlanCoverageRuleDAO.class);
        priorAuthDAO = Mockito.mock(PriorAuthorizationDAO.class);
        referralDAO = Mockito.mock(ReferralDAO.class);

        plan = new Plan();
        plan.setId(1);
        plan.setPlanType(PlanType.PPO);
        plan.setTimelyFilingDays(180);
        plan.setDeductibleAmount(Money.of("500.00"));
        plan.setOopMax(Money.of("3000.00"));
        plan.setCopayAmount(Money.of("30.00"));
        plan.setCoveragePctInNetwork(Money.of("80.00"));
        plan.setCoveragePctOutNetwork(Money.of("60.00"));
        plan.setBenefitYearStart(date(2026, 1, 1));

        member = new Member();
        member.setId(10);

        provider = new Provider();
        provider.setId(5);
        provider.setNetworkStatus(NetworkStatus.IN_NETWORK);

        activeCoverage = new MemberCoverage();
        activeCoverage.setEffectiveDate(date(2026, 1, 1));
        activeCoverage.setTerminationDate(null);

        claim = new Claim();
        claim.setId(100);
        claim.setMemberId(10);
        claim.setProviderId(5);
        claim.setClaimType(ClaimType.ORIGINAL);
        claim.setPlanId(1);
        claim.setCoverageOrder("PRIMARY");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmissionDate(date(2026, 3, 1));
        claim.setDateOfService(date(2026, 2, 15));
    }

    private AdjudicationContext ctx() {
        return ctx(new ArrayList<ClaimLineItem>());
    }

    private AdjudicationContext ctx(List<ClaimLineItem> lineItems) {
        return new AdjudicationContext(claim, lineItems,
            new ArrayList<>(), plan, member, provider, activeCoverage,
            claimDAO, feeScheduleRateDAO, planCoverageRuleDAO, priorAuthDAO, referralDAO);
    }

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    // -------------------------------------------------------------------------
    // Rule 1 — TimelyFilingRule
    // -------------------------------------------------------------------------

    @Test
    public void timelyFiling_pass_withinLimit() {
        claim.setDateOfService(date(2026, 2, 1));
        claim.setSubmissionDate(date(2026, 3, 1));  // 28 days — well within 180
        AdjudicationRuleResult result = new TimelyFilingRule().evaluate(ctx());
        assertTrue(result.isPassed());
    }

    @Test
    public void timelyFiling_fail_exceeded() {
        claim.setDateOfService(date(2025, 1, 1));
        claim.setSubmissionDate(date(2026, 3, 1));  // > 180 days
        AdjudicationRuleResult result = new TimelyFilingRule().evaluate(ctx());
        assertFalse(result.isPassed());
    }

    @Test
    public void timelyFiling_exactBoundary_pass() {
        // Exactly 180 days — should pass
        Calendar dos = Calendar.getInstance();
        dos.clear();
        dos.set(2026, Calendar.JANUARY, 1);
        Calendar sub = (Calendar) dos.clone();
        sub.add(Calendar.DAY_OF_YEAR, 180);
        claim.setDateOfService(dos.getTime());
        claim.setSubmissionDate(sub.getTime());
        assertTrue(new TimelyFilingRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void timelyFiling_skipped_forCorrected() {
        claim.setClaimType(ClaimType.CORRECTED);
        claim.setDateOfService(date(2020, 1, 1));  // ancient DOS — would fail if evaluated
        claim.setSubmissionDate(date(2026, 3, 1));
        assertTrue(new TimelyFilingRule().evaluate(ctx()).isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 2 — EligibilityRule
    // -------------------------------------------------------------------------

    @Test
    public void eligibility_pass() {
        activeCoverage.setEffectiveDate(date(2026, 1, 1));
        claim.setDateOfService(date(2026, 2, 15));
        assertTrue(new EligibilityRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void eligibility_fail_beforeEffective() {
        activeCoverage.setEffectiveDate(date(2026, 3, 1));
        claim.setDateOfService(date(2026, 2, 15));
        assertFalse(new EligibilityRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void eligibility_fail_afterTermination() {
        activeCoverage.setTerminationDate(date(2026, 1, 31));
        claim.setDateOfService(date(2026, 2, 15));
        assertFalse(new EligibilityRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void eligibility_fail_noCoverage() {
        AdjudicationContext ctx = new AdjudicationContext(claim, new ArrayList<>(),
            new ArrayList<>(), plan, member, provider, null,
            claimDAO, feeScheduleRateDAO, planCoverageRuleDAO, priorAuthDAO, referralDAO);
        assertFalse(new EligibilityRule().evaluate(ctx).isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 3 — DuplicateRule
    // -------------------------------------------------------------------------

    @Test
    public void duplicate_pass_noDuplicate() {
        Mockito.when(claimDAO.findByMemberAndDOS(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(null);
        assertTrue(new DuplicateRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void duplicate_fail_duplicateFound() {
        Claim dupe = new Claim();
        dupe.setId(999);
        dupe.setClaimNumber("CLM-OLD");
        Mockito.when(claimDAO.findByMemberAndDOS(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(dupe);
        assertFalse(new DuplicateRule().evaluate(ctx()).isPassed());
    }

    @Test
    public void duplicate_skipped_forCorrected() {
        claim.setClaimType(ClaimType.CORRECTED);
        // Even if DAO returns a match, CORRECTED skips duplicate check
        Claim dupe = new Claim();
        dupe.setId(999);
        Mockito.when(claimDAO.findByMemberAndDOS(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(dupe);
        assertTrue(new DuplicateRule().evaluate(ctx()).isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 9 — DeductibleRule
    // -------------------------------------------------------------------------

    @Test
    public void deductible_partiallyExhausted() {
        DeductibleAccumulatorDAO accDAO = Mockito.mock(DeductibleAccumulatorDAO.class);
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setId(1);
        acc.setDeductibleAccumulated(Money.of("300.00"));
        acc.setOopAccumulated(Money.of("300.00"));
        Mockito.when(accDAO.findOrCreateForUpdate(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(acc);

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setDeductibleApplied(Money.ZERO);

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        new DeductibleRule(accDAO).evaluate(ctx);

        // Remaining deductible = 500 - 300 = 200; apply 200 to line item
        assertEquals(Money.of("200.00"), li.getDeductibleApplied());
        assertEquals(Money.of("200.00"), ctx.getDeductibleAppliedThisClaim());
    }

    @Test
    public void deductible_fullyExhausted_zeroApplied() {
        DeductibleAccumulatorDAO accDAO = Mockito.mock(DeductibleAccumulatorDAO.class);
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setId(1);
        acc.setDeductibleAccumulated(Money.of("500.00"));  // fully met
        acc.setOopAccumulated(Money.of("500.00"));
        Mockito.when(accDAO.findOrCreateForUpdate(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(acc);

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setDeductibleApplied(Money.ZERO);

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        new DeductibleRule(accDAO).evaluate(ctx);

        assertEquals(Money.ZERO, li.getDeductibleApplied());
        assertEquals(Money.ZERO, ctx.getDeductibleAppliedThisClaim());
    }

    // -------------------------------------------------------------------------
    // Rule 11 — BenefitCalculator
    // -------------------------------------------------------------------------

    @Test
    public void benefit_primaryMidDeductible_workedExample() {
        // Plan: $500 deductible, $30 copay, 80% in-network, $3000 OOP max
        // Member: $300 accumulated deductible. Billed $1000, allowed $1000.
        // Expected: deductible $200, copay $30, plan pays $616, member $384

        DeductibleAccumulatorDAO accDAO = Mockito.mock(DeductibleAccumulatorDAO.class);
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setId(1);
        acc.setDeductibleAccumulated(Money.of("300.00"));
        acc.setOopAccumulated(Money.of("300.00"));
        Mockito.when(accDAO.findOrCreateForUpdate(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(acc);

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setDeductibleApplied(Money.ZERO);
        li.setCopayApplied(Money.ZERO);

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        ctx.setNetworkCoveragePct(Money.of("80.00"));

        // Run DeductibleRule first
        new DeductibleRule(accDAO).evaluate(ctx);
        assertEquals(Money.of("200.00"), li.getDeductibleApplied());

        // Set copay manually (CopayRule needs serviceType; simulate result)
        li.setCopayApplied(Money.of("30.00"));

        // Run BenefitCalculator
        new BenefitCalculator().evaluate(ctx);

        // coinsuranceBase = 1000 - 200 - 30 = 770; planPaid = 80% * 770 = 616
        assertEquals(Money.of("616.00"), li.getPlanPaidAmount());
        // memberResp = 200 + 30 + (770 - 616) = 200 + 30 + 154 = 384
        assertEquals(Money.of("384.00"), li.getMemberResponsibility());
    }

    // -------------------------------------------------------------------------
    // Rule 12 — OopMaxRule
    // -------------------------------------------------------------------------

    @Test
    public void oopMax_caps_memberResponsibility() {
        // Member already at $2900 of $3000 OOP; new claim adds $384
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setOopAccumulated(Money.of("2900.00"));

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setPlanPaidAmount(Money.of("616.00"));
        li.setMemberResponsibility(Money.of("384.00"));

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        ctx.setAccumulator(acc);

        new OopMaxRule().evaluate(ctx);

        // remainingOop = 3000 - 2900 = 100; excess = 384 - 100 = 284 shifted to plan
        assertEquals(Money.of("100.00"), li.getMemberResponsibility());
        assertEquals(Money.of("900.00"), li.getPlanPaidAmount());  // 616 + 284
        assertEquals(Money.of("100.00"), ctx.getOopAppliedThisClaim());
    }

    // -------------------------------------------------------------------------
    // Rule 13 — CobAdjustmentRule (non-duplication, capped)
    // -------------------------------------------------------------------------

    @Test
    public void cob_secondaryCapped_workedExample() {
        // allowed=$1000, normal liability=$616, primary paid=$500
        // finalPlanPaid = min(616, max(0, 1000-500)) = min(616, 500) = $500
        // member = 1000 - 500(primary) - 500(secondary) = $0

        claim.setCoverageOrder("SECONDARY");
        claim.setCobPrimaryPaid(Money.of("500.00"));

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setPlanPaidAmount(Money.of("616.00"));
        li.setMemberResponsibility(Money.of("384.00"));

        AdjudicationContext ctx = ctx(Arrays.asList(li));

        new CobAdjustmentRule().evaluate(ctx);

        assertEquals(Money.of("500.00"), li.getPlanPaidAmount());
        assertEquals(Money.of("0.00"), li.getMemberResponsibility());
    }

    @Test
    public void cob_skipped_forPrimary() {
        claim.setCoverageOrder("PRIMARY");

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setPlanPaidAmount(Money.of("616.00"));
        li.setMemberResponsibility(Money.of("384.00"));

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        new CobAdjustmentRule().evaluate(ctx);

        // No change for PRIMARY
        assertEquals(Money.of("616.00"), li.getPlanPaidAmount());
        assertEquals(Money.of("384.00"), li.getMemberResponsibility());
    }

    // -------------------------------------------------------------------------
    // Helpers for the rules added below
    // -------------------------------------------------------------------------

    private ClaimLineItem lineItem(String procedureCode, String serviceType, PlanCoverageRule rule) {
        ClaimLineItem li = new ClaimLineItem();
        li.setProcedureCode(procedureCode);
        li.setServiceType(serviceType);
        li.setCoverageRule(rule);
        li.setBilledAmount(Money.of("1000.00"));
        return li;
    }

    private PlanCoverageRule coverageRule(String serviceType, boolean requiresPriorAuth, boolean requiresReferral) {
        PlanCoverageRule rule = new PlanCoverageRule();
        rule.setPlanId(1);
        rule.setServiceType(serviceType);
        rule.setCoveragePct(Money.of("80.00"));
        rule.setRequiresPriorAuth(requiresPriorAuth);
        rule.setRequiresReferral(requiresReferral);
        return rule;
    }

    // -------------------------------------------------------------------------
    // Rule 4 — CoverageRule (HARD)
    // -------------------------------------------------------------------------

    @Test
    public void coverage_pass_knownProcedureWithPlanRule() {
        ProcedureCode pc = new ProcedureCode();
        pc.setCode("99213");
        pc.setServiceType("OFFICE_VISIT");
        PlanCoverageRule rule = coverageRule("OFFICE_VISIT", false, false);
        Mockito.when(planCoverageRuleDAO.findByPlanId(1)).thenReturn(Arrays.asList(rule));

        ClaimLineItem li = lineItem("99213", null, null);
        AdjudicationContext ctx = ctx(Arrays.asList(li));

        AdjudicationRuleResult result = new CoverageRule(Arrays.asList(pc)).evaluate(ctx);
        assertTrue(result.isPassed());
        assertEquals("OFFICE_VISIT", li.getServiceType());
        assertEquals(rule, li.getCoverageRule());
    }

    @Test
    public void coverage_fail_unknownProcedure() {
        // Procedure not in master data → service type unresolved → not covered
        Mockito.when(planCoverageRuleDAO.findByPlanId(1)).thenReturn(new ArrayList<PlanCoverageRule>());
        ClaimLineItem li = lineItem("00000", null, null);
        AdjudicationContext ctx = ctx(Arrays.asList(li));

        AdjudicationRuleResult result = new CoverageRule(new ArrayList<ProcedureCode>()).evaluate(ctx);
        assertFalse(result.isPassed());
    }

    @Test
    public void coverage_fail_noMatchingPlanRule() {
        // Known procedure/service type, but the plan has no coverage rule for that service type
        ProcedureCode pc = new ProcedureCode();
        pc.setCode("99213");
        pc.setServiceType("OFFICE_VISIT");
        Mockito.when(planCoverageRuleDAO.findByPlanId(1)).thenReturn(
            Arrays.asList(coverageRule("SURGERY", false, false)));

        ClaimLineItem li = lineItem("99213", null, null);
        AdjudicationContext ctx = ctx(Arrays.asList(li));

        AdjudicationRuleResult result = new CoverageRule(Arrays.asList(pc)).evaluate(ctx);
        assertFalse(result.isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 5 — PriorAuthRule (HARD)
    // -------------------------------------------------------------------------

    @Test
    public void priorAuth_pass_whenValidAuthPresent() {
        PlanCoverageRule rule = coverageRule("SURGERY", true, false);
        ClaimLineItem li = lineItem("27447", "SURGERY", rule);
        Mockito.when(priorAuthDAO.findValid(Mockito.anyInt(), Mockito.eq("27447"), Mockito.any()))
            .thenReturn(new PriorAuthorization());

        assertTrue(new PriorAuthRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    @Test
    public void priorAuth_fail_whenRequiredButMissing() {
        PlanCoverageRule rule = coverageRule("SURGERY", true, false);
        ClaimLineItem li = lineItem("27447", "SURGERY", rule);
        Mockito.when(priorAuthDAO.findValid(Mockito.anyInt(), Mockito.eq("27447"), Mockito.any()))
            .thenReturn(null);

        assertFalse(new PriorAuthRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    @Test
    public void priorAuth_pass_whenNotRequired() {
        PlanCoverageRule rule = coverageRule("OFFICE_VISIT", false, false);
        ClaimLineItem li = lineItem("99213", "OFFICE_VISIT", rule);
        // DAO never consulted because the rule does not require prior auth
        assertTrue(new PriorAuthRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 6 — ReferralRule (HARD)
    // -------------------------------------------------------------------------

    @Test
    public void referral_pass_forNonHmoPlan() {
        plan.setPlanType(PlanType.PPO);
        PlanCoverageRule rule = coverageRule("SPECIALIST", false, true);
        ClaimLineItem li = lineItem("99244", "SPECIALIST", rule);
        // Non-HMO short-circuits before consulting the DAO
        assertTrue(new ReferralRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    @Test
    public void referral_fail_hmoRequiresReferralMissing() {
        plan.setPlanType(PlanType.HMO);
        PlanCoverageRule rule = coverageRule("SPECIALIST", false, true);
        ClaimLineItem li = lineItem("99244", "SPECIALIST", rule);
        Mockito.when(referralDAO.findValid(Mockito.anyInt(), Mockito.eq("SPECIALIST"), Mockito.any()))
            .thenReturn(null);

        assertFalse(new ReferralRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    @Test
    public void referral_pass_hmoWithValidReferral() {
        plan.setPlanType(PlanType.HMO);
        PlanCoverageRule rule = coverageRule("SPECIALIST", false, true);
        ClaimLineItem li = lineItem("99244", "SPECIALIST", rule);
        Mockito.when(referralDAO.findValid(Mockito.anyInt(), Mockito.eq("SPECIALIST"), Mockito.any()))
            .thenReturn(new Referral());

        assertTrue(new ReferralRule().evaluate(ctx(Arrays.asList(li))).isPassed());
    }

    // -------------------------------------------------------------------------
    // Rule 7 — NetworkRule (SOFT)
    // -------------------------------------------------------------------------

    @Test
    public void network_inNetwork_setsInNetworkPct() {
        provider.setNetworkStatus(NetworkStatus.IN_NETWORK);
        AdjudicationContext ctx = ctx();
        new NetworkRule().evaluate(ctx);
        assertEquals(Money.of("80.00"), ctx.getNetworkCoveragePct());
    }

    @Test
    public void network_outOfNetwork_setsOutNetworkPct() {
        provider.setNetworkStatus(NetworkStatus.OUT_OF_NETWORK);
        AdjudicationContext ctx = ctx();
        new NetworkRule().evaluate(ctx);
        assertEquals(Money.of("60.00"), ctx.getNetworkCoveragePct());
    }

    // -------------------------------------------------------------------------
    // Rule 8 — FeeScheduleRule (SOFT)
    // -------------------------------------------------------------------------

    @Test
    public void feeSchedule_providerSpecificRate_wins() {
        ClaimLineItem li = lineItem("99213", "OFFICE_VISIT", null);
        Mockito.when(feeScheduleRateDAO.resolveAllowedAmount(1, 5, "99213", claim.getDateOfService()))
            .thenReturn(Money.of("150.00"));

        new FeeScheduleRule().evaluate(ctx(Arrays.asList(li)));
        assertEquals(Money.of("150.00"), li.getAllowedAmount());
        assertEquals(RateSource.PROVIDER_SPECIFIC.name(), li.getRateSource());
    }

    @Test
    public void feeSchedule_planWideFallback() {
        ClaimLineItem li = lineItem("99213", "OFFICE_VISIT", null);
        Mockito.when(feeScheduleRateDAO.resolveAllowedAmount(1, 5, "99213", claim.getDateOfService()))
            .thenReturn(null);
        Mockito.when(feeScheduleRateDAO.resolveAllowedAmount(1, null, "99213", claim.getDateOfService()))
            .thenReturn(Money.of("120.00"));

        new FeeScheduleRule().evaluate(ctx(Arrays.asList(li)));
        assertEquals(Money.of("120.00"), li.getAllowedAmount());
        assertEquals(RateSource.PLAN_WIDE.name(), li.getRateSource());
    }

    @Test
    public void feeSchedule_noRate_allowedNullNeverZero() {
        ClaimLineItem li = lineItem("99213", "OFFICE_VISIT", null);
        Mockito.when(feeScheduleRateDAO.resolveAllowedAmount(Mockito.anyInt(), Mockito.any(), Mockito.anyString(), Mockito.any()))
            .thenReturn(null);

        new FeeScheduleRule().evaluate(ctx(Arrays.asList(li)));
        // Allowed must be null (NO_RATE) — never silently zero
        org.junit.Assert.assertNull(li.getAllowedAmount());
        assertEquals(RateSource.NO_RATE.name(), li.getRateSource());
    }

    // -------------------------------------------------------------------------
    // Rule 10 — CopayRule (SOFT)
    // -------------------------------------------------------------------------

    @Test
    public void copay_oncePerDistinctServiceType() {
        // Two lines of the same service type — only the first gets a copay
        ClaimLineItem a = lineItem("99213", "OFFICE_VISIT", null);
        a.setAllowedAmount(Money.of("150.00"));
        ClaimLineItem b = lineItem("99214", "OFFICE_VISIT", null);
        b.setAllowedAmount(Money.of("200.00"));

        new CopayRule().evaluate(ctx(Arrays.asList(a, b)));
        assertEquals(Money.of("30.00"), a.getCopayApplied());
        assertEquals(Money.ZERO, b.getCopayApplied());
    }

    @Test
    public void copay_distinctServiceTypesEachGetCopay() {
        ClaimLineItem a = lineItem("99213", "OFFICE_VISIT", null);
        a.setAllowedAmount(Money.of("150.00"));
        ClaimLineItem b = lineItem("70450", "IMAGING", null);
        b.setAllowedAmount(Money.of("400.00"));

        new CopayRule().evaluate(ctx(Arrays.asList(a, b)));
        assertEquals(Money.of("30.00"), a.getCopayApplied());
        assertEquals(Money.of("30.00"), b.getCopayApplied());
    }

    @Test
    public void copay_noRateLine_zeroCopay() {
        ClaimLineItem a = lineItem("99213", "OFFICE_VISIT", null);
        a.setAllowedAmount(null);  // NO_RATE line
        new CopayRule().evaluate(ctx(Arrays.asList(a)));
        assertEquals(Money.ZERO, a.getCopayApplied());
    }

    // -------------------------------------------------------------------------
    // Rule 9 — DeductibleRule, NO_RATE line edge case
    // -------------------------------------------------------------------------

    @Test
    public void deductible_noRateLine_skipped() {
        DeductibleAccumulatorDAO accDAO = Mockito.mock(DeductibleAccumulatorDAO.class);
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setDeductibleAccumulated(Money.ZERO);
        acc.setOopAccumulated(Money.ZERO);
        Mockito.when(accDAO.findOrCreateForUpdate(Mockito.anyInt(), Mockito.anyInt(), Mockito.any())).thenReturn(acc);

        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(null);  // NO_RATE — deductible not applied

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        new DeductibleRule(accDAO).evaluate(ctx);

        assertEquals(Money.ZERO, li.getDeductibleApplied());
        assertEquals(Money.ZERO, ctx.getDeductibleAppliedThisClaim());
    }

    // -------------------------------------------------------------------------
    // Rule 11 — BenefitCalculator, additional cases
    // -------------------------------------------------------------------------

    @Test
    public void benefit_outOfNetworkPct_used() {
        // No deductible/copay; allowed 1000; out-of-network 60%
        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setDeductibleApplied(Money.ZERO);
        li.setCopayApplied(Money.ZERO);

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        ctx.setNetworkCoveragePct(Money.of("60.00"));

        new BenefitCalculator().evaluate(ctx);
        assertEquals(Money.of("600.00"), li.getPlanPaidAmount());
        assertEquals(Money.of("400.00"), li.getMemberResponsibility());
    }

    @Test
    public void benefit_serviceTypeOverridePct_beatsNetworkFallback() {
        // Coverage rule pct (90%) overrides the network fallback (60%)
        PlanCoverageRule rule = coverageRule("OFFICE_VISIT", false, false);
        rule.setCoveragePct(Money.of("90.00"));
        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of("1000.00"));
        li.setDeductibleApplied(Money.ZERO);
        li.setCopayApplied(Money.ZERO);
        li.setCoverageRule(rule);

        AdjudicationContext ctx = ctx(Arrays.asList(li));
        ctx.setNetworkCoveragePct(Money.of("60.00"));

        new BenefitCalculator().evaluate(ctx);
        assertEquals(Money.of("900.00"), li.getPlanPaidAmount());
        assertEquals(Money.of("100.00"), li.getMemberResponsibility());
    }

    @Test
    public void benefit_noRateLine_leavesAmountsNull() {
        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(null);
        AdjudicationContext ctx = ctx(Arrays.asList(li));
        ctx.setNetworkCoveragePct(Money.of("80.00"));

        new BenefitCalculator().evaluate(ctx);
        org.junit.Assert.assertNull(li.getPlanPaidAmount());
        org.junit.Assert.assertNull(li.getMemberResponsibility());
    }

    // -------------------------------------------------------------------------
    // Rounding — last line absorbs remainder (OopMaxRule & CobAdjustmentRule)
    // -------------------------------------------------------------------------

    @Test
    public void oopMax_threeLines_planPaidPlusMemberRespExact() {
        // 3 lines, member responsibility 133.33 + 133.33 + 133.34 = 400.00; remaining OOP = 40.00.
        // Excess 360.00 must shift to plan exactly, with no lost/extra cent.
        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setOopAccumulated(Money.of("2960.00"));  // remaining = 3000 - 2960 = 40.00

        ClaimLineItem a = newLine("400.00", "133.33", "266.67");
        ClaimLineItem b = newLine("400.00", "133.33", "266.67");
        ClaimLineItem c = newLine("400.00", "133.34", "266.66");
        List<ClaimLineItem> items = Arrays.asList(a, b, c);

        AdjudicationContext ctx = ctx(items);
        ctx.setAccumulator(acc);

        new OopMaxRule().evaluate(ctx);

        BigDecimalSums sums = sum(items);
        // Σ member must equal remaining OOP (40.00) exactly; Σ plan = original plan + excess
        assertEquals(Money.of("40.00"), sums.member);
        // Total allowed 1200 = Σ plan + Σ member  →  Σ plan = 1160.00
        assertEquals(Money.of("1160.00"), sums.plan);
        assertEquals(Money.of("40.00"), ctx.getOopAppliedThisClaim());
    }

    @Test
    public void cob_threeLines_planPaidSumsToFinalExact() {
        // SECONDARY claim, 3 lines, normal liability 616 split unevenly; primary paid 500.
        // finalPlanPaid = min(616, 1200-500=700) = 616. Σ planPaid must equal 616 exactly.
        claim.setCoverageOrder("SECONDARY");
        claim.setCobPrimaryPaid(Money.of("500.00"));

        // planPaid (normal liability) sums to 616.00; memberResp is overwritten by COB.
        ClaimLineItem a = newLine("400.00", "0.00", "205.33");
        ClaimLineItem b = newLine("400.00", "0.00", "205.33");
        ClaimLineItem c = newLine("400.00", "0.00", "205.34");
        List<ClaimLineItem> items = Arrays.asList(a, b, c);

        AdjudicationContext ctx = ctx(items);
        new CobAdjustmentRule().evaluate(ctx);

        BigDecimalSums sums = sum(items);
        assertEquals(Money.of("616.00"), sums.plan);
        // Each line: member = allowed - primaryProportion - secondaryPaid. Σ allowed=1200,
        // Σ primary=500, Σ plan=616  →  Σ member = 1200 - 500 - 616 = 84.00 exactly.
        assertEquals(Money.of("84.00"), sums.member);
    }

    // -------------------------------------------------------------------------
    // NetworkRule — null network status (regression for NPE fix)
    // -------------------------------------------------------------------------

    @Test
    public void networkRule_nullNetworkStatus_treatedAsOutOfNetwork() {
        // Provider with null networkStatus must not throw and must use out-of-network coverage %.
        provider.setNetworkStatus(null);
        AdjudicationContext ctx = ctx();
        AdjudicationRuleResult result = new NetworkRule().evaluate(ctx);
        assertTrue("null networkStatus should pass (treated as out-of-network)", result.isPassed());
        assertEquals(0, plan.getCoveragePctOutNetwork().compareTo(ctx.getNetworkCoveragePct()));
    }

    private ClaimLineItem newLine(String allowed, String memberResp, String planPaid) {
        ClaimLineItem li = new ClaimLineItem();
        li.setAllowedAmount(Money.of(allowed));
        li.setMemberResponsibility(Money.of(memberResp));
        li.setPlanPaidAmount(Money.of(planPaid));
        return li;
    }

    private static final class BigDecimalSums {
        final java.math.BigDecimal plan;
        final java.math.BigDecimal member;
        BigDecimalSums(java.math.BigDecimal plan, java.math.BigDecimal member) {
            this.plan = plan;
            this.member = member;
        }
    }

    private BigDecimalSums sum(List<ClaimLineItem> items) {
        java.math.BigDecimal plan = Money.ZERO;
        java.math.BigDecimal member = Money.ZERO;
        for (ClaimLineItem li : items) {
            plan = Money.add(plan, li.getPlanPaidAmount());
            member = Money.add(member, li.getMemberResponsibility());
        }
        return new BigDecimalSums(plan, member);
    }
}
