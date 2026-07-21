package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;

public class BenefitCalculator implements AdjudicationRule {

    @Override
    public String getRuleName() { return "BenefitCalculator"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        for (ClaimLineItem item : ctx.getLineItems()) {
            if (item.getAllowedAmount() == null) {
                // NO_RATE — cannot compute benefit; leave plan_paid and member_resp null
                item.setPlanPaidAmount(null);
                item.setMemberResponsibility(null);
                continue;
            }

            BigDecimal allowed = item.getAllowedAmount();
            BigDecimal deductible = item.getDeductibleApplied() != null ? item.getDeductibleApplied() : Money.ZERO;
            BigDecimal copay = item.getCopayApplied() != null ? item.getCopayApplied() : Money.ZERO;

            // Coinsurance base = allowed - deductible - copay (floored at 0)
            BigDecimal coinsuranceBase = Money.max(Money.ZERO, Money.subtract(allowed, Money.add(deductible, copay)));

            // Coverage % — service-type rule takes precedence over network fallback
            PlanCoverageRule rule = item.getCoverageRule();
            BigDecimal coveragePct = (rule != null && rule.getCoveragePct() != null)
                ? rule.getCoveragePct()
                : ctx.getNetworkCoveragePct();

            BigDecimal planPaid = Money.percentOf(coinsuranceBase, coveragePct);
            BigDecimal memberCoinsurance = Money.subtract(coinsuranceBase, planPaid);

            item.setPlanPaidAmount(planPaid);
            item.setMemberResponsibility(Money.add(Money.add(deductible, copay), memberCoinsurance));
        }
        return AdjudicationRuleResult.passed("Benefit amounts calculated for all line items");
    }
}
