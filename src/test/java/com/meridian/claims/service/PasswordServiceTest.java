package com.meridian.claims.service;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PasswordServiceTest {

    private PasswordService service;

    @Before
    public void setUp() {
        service = new PasswordService(SecurityPolicy.defaults());
    }

    @Test
    public void hashIsVerifiable() {
        String hash = service.hash("Admin#2026");
        assertTrue(service.verify("Admin#2026", hash));
    }

    @Test
    public void wrongPasswordDoesNotVerify() {
        String hash = service.hash("Admin#2026");
        assertFalse(service.verify("wrong#2026", hash));
    }

    @Test
    public void hashIsSaltedAndNotPlaintext() {
        String h1 = service.hash("Admin#2026");
        String h2 = service.hash("Admin#2026");
        // bcrypt salts each hash, so two hashes of the same password differ
        assertNotEquals(h1, h2);
        assertNotEquals("Admin#2026", h1);
    }

    @Test
    public void verifyHandlesMalformedHashGracefully() {
        assertFalse(service.verify("anything", "not-a-bcrypt-hash"));
    }

    @Test
    public void complexityRequiresMinimumLength() {
        assertFalse(service.meetsComplexity("Ab#1"));        // too short
        assertTrue(service.meetsComplexity("Abcd123!"));      // exactly 8, has digit + special
    }

    @Test
    public void complexityRequiresDigit() {
        assertFalse(service.meetsComplexity("password!"));    // no digit
        assertTrue(service.meetsComplexity("password1!"));
    }

    @Test
    public void complexityRequiresSpecialChar() {
        assertFalse(service.meetsComplexity("password1"));    // no special
        assertTrue(service.meetsComplexity("password1!"));
    }

    @Test
    public void complexityRejectsNull() {
        assertFalse(service.meetsComplexity(null));
    }

    @Test
    public void complexityHonoursConfiguredMinLength() {
        // Policy requiring a 12-char minimum should reject an 8-char password
        // that the default policy accepts.
        PasswordService strict = new PasswordService(new SecurityPolicy(5, 30L, 90, 12, 12));
        assertFalse(strict.meetsComplexity("Abcd123!"));      // 8 chars — too short for min 12
        assertTrue(strict.meetsComplexity("Abcdefgh123!"));   // 12 chars, digit + special
    }

    @Test
    public void complexityMessageReflectsConfiguredMinLength() {
        PasswordService strict = new PasswordService(new SecurityPolicy(5, 30L, 90, 12, 12));
        assertTrue(strict.complexityMessage().contains("12"));
    }
}
