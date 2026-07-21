package com.meridian.claims.service;

import com.meridian.claims.dao.UserDAO;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    private final UserDAO userDAO;
    private final PasswordService passwordService;

    @Autowired
    public UserService(UserDAO userDAO, PasswordService passwordService) {
        this.userDAO = userDAO;
        this.passwordService = passwordService;
    }

    public List<User> listAll() {
        return userDAO.findAll();
    }

    /**
     * Active users eligible to be assigned a claim for review. Claims are worked
     * by REVIEWER and ADMIN roles, so only those appear in the assignment dropdown.
     */
    public List<User> listAssignableReviewers() {
        List<User> active = userDAO.findAllActive();
        List<User> reviewers = new java.util.ArrayList<User>();
        for (int i = 0; i < active.size(); i++) {
            User u = active.get(i);
            if (u.getRole() == UserRole.REVIEWER || u.getRole() == UserRole.ADMIN) {
                reviewers.add(u);
            }
        }
        return reviewers;
    }

    public User findById(int id) {
        User user = userDAO.findById(id);
        if (user == null) {
            throw new ServiceException("User not found: id=" + id);
        }
        return user;
    }

    @Transactional
    public void createUser(String username, String fullName, UserRole role, String tempPassword) {
        if (userDAO.findByUsername(username) != null) {
            throw new ServiceException("Username already exists: " + username);
        }
        if (!passwordService.meetsComplexity(tempPassword)) {
            throw new ServiceException(passwordService.complexityMessage());
        }
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        user.setForceReset(true);
        user.setPasswordHash(passwordService.hash(tempPassword));
        // Stamp the password age now so a brand-new account is not treated as
        // already-expired; force_reset still requires a change on first login.
        user.setPasswordChangedAt(new Date());
        userDAO.insert(user);
        LOG.info("Created user: username=" + username + " role=" + role);
    }

    @Transactional
    public void updateUser(int id, String fullName, UserRole role) {
        User user = findById(id);
        user.setFullName(fullName);
        user.setRole(role);
        userDAO.update(user);
        LOG.info("Updated user id=" + id);
    }

    @Transactional
    public void deactivateUser(int id) {
        userDAO.setActive(id, false);
        LOG.info("Deactivated user id=" + id);
    }

    @Transactional
    public void activateUser(int id) {
        userDAO.setActive(id, true);
        LOG.info("Activated user id=" + id);
    }

    @Transactional
    public void unlockUser(int id) {
        userDAO.updateLockedUntil(id, null);
        userDAO.updateFailedLoginCount(id, 0);
        LOG.info("Unlocked user id=" + id);
    }

    @Transactional
    public void forcePasswordReset(int id) {
        userDAO.updateForceReset(id, true);
        LOG.info("Force-reset flag set for user id=" + id);
    }

    @Transactional
    public void resetPassword(int id, String newPassword) {
        if (!passwordService.meetsComplexity(newPassword)) {
            throw new ServiceException(passwordService.complexityMessage());
        }
        userDAO.updatePasswordHash(id, passwordService.hash(newPassword), new Date());
        LOG.info("Password reset by admin for user id=" + id);
    }

    /**
     * Persist a pre-hashed password and update the password_changed_at timestamp.
     * Used by PasswordController when the user changes their own password after
     * all validation (current-password check, complexity, confirm match) has
     * already been performed in the controller.
     */
    @Transactional
    public void updatePasswordHash(int id, String newHash, Date changedAt) {
        userDAO.updatePasswordHash(id, newHash, changedAt);
        LOG.info("Password changed by user id=" + id);
    }

    /**
     * Look up a user by username.  Returns null if no match exists.
     * Used by PasswordController to refresh the session user after a password change.
     */
    public User findByUsername(String username) {
        return userDAO.findByUsername(username);
    }
}
