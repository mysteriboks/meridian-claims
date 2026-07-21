package com.meridian.claims.web;

import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuthenticationService;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Enforces role-based access on URL prefixes. SecurityFilter runs first and
 * guarantees a logged-in user is in session before this filter is reached.
 *
 * URL prefix → required role(s):
 *   /admin/**        → ADMIN only
 *   /finance/**      → FINANCE or ADMIN
 *   /review/**       → REVIEWER or ADMIN
 *   /analyst/**      → ANALYST or ADMIN
 *   /reports/**      → ANALYST, REVIEWER, FINANCE, or ADMIN (not STAFF —
 *                       aggregate report data exceeds STAFF minimum-necessary access)
 *   /dashboard       → same as /reports
 *   Domain data (/members, /providers, /prior-auth, /referrals, /claims):
 *       ANALYST is read-only (GET allowed, mutating methods denied)
 *   All others       → any authenticated user
 */
public class RoleFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(request, response);
            return;
        }

        User user = (User) session.getAttribute(AuthenticationService.SESSION_USER_KEY);
        if (user == null) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();

        if (!isAuthorized(user.getRole(), path, request.getMethod())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isAuthorized(UserRole role, String path, String method) {
        if (path.startsWith("/admin/")) {
            return role == UserRole.ADMIN;
        }
        if (path.startsWith("/finance/")) {
            return role == UserRole.FINANCE || role == UserRole.ADMIN;
        }
        if (path.startsWith("/review/")) {
            return role == UserRole.REVIEWER || role == UserRole.ADMIN;
        }
        if (path.startsWith("/analyst/")) {
            return role == UserRole.ANALYST || role == UserRole.ADMIN;
        }
        // Reports and dashboard: require ANALYST, REVIEWER, FINANCE, or ADMIN.
        // STAFF may not access aggregate reporting data (HIPAA minimum-necessary).
        if (path.startsWith("/reports") || path.startsWith("/dashboard")) {
            return role == UserRole.ANALYST
                || role == UserRole.REVIEWER
                || role == UserRole.FINANCE
                || role == UserRole.ADMIN;
        }
        // Domain data: ANALYST is read-only — may GET but not mutate.
        if (isDomainDataPath(path) && role == UserRole.ANALYST) {
            return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        }
        return true;
    }

    private boolean isDomainDataPath(String path) {
        return path.startsWith("/members")
            || path.startsWith("/providers")
            || path.startsWith("/prior-auth")
            || path.startsWith("/referrals")
            || path.startsWith("/claims");
    }

    @Override
    public void destroy() {
    }
}
