package com.meridian.claims.model;

import java.math.BigDecimal;
import java.util.Date;

public class Claim {

    private int id;
    private String claimNumber;
    private int memberId;
    private int providerId;
    private ClaimType claimType;
    private Integer originalClaimId;
    private Date dateOfService;
    private Date submissionDate;
    private ClaimStatus status;
    private int planId;
    private String coverageOrder;
    private String priorAuthNumber;
    private String referralNumber;
    private BigDecimal cobPrimaryPaid;
    private boolean accidentIndicator;
    private String accidentType;
    private Date accidentDate;
    private String denialReasonCode;
    private String notes;
    private String externalReference;
    private Integer assignedToUserId;
    private Integer createdByUserId;
    private Date statusEnteredAt;
    private int version;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getClaimNumber() { return claimNumber; }
    public void setClaimNumber(String claimNumber) { this.claimNumber = claimNumber; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getProviderId() { return providerId; }
    public void setProviderId(int providerId) { this.providerId = providerId; }

    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType claimType) { this.claimType = claimType; }

    public Integer getOriginalClaimId() { return originalClaimId; }
    public void setOriginalClaimId(Integer originalClaimId) { this.originalClaimId = originalClaimId; }

    public Date getDateOfService() { return dateOfService; }
    public void setDateOfService(Date dateOfService) { this.dateOfService = dateOfService; }

    public Date getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(Date submissionDate) { this.submissionDate = submissionDate; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getCoverageOrder() { return coverageOrder; }
    public void setCoverageOrder(String coverageOrder) { this.coverageOrder = coverageOrder; }

    public String getPriorAuthNumber() { return priorAuthNumber; }
    public void setPriorAuthNumber(String priorAuthNumber) { this.priorAuthNumber = priorAuthNumber; }

    public String getReferralNumber() { return referralNumber; }
    public void setReferralNumber(String referralNumber) { this.referralNumber = referralNumber; }

    public BigDecimal getCobPrimaryPaid() { return cobPrimaryPaid; }
    public void setCobPrimaryPaid(BigDecimal cobPrimaryPaid) { this.cobPrimaryPaid = cobPrimaryPaid; }

    public boolean isAccidentIndicator() { return accidentIndicator; }
    public void setAccidentIndicator(boolean accidentIndicator) { this.accidentIndicator = accidentIndicator; }

    public String getAccidentType() { return accidentType; }
    public void setAccidentType(String accidentType) { this.accidentType = accidentType; }

    public Date getAccidentDate() { return accidentDate; }
    public void setAccidentDate(Date accidentDate) { this.accidentDate = accidentDate; }

    public String getDenialReasonCode() { return denialReasonCode; }
    public void setDenialReasonCode(String denialReasonCode) { this.denialReasonCode = denialReasonCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public Integer getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Integer assignedToUserId) { this.assignedToUserId = assignedToUserId; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }

    public Date getStatusEnteredAt() { return statusEnteredAt; }
    public void setStatusEnteredAt(Date statusEnteredAt) { this.statusEnteredAt = statusEnteredAt; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
