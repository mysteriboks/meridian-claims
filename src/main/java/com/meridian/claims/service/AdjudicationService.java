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
import com.meridian.claims.model.AdjudicationResult;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimAccumulatorContribution;
import com.meridian.claims.model.ClaimDiagnosis;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.RateSource;
import com.meridian.claims.service.adjudication.AdjudicationContext;
import com.meridian.claims.service.adjudication.AdjudicationRule;
import com.meridian.claims.service.adjudication.AdjudicationRuleResult;
import com.meridian.claims.service.adjudication.BenefitCalculator;
import com.meridian.claims.service.adjudication.CobAdjustmentRule;
import com.meridian.claims.service.adjudication.CopayRule;
import com.meridian.claims.service.adjudication.CoverageRule;
import com.meridian.claims.service.adjudication.DeductibleRule;
import com.meridian.claims.service.adjudication.DuplicateRule;
import com.meridian.claims.service.adjudication.EligibilityRule;
import com.meridian.claims.service.adjudication.FeeScheduleRule;
import com.meridian.claims.service.adjudication.NetworkRule;
import com.meridian.claims.service.adjudication.OopMaxRule;
import com.meridian.claims.service.adjudication.PriorAuthRule;
import com.meridian.claims.service.adjudication.ReferralRule;
import com.meridian.claims.service.adjudication.TimelyFilingRule;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the 13-rule adjudication chain for a single claim.
 * Must be called inside an active @Transactional context (provided by ClaimService).
 */
@Service
public class AdjudicationService {

    private static final Logger LOG = Logger.getLogger(AdjudicationService.class);

    // Denial reason codes matching V4/V6 seeds
    private static final String DENIAL_TIMELY_FILING = "TIMELY_FILING";
    private static final String DENIAL_NOT_ELIGIBLE   = "NOT_ELIGIBLE";
    private static final String DENIAL_DUPLICATE      = "DUPLICATE";
    private static final String DENIAL_NOT_COVERED    = "NOT_COVERED";
    private static final String DENIAL_NO_AUTH        = "NO_AUTH";
    private static final String DENIAL_NO_REFERRAL    = "NO_REFERRAL";

    @Value("${claims.auto.approve.threshold:500.00}")
    private String autoApproveThresholdStr;

    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO claimLineItemDAO;
    @Autowired private FeeScheduleRateDAO feeScheduleRateDAO;
    @Autowired private PlanCoverageRuleDAO planCoverageRuleDAO;
    @Autowired private PriorAuthorizationDAO priorAuthDAO;
    @Autowired private ReferralDAO referralDAO;
    @Autowired private DeductibleAccumulatorDAO accumulatorDAO;
    @Autowired private ClaimAccumulatorContributionDAO contributionDAO;
    @Autowired private AdjudicationResultsDAO adjudicationResultsDAO;
    @Autowired private LookupDAO lookupDAO;

    /**
     * Adjudicates the claim, writes adjudication_results, writes accumulator
     * contribution (on non-DENIED outcome), and updates line item amounts.
     *
     * @return the target ClaimStatus after adjudication (never SUBMITTED)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public ClaimStatus adjudicate(Claim claim,
                                  List<ClaimLineItem> lineItems,
                                  List<ClaimDiagnosis> diagnoses,
                                  Plan plan,
                                  Member member,
                                  Provider provider,
                                  MemberCoverage activeCoverage,
                                  int runId) {
        List<ProcedureCode> procedureCodes = lookupDAO.findAllProcedureCodes();

        AdjudicationContext ctx = new AdjudicationContext(
            claim, lineItems, diagnoses, plan, member, provider, activeCoverage,
            claimDAO, feeScheduleRateDAO, planCoverageRuleDAO, priorAuthDAO, referralDAO);

        List<AdjudicationRule> rules = buildRuleChain(procedureCodes);
        List<AdjudicationResult> results = new ArrayList<AdjudicationResult>();

        for (int i = 0; i < rules.size(); i++) {
            AdjudicationRule rule = rules.get(i);
            AdjudicationRuleResult ruleResult;
            try {
                ruleResult = rule.evaluate(ctx);
            } catch (Exception e) {
                LOG.error("Rule " + rule.getRuleName() + " threw exception for claim " + claim.getClaimNumber(), e);
                ruleResult = AdjudicationRuleResult.failed("Rule evaluation error: " + e.getMessage());
            }

            AdjudicationResult ar = buildAdjudicationResult(claim.getId(), runId, i + 1, rule, ruleResult);
            results.add(ar);

            if ("HARD".equals(rule.getRuleType()) && !ruleResult.isPassed()) {
                // Hard rule failed — mark remaining rules as skipped, return DENIED
                for (int j = i + 1; j < rules.size(); j++) {
                    AdjudicationRule skipped = rules.get(j);
                    AdjudicationResult skip = new AdjudicationResult();
                    skip.setClaimId(claim.getId());
                    skip.setRunId(runId);
                    skip.setStepNumber(j + 1);
                    skip.setRuleName(skipped.getRuleName());
                    skip.setRuleType(skipped.getRuleType());
                    skip.setPassed(false);
                    skip.setReason("skipped — prior hard rule failed: " + rule.getRuleName());
                    results.add(skip);
                }
                adjudicationResultsDAO.insertBatch(results);
                claim.setDenialReasonCode(mapDenialCode(rule.getRuleName()));
                return ClaimStatus.DENIED;
            }
        }

        adjudicationResultsDAO.insertBatch(results);

        // Finalization — persist adjudicated financial amounts on each line item
        for (ClaimLineItem item : lineItems) {
            claimLineItemDAO.updateAmounts(item);
        }

        writeAccumulatorContribution(claim, ctx, plan);

        return determineDisposition(claim, lineItems);
    }

    private void writeAccumulatorContribution(Claim claim, AdjudicationContext ctx, Plan plan) {
        DeductibleAccumulator acc = ctx.getAccumulator();
        if (acc == null) {
            // No accumulator acquired (can happen if DeductibleRule short-circuited or was skipped)
            return;
        }
        // Recompute contributions from the FINAL line-item state rather than the running totals
        // captured mid-chain. The OOP cap (OopMaxRule) and COB (CobAdjustmentRule) can lower member
        // responsibility after the deductible was applied, so the contribution row must reflect what
        // the member actually paid — otherwise the reversal on VOID would over-credit the accumulator.
        BigDecimal oopContrib = Money.ZERO;
        BigDecimal deductibleApplied = Money.ZERO;
        for (ClaimLineItem item : ctx.getLineItems()) {
            if (item.getMemberResponsibility() != null) {
                oopContrib = Money.add(oopContrib, item.getMemberResponsibility());
            }
            if (item.getDeductibleApplied() != null) {
                deductibleApplied = Money.add(deductibleApplied, item.getDeductibleApplied());
            }
        }
        // Deductible counts toward OOP and is a subset of member responsibility, so it can never
        // exceed the final OOP contribution (relevant when the OOP cap reduced member responsibility).
        BigDecimal deductibleContrib = Money.min(deductibleApplied, oopContrib);

        acc.setDeductibleAccumulated(Money.add(acc.getDeductibleAccumulated(), deductibleContrib));
        acc.setOopAccumulated(Money.add(acc.getOopAccumulated(), oopContrib));
        accumulatorDAO.update(acc);

        ClaimAccumulatorContribution contribution = new ClaimAccumulatorContribution();
        contribution.setClaimId(claim.getId());
        contribution.setBenefitYearStart(plan.getBenefitYearStart());
        contribution.setDeductibleContributed(deductibleContrib);
        contribution.setOopContributed(oopContrib);
        contribution.setReversed(false);
        contributionDAO.insert(contribution);
    }

    private ClaimStatus determineDisposition(Claim claim, List<ClaimLineItem> lineItems) {
        // Any NO_RATE line → manual review
        for (ClaimLineItem item : lineItems) {
            if (RateSource.NO_RATE.name().equals(item.getRateSource())) {
                return ClaimStatus.IN_REVIEW;
            }
        }
        // CORRECTED always routes to IN_REVIEW. Accumulator reversal of the original
        // claim and the PAID→REPLACED transition are deferred to a future phase.
        if (claim.getClaimType() != null) {
            switch (claim.getClaimType()) {
                case CORRECTED:
                    return ClaimStatus.IN_REVIEW;
                default:
                    break;
            }
        }
        // SECONDARY COB → manual review
        if ("SECONDARY".equals(claim.getCoverageOrder())) {
            return ClaimStatus.IN_REVIEW;
        }
        // Large claim → manual review
        BigDecimal totalPlanPaid = Money.ZERO;
        for (ClaimLineItem item : lineItems) {
            if (item.getPlanPaidAmount() != null) {
                totalPlanPaid = Money.add(totalPlanPaid, item.getPlanPaidAmount());
            }
        }
        BigDecimal threshold = Money.of(autoApproveThresholdStr);
        if (totalPlanPaid.compareTo(threshold) > 0) {
            return ClaimStatus.IN_REVIEW;
        }
        return ClaimStatus.APPROVED;
    }

    private List<AdjudicationRule> buildRuleChain(List<ProcedureCode> procedureCodes) {
        List<AdjudicationRule> rules = new ArrayList<AdjudicationRule>();
        rules.add(new TimelyFilingRule());
        rules.add(new EligibilityRule());
        rules.add(new DuplicateRule());
        rules.add(new CoverageRule(procedureCodes));
        rules.add(new PriorAuthRule());
        rules.add(new ReferralRule());
        rules.add(new NetworkRule());
        rules.add(new FeeScheduleRule());
        rules.add(new DeductibleRule(accumulatorDAO));
        rules.add(new CopayRule());
        rules.add(new BenefitCalculator());
        rules.add(new OopMaxRule());
        rules.add(new CobAdjustmentRule());
        return rules;
    }

    private AdjudicationResult buildAdjudicationResult(int claimId, int runId, int stepNumber,
                                                        AdjudicationRule rule,
                                                        AdjudicationRuleResult ruleResult) {
        AdjudicationResult ar = new AdjudicationResult();
        ar.setClaimId(claimId);
        ar.setRunId(runId);
        ar.setStepNumber(stepNumber);
        ar.setRuleName(rule.getRuleName());
        ar.setRuleType(rule.getRuleType());
        ar.setPassed(ruleResult.isPassed());
        ar.setReason(ruleResult.getReason());
        return ar;
    }

    private String mapDenialCode(String ruleName) {
        if ("TimelyFilingRule".equals(ruleName)) return DENIAL_TIMELY_FILING;
        if ("EligibilityRule".equals(ruleName))  return DENIAL_NOT_ELIGIBLE;
        if ("DuplicateRule".equals(ruleName))     return DENIAL_DUPLICATE;
        if ("CoverageRule".equals(ruleName))      return DENIAL_NOT_COVERED;
        if ("PriorAuthRule".equals(ruleName))     return DENIAL_NO_AUTH;
        if ("ReferralRule".equals(ruleName))      return DENIAL_NO_REFERRAL;
        return "DENIED";
    }
}
