package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** FHIR R4 Coding — a code within a code system (e.g. ICD-10, CPT). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirCoding {

    private String system;
    private String code;
    private String display;

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDisplay() { return display; }
    public void setDisplay(String display) { this.display = display; }
}
