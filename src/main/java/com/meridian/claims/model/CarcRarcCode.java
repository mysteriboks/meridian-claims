package com.meridian.claims.model;

/**
 * One entry from the official X12 external CARC (Claim Adjustment Reason
 * Code) or RARC (Remittance Advice Remark Code) list (Phase 19) — distinct
 * from the app's own curated {@code denial_reason_codes}, which map an
 * internal denial reason to a CARC code rather than holding the full
 * official list.
 */
public class CarcRarcCode {

    public static final String TYPE_CARC = "CARC";
    public static final String TYPE_RARC = "RARC";

    private String code;
    private String codeType;
    private String description;
    private boolean active;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeType() { return codeType; }
    public void setCodeType(String codeType) { this.codeType = codeType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
