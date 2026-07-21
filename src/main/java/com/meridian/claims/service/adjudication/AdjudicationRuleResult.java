package com.meridian.claims.service.adjudication;

public class AdjudicationRuleResult {

    private final boolean passed;
    private final String reason;

    private AdjudicationRuleResult(boolean passed, String reason) {
        this.passed = passed;
        this.reason = reason;
    }

    public static AdjudicationRuleResult passed(String reason) {
        return new AdjudicationRuleResult(true, reason);
    }

    public static AdjudicationRuleResult failed(String reason) {
        return new AdjudicationRuleResult(false, reason);
    }

    public boolean isPassed() { return passed; }
    public String getReason() { return reason; }
}
