package com.meridian.claims.dao;

import com.meridian.claims.model.User;

import java.util.Date;
import java.util.List;

public interface UserDAO {

    User findById(int id);

    User findByUsername(String username);

    List<User> findAll();

    List<User> findAllActive();

    void insert(User user);

    void update(User user);

    void updateFailedLoginCount(int userId, int count);

    void updateLockedUntil(int userId, Date lockedUntil);

    void updateLastLoginAt(int userId, Date lastLoginAt);

    void updatePasswordHash(int userId, String passwordHash, Date passwordChangedAt);

    void updateForceReset(int userId, boolean forceReset);

    void setActive(int userId, boolean active);

    void insertSession(int userId, String sessionId, String ipAddress);

    void closeSession(String sessionId, Date logoutAt);
}
