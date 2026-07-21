package com.meridian.claims.dao;

/**
 * Unchecked exception that wraps any persistence-layer failure (SQL errors,
 * data access problems). DAO implementations catch low-level JDBC / Spring
 * DataAccessException and rethrow as DAOException so that callers in the
 * service layer deal with a single, project-specific type.
 *
 * This is intentionally a RuntimeException: persistence failures are not
 * something a controller can sensibly recover from, and we do not want to
 * litter every DAO call with checked-exception plumbing.
 */
public class DAOException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DAOException(String message) {
        super(message);
    }

    public DAOException(String message, Throwable cause) {
        super(message, cause);
    }
}
