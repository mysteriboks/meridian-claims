package com.meridian.claims.service;

import com.meridian.claims.dao.AuditLogDAO;
import com.meridian.claims.model.AuditLogEntry;
import com.meridian.claims.model.User;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Writes generic system audit entries (audit_log). Honors the cross-cutting
 * rule "every status change is audited" for the domain-config entities managed
 * in Phase 3 (member/provider/plan/etc.).
 *
 * The acting user is resolved from the current HTTP session via
 * RequestContextHolder so callers need not thread the user through every
 * service signature. When no request/session is bound (e.g. a scheduled job or
 * a unit test), userId is left null — the audit row is still written.
 */
@Service
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    static final String SESSION_USER_KEY = AuthenticationService.SESSION_USER_KEY;

    private final AuditLogDAO auditLogDAO;

    @Autowired
    public AuditService(AuditLogDAO auditLogDAO) {
        this.auditLogDAO = auditLogDAO;
    }

    public Page<AuditLogEntry> search(String username, String eventType, String entityType,
                                      java.util.Date from, java.util.Date to,
                                      int page, int pageSize) {
        return auditLogDAO.search(username, eventType, entityType, from, to, page, pageSize);
    }

    public void record(String eventType, String entityType, Long entityId, String description) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setEventType(eventType);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setUserId(currentUserId());
        entry.setDescription(description);
        auditLogDAO.insert(entry);
    }

    private Integer currentUserId() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) {
                return null;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attrs).getRequest();
            HttpSession session = request.getSession(false);
            if (session == null) {
                return null;
            }
            Object user = session.getAttribute(SESSION_USER_KEY);
            if (user instanceof User) {
                return ((User) user).getId();
            }
            return null;
        } catch (RuntimeException e) {
            // Auditing must never break the business operation; log and continue.
            LOG.warn("Could not resolve current user for audit entry", e);
            return null;
        }
    }
}
