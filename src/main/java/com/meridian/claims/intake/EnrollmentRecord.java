package com.meridian.claims.intake;

/**
 * Carries one parsed member enrollment/maintenance record from an X12 834
 * (Benefit Enrollment and Maintenance) file — one per INS loop. Same flat
 * carrier style as {@link PriorAuthRequest} / {@link ClaimStatusInquiry}.
 */
public class EnrollmentRecord {

    public static final String MAINTENANCE_ADD    = "021";
    public static final String MAINTENANCE_CHANGE = "001";
    public static final String MAINTENANCE_TERM   = "024";

    private String isaControlNumber;
    private String gsControlNumber;
    private String stControlNumber;
    private String maintenanceTypeCode;
    private String memberNumber;
    private String firstName;
    private String lastName;
    private String dobString;
    private String planName;
    private String effectiveDateString;
    private String terminationDateString;

    public String getIsaControlNumber() { return isaControlNumber; }
    public void setIsaControlNumber(String isaControlNumber) { this.isaControlNumber = isaControlNumber; }

    public String getGsControlNumber() { return gsControlNumber; }
    public void setGsControlNumber(String gsControlNumber) { this.gsControlNumber = gsControlNumber; }

    public String getStControlNumber() { return stControlNumber; }
    public void setStControlNumber(String stControlNumber) { this.stControlNumber = stControlNumber; }

    public String getMaintenanceTypeCode() { return maintenanceTypeCode; }
    public void setMaintenanceTypeCode(String maintenanceTypeCode) { this.maintenanceTypeCode = maintenanceTypeCode; }

    public String getMemberNumber() { return memberNumber; }
    public void setMemberNumber(String memberNumber) { this.memberNumber = memberNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getDobString() { return dobString; }
    public void setDobString(String dobString) { this.dobString = dobString; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getEffectiveDateString() { return effectiveDateString; }
    public void setEffectiveDateString(String effectiveDateString) { this.effectiveDateString = effectiveDateString; }

    public String getTerminationDateString() { return terminationDateString; }
    public void setTerminationDateString(String terminationDateString) { this.terminationDateString = terminationDateString; }
}
