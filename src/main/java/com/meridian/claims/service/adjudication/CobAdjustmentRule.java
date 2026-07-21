package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rule 13 — COB adjustment (non-duplication, capped).
 *
 * Only acts when coverage_order == SECONDARY.
 * Secondary plan pays: min(normalPlanLiability, max(0, totalAllowed - cobPrimaryPaid))
 *
 * Worked example: allowed=$1000, normal liability=$616, primary paid=$500
 *   finalPlanPaid = min(616, max(0, 1000-500)) = min(616, 500) = $500
 *   member = 1000 - 500(primary) - 500(secondary) = $0
 */
public class CobAdjustmentRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "CobAdjustmentRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        if (!"SECONDARY".equals(ctx.getClaim().getCoverageOrder())) {
            return AdjudicationRuleResult.passed("COB adjustment not applicable (PRIMARY coverage)");
        }

        BigDecimal cobPrimaryPaid = ctx.getClaim().getCobPrimaryPaid();
        if (cobPrimaryPaid == null) {
            return AdjudicationRuleResult.passed("No primary paid amount provided; COB adjustment skipped");
        }

        List<ClaimLineItem> items = ctx.getLineItems();

        // Sum total allowed and normal plan liability across lines
        BigDecimal totalAllowed = Money.ZERO;
        BigDecimal normalPlanLiability = Money.ZERO;
        for (ClaimLineItem item : items) {
            if (item.getAllowedAmount() != null) {
                totalAllowed = Money.add(totalAllowed, item.getAllowedAmount());
            }
            if (item.getPlanPaidAmount() != null) {
                normalPlanLiability = Money.add(normalPlanLiability, item.getPlanPaidAmount());
            }
        }

        // Non-duplication capped formula
        BigDecimal cap = Money.max(Money.ZERO, Money.subtract(totalAllowed, cobPrimaryPaid));
        BigDecimal finalPlanPaid = Money.min(normalPlanLiability, cap);

        if (normalPlanLiability.compareTo(Money.ZERO) == 0) {
            return AdjudicationRuleResult.passed("Normal plan liability is zero; no COB adjustment needed");
        }

        // Distribute finalPlanPaid (and the primary-paid offset) proportionally across lines.
        // Proportional rounding can leave a sub-cent residual on both distributions, so the last
        // processed line absorbs both remainders — deterministic, audit-friendly (HIGH_LEVEL_DESIGN.md §10).
        BigDecimal distributedPlanPaid = Money.ZERO;
        BigDecimal distributedPrimary = Money.ZERO;
        ClaimLineItem lastProcessed = null;
        BigDecimal lastPrimaryProportion = Money.ZERO;
        for (ClaimLineItem item : items) {
            if (item.getPlanPaidAmount() == null || item.getAllowedAmount() == null) {
                continue;
            }
            BigDecimal fraction = item.getPlanPaidAmount().divide(normalPlanLiability, 10, Money.ROUNDING);
            BigDecimal itemPlanPaid = Money.scale(finalPlanPaid.multiply(fraction));
            item.setPlanPaidAmount(itemPlanPaid);

            // member = allowed - primaryPaid(proportional) - secondaryPaid, floored at 0
            BigDecimal primaryProportion = Money.scale(cobPrimaryPaid.multiply(fraction));
            BigDecimal memberResp = Money.max(Money.ZERO,
                Money.subtract(item.getAllowedAmount(), Money.add(primaryProportion, itemPlanPaid)));
            item.setMemberResponsibility(memberResp);

            distributedPlanPaid = Money.add(distributedPlanPaid, itemPlanPaid);
            distributedPrimary = Money.add(distributedPrimary, primaryProportion);
            lastProcessed = item;
            lastPrimaryProportion = primaryProportion;
        }
        // Absorb both residuals on the last processed line and recompute its member responsibility
        // from the corrected components so Σ planPaid == finalPlanPaid and Σ primary == cobPrimaryPaid.
        if (lastProcessed != null) {
            BigDecimal planResidual = Money.subtract(finalPlanPaid, distributedPlanPaid);
            BigDecimal primaryResidual = Money.subtract(cobPrimaryPaid, distributedPrimary);
            if (planResidual.compareTo(Money.ZERO) != 0 || primaryResidual.compareTo(Money.ZERO) != 0) {
                BigDecimal correctedPlanPaid = Money.add(lastProcessed.getPlanPaidAmount(), planResidual);
                BigDecimal correctedPrimary = Money.add(lastPrimaryProportion, primaryResidual);
                lastProcessed.setPlanPaidAmount(correctedPlanPaid);
                lastProcessed.setMemberResponsibility(Money.max(Money.ZERO,
                    Money.subtract(lastProcessed.getAllowedAmount(),
                        Money.add(correctedPrimary, correctedPlanPaid))));
            }
        }

        // Recompute OOP contribution from final member responsibility post-COB
        BigDecimal finalMemberResp = Money.ZERO;
        for (ClaimLineItem item : items) {
            if (item.getMemberResponsibility() != null) {
                finalMemberResp = Money.add(finalMemberResp, item.getMemberResponsibility());
            }
        }
        ctx.setOopAppliedThisClaim(finalMemberResp);

        return AdjudicationRuleResult.passed(
            "COB adjusted: finalPlanPaid=$" + finalPlanPaid + " (normal=$" + normalPlanLiability +
            ", cap=$" + cap + ", primaryPaid=$" + cobPrimaryPaid + ")");
    }
}
