package com.meridian.claims.web;

import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.SecurityPolicy;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

public class SecurityFilter implements Filter {

    private SecurityPolicy securityPolicy;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        WebApplicationContext ctx =
                WebApplicationContextUtils.getRequiredWebApplicationContext(filterConfig.getServletContext());
        securityPolicy = ctx.getBean(SecurityPolicy.class);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getServletPath();

        // Always allow login page, static resources, and health endpoint
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        User user = (session == null) ? null
                : (User) session.getAttribute(AuthenticationService.SESSION_USER_KEY);

        if (user == null) {
            String loginUrl = request.getContextPath() + "/login";
            response.sendRedirect(loginUrl);
            return;
        }

        // Force password-reset flow before any other page. Allow /password
        // (the change screen itself) and /logout (so a forced-reset user is not
        // trapped and can still sign out).
        if ((user.isForceReset() || user.isPasswordExpired(securityPolicy.getPasswordExpiryDays()))
                && !path.startsWith("/password")
                && !path.equals("/logout")) {
            response.sendRedirect(request.getContextPath() + "/password/change");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        return path.equals("/login")
            || path.startsWith("/static/")
            || path.equals("/health");
    }

    @Override
    public void destroy() {
    }
}
