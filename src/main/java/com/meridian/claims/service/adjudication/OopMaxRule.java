package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;
import java.util.List;

public class OopMaxRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "OopMaxRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        if (ctx.getAccumulator() == null) {
            return AdjudicationRuleResult.passed("OOP max skipped — accumulator not acquired");
        }
        BigDecimal oopMax = ctx.getPlan().getOopMax();
        BigDecimal oopAccumulated = ctx.getAccumulator().getOopAccumulated();
        BigDecimal remainingOop = Money.max(Money.ZERO, Money.subtract(oopMax, oopAccumulated));

        // Sum total member responsibility across all lines that have a computed value
        BigDecimal totalMemberResp = Money.ZERO;
        List<ClaimLineItem> items = ctx.getLineItems();
        for (ClaimLineItem item : items) {
            if (item.getMemberResponsibility() != null) {
                totalMemberResp = Money.add(totalMemberResp, item.getMemberResponsibility());
            }
        }

        if (totalMemberResp.compareTo(remainingOop) > 0) {
            // Cap member at remaining OOP; shift excess to plan.
            BigDecimal excess = Money.subtract(totalMemberResp, remainingOop);
            // Distribute the excess proportionally across lines, accumulating the running
            // distributed sum and tracking the last processed line. Proportional rounding can
            // leave a sub-cent residual, so the last processed line absorbs the remainder
            // (deterministic, audit-friendly) — see HIGH_LEVEL_DESIGN.md §10.
            BigDecimal distributed = Money.ZERO;
            ClaimLineItem lastProcessed = null;
            for (ClaimLineItem item : items) {
                if (item.getMemberResponsibility() == null || item.getPlanPaidAmount() == null) {
                    continue;
                }
                if (totalMemberResp.compareTo(Money.ZERO) == 0) {
                    continue;
                }
                BigDecimal fraction = item.getMemberResponsibility()
                    .divide(totalMemberResp, 10, Money.ROUNDING);
                BigDecimal itemExcess = Money.scale(excess.multiply(fraction));
                item.setMemberResponsibility(Money.subtract(item.getMemberResponsibility(), itemExcess));
                item.setPlanPaidAmount(Money.add(item.getPlanPaidAmount(), itemExcess));
                distributed = Money.add(distributed, itemExcess);
                lastProcessed = item;
            }
            // Apply any residual to the last processed line so the total shifted equals `excess` exactly.
            if (lastProcessed != null) {
                BigDecimal residual = Money.subtract(excess, distributed);
                if (residual.compareTo(Money.ZERO) != 0) {
                    lastProcessed.setMemberResponsibility(
                        Money.subtract(lastProcessed.getMemberResponsibility(), residual));
                    lastProcessed.setPlanPaidAmount(
                        Money.add(lastProcessed.getPlanPaidAmount(), residual));
                }
            }
            ctx.setOopAppliedThisClaim(remainingOop);
        } else {
            ctx.setOopAppliedThisClaim(totalMemberResp);
        }

        return AdjudicationRuleResult.passed("OOP cap applied; member OOP this claim: $" + ctx.getOopAppliedThisClaim());
    }
}
