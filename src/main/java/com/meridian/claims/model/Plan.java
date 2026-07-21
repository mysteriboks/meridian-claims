package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class Plan {

    private int id;
    private String planName;
    private PlanType planType;
    private BigDecimal deductibleAmount;
    private BigDecimal oopMax;
    private BigDecimal copayAmount;
    private BigDecimal coveragePctInNetwork;
    private BigDecimal coveragePctOutNetwork;
    private Date benefitYearStart;
    private int timelyFilingDays;
    private Date deletedAt;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public PlanType getPlanType() { return planType; }
    public void setPlanType(PlanType planType) { this.planType = planType; }

    public BigDecimal getDeductibleAmount() { return deductibleAmount; }
    public void setDeductibleAmount(BigDecimal deductibleAmount) { this.deductibleAmount = deductibleAmount; }

    public BigDecimal getOopMax() { return oopMax; }
    public void setOopMax(BigDecimal oopMax) { this.oopMax = oopMax; }

    public BigDecimal getCopayAmount() { return copayAmount; }
    public void setCopayAmount(BigDecimal copayAmount) { this.copayAmount = copayAmount; }

    public BigDecimal getCoveragePctInNetwork() { return coveragePctInNetwork; }
    public void setCoveragePctInNetwork(BigDecimal coveragePctInNetwork) { this.coveragePctInNetwork = coveragePctInNetwork; }

    public BigDecimal getCoveragePctOutNetwork() { return coveragePctOutNetwork; }
    public void setCoveragePctOutNetwork(BigDecimal coveragePctOutNetwork) { this.coveragePctOutNetwork = coveragePctOutNetwork; }

    public Date getBenefitYearStart() { return benefitYearStart; }
    public void setBenefitYearStart(Date benefitYearStart) { this.benefitYearStart = benefitYearStart; }

    public int getTimelyFilingDays() { return timelyFilingDays; }
    public void setTimelyFilingDays(int timelyFilingDays) { this.timelyFilingDays = timelyFilingDays; }

    public Date getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Date deletedAt) { this.deletedAt = deletedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
