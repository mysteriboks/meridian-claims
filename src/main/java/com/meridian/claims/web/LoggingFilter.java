package com.meridian.claims.web;

import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import org.apache.log4j.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Logs one line per request: method, URI, resolved username, and response time.
 * Runs after encoding but before CSRF/security filters so the user context is
 * available for all authenticated requests.
 *
 * Format: REQUEST method=GET uri=/claims status=200 user=jdoe time=42ms
 * Slow requests (&gt;=500ms) are logged at WARN level; others at INFO.
 */
public class LoggingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(LoggingFilter.class);

    private static final long SLOW_THRESHOLD_MS = 500;

    public void init(FilterConfig config) throws ServletException {
        // nothing to initialise
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  httpReq = (HttpServletRequest)  req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            String method = httpReq.getMethod();
            String uri    = httpReq.getRequestURI();
            int    status = httpRes.getStatus();
            String user   = resolveUsername(httpReq);

            String msg = "REQUEST method=" + method
                    + " uri=" + uri
                    + " status=" + status
                    + " user=" + user
                    + " time=" + elapsed + "ms";

            if (elapsed >= SLOW_THRESHOLD_MS) {
                LOG.warn(msg);
            } else {
                LOG.info(msg);
            }
        }
    }

    public void destroy() {
        // nothing to release
    }

    private String resolveUsername(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return "anonymous";
        Object u = session.getAttribute(AuthenticationService.SESSION_USER_KEY);
        if (u instanceof User) return ((User) u).getUsername();
        return "anonymous";
    }
}
