package com.meridian.claims.service;

import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuthenticationService.LoginResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AuthenticationServiceTest {

    private UserDAO userDAO;
    private PasswordService passwordService;
    private AuthenticationService authService;

    private HttpServletRequest request;
    private HttpSession oldSession;
    private HttpSession newSession;

    @Before
    public void setUp() {
        userDAO = mock(UserDAO.class);
        passwordService = mock(PasswordService.class);
        authService = new AuthenticationService(userDAO, passwordService, SecurityPolicy.defaults());

        request = mock(HttpServletRequest.class);
        oldSession = mock(HttpSession.class);
        newSession = mock(HttpSession.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(newSession.getId()).thenReturn("NEW-SESSION-ID");
        // getSession(false) -> existing session to invalidate; getSession(true) -> new
        when(request.getSession(false)).thenReturn(oldSession);
        when(request.getSession(true)).thenReturn(newSession);
    }

    private User activeUser(String hash) {
        User u = new User();
        u.setId(7);
        u.setUsername("jdoe");
        u.setPasswordHash(hash);
        u.setRole(UserRole.STAFF);
        u.setActive(true);
        u.setForceReset(false);
        u.setPasswordChangedAt(new Date()); // fresh, not expired
        return u;
    }

    @Test
    public void unknownUserReturnsInvalidCredentials() {
        when(userDAO.findByUsername("ghost")).thenReturn(null);
        assertEquals(LoginResult.INVALID_CREDENTIALS,
                authService.login("ghost", "x", request));
        verify(userDAO, never()).insertSession(anyInt(), any(), any());
    }

    @Test
    public void inactiveUserIsRejected() {
        User u = activeUser("hash");
        u.setActive(false);
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        assertEquals(LoginResult.ACCOUNT_INACTIVE,
                authService.login("jdoe", "pw", request));
    }

    @Test
    public void lockedUserIsRejectedWithoutCheckingPassword() {
        User u = activeUser("hash");
        u.setLockedUntil(new Date(System.currentTimeMillis() + 60_000)); // locked 1 min out
        when(userDAO.findByUsername("jdoe")).thenReturn(u);

        assertEquals(LoginResult.ACCOUNT_LOCKED,
                authService.login("jdoe", "pw", request));
        verify(passwordService, never()).verify(any(), any());
    }

    @Test
    public void fifthFailedAttemptLocksAccount() {
        User u = activeUser("hash");
        u.setFailedLoginCount(4); // this attempt will be the 5th
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        when(passwordService.verify("wrong", "hash")).thenReturn(false);

        LoginResult result = authService.login("jdoe", "wrong", request);

        assertEquals(LoginResult.INVALID_CREDENTIALS, result);
        verify(userDAO).updateFailedLoginCount(7, 5);
        ArgumentCaptor<Date> lockCaptor = ArgumentCaptor.forClass(Date.class);
        verify(userDAO).updateLockedUntil(eq(7), lockCaptor.capture());
        assertNotNull("lock timestamp should be set", lockCaptor.getValue());
        assertTrue("lock should be in the future",
                lockCaptor.getValue().after(new Date()));
    }

    @Test
    public void earlyFailedAttemptIncrementsButDoesNotLock() {
        User u = activeUser("hash");
        u.setFailedLoginCount(1); // this attempt -> 2, below threshold
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        when(passwordService.verify("wrong", "hash")).thenReturn(false);

        authService.login("jdoe", "wrong", request);

        verify(userDAO).updateFailedLoginCount(7, 2);
        verify(userDAO, never()).updateLockedUntil(eq(7), any());
    }

    @Test
    public void successResetsCountersAndStartsNewSession() {
        User u = activeUser("hash");
        u.setFailedLoginCount(3);
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        when(passwordService.verify("rightpw", "hash")).thenReturn(true);

        LoginResult result = authService.login("jdoe", "rightpw", request);

        assertEquals(LoginResult.SUCCESS, result);
        verify(userDAO).updateFailedLoginCount(7, 0);
        verify(userDAO).updateLockedUntil(7, null);
        verify(userDAO).updateLastLoginAt(eq(7), any(Date.class));
        // session fixation: old invalidated, new created and user stored
        verify(oldSession).invalidate();
        verify(newSession).setAttribute(AuthenticationService.SESSION_USER_KEY, u);
        verify(userDAO).insertSession(7, "NEW-SESSION-ID", "127.0.0.1");
    }

    @Test
    public void forceResetUserGetsForceResetResult() {
        User u = activeUser("hash");
        u.setForceReset(true);
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        when(passwordService.verify("rightpw", "hash")).thenReturn(true);

        assertEquals(LoginResult.SUCCESS_FORCE_RESET,
                authService.login("jdoe", "rightpw", request));
    }

    @Test
    public void expiredPasswordGetsExpiredResult() {
        User u = activeUser("hash");
        long over90Days = 91L * 24 * 60 * 60 * 1000;
        u.setPasswordChangedAt(new Date(System.currentTimeMillis() - over90Days));
        when(userDAO.findByUsername("jdoe")).thenReturn(u);
        when(passwordService.verify("rightpw", "hash")).thenReturn(true);

        assertEquals(LoginResult.SUCCESS_PASSWORD_EXPIRED,
                authService.login("jdoe", "rightpw", request));
    }
}
