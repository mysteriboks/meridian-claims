package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.MemberCoverage;

import java.util.Date;

public class EligibilityRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "EligibilityRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        MemberCoverage cov = ctx.getActiveCoverage();
        Date dos = ctx.getClaim().getDateOfService();
        if (cov == null) {
            return AdjudicationRuleResult.failed("No active coverage found for member on date of service");
        }
        if (!cov.isActiveOn(dos)) {
            return AdjudicationRuleResult.failed("Coverage is not active on date of service " + dos);
        }
        return AdjudicationRuleResult.passed("Member eligible on date of service");
    }
}
