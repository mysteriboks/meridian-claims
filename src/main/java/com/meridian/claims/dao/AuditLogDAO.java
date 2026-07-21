package com.meridian.claims.dao;

import com.meridian.claims.model.AuditLogEntry;
import com.meridian.claims.util.Page;

import java.util.List;

public interface AuditLogDAO {

    void insert(AuditLogEntry entry);

    /** Most recent audit entries for a given entity (newest first). */
    List<AuditLogEntry> findByEntity(String entityType, long entityId);

    /**
     * Paginated search across audit_log.
     * All filter params are optional (null/empty = no filter).
     */
    Page<AuditLogEntry> search(String username, String eventType, String entityType,
                               java.util.Date from, java.util.Date to,
                               int page, int pageSize);
}
