package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class ClaimAccumulatorContribution {

    private int id;
    private int claimId;
    private Date benefitYearStart;
    private BigDecimal deductibleContributed;
    private BigDecimal oopContributed;
    private boolean reversed;
    private Date appliedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public Date getBenefitYearStart() { return benefitYearStart; }
    public void setBenefitYearStart(Date benefitYearStart) { this.benefitYearStart = benefitYearStart; }

    public BigDecimal getDeductibleContributed() { return deductibleContributed; }
    public void setDeductibleContributed(BigDecimal deductibleContributed) { this.deductibleContributed = deductibleContributed; }

    public BigDecimal getOopContributed() { return oopContributed; }
    public void setOopContributed(BigDecimal oopContributed) { this.oopContributed = oopContributed; }

    public boolean isReversed() { return reversed; }
    public void setReversed(boolean reversed) { this.reversed = reversed; }

    public Date getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Date appliedAt) { this.appliedAt = appliedAt; }
}
