package com.meridian.claims.service;

import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.model.User;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Date;

@Service
public class AuthenticationService {

    private static final Logger LOG = Logger.getLogger(AuthenticationService.class);

    public static final String SESSION_USER_KEY = "MERIDIAN_USER";

    private final UserDAO userDAO;
    private final PasswordService passwordService;
    private final SecurityPolicy securityPolicy;

    @Autowired
    public AuthenticationService(UserDAO userDAO, PasswordService passwordService,
                                 SecurityPolicy securityPolicy) {
        this.userDAO = userDAO;
        this.passwordService = passwordService;
        this.securityPolicy = securityPolicy;
    }

    public enum LoginResult {
        SUCCESS,
        SUCCESS_PASSWORD_EXPIRED,
        SUCCESS_FORCE_RESET,
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED,
        ACCOUNT_INACTIVE
    }

    @Transactional
    public LoginResult login(String username, String password, HttpServletRequest request) {
        User user = userDAO.findByUsername(username);

        if (user == null) {
            LOG.info("Login failed: unknown username=" + username);
            return LoginResult.INVALID_CREDENTIALS;
        }

        if (!user.isActive()) {
            LOG.info("Login denied: inactive user id=" + user.getId());
            return LoginResult.ACCOUNT_INACTIVE;
        }

        if (user.isLocked()) {
            LOG.info("Login denied: account locked user id=" + user.getId());
            return LoginResult.ACCOUNT_LOCKED;
        }

        if (!passwordService.verify(password, user.getPasswordHash())) {
            int newCount = user.getFailedLoginCount() + 1;
            userDAO.updateFailedLoginCount(user.getId(), newCount);
            if (newCount >= securityPolicy.getMaxFailedAttempts()) {
                Date lockUntil = new Date(new Date().getTime() + securityPolicy.getLockoutMillis());
                userDAO.updateLockedUntil(user.getId(), lockUntil);
                LOG.warn("Account locked after " + newCount + " failures: user id=" + user.getId());
            }
            LOG.info("Login failed: wrong password user id=" + user.getId() + " attempts=" + newCount);
            return LoginResult.INVALID_CREDENTIALS;
        }

        // Successful authentication — reset failure counters
        userDAO.updateFailedLoginCount(user.getId(), 0);
        userDAO.updateLockedUntil(user.getId(), null);
        userDAO.updateLastLoginAt(user.getId(), new Date());

        // Session fixation protection: invalidate old session, get a new one
        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute(SESSION_USER_KEY, user);

        String ipAddress = request.getRemoteAddr();
        userDAO.insertSession(user.getId(), newSession.getId(), ipAddress);

        LOG.info("Login successful: user id=" + user.getId() + " role=" + user.getRole());

        if (user.isForceReset()) {
            return LoginResult.SUCCESS_FORCE_RESET;
        }
        if (user.isPasswordExpired(securityPolicy.getPasswordExpiryDays())) {
            return LoginResult.SUCCESS_PASSWORD_EXPIRED;
        }
        return LoginResult.SUCCESS;
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            User user = (User) session.getAttribute(SESSION_USER_KEY);
            if (user != null) {
                userDAO.closeSession(session.getId(), new Date());
                LOG.info("Logout: user id=" + user.getId());
            }
            session.invalidate();
        }
    }

    public User getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (User) session.getAttribute(SESSION_USER_KEY);
    }
}
