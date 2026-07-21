package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.RateSource;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;
import java.util.Date;

public class FeeScheduleRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "FeeScheduleRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        int planId = ctx.getPlan().getId();
        int providerId = ctx.getClaim().getProviderId();
        Date dos = ctx.getClaim().getDateOfService();
        boolean anyNoRate = false;

        for (ClaimLineItem item : ctx.getLineItems()) {
            // Check provider-specific first, then plan-wide
            BigDecimal providerRate = ctx.getFeeScheduleRateDAO()
                .resolveAllowedAmount(planId, providerId, item.getProcedureCode(), dos);
            if (providerRate != null) {
                // Cap at billed — plan must never pay more than the submitted charge.
                item.setAllowedAmount(Money.min(providerRate, item.getBilledAmount()));
                item.setRateSource(RateSource.PROVIDER_SPECIFIC.name());
                continue;
            }
            BigDecimal planRate = ctx.getFeeScheduleRateDAO()
                .resolveAllowedAmount(planId, null, item.getProcedureCode(), dos);
            if (planRate != null) {
                item.setAllowedAmount(Money.min(planRate, item.getBilledAmount()));
                item.setRateSource(RateSource.PLAN_WIDE.name());
                continue;
            }
            // No rate found — mark NO_RATE, never zero
            item.setAllowedAmount(null);
            item.setRateSource(RateSource.NO_RATE.name());
            anyNoRate = true;
        }
        if (anyNoRate) {
            return AdjudicationRuleResult.passed(
                "One or more line items have no fee schedule rate (NO_RATE); claim will route to manual review");
        }
        return AdjudicationRuleResult.passed("Fee schedule rates resolved for all line items");
    }
}
