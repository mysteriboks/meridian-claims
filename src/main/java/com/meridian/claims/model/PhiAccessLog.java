package com.meridian.claims.model;

import java.util.Date;

public class PhiAccessLog {

    private int id;
    private Integer userId;
    private int memberId;
    private int claimId;
    private String action;
    private Date accessedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Date getAccessedAt() { return accessedAt; }
    public void setAccessedAt(Date accessedAt) { this.accessedAt = accessedAt; }
}
