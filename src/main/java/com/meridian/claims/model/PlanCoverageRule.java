package com.meridian.claims.model;

import java.math.BigDecimal;

public class PlanCoverageRule {

    private int id;
    private int planId;
    private String serviceType;
    private BigDecimal coveragePct;
    private boolean requiresReferral;
    private boolean requiresPriorAuth;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPlanId() { return planId; }
    public void setPlanId(int planId) { this.planId = planId; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public BigDecimal getCoveragePct() { return coveragePct; }
    public void setCoveragePct(BigDecimal coveragePct) { this.coveragePct = coveragePct; }

    public boolean isRequiresReferral() { return requiresReferral; }
    public void setRequiresReferral(boolean requiresReferral) { this.requiresReferral = requiresReferral; }

    public boolean isRequiresPriorAuth() { return requiresPriorAuth; }
    public void setRequiresPriorAuth(boolean requiresPriorAuth) { this.requiresPriorAuth = requiresPriorAuth; }
}
