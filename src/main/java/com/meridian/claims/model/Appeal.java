package com.meridian.claims.model;

import java.util.Date;

public class Appeal {

    private int id;
    private int claimId;
    private int memberId;
    private String appealType;          // INTERNAL / EXTERNAL
    private Date submittedDate;
    private Date deadlineDate;
    private String status;              // OPEN / APPROVED / DENIED / WITHDRAWN
    private Integer assignedTo;
    private Date resolvedDate;
    private String outcome;             // APPROVED / DENIED / WITHDRAWN
    private String outcomeNotes;
    private Integer submittedByUserId;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getAppealType() { return appealType; }
    public void setAppealType(String appealType) { this.appealType = appealType; }

    public Date getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(Date submittedDate) { this.submittedDate = submittedDate; }

    public Date getDeadlineDate() { return deadlineDate; }
    public void setDeadlineDate(Date deadlineDate) { this.deadlineDate = deadlineDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Integer assignedTo) { this.assignedTo = assignedTo; }

    public Date getResolvedDate() { return resolvedDate; }
    public void setResolvedDate(Date resolvedDate) { this.resolvedDate = resolvedDate; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getOutcomeNotes() { return outcomeNotes; }
    public void setOutcomeNotes(String outcomeNotes) { this.outcomeNotes = outcomeNotes; }

    public Integer getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Integer submittedByUserId) { this.submittedByUserId = submittedByUserId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
