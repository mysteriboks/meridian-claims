package com.meridian.claims.model;

import java.util.Date;

public class ClaimNote {

    private int id;
    private int claimId;
    private int authorUserId;
    private String note;
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(int authorUserId) { this.authorUserId = authorUserId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
