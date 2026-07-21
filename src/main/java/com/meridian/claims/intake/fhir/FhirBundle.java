package com.meridian.claims.intake.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * FHIR R4 Bundle — a collection of resources.
 * We support a Bundle of Claim resources as an alternative to a single Claim.
 * Only the entry[].resource field is mapped; all other Bundle fields are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FhirBundle {

    private String resourceType;
    private List<Entry> entry;

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public List<Entry> getEntry() { return entry; }
    public void setEntry(List<Entry> entry) { this.entry = entry; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entry {
        private FhirClaim resource;

        public FhirClaim getResource() { return resource; }
        public void setResource(FhirClaim resource) { this.resource = resource; }
    }
}
