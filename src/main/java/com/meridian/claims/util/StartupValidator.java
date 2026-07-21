package com.meridian.claims.util;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

/**
 * Fail-fast startup gate.
 *
 * On context initialisation this bean:
 *   1. Logs the active deployment profile (dev | prod) prominently.
 *   2. Under prod: asserts that required environment variables are present.
 *   3. Runs Flyway migrations (validate-only in prod to prevent auto-repair).
 *   4. Verifies a real connection can be obtained from the pool.
 *
 * If any step fails it throws, aborting Spring context startup so the
 * application never comes up in a half-broken state.
 */
public class StartupValidator implements InitializingBean {

    private static final Logger LOG = Logger.getLogger(StartupValidator.class);

    private static final String DEFAULT_LOCATIONS = "classpath:db/migration";

    private final DataSource dataSource;
    private final String migrationsLocation;
    private AppProfile appProfile;

    public StartupValidator(DataSource dataSource) {
        this(dataSource, DEFAULT_LOCATIONS);
    }

    public StartupValidator(DataSource dataSource, String migrationsLocation) {
        this.dataSource = dataSource;
        this.migrationsLocation = migrationsLocation;
    }

    public void setAppProfile(AppProfile appProfile) {
        this.appProfile = appProfile;
    }

    public void afterPropertiesSet() throws Exception {
        logProfileBanner();
        if (appProfile != null && appProfile.isProd()) {
            assertProdRequirements();
        }
        verifyConnectivity();
        runMigrations();
        LOG.info("Meridian Claims startup validation passed: database reachable and schema migrated.");
    }

    private void logProfileBanner() {
        String profile = (appProfile != null) ? appProfile.getProfile() : AppProfile.DEV;
        LOG.info("=========================================================");
        LOG.info("  Meridian Claims starting — profile: " + profile.toUpperCase());
        LOG.info("=========================================================");
    }

    private void assertProdRequirements() {
        String dbUser = System.getenv("MERIDIAN_DB_USER");
        String dbPass = System.getenv("MERIDIAN_DB_PASSWORD");
        if (dbUser == null || dbUser.trim().length() == 0) {
            throw new IllegalStateException(
                    "STARTUP ABORTED [prod]: environment variable MERIDIAN_DB_USER is not set. "
                    + "Production requires DB credentials via environment variables only.");
        }
        if (dbPass == null || dbPass.trim().length() == 0) {
            throw new IllegalStateException(
                    "STARTUP ABORTED [prod]: environment variable MERIDIAN_DB_PASSWORD is not set. "
                    + "Production requires DB credentials via environment variables only.");
        }
    }

    private void runMigrations() {
        boolean isProd = (appProfile != null && appProfile.isProd());
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migrationsLocation)
                    .load();
            if (isProd) {
                // In prod: validate schema is in sync; never auto-repair.
                flyway.validate();
                LOG.info("Flyway schema validation passed (prod — auto-repair is disabled).");
            } else {
                flyway.migrate();
                LOG.info("Flyway migrations applied; schema version is up to date.");
            }
        } catch (RuntimeException ex) {
            String msg = isProd
                    ? "STARTUP ABORTED [prod]: Flyway schema validation failed. "
                      + "Apply pending migrations manually before deploying."
                    : "STARTUP ABORTED: Flyway migration failed. "
                      + "Check db/migration scripts and the schema_version table.";
            throw new IllegalStateException(msg, ex);
        }
    }

    private void verifyConnectivity() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (connection == null || !connection.isValid(5)) {
                throw new IllegalStateException(
                        "STARTUP ABORTED: obtained a connection from the pool but it is not valid.");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "STARTUP ABORTED: database is unreachable. Verify jdbc.url / credentials in "
                    + "db.properties and that PostgreSQL is running.",
                    ex);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignore) {
                    // returning to pool; nothing actionable
                }
            }
        }
    }
}
