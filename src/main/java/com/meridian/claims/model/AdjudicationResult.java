package com.meridian.claims.model;

import java.util.Date;

public class AdjudicationResult {

    private int id;
    private int claimId;
    private int runId;
    private int stepNumber;
    private String ruleName;
    private String ruleType;  // HARD / SOFT
    private boolean passed;
    private String reason;
    private Date evaluatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getRunId() { return runId; }
    public void setRunId(int runId) { this.runId = runId; }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Date getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Date evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
