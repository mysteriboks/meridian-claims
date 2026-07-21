package com.meridian.claims.service;

import com.meridian.claims.dao.PhiAccessLogDAO;
import com.meridian.claims.model.PhiAccessLog;
import com.meridian.claims.model.User;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;

/**
 * Records PHI access events (VIEW, APPROVED, DENIED, etc.) to phi_access_log.
 * User is resolved from the current session, identical to AuditService.
 * Logging never throws — a failed write must not abort the business operation.
 */
@Service
public class PhiAccessLogService {

    private static final Logger LOG = Logger.getLogger(PhiAccessLogService.class);

    private final PhiAccessLogDAO phiAccessLogDAO;

    @Autowired
    public PhiAccessLogService(PhiAccessLogDAO phiAccessLogDAO) {
        this.phiAccessLogDAO = phiAccessLogDAO;
    }

    public void record(int memberId, int claimId, String action) {
        try {
            PhiAccessLog entry = new PhiAccessLog();
            entry.setUserId(currentUserId());
            entry.setMemberId(memberId);
            entry.setClaimId(claimId);
            entry.setAction(action);
            phiAccessLogDAO.insert(entry);
        } catch (Exception e) {
            LOG.warn("PHI access log write failed claimId=" + claimId + " action=" + action, e);
        }
    }

    private Integer currentUserId() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes)) {
                return null;
            }
            HttpSession session = ((ServletRequestAttributes) attrs).getRequest().getSession(false);
            if (session == null) {
                return null;
            }
            Object user = session.getAttribute(AuthenticationService.SESSION_USER_KEY);
            if (user instanceof User) {
                return ((User) user).getId();
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
