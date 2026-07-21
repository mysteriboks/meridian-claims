package com.meridian.claims.model;

import java.util.Date;

public class EobDocument {

    private int id;
    private int claimId;
    private int memberId;
    private String content;
    private String deliveryMethod;  // PENDING / MAILED / EMAILED
    private Date deliveredAt;
    private Integer mailedByUserId;
    private Date generatedAt;
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public Date getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Date deliveredAt) { this.deliveredAt = deliveredAt; }

    public Integer getMailedByUserId() { return mailedByUserId; }
    public void setMailedByUserId(Integer mailedByUserId) { this.mailedByUserId = mailedByUserId; }

    public Date getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
