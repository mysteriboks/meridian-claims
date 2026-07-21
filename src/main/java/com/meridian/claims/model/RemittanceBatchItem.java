package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class RemittanceBatchItem {

    private int id;
    private int batchId;
    private int claimId;
    private int providerId;
    private BigDecimal billed;
    private BigDecimal allowed;
    private BigDecimal planPaid;
    private String adjustmentReasonCode;
    private String procedureCode;
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBatchId() { return batchId; }
    public void setBatchId(int batchId) { this.batchId = batchId; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getProviderId() { return providerId; }
    public void setProviderId(int providerId) { this.providerId = providerId; }

    public BigDecimal getBilled() { return billed; }
    public void setBilled(BigDecimal billed) { this.billed = billed; }

    public BigDecimal getAllowed() { return allowed; }
    public void setAllowed(BigDecimal allowed) { this.allowed = allowed; }

    public BigDecimal getPlanPaid() { return planPaid; }
    public void setPlanPaid(BigDecimal planPaid) { this.planPaid = planPaid; }

    public String getAdjustmentReasonCode() { return adjustmentReasonCode; }
    public void setAdjustmentReasonCode(String adjustmentReasonCode) { this.adjustmentReasonCode = adjustmentReasonCode; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
