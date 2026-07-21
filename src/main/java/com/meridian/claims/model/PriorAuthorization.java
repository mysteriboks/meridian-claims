package com.meridian.claims.model;

import java.util.Date;

public class PriorAuthorization {

    private int id;
    private int memberId;
    private String memberName;
    private int providerId;
    private String providerName;
    private String procedureCode;
    private String serviceType;
    private Date authorizedFrom;
    private Date authorizedTo;
    private String authNumber;
    private PriorAuthStatus status;
    private int approvedUnits;
    private String notes;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public int getProviderId() { return providerId; }
    public void setProviderId(int providerId) { this.providerId = providerId; }

    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public Date getAuthorizedFrom() { return authorizedFrom; }
    public void setAuthorizedFrom(Date authorizedFrom) { this.authorizedFrom = authorizedFrom; }

    public Date getAuthorizedTo() { return authorizedTo; }
    public void setAuthorizedTo(Date authorizedTo) { this.authorizedTo = authorizedTo; }

    public String getAuthNumber() { return authNumber; }
    public void setAuthNumber(String authNumber) { this.authNumber = authNumber; }

    public PriorAuthStatus getStatus() { return status; }
    public void setStatus(PriorAuthStatus status) { this.status = status; }

    public int getApprovedUnits() { return approvedUnits; }
    public void setApprovedUnits(int approvedUnits) { this.approvedUnits = approvedUnits; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
