package com.meridian.claims.model;

import java.util.Date;

public class ClaimAuditEntry {

    private int id;
    private int claimId;
    private String eventType;
    private String oldStatus;
    private String newStatus;
    private Integer changedByUserId;
    private String changeReason;
    private String notes;
    private Date changedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public Integer getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Integer changedByUserId) { this.changedByUserId = changedByUserId; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getChangedAt() { return changedAt; }
    public void setChangedAt(Date changedAt) { this.changedAt = changedAt; }
}
