package com.meridian.claims.model;

public class DenialReasonCode {

    private String code;
    private String carcCode;
    private String description;
    private boolean active;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCarcCode() { return carcCode; }
    public void setCarcCode(String carcCode) { this.carcCode = carcCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
