package com.meridian.claims.model;

import java.util.Date;

/**
 * A trading partner's X12 identity and transport configuration (Phase 13).
 * Transport credentials are never stored here in plaintext — {@link #transportCredentialRef}
 * is a key name resolved from the external prod property overlay at runtime
 * (see docs/meridian-claims-prod.properties.sample), the same pattern the DB
 * datasource itself uses.
 */
public class TradingPartner {

    public static final String TRANSPORT_LOCAL = "LOCAL";
    public static final String TRANSPORT_SFTP = "SFTP";

    private int id;
    private String partnerName;
    private String isaQualifier;
    private String isaId;
    private String gsId;
    private String enabledTransactions;
    private String transportType;
    private String transportHost;
    private Integer transportPort;
    private String transportUsername;
    private String transportCredentialRef;
    private String inboundPath;
    private String outboundPath;
    private boolean active;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPartnerName() { return partnerName; }
    public void setPartnerName(String partnerName) { this.partnerName = partnerName; }

    public String getIsaQualifier() { return isaQualifier; }
    public void setIsaQualifier(String isaQualifier) { this.isaQualifier = isaQualifier; }

    public String getIsaId() { return isaId; }
    public void setIsaId(String isaId) { this.isaId = isaId; }

    public String getGsId() { return gsId; }
    public void setGsId(String gsId) { this.gsId = gsId; }

    public String getEnabledTransactions() { return enabledTransactions; }
    public void setEnabledTransactions(String enabledTransactions) { this.enabledTransactions = enabledTransactions; }

    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }

    public String getTransportHost() { return transportHost; }
    public void setTransportHost(String transportHost) { this.transportHost = transportHost; }

    public Integer getTransportPort() { return transportPort; }
    public void setTransportPort(Integer transportPort) { this.transportPort = transportPort; }

    public String getTransportUsername() { return transportUsername; }
    public void setTransportUsername(String transportUsername) { this.transportUsername = transportUsername; }

    public String getTransportCredentialRef() { return transportCredentialRef; }
    public void setTransportCredentialRef(String transportCredentialRef) { this.transportCredentialRef = transportCredentialRef; }

    public String getInboundPath() { return inboundPath; }
    public void setInboundPath(String inboundPath) { this.inboundPath = inboundPath; }

    public String getOutboundPath() { return outboundPath; }
    public void setOutboundPath(String outboundPath) { this.outboundPath = outboundPath; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
