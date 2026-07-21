package com.meridian.claims.model;

import java.util.Date;

/**
 * One row of the generic system audit_log table. Claim-specific status
 * auditing lives in claim_audit (Phase 4); this captures system/config events
 * such as member/provider/plan create and deactivate.
 */
public class AuditLogEntry {

    private long id;
    private String eventType;
    private String entityType;
    private Long entityId;
    private Integer userId;
    private String username;
    private String description;
    private Date createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
