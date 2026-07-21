package com.meridian.claims.web;

import com.meridian.claims.util.AppProfile;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HttpsEnforcementFilterTest {

    private HttpsEnforcementFilter filterFor(String profile) {
        // appProfile is wired by init() from the Spring context in production;
        // for unit tests we set it directly via the package-private setter.
        HttpsEnforcementFilter filter = new HttpsEnforcementFilter();
        String saved = System.getProperty("claims.profile");
        System.setProperty("claims.profile", profile);
        try {
            filter.setAppProfile(new AppProfile());
        } finally {
            if (saved != null) System.setProperty("claims.profile", saved);
            else System.clearProperty("claims.profile");
        }
        return filter;
    }

    @Test
    public void devProfile_httpRequest_passesThrough() throws Exception {
        HttpsEnforcementFilter filter = filterFor(AppProfile.DEV);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setScheme("http");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("dev: must not redirect", 200, res.getStatus());
        assertNull("dev: no HSTS header", res.getHeader("Strict-Transport-Security"));
        assertNotNull("dev: chain must proceed", chain.getRequest());
    }

    @Test
    public void prodProfile_httpRequest_redirectsToHttps() throws Exception {
        HttpsEnforcementFilter filter = filterFor(AppProfile.PROD);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setScheme("http");
        req.setServerName("app.meridian.local");
        req.setRequestURI("/dashboard");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("prod: HTTP must be 301", 301, res.getStatus());
        String location = res.getHeader("Location");
        assertNotNull("prod: Location header must be set", location);
        assertTrue("prod: redirect must be to https", location.startsWith("https://"));
    }

    @Test
    public void prodProfile_httpsRequest_passesThrough() throws Exception {
        HttpsEnforcementFilter filter = filterFor(AppProfile.PROD);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setScheme("https");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("prod: HTTPS must not redirect", 200, res.getStatus());
        assertNotNull("prod: chain must proceed on HTTPS", chain.getRequest());
    }

    @Test
    public void prodProfile_emitsHstsOnHttps() throws Exception {
        HttpsEnforcementFilter filter = filterFor(AppProfile.PROD);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setScheme("https");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertNotNull("prod: HSTS header must be present on HTTPS", res.getHeader("Strict-Transport-Security"));
    }
}
