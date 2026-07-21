package com.meridian.claims.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared base for all JDBC DAO implementations. Holds the single
 * application-wide {@link JdbcTemplate} (wired from the root Spring context)
 * so concrete DAOs do not each re-declare datasource plumbing.
 *
 * Concrete DAOs extend this and call {@link #getJdbcTemplate()} to run
 * hand-written, parameterised SQL. Per project rules: no JPA, no ORM, plain
 * SQL strings only.
 */
public abstract class BaseDAO {

    private JdbcTemplate jdbcTemplate;

    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    protected JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }
}
