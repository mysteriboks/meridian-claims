package com.meridian.claims.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppProfileTest {

    @Test
    public void defaultsToDevWhenNoSystemProperty() {
        // System property is not set in the test JVM (maven-surefire clears it),
        // so the default 'dev' profile must be active.
        String savedProp = System.getProperty("claims.profile");
        System.clearProperty("claims.profile");
        try {
            AppProfile p = new AppProfile();
            assertEquals(AppProfile.DEV, p.getProfile());
            assertTrue(p.isDev());
            assertFalse(p.isProd());
        } finally {
            if (savedProp != null) System.setProperty("claims.profile", savedProp);
        }
    }

    @Test
    public void recognisesProdFromSystemProperty() {
        String saved = System.getProperty("claims.profile");
        System.setProperty("claims.profile", "prod");
        try {
            AppProfile p = new AppProfile();
            assertEquals(AppProfile.PROD, p.getProfile());
            assertTrue(p.isProd());
            assertFalse(p.isDev());
        } finally {
            if (saved != null) System.setProperty("claims.profile", saved);
            else System.clearProperty("claims.profile");
        }
    }

    @Test
    public void normalisesCaseFromSystemProperty() {
        String saved = System.getProperty("claims.profile");
        System.setProperty("claims.profile", "PROD");
        try {
            AppProfile p = new AppProfile();
            assertTrue(p.isProd());
        } finally {
            if (saved != null) System.setProperty("claims.profile", saved);
            else System.clearProperty("claims.profile");
        }
    }
}
