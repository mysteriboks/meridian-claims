package com.meridian.claims.model;

public class ProcedureCode {

    private String code;
    private String description;
    private String serviceType;
    private boolean active;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
