package com.meridian.claims.model;

import java.util.Date;

public class ClaimDiagnosis {

    private int id;
    private int claimId;
    private String diagnosisCode;
    private int sequenceNumber;
    private String diagnosisType;  // PRIMARY / SECONDARY
    private Date createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public String getDiagnosisCode() { return diagnosisCode; }
    public void setDiagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getDiagnosisType() { return diagnosisType; }
    public void setDiagnosisType(String diagnosisType) { this.diagnosisType = diagnosisType; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
