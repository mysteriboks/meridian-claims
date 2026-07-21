package com.meridian.claims.web;

import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuthenticationService;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RoleFilterTest {

    private RoleFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;

    @Before
    public void setUp() {
        filter = new RoleFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
    }

    private void loginAs(UserRole role) {
        User u = new User();
        u.setId(1);
        u.setRole(role);
        when(session.getAttribute(AuthenticationService.SESSION_USER_KEY)).thenReturn(u);
    }

    private void request(String method, String path) {
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(path);
    }

    @Test
    public void analystCannotPostToMembers() throws Exception {
        loginAs(UserRole.ANALYST);
        request("POST", "/members/new");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    public void analystCanGetMembers() throws Exception {
        loginAs(UserRole.ANALYST);
        request("GET", "/members");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void analystCannotPostToPriorAuth() throws Exception {
        loginAs(UserRole.ANALYST);
        request("POST", "/prior-auth/new");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void staffCanPostToMembers() throws Exception {
        loginAs(UserRole.STAFF);
        request("POST", "/members/new");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void nonAdminForbiddenFromAdminPlans() throws Exception {
        loginAs(UserRole.STAFF);
        request("GET", "/admin/plans");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    public void adminReachesAdminPlans() throws Exception {
        loginAs(UserRole.ADMIN);
        request("GET", "/admin/plans");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    public void noSessionPassesThrough() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    public void analystCanGetClaims() throws Exception {
        loginAs(UserRole.ANALYST);
        request("GET", "/claims");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void analystCannotPostToClaims() throws Exception {
        loginAs(UserRole.ANALYST);
        request("POST", "/claims");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // /reports/** — MED-11
    // -------------------------------------------------------------------------

    @Test
    public void staffCannotAccessReports() throws Exception {
        loginAs(UserRole.STAFF);
        request("GET", "/reports/claims-summary");

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    public void analystCanAccessReports() throws Exception {
        loginAs(UserRole.ANALYST);
        request("GET", "/reports/denials");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    public void financeCanAccessReports() throws Exception {
        loginAs(UserRole.FINANCE);
        request("GET", "/reports/payments");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
