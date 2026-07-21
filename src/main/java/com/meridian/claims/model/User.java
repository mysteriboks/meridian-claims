package com.meridian.claims.model;

import java.util.Date;

public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String fullName;
    private UserRole role;
    private boolean active;
    private int failedLoginCount;
    private Date lockedUntil;
    private Date lastLoginAt;
    private Date passwordChangedAt;
    private boolean forceReset;
    private Date createdAt;
    private Date updatedAt;

    public User() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }

    public Date getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Date lockedUntil) { this.lockedUntil = lockedUntil; }

    public Date getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Date lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Date getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(Date passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }

    public boolean isForceReset() { return forceReset; }
    public void setForceReset(boolean forceReset) { this.forceReset = forceReset; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        return new Date().before(lockedUntil);
    }

    public boolean isPasswordExpired(int expiryDays) {
        if (passwordChangedAt == null) {
            return true;
        }
        long expiryMillis = (long) expiryDays * 24 * 60 * 60 * 1000;
        return (new Date().getTime() - passwordChangedAt.getTime()) > expiryMillis;
    }
}
