package com.meridian.claims.model;

import java.util.Date;

public class MemberCoverage {

    private int id;
    private int memberId;
    private int planId;
    private String planName;
    private CoverageOrder coverageOrder;
    private Date effectiveDate;
    private Date terminationDate;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public CoverageOrder getCoverageOrder() { return coverageOrder; }
    public void setCoverageOrder(CoverageOrder coverageOrder) { this.coverageOrder = coverageOrder; }

    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }

    public Date getTerminationDate() { return terminationDate; }
    public void setTerminationDate(Date terminationDate) { this.terminationDate = terminationDate; }

    public boolean isActiveOn(Date date) {
        if (effectiveDate != null && date.before(effectiveDate)) {
            return false;
        }
        if (terminationDate != null && date.after(terminationDate)) {
            return false;
        }
        return true;
    }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
