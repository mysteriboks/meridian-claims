package com.meridian.claims.model;

import java.util.Date;

public class Referral {

    private int id;
    private int memberId;
    private String memberName;
    private int referringProviderId;
    private String referringProviderName;
    private int referredToProviderId;
    private String referredToProviderName;
    private String serviceType;
    private Date validFrom;
    private Date validTo;
    private String referralNumber;
    private ReferralStatus status;
    private String notes;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public int getReferringProviderId() { return referringProviderId; }
    public void setReferringProviderId(int referringProviderId) { this.referringProviderId = referringProviderId; }

    public String getReferringProviderName() { return referringProviderName; }
    public void setReferringProviderName(String referringProviderName) { this.referringProviderName = referringProviderName; }

    public int getReferredToProviderId() { return referredToProviderId; }
    public void setReferredToProviderId(int referredToProviderId) { this.referredToProviderId = referredToProviderId; }

    public String getReferredToProviderName() { return referredToProviderName; }
    public void setReferredToProviderName(String referredToProviderName) { this.referredToProviderName = referredToProviderName; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }

    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }

    public String getReferralNumber() { return referralNumber; }
    public void setReferralNumber(String referralNumber) { this.referralNumber = referralNumber; }

    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
