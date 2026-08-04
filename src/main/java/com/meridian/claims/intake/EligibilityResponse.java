package com.meridian.claims.intake;

/**
 * Carries the parsed contents of one X12 271 (Eligibility, Coverage or Benefit
 * Information) response transaction — the reverse-direction counterpart of
 * {@link ClaimStatusInquiry} / {@link PriorAuthRequest}. Meridian is the
 * requester for 270/271 (Phase 16), so this is the first *inbound response*
 * carrier rather than an inbound request.
 */
public class EligibilityResponse {

    private String isaControlNumber;
    private String gsControlNumber;
    private String stControlNumber;
    private String memberNumber;
    /** Raw EB01 eligibility/benefit information code (e.g. "1" = active, "6" = inactive). */
    private String eb01Code;
    /** Free-text plan/benefit description from the EB or MSG segment, if present. */
    private String planDescription;

    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    public String getStControlNumber() { return stControlNumber; }
    public void setStControlNumber(String stControlNumber) { this.stControlNumber = stControlNumber; }

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }

    public String getEb01Code() { return eb01Code; }
    public void setEb01Code(String eb01Code) { this.eb01Code = eb01Code; }

    public String getPlanDescription() { return planDescription; }
    public void setPlanDescription(String planDescription) { this.planDescription = planDescription; }
}
