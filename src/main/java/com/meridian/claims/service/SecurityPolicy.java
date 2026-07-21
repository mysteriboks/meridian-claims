package com.meridian.claims.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralised, externally-configurable security policy values.
 *
 * All values are overridable via application.properties (or the prod overlay)
 * so account-lockout and password-policy rules can be tuned per environment
 * without a recompile. Defaults match the original hardcoded constants.
 */
@Component
public class SecurityPolicy {

    @Value("${claims.security.lockout.max-attempts:5}")
    private int maxFailedAttempts;

    @Value("${claims.security.lockout.minutes:30}")
    private long lockoutMinutes;

    @Value("${claims.security.password.expiry-days:90}")
    private int passwordExpiryDays;

    @Value("${claims.security.password.min-length:8}")
    private int passwordMinLength;

    @Value("${claims.security.password.bcrypt-rounds:12}")
    private int bcryptRounds;

    /** Default constructor — Spring populates the fields via @Value injection. */
    public SecurityPolicy() {
    }

    /** Explicit constructor for unit tests (bypasses Spring property injection). */
    public SecurityPolicy(int maxFailedAttempts, long lockoutMinutes, int passwordExpiryDays,
                          int passwordMinLength, int bcryptRounds) {
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockoutMinutes = lockoutMinutes;
        this.passwordExpiryDays = passwordExpiryDays;
        this.passwordMinLength = passwordMinLength;
        this.bcryptRounds = bcryptRounds;
    }

    /** Policy with the production default values; convenient for tests. */
    public static SecurityPolicy defaults() {
        return new SecurityPolicy(5, 30L, 90, 8, 12);
    }

    /** Number of consecutive failed logins before the account is locked. */
    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    /** Lockout duration in minutes once the failed-attempt threshold is hit. */
    public long getLockoutMinutes() {
        return lockoutMinutes;
    }

    /** Lockout duration in milliseconds (convenience for Date arithmetic). */
    public long getLockoutMillis() {
        return lockoutMinutes * 60L * 1000L;
    }

    /** Maximum password age in days before a reset is forced. */
    public int getPasswordExpiryDays() {
        return passwordExpiryDays;
    }

    /** Minimum password length enforced by the complexity check. */
    public int getPasswordMinLength() {
        return passwordMinLength;
    }

    /** BCrypt work factor (log2 of rounds) used when hashing passwords. */
    public int getBcryptRounds() {
        return bcryptRounds;
    }
}
