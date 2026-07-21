package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class ClaimLineItem {

    private int id;
    private int claimId;
    private String procedureCode;
    private String description;
    private BigDecimal billedAmount;
    private BigDecimal allowedAmount;
    private BigDecimal planPaidAmount;
    private BigDecimal memberResponsibility;
    private BigDecimal deductibleApplied;
    private BigDecimal copayApplied;
    private String rateSource;  // RateSource.name()
    private String adjustmentReasonCode;
    private Date createdAt;

    // Transient — set by the adjudication engine from procedure_codes.service_type
    private transient String serviceType;
    // Transient — matched plan_coverage_rules row (nullable)
    private transient PlanCoverageRule coverageRule;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getBilledAmount() { return billedAmount; }
    public void setBilledAmount(BigDecimal billedAmount) { this.billedAmount = billedAmount; }

    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal allowedAmount) { this.allowedAmount = allowedAmount; }

    public BigDecimal getPlanPaidAmount() { return planPaidAmount; }
    public void setPlanPaidAmount(BigDecimal planPaidAmount) { this.planPaidAmount = planPaidAmount; }

    public BigDecimal getMemberResponsibility() { return memberResponsibility; }
    public void setMemberResponsibility(BigDecimal memberResponsibility) { this.memberResponsibility = memberResponsibility; }

    public BigDecimal getDeductibleApplied() { return deductibleApplied; }
    public void setDeductibleApplied(BigDecimal deductibleApplied) { this.deductibleApplied = deductibleApplied; }

    public BigDecimal getCopayApplied() { return copayApplied; }
    public void setCopayApplied(BigDecimal copayApplied) { this.copayApplied = copayApplied; }

    public String getRateSource() { return rateSource; }
    public void setRateSource(String rateSource) { this.rateSource = rateSource; }

    public String getAdjustmentReasonCode() { return adjustmentReasonCode; }
    public void setAdjustmentReasonCode(String adjustmentReasonCode) { this.adjustmentReasonCode = adjustmentReasonCode; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public PlanCoverageRule getCoverageRule() { return coverageRule; }
    public void setCoverageRule(PlanCoverageRule coverageRule) { this.coverageRule = coverageRule; }
}
