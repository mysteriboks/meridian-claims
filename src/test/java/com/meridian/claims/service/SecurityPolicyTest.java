package com.meridian.claims.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SecurityPolicyTest {

    @Test
    public void defaultsMatchOriginalHardcodedValues() {
        SecurityPolicy p = SecurityPolicy.defaults();
        assertEquals(5, p.getMaxFailedAttempts());
        assertEquals(30L, p.getLockoutMinutes());
        assertEquals(90, p.getPasswordExpiryDays());
        assertEquals(8, p.getPasswordMinLength());
        assertEquals(12, p.getBcryptRounds());
    }

    @Test
    public void lockoutMillisDerivesFromMinutes() {
        SecurityPolicy p = new SecurityPolicy(5, 30L, 90, 8, 12);
        assertEquals(30L * 60L * 1000L, p.getLockoutMillis());
    }

    @Test
    public void explicitConstructorOverridesDefaults() {
        SecurityPolicy p = new SecurityPolicy(3, 15L, 60, 12, 10);
        assertEquals(3, p.getMaxFailedAttempts());
        assertEquals(15L, p.getLockoutMinutes());
        assertEquals(15L * 60L * 1000L, p.getLockoutMillis());
        assertEquals(60, p.getPasswordExpiryDays());
        assertEquals(12, p.getPasswordMinLength());
        assertEquals(10, p.getBcryptRounds());
    }
}
