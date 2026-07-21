package com.meridian.claims.controller;

import com.meridian.claims.model.ClaimType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Form-backing POJO for claim submission.
 * Spring MVC binds indexed list parameters: lineItems[0].procedureCode, diagnoses[0].diagnosisCode, etc.
 */
public class SubmitClaimRequest {

    private int memberId;
    private int providerId;
    private ClaimType claimType = ClaimType.ORIGINAL;
    private Integer originalClaimId;
    private Date dateOfService;
    private String coverageOrder = "PRIMARY";
    private String priorAuthNumber;
    private String referralNumber;
    private BigDecimal cobPrimaryPaid;
    private boolean accidentIndicator;
    private String accidentType;
    private Date accidentDate;
    private String notes;
    private String externalReference;

    private List<LineItemRow> lineItems = new ArrayList<LineItemRow>();
    private List<DiagnosisRow> diagnoses = new ArrayList<DiagnosisRow>();

    // -------------------------------------------------------------------------
    // Nested row types for Spring MVC indexed binding
    // -------------------------------------------------------------------------

    public static class LineItemRow {
        private String procedureCode;
        private String description;
        private BigDecimal billedAmount;

        public String getProcedureCode() { return procedureCode; }
        public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getBilledAmount() { return billedAmount; }
        public void setBilledAmount(BigDecimal billedAmount) { this.billedAmount = billedAmount; }
    }

    public static class DiagnosisRow {
        private String diagnosisCode;
        private String diagnosisType = "SECONDARY";

        public String getDiagnosisCode() { return diagnosisCode; }
        public void setDiagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; }

        public String getDiagnosisType() { return diagnosisType; }
        public void setDiagnosisType(String diagnosisType) { this.diagnosisType = diagnosisType; }
    }

    // -------------------------------------------------------------------------

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

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String externalReference) { this.externalReference = externalReference; }

    public List<LineItemRow> getLineItems() { return lineItems; }
    public void setLineItems(List<LineItemRow> lineItems) { this.lineItems = lineItems; }

    public List<DiagnosisRow> getDiagnoses() { return diagnoses; }
    public void setDiagnoses(List<DiagnosisRow> diagnoses) { this.diagnoses = diagnoses; }
}
