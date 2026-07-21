package com.meridian.claims.web;

import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import org.apache.log4j.Logger;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.Date;

/**
 * Closes the open user_sessions row whenever a session ends — including idle
 * timeout, which the explicit logout path never sees. Explicit logout also
 * closes the row, but closeSession is guarded by "logout_at IS NULL", so the
 * second close is a harmless no-op (idempotent).
 *
 * Not a Spring bean (servlet container manages listeners), so the UserDAO is
 * pulled from the root WebApplicationContext on demand.
 */
public class SessionCleanupListener implements HttpSessionListener {

    private static final Logger LOG = Logger.getLogger(SessionCleanupListener.class);

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        // no-op; session rows are created by AuthenticationService on login
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();

        // Only sessions that belonged to a logged-in user have a row to close.
        Object userAttr = session.getAttribute(AuthenticationService.SESSION_USER_KEY);
        if (userAttr == null) {
            return;
        }

        try {
            UserDAO userDAO = lookupUserDAO(session);
            if (userDAO == null) {
                LOG.warn("SessionCleanupListener could not resolve UserDAO; session "
                        + session.getId() + " left open in user_sessions");
                return;
            }
            userDAO.closeSession(session.getId(), new Date());
            if (userAttr instanceof User) {
                LOG.info("Session closed (timeout/invalidate) for user id=" + ((User) userAttr).getId());
            }
        } catch (RuntimeException ex) {
            // Never let cleanup failure escape the container's session teardown.
            LOG.error("Failed to close user_sessions row for session " + session.getId(), ex);
        }
    }

    private UserDAO lookupUserDAO(HttpSession session) {
        WebApplicationContext ctx = WebApplicationContextUtils
                .getWebApplicationContext(session.getServletContext());
        if (ctx == null) {
            return null;
        }
        return ctx.getBean(UserDAO.class);
    }
}
