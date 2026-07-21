package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** FHIR R4 Reference — a reference to another resource, with optional identifier. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirReference {

    private String reference;
    private FhirIdentifier identifier;

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public FhirIdentifier getIdentifier() { return identifier; }
    public void setIdentifier(FhirIdentifier identifier) { this.identifier = identifier; }

    // ---- nested identifier support ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FhirIdentifier {
        private String system;
        private String value;

        public String getSystem() { return system; }
        public void setSystem(String system) { this.system = system; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
