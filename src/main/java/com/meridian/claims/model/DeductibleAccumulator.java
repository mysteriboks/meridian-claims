package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class DeductibleAccumulator {

    private int id;
    private int memberId;
    private int planId;
    private Date benefitYearStart;
    private BigDecimal deductibleAccumulated;
    private BigDecimal oopAccumulated;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public Date getBenefitYearStart() { return benefitYearStart; }
    public void setBenefitYearStart(Date benefitYearStart) { this.benefitYearStart = benefitYearStart; }

    public BigDecimal getDeductibleAccumulated() { return deductibleAccumulated; }
    public void setDeductibleAccumulated(BigDecimal deductibleAccumulated) { this.deductibleAccumulated = deductibleAccumulated; }

    public BigDecimal getOopAccumulated() { return oopAccumulated; }
    public void setOopAccumulated(BigDecimal oopAccumulated) { this.oopAccumulated = oopAccumulated; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
