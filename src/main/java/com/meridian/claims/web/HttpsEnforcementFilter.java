package com.meridian.claims.web;

import com.meridian.claims.util.AppProfile;
import org.apache.log4j.Logger;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Enforces HTTPS in production only.
 *
 * Under the prod profile:
 *   - Any HTTP request is 301-redirected to the equivalent HTTPS URL.
 *   - All responses receive Strict-Transport-Security (1 year, includeSubDomains).
 *
 * Under the dev profile (default) this filter passes through completely,
 * preserving plain-HTTP access on http://localhost:8090.
 *
 * This filter must be declared FIRST in the filter chain (before encodingFilter)
 * so the redirect happens before any request body is read.
 */
public class HttpsEnforcementFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(HttpsEnforcementFilter.class);
    private static final String HSTS_HEADER = "Strict-Transport-Security";
    private static final String HSTS_VALUE  = "max-age=31536000; includeSubDomains";

    private AppProfile appProfile;

    public void init(FilterConfig config) throws ServletException {
        WebApplicationContext ctx =
                WebApplicationContextUtils.getRequiredWebApplicationContext(config.getServletContext());
        appProfile = ctx.getBean(AppProfile.class);
        if (appProfile.isProd()) {
            LOG.info("HttpsEnforcementFilter active: HTTP requests will be redirected to HTTPS.");
        } else {
            LOG.info("HttpsEnforcementFilter inactive (profile=" + appProfile.getProfile() + ").");
        }
    }

    /** Package-private setter for unit tests (production wires this via init()). */
    void setAppProfile(AppProfile appProfile) {
        this.appProfile = appProfile;
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (appProfile == null || !appProfile.isProd()) {
            chain.doFilter(req, res);
            return;
        }

        HttpServletRequest  httpReq  = (HttpServletRequest)  req;
        HttpServletResponse httpRes  = (HttpServletResponse) res;

        // Add HSTS on every response under prod (both HTTP and HTTPS).
        httpRes.setHeader(HSTS_HEADER, HSTS_VALUE);

        if ("http".equalsIgnoreCase(httpReq.getScheme())) {
            String httpsUrl = buildHttpsUrl(httpReq);
            httpRes.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            httpRes.setHeader("Location", httpsUrl);
            return;
        }

        chain.doFilter(req, res);
    }

    public void destroy() {
        // nothing to release
    }

    private String buildHttpsUrl(HttpServletRequest req) {
        StringBuffer url = req.getRequestURL();
        // Replace the scheme; keep host + port + path + query.
        String httpsUrl = "https" + url.substring(4); // "http" is 4 chars
        String query = req.getQueryString();
        if (query != null && query.length() > 0) {
            httpsUrl = httpsUrl + "?" + query;
        }
        return httpsUrl;
    }
}
