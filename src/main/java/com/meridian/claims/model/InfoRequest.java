package com.meridian.claims.model;

import java.util.Date;

public class InfoRequest {

    private int id;
    private int claimId;
    private String requestedFrom;   // MEMBER / PROVIDER / BOTH
    private int requestedByUserId;
    private Date requestedAt;
    private Date dueDate;
    private String requestNotes;
    private Date responseReceivedAt;
    private String responseNotes;
    private String status;          // OPEN / RESPONDED / WAIVED
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getRequestedFrom() { return requestedFrom; }
    public void setRequestedFrom(String requestedFrom) { this.requestedFrom = requestedFrom; }

    public int getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(int requestedByUserId) { this.requestedByUserId = requestedByUserId; }

    public Date getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Date requestedAt) { this.requestedAt = requestedAt; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public String getRequestNotes() { return requestNotes; }
    public void setRequestNotes(String requestNotes) { this.requestNotes = requestNotes; }

    public Date getResponseReceivedAt() { return responseReceivedAt; }
    public void setResponseReceivedAt(Date responseReceivedAt) { this.responseReceivedAt = responseReceivedAt; }

    public String getResponseNotes() { return responseNotes; }
    public void setResponseNotes(String responseNotes) { this.responseNotes = responseNotes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public boolean isResolved() {
        return "RESPONDED".equals(status) || "WAIVED".equals(status);
    }
}
