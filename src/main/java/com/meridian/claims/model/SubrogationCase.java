package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class SubrogationCase {

    private int id;
    private int claimId;
    private Date openedDate;
    private String status;          // OPEN / RECOVERED / CLOSED
    private String liableParty;
    private BigDecimal recoveryAmount;
    private String notes;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public Date getOpenedDate() { return openedDate; }
    public void setOpenedDate(Date openedDate) { this.openedDate = openedDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getLiableParty() { return liableParty; }
    public void setLiableParty(String liableParty) { this.liableParty = liableParty; }

    public BigDecimal getRecoveryAmount() { return recoveryAmount; }
    public void setRecoveryAmount(BigDecimal recoveryAmount) { this.recoveryAmount = recoveryAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
