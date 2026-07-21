package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimType;

public class TimelyFilingRule implements AdjudicationRule {

    @Override
    public String getRuleName() { return "TimelyFilingRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        // CORRECTED/VOID are exempt — timely filing is measured on the original
        if (ctx.getClaim().getClaimType() != ClaimType.ORIGINAL) {
            return AdjudicationRuleResult.passed("Timely filing not evaluated for " + ctx.getClaim().getClaimType());
        }
        long dos = ctx.getClaim().getDateOfService().getTime();
        long sub = ctx.getClaim().getSubmissionDate().getTime();
        long diffDays = (sub - dos) / (1000L * 60 * 60 * 24);
        int limit = ctx.getPlan().getTimelyFilingDays();
        if (diffDays <= limit) {
            return AdjudicationRuleResult.passed("Filed within " + diffDays + " days (limit " + limit + ")");
        }
        return AdjudicationRuleResult.failed("Filed " + diffDays + " days after DOS; limit is " + limit + " days");
    }
}
