package com.meridian.claims.service.adjudication;

public interface AdjudicationRule {
    AdjudicationRuleResult evaluate(AdjudicationContext context);
    String getRuleName();
    String getRuleType();  // "HARD" or "SOFT"
}
