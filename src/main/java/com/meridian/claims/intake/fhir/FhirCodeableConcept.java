package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** FHIR R4 CodeableConcept — one or more codings plus optional text. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirCodeableConcept {

    private List<FhirCoding> coding;
    private String text;

    public List<FhirCoding> getCoding() { return coding; }
    public void setCoding(List<FhirCoding> coding) { this.coding = coding; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    /** Returns the first code value across all codings, or null. */
    public String firstCode() {
        if (coding == null) return null;
        for (FhirCoding c : coding) {
            if (c.getCode() != null && !c.getCode().trim().isEmpty()) {
                return c.getCode().trim();
            }
        }
        return null;
    }
}
