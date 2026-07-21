package com.meridian.claims.model;

import java.util.Date;

public class SlaBreach {

    private int id;
    private int claimId;
    private String status;
    private Date expectedBy;
    private Date breachedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getExpectedBy() { return expectedBy; }
    public void setExpectedBy(Date expectedBy) { this.expectedBy = expectedBy; }

    public Date getBreachedAt() { return breachedAt; }
    public void setBreachedAt(Date breachedAt) { this.breachedAt = breachedAt; }
}
