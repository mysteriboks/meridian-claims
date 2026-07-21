package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** FHIR R4 Period — a date/time interval (start + optional end). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirPeriod {

    private String start;
    private String end;

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }

    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }
}
