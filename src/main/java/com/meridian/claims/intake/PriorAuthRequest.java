package com.meridian.claims.intake;

/**
 * One parsed X12 278 prior-authorization request (one ST..SE transaction).
 * Mirrors {@link ClaimStatusInquiry}'s carrier shape for the same reason —
 * a flat set of fields extracted from a handful of segments, not a full
 * implementation-guide object model.
 */
public class PriorAuthRequest {

    private String isaControlNumber;
    private String gsControlNumber;
    private String stControlNumber;
    private String memberNumber;        // NM1*IL
    private String providerNpi;         // NM1*1P / 41 / 82 / 85
    private String procedureCode;       // SV1 composite (HC:xxx)
    private String serviceType;         // UM03
    private Integer requestedUnits;     // SV1 quantity element
    private String authorizedFromString; // DTP*291 (requested period), first half of an R8 range
    private String authorizedToString;   // DTP*291, second half

    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    public String getStControlNumber() { return stControlNumber; }
    public void setStControlNumber(String stControlNumber) { this.stControlNumber = stControlNumber; }

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }

    public String getProviderNpi() { return providerNpi; }
    public void setProviderNpi(String providerNpi) { this.providerNpi = providerNpi; }

    public String getProcedureCode() { return procedureCode; }
    public void setProcedureCode(String procedureCode) { this.procedureCode = procedureCode; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public Integer getRequestedUnits() { return requestedUnits; }
    public void setRequestedUnits(Integer requestedUnits) { this.requestedUnits = requestedUnits; }

    public String getAuthorizedFromString() { return authorizedFromString; }
    public void setAuthorizedFromString(String authorizedFromString) { this.authorizedFromString = authorizedFromString; }

    public String getAuthorizedToString() { return authorizedToString; }
    public void setAuthorizedToString(String authorizedToString) { this.authorizedToString = authorizedToString; }
}
