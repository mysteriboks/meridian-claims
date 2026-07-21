package com.meridian.claims.util;

/**
 * Exposes the active deployment profile (dev | prod) resolved once at startup.
 *
 * Resolution order:
 *   1. JVM system property:  -Dclaims.profile=prod
 *   2. Environment variable: CLAIMS_PROFILE=prod
 *   3. Default:              dev
 *
 * Injected as a Spring bean so filters and services can branch without
 * re-reading system properties on every request.
 */
public class AppProfile {

    public static final String DEV  = "dev";
    public static final String PROD = "prod";

    private final String profile;

    public AppProfile() {
        String sysProp = System.getProperty("claims.profile");
        if (sysProp != null && sysProp.trim().length() > 0) {
            this.profile = sysProp.trim().toLowerCase();
        } else {
            String envVar = System.getenv("CLAIMS_PROFILE");
            if (envVar != null && envVar.trim().length() > 0) {
                this.profile = envVar.trim().toLowerCase();
            } else {
                this.profile = DEV;
            }
        }
    }

    public String getProfile() {
        return profile;
    }

    public boolean isProd() {
        return PROD.equals(profile);
    }

    public boolean isDev() {
        return DEV.equals(profile);
    }
}
