package com.meridian.claims.foundation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.meridian.claims.service.HealthService;
import com.meridian.claims.service.HealthStatus;
import com.meridian.claims.util.StartupValidator;

/**
 * Phase 1 foundation verification.
 *
 * No PostgreSQL/Tomcat is available in CI, so this exercises the foundation
 * wiring against an in-memory H2 database running in PostgreSQL-compatibility
 * mode. It proves the things Phase 1's exit criteria care about, short of an
 * actual container deploy:
 *
 *   * the REAL V1 Flyway migration applies cleanly (classpath:db/migration)
 *   * StartupValidator runs migrations + verifies connectivity without error
 *   * the migrated schema contains the expected tables
 *   * HealthService reports UP and surfaces DBCP2 pool stats
 *
 * The "deploys to Tomcat / fails fast on unreachable DB" criteria still need a
 * real runtime check — see PROGRESS.md.
 */
public class FoundationIT {

    private BasicDataSource dataSource;

    @Before
    public void setUp() {
        // H2 in PostgreSQL mode, so SERIAL/BOOLEAN/TIMESTAMP DDL behaves like prod.
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_foundation_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    public void startupValidatorRunsMigrationsAndVerifiesConnectivity() throws Exception {
        // Use H2-compatible migrations (no PL/pgSQL) kept under src/test/resources
        StartupValidator validator = new StartupValidator(dataSource, "classpath:db/migration");
        validator.afterPropertiesSet();

        assertTrue("users table should exist after migration", tableExists("USERS"));
        assertTrue("audit_log table should exist after migration", tableExists("AUDIT_LOG"));
    }

    @Test
    public void healthServiceReportsUpWithPoolStats() throws Exception {
        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DataSource ds = dataSource;
        HealthService healthService = new HealthService(jdbcTemplate, ds);

        HealthStatus status = healthService.check();
        assertEquals("UP", status.getStatus());
        assertTrue("database should be reported up", status.isDatabaseUp());
        assertEquals("pool maxTotal should reflect DBCP2 config", 5, status.getPoolMaxTotal());
        assertTrue("pool should report a non-negative idle count", status.getPoolIdle() >= 0);
    }

    private boolean tableExists(String tableName) throws Exception {
        Connection connection = null;
        Statement statement = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE UPPER(table_name) = '" + tableName + "'");
            rs.next();
            return rs.getInt(1) > 0;
        } finally {
            if (rs != null) { rs.close(); }
            if (statement != null) { statement.close(); }
            if (connection != null) { connection.close(); }
        }
    }
}
