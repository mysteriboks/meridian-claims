package com.meridian.claims.service;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reports application health: database reachability plus DBCP2 connection-pool
 * statistics. Backs the /health endpoint (PHASES.md Phase 1 exit criterion
 * "/health returns DB status and pool stats").
 */
@Service
public class HealthService {

    private static final Logger LOG = Logger.getLogger(HealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Autowired
    public HealthService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public HealthStatus check() {
        boolean databaseUp;
        String databaseMessage;
        try {
            // Cheap round-trip that proves the pool can hand out a working connection.
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            databaseUp = true;
            databaseMessage = "Database connection OK";
        } catch (RuntimeException ex) {
            databaseUp = false;
            databaseMessage = "Database unreachable";
            LOG.error("Health check failed: database query did not succeed", ex);
        }

        int active = -1;
        int idle = -1;
        int maxTotal = -1;
        if (dataSource instanceof BasicDataSource) {
            BasicDataSource bds = (BasicDataSource) dataSource;
            active = bds.getNumActive();
            idle = bds.getNumIdle();
            maxTotal = bds.getMaxTotal();
        }

        return new HealthStatus(databaseUp, databaseMessage, active, idle, maxTotal);
    }
}
