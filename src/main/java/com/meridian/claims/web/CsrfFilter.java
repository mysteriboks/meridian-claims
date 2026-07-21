package com.meridian.claims.web;

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
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Synchroniser-token CSRF protection for all state-changing requests.
 *
 * On every request:
 *   - A per-session token is generated on first access and stored in the session.
 *   - The token is exposed as request attribute "_csrf" so JSPs can include it
 *     in a hidden field: &lt;input type="hidden" name="_csrf" value="${_csrf}"&gt;
 *   - On every POST, the submitted "_csrf" parameter is compared to the session
 *     token. A mismatch returns 403.
 *
 * GET/HEAD/OPTIONS are not mutating and are not validated.
 * Login POSTs are validated (a token is seeded when the login page is served).
 */
public class CsrfFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class);

    static final String SESSION_ATTR = "CSRF_TOKEN";
    static final String REQUEST_ATTR = "_csrf";
    static final String PARAM_NAME   = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    public void init(FilterConfig config) throws ServletException {
        // nothing to initialise
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  httpReq = (HttpServletRequest)  req;
        HttpServletResponse httpRes = (HttpServletResponse) res;

        // Static assets and health checks never carry forms and are polled
        // frequently — do NOT create a session (and Set-Cookie) for them. The
        // /login page is the one public path that renders a form, so it still
        // gets a token seeded below.
        if (isStaticOrHealth(httpReq.getServletPath())) {
            chain.doFilter(req, res);
            return;
        }

        // Ensure a token exists in the session; expose it as a request attribute.
        String token = ensureToken(httpReq);
        httpReq.setAttribute(REQUEST_ATTR, token);

        if ("POST".equalsIgnoreCase(httpReq.getMethod())) {
            String submitted = httpReq.getParameter(PARAM_NAME);
            if (submitted == null || !submitted.equals(token)) {
                LOG.warn("CSRF token mismatch on POST " + httpReq.getRequestURI()
                        + " from " + httpReq.getRemoteAddr());
                httpRes.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token invalid");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    public void destroy() {
        // nothing to release
    }

    private boolean isStaticOrHealth(String path) {
        return path.startsWith("/static/") || path.equals("/health");
    }

    private String ensureToken(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            // No session yet — create one to hold the token.
            session = req.getSession(true);
        }
        String token = (String) session.getAttribute(SESSION_ATTR);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(SESSION_ATTR, token);
        }
        return token;
    }
}
