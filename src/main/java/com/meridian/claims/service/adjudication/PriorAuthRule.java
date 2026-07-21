package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.model.PriorAuthorization;

import java.util.Date;

public class PriorAuthRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "PriorAuthRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        int memberId = ctx.getClaim().getMemberId();
        Date dos = ctx.getClaim().getDateOfService();

        for (ClaimLineItem item : ctx.getLineItems()) {
            PlanCoverageRule rule = item.getCoverageRule();
            if (rule != null && rule.isRequiresPriorAuth()) {
                PriorAuthorization auth = ctx.getPriorAuthDAO().findValid(memberId, item.getProcedureCode(), dos);
                if (auth == null) {
                    return AdjudicationRuleResult.failed(
                        "Prior authorization required but not found for procedure " + item.getProcedureCode());
                }
            }
        }
        return AdjudicationRuleResult.passed("Prior authorization requirements satisfied");
    }
}
