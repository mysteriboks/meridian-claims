package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Referral;

import java.util.Date;

public class ReferralRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "ReferralRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        // Referral requirement only applies to HMO plans
        if (ctx.getPlan().getPlanType() != PlanType.HMO) {
            return AdjudicationRuleResult.passed("Referral not required for non-HMO plan");
        }
        int memberId = ctx.getClaim().getMemberId();
        Date dos = ctx.getClaim().getDateOfService();

        for (ClaimLineItem item : ctx.getLineItems()) {
            PlanCoverageRule rule = item.getCoverageRule();
            if (rule != null && rule.isRequiresReferral()) {
                Referral referral = ctx.getReferralDAO().findValid(memberId, item.getServiceType(), dos);
                if (referral == null) {
                    return AdjudicationRuleResult.failed(
                        "Referral required but not found for service type " + item.getServiceType());
                }
            }
        }
        return AdjudicationRuleResult.passed("Referral requirements satisfied");
    }
}
