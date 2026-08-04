package com.meridian.claims.intake;

/**
 * One parsed X12 276 claim status inquiry (one ST..SE transaction). Carries
 * whatever identifying fields the inquiry supplied — a submitter may send the
 * payer claim control number (REF*1K, our own claim number) if they have it
 * from an earlier 277CA, or fall back to member + provider + date of service.
 */
public class ClaimStatusInquiry {

    private String isaControlNumber;
    private String gsControlNumber;
    private String stControlNumber;
    private String claimControlNumber; // REF*1K — our own claim number, if the submitter has it
    private String memberNumber;       // NM1*IL
    private String providerNpi;        // NM1*1P / 41 / 82 / 85
    private String dateOfServiceString; // DTP*472, raw (yyyyMMdd or yyyy-MM-dd)

    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    public String getStControlNumber() { return stControlNumber; }
    public void setStControlNumber(String stControlNumber) { this.stControlNumber = stControlNumber; }

    public String getClaimControlNumber() { return claimControlNumber; }
    public void setClaimControlNumber(String claimControlNumber) { this.claimControlNumber = claimControlNumber; }

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }

    public String getProviderNpi() { return providerNpi; }
    public void setProviderNpi(String providerNpi) { this.providerNpi = providerNpi; }

    public String getDateOfServiceString() { return dateOfServiceString; }
    public void setDateOfServiceString(String dateOfServiceString) { this.dateOfServiceString = dateOfServiceString; }
}
