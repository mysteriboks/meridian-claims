package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CopayRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "CopayRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        // Copay applies once per distinct service type present on the claim
        Set<String> copayedServiceTypes = new HashSet<String>();
        List<ClaimLineItem> items = ctx.getLineItems();

        for (ClaimLineItem item : items) {
            if (item.getAllowedAmount() == null) {
                item.setCopayApplied(Money.ZERO);
                continue;
            }
            String serviceType = item.getServiceType();
            if (serviceType == null || copayedServiceTypes.contains(serviceType)) {
                item.setCopayApplied(Money.ZERO);
                continue;
            }
            // One copay per distinct service type, sourced from plan.copay_amount
            BigDecimal copay = ctx.getPlan().getCopayAmount();
            if (copay == null) {
                copay = Money.ZERO;
            }
            item.setCopayApplied(copay);
            copayedServiceTypes.add(serviceType);
        }
        return AdjudicationRuleResult.passed("Copay applied for " + copayedServiceTypes.size() + " service type(s)");
    }
}
