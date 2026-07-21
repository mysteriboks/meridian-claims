package com.meridian.claims.web;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CsrfFilterTest {

    private CsrfFilter filter;

    @Before
    public void setUp() throws Exception {
        filter = new CsrfFilter();
        filter.init(null);
    }

    @Test
    public void getRequest_tokenSeededAndPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/claims");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull("token must be seeded on GET", req.getAttribute(CsrfFilter.REQUEST_ATTR));
        assertEquals("GET must not be blocked", 200, res.getStatus());
        assertNotNull("chain must have been invoked", chain.getRequest());
    }

    @Test
    public void postWithValidToken_passesThrough() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = "test-token-value";
        session.setAttribute(CsrfFilter.SESSION_ATTR, token);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/claims");
        req.setSession(session);
        req.addParameter(CsrfFilter.PARAM_NAME, token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("valid POST must not be blocked", 200, res.getStatus());
        assertNotNull("chain must have been invoked", chain.getRequest());
    }

    @Test
    public void postWithMissingToken_returns403() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfFilter.SESSION_ATTR, "expected-token");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/claims");
        req.setSession(session);
        // no _csrf parameter
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("missing token must return 403", 403, res.getStatus());
    }

    @Test
    public void postWithWrongToken_returns403() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CsrfFilter.SESSION_ATTR, "correct-token");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/claims");
        req.setSession(session);
        req.addParameter(CsrfFilter.PARAM_NAME, "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals("wrong token must return 403", 403, res.getStatus());
    }

    @Test
    public void tokenIsConsistentAcrossRequestsOnSameSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/form");
        req1.setSession(session);
        filter.doFilter(req1, new MockHttpServletResponse(), new MockFilterChain());
        String token1 = (String) req1.getAttribute(CsrfFilter.REQUEST_ATTR);

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/form");
        req2.setSession(session);
        filter.doFilter(req2, new MockHttpServletResponse(), new MockFilterChain());
        String token2 = (String) req2.getAttribute(CsrfFilter.REQUEST_ATTR);

        assertEquals("same session must yield same token", token1, token2);
    }
}
