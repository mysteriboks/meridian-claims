package com.meridian.claims.model;

import java.util.Date;

/**
 * A record of a real-time eligibility inquiry (X12 270) and its response
 * (X12 271), triggered by the "Check Eligibility" action on the member
 * screen (Phase 16). Not tied to a claim — a standalone verification event.
 */
public class EligibilityCheck {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";
    public static final String STATUS_ERROR = "ERROR";

    private int id;
    private int memberId;
    private Integer providerId;
    private String serviceType;
    private Date inquiryAt;
    private Date responseAt;
    private String resultStatus;
    private String coverageSnapshot;
    private Integer checkedByUserId;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public Integer getProviderId() { return providerId; }
    public void setProviderId(Integer providerId) { this.providerId = providerId; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public Date getInquiryAt() { return inquiryAt; }
    public void setInquiryAt(Date inquiryAt) { this.inquiryAt = inquiryAt; }

    public Date getResponseAt() { return responseAt; }
    public void setResponseAt(Date responseAt) { this.responseAt = responseAt; }

    public String getResultStatus() { return resultStatus; }
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }

    public String getCoverageSnapshot() { return coverageSnapshot; }
    public void setCoverageSnapshot(String coverageSnapshot) { this.coverageSnapshot = coverageSnapshot; }

    public Integer getCheckedByUserId() { return checkedByUserId; }
    public void setCheckedByUserId(Integer checkedByUserId) { this.checkedByUserId = checkedByUserId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
