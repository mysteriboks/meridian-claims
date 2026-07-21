package com.meridian.claims.service;

import com.meridian.claims.dao.AuditLogDAO;
import com.meridian.claims.model.AuditLogEntry;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AuditServiceTest {

    private AuditLogDAO auditLogDAO;
    private AuditService auditService;

    @Before
    public void setUp() {
        auditLogDAO = mock(AuditLogDAO.class);
        auditService = new AuditService(auditLogDAO);
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void recordsEntryWithUserFromSession() {
        // Bind a request whose session holds a logged-in user
        MockHttpServletRequest request = new MockHttpServletRequest();
        User user = new User();
        user.setId(42);
        user.setRole(UserRole.STAFF);
        request.getSession().setAttribute(AuthenticationService.SESSION_USER_KEY, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record("MEMBER_CREATED", "MEMBER", 7L, "Created member M001");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogDAO).insert(captor.capture());
        AuditLogEntry e = captor.getValue();
        assertEquals("MEMBER_CREATED", e.getEventType());
        assertEquals("MEMBER", e.getEntityType());
        assertEquals(Long.valueOf(7L), e.getEntityId());
        assertEquals(Integer.valueOf(42), e.getUserId());
        assertEquals("Created member M001", e.getDescription());
    }

    @Test
    public void recordsEntryWithNullUserWhenNoRequestBound() {
        // No RequestContextHolder bound (e.g. scheduled job)
        auditService.record("PLAN_DEACTIVATED", "PLAN", 3L, "Deactivated plan");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogDAO).insert(captor.capture());
        assertNull(captor.getValue().getUserId());
    }

    @Test
    public void nullUserWhenSessionHasNoUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // session exists but no MERIDIAN_USER attribute
        request.getSession();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.record("PROVIDER_CREATED", "PROVIDER", 9L, "Created provider");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogDAO).insert(captor.capture());
        assertNull(captor.getValue().getUserId());
    }
}
