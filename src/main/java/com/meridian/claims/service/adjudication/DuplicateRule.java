package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimType;

public class DuplicateRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "DuplicateRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        // CORRECTED/VOID are deliberately linked to an original — not duplicates
        if (ctx.getClaim().getClaimType() != ClaimType.ORIGINAL) {
            return AdjudicationRuleResult.passed("Duplicate check skipped for " + ctx.getClaim().getClaimType());
        }
        Claim existing = ctx.getClaimDAO().findByMemberAndDOS(
            ctx.getClaim().getMemberId(),
            ctx.getClaim().getProviderId(),
            ctx.getClaim().getDateOfService());
        if (existing != null && existing.getId() != ctx.getClaim().getId()) {
            return AdjudicationRuleResult.failed(
                "Duplicate of claim " + existing.getClaimNumber() + " (same member/provider/DOS)");
        }
        return AdjudicationRuleResult.passed("No duplicate found");
    }
}
