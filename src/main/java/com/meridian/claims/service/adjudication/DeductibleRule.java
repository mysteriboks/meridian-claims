package com.meridian.claims.service.adjudication;

import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.util.Money;

import java.math.BigDecimal;

public class DeductibleRule implements AdjudicationRule {

    private final DeductibleAccumulatorDAO accumulatorDAO;

    public DeductibleRule(DeductibleAccumulatorDAO accumulatorDAO) {
        this.accumulatorDAO = accumulatorDAO;
    }

    @Override
    public String getRuleName() { return "DeductibleRule"; }

    @Override
    public String getRuleType() { return "SOFT"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        // Acquire the locked accumulator row (SELECT FOR UPDATE — held until txn ends)
        DeductibleAccumulator acc = accumulatorDAO.findOrCreateForUpdate(
            ctx.getClaim().getMemberId(),
            ctx.getPlan().getId(),
            ctx.getPlan().getBenefitYearStart());
        ctx.setAccumulator(acc);

        BigDecimal planDeductible = ctx.getPlan().getDeductibleAmount();
        BigDecimal remainingDeductible = Money.max(
            Money.ZERO,
            Money.subtract(planDeductible, acc.getDeductibleAccumulated()));

        BigDecimal totalDeductibleApplied = Money.ZERO;
        for (ClaimLineItem item : ctx.getLineItems()) {
            if (item.getAllowedAmount() == null) {
                // NO_RATE line — skip deductible application
                item.setDeductibleApplied(Money.ZERO);
                continue;
            }
            BigDecimal toApply = Money.min(remainingDeductible, item.getAllowedAmount());
            item.setDeductibleApplied(toApply);
            remainingDeductible = Money.subtract(remainingDeductible, toApply);
            totalDeductibleApplied = Money.add(totalDeductibleApplied, toApply);
        }
        // Store running total — will be written to DB in the finalization step after rules 12 and 13
        ctx.setDeductibleAppliedThisClaim(totalDeductibleApplied);

        return AdjudicationRuleResult.passed("Deductible applied: $" + totalDeductibleApplied +
            " (remaining before: $" + Money.max(Money.ZERO, Money.subtract(planDeductible, acc.getDeductibleAccumulated())) + ")");
    }
}
