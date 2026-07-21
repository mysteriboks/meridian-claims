package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FHIR R4 Claim resource — mapped subset only.
 * Unmapped FHIR fields are silently ignored via @JsonIgnoreProperties.
 *
 * Mapped fields:
 *   patient         → member (resolved by member number from identifier)
 *   provider        → provider (resolved by NPI from identifier)
 *   billablePeriod  → date of service (start date used)
 *   insurance       → coverage order (focal=true → PRIMARY, else SECONDARY)
 *   diagnosis[]     → diagnoses (ICD-10 codes + type PRIMARY/SECONDARY)
 *   item[]          → line items (CPT code + net amount)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirClaim {

    private String resourceType;
    private FhirReference patient;
    private FhirReference provider;
    private FhirPeriod billablePeriod;
    private List<Insurance> insurance;
    private List<Diagnosis> diagnosis;
    private List<Item> item;

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public FhirReference getPatient() { return patient; }
    public void setPatient(FhirReference patient) { this.patient = patient; }

    public FhirReference getProvider() { return provider; }
    public void setProvider(FhirReference provider) { this.provider = provider; }

    public FhirPeriod getBillablePeriod() { return billablePeriod; }
    public void setBillablePeriod(FhirPeriod billablePeriod) { this.billablePeriod = billablePeriod; }

    public List<Insurance> getInsurance() { return insurance; }
    public void setInsurance(List<Insurance> insurance) { this.insurance = insurance; }

    public List<Diagnosis> getDiagnosis() { return diagnosis; }
    public void setDiagnosis(List<Diagnosis> diagnosis) { this.diagnosis = diagnosis; }

    public List<Item> getItem() { return item; }
    public void setItem(List<Item> item) { this.item = item; }

    // ---- nested types ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Insurance {
        private int sequence;
        private boolean focal;
        private FhirReference coverage;

        public int getSequence() { return sequence; }
        public void setSequence(int sequence) { this.sequence = sequence; }

        public boolean isFocal() { return focal; }
        public void setFocal(boolean focal) { this.focal = focal; }

        public FhirReference getCoverage() { return coverage; }
        public void setCoverage(FhirReference coverage) { this.coverage = coverage; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Diagnosis {
        private int sequence;
        private List<FhirCodeableConcept> type;
        private FhirCodeableConcept diagnosisCodeableConcept;

        public int getSequence() { return sequence; }
        public void setSequence(int sequence) { this.sequence = sequence; }

        public List<FhirCodeableConcept> getType() { return type; }
        public void setType(List<FhirCodeableConcept> type) { this.type = type; }

        public FhirCodeableConcept getDiagnosisCodeableConcept() { return diagnosisCodeableConcept; }
        public void setDiagnosisCodeableConcept(FhirCodeableConcept d) { this.diagnosisCodeableConcept = d; }

        /**
         * Returns true if this diagnosis is flagged principal/primary via its type
         * coding. The parser normalises the overall claim to exactly one PRIMARY
         * (promoting the first row when no entry is flagged), so this only reports
         * the explicit FHIR flag — it does not fall back to sequence here.
         */
        public boolean isPrimary() {
            if (type == null) return false;
            for (FhirCodeableConcept t : type) {
                String code = t.firstCode();
                if (code != null && (code.equalsIgnoreCase("principal") || code.equalsIgnoreCase("primary"))) {
                    return true;
                }
            }
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private int sequence;
        private FhirCodeableConcept productOrService;
        private FhirMoney net;
        private String servicedDate;

        public int getSequence() { return sequence; }
        public void setSequence(int sequence) { this.sequence = sequence; }

        public FhirCodeableConcept getProductOrService() { return productOrService; }
        public void setProductOrService(FhirCodeableConcept productOrService) { this.productOrService = productOrService; }

        public FhirMoney getNet() { return net; }
        public void setNet(FhirMoney net) { this.net = net; }

        public String getServicedDate() { return servicedDate; }
        public void setServicedDate(String servicedDate) { this.servicedDate = servicedDate; }
    }
}
