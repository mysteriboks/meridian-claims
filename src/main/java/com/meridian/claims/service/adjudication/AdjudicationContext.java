package com.meridian.claims.service.adjudication;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.FeeScheduleRateDAO;
import com.meridian.claims.dao.PlanCoverageRuleDAO;
import com.meridian.claims.dao.PriorAuthorizationDAO;
import com.meridian.claims.dao.ReferralDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimDiagnosis;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mutable value object passed through all 13 adjudication rules.
 * Rules read inputs and write financial outputs directly onto line items.
 * Running totals used by the post-rule finalization step are accumulated here.
 */
public class AdjudicationContext {

    private final Claim claim;
    private final List<ClaimLineItem> lineItems;
    private final List<ClaimDiagnosis> diagnoses;
    private final Plan plan;
    private final Member member;
    private final Provider provider;
    private final MemberCoverage activeCoverage;

    // DAO access needed by rules
    private final ClaimDAO claimDAO;
    private final FeeScheduleRateDAO feeScheduleRateDAO;
    private final PlanCoverageRuleDAO planCoverageRuleDAO;
    private final PriorAuthorizationDAO priorAuthDAO;
    private final ReferralDAO referralDAO;

    // Set by NetworkRule (#7); used as per-line fallback in BenefitCalculator (#11)
    private BigDecimal networkCoveragePct = Money.ZERO;

    // Locked accumulator row, acquired by DeductibleRule (#9), held until finalization
    private DeductibleAccumulator accumulator;

    // Running totals finalized after rule 13
    private BigDecimal deductibleAppliedThisClaim = Money.ZERO;
    private BigDecimal oopAppliedThisClaim = Money.ZERO;

    public AdjudicationContext(Claim claim,
                               List<ClaimLineItem> lineItems,
                               List<ClaimDiagnosis> diagnoses,
                               Plan plan,
                               Member member,
                               Provider provider,
                               MemberCoverage activeCoverage,
                               ClaimDAO claimDAO,
                               FeeScheduleRateDAO feeScheduleRateDAO,
                               PlanCoverageRuleDAO planCoverageRuleDAO,
                               PriorAuthorizationDAO priorAuthDAO,
                               ReferralDAO referralDAO) {
        this.claim = claim;
        this.lineItems = lineItems;
        this.diagnoses = diagnoses;
        this.plan = plan;
        this.member = member;
        this.provider = provider;
        this.activeCoverage = activeCoverage;
        this.claimDAO = claimDAO;
        this.feeScheduleRateDAO = feeScheduleRateDAO;
        this.planCoverageRuleDAO = planCoverageRuleDAO;
        this.priorAuthDAO = priorAuthDAO;
        this.referralDAO = referralDAO;
    }

    public Claim getClaim() { return claim; }
    public List<ClaimLineItem> getLineItems() { return lineItems; }
    public List<ClaimDiagnosis> getDiagnoses() { return diagnoses; }
    public Plan getPlan() { return plan; }
    public Member getMember() { return member; }
    public Provider getProvider() { return provider; }
    public MemberCoverage getActiveCoverage() { return activeCoverage; }

    public ClaimDAO getClaimDAO() { return claimDAO; }
    public FeeScheduleRateDAO getFeeScheduleRateDAO() { return feeScheduleRateDAO; }
    public PlanCoverageRuleDAO getPlanCoverageRuleDAO() { return planCoverageRuleDAO; }
    public PriorAuthorizationDAO getPriorAuthDAO() { return priorAuthDAO; }
    public ReferralDAO getReferralDAO() { return referralDAO; }

    public BigDecimal getNetworkCoveragePct() { return networkCoveragePct; }
    public void setNetworkCoveragePct(BigDecimal networkCoveragePct) { this.networkCoveragePct = networkCoveragePct; }

    public DeductibleAccumulator getAccumulator() { return accumulator; }
    public void setAccumulator(DeductibleAccumulator accumulator) { this.accumulator = accumulator; }

    public BigDecimal getDeductibleAppliedThisClaim() { return deductibleAppliedThisClaim; }
    public void setDeductibleAppliedThisClaim(BigDecimal deductibleAppliedThisClaim) {
        this.deductibleAppliedThisClaim = deductibleAppliedThisClaim;
    }

    public BigDecimal getOopAppliedThisClaim() { return oopAppliedThisClaim; }
    public void setOopAppliedThisClaim(BigDecimal oopAppliedThisClaim) {
        this.oopAppliedThisClaim = oopAppliedThisClaim;
    }
}
