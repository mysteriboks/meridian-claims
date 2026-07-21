package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class FeeScheduleRate {

    private int id;
    private int planId;
    private String planName;
    private Integer providerId;
    private String providerName;
    private String procedureCode;
    private BigDecimal allowedAmount;
    private Date effectiveDate;
    private Date terminationDate;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public Integer getProviderId() { return providerId; }
    public void setProviderId(Integer providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public BigDecimal getAllowedAmount() { return allowedAmount; }
    public void setAllowedAmount(BigDecimal allowedAmount) { this.allowedAmount = allowedAmount; }

    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }

    public Date getTerminationDate() { return terminationDate; }
    public void setTerminationDate(Date terminationDate) { this.terminationDate = terminationDate; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
