package com.meridian.claims.dao;

import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

@Repository
public class JdbcUserDAO extends BaseDAO implements UserDAO {

    private static final Logger LOG = Logger.getLogger(JdbcUserDAO.class);

    private static final String SELECT_COLS =
        "id, username, password_hash, full_name, role, active, " +
        "failed_login_count, locked_until, last_login_at, password_changed_at, " +
        "force_reset, created_at, updated_at";

    @Override
    public User findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM users WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new UserRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed for id=" + id, e);
            throw new DAOException("Could not load user id=" + id, e);
        }
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT " + SELECT_COLS + " FROM users WHERE username = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new UserRowMapper(), username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByUsername failed for username=" + username, e);
            throw new DAOException("Could not load user username=" + username, e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT " + SELECT_COLS + " FROM users ORDER BY full_name";
        try {
            return getJdbcTemplate().query(sql, new UserRowMapper());
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list users", e);
        }
    }

    @Override
    public List<User> findAllActive() {
        String sql = "SELECT " + SELECT_COLS + " FROM users WHERE active = TRUE ORDER BY full_name";
        try {
            return getJdbcTemplate().query(sql, new UserRowMapper());
        } catch (Exception e) {
            LOG.error("findAllActive failed", e);
            throw new DAOException("Could not list active users", e);
        }
    }

    @Override
    public void insert(User user) {
        String sql = "INSERT INTO users " +
            "(username, password_hash, full_name, role, active, failed_login_count, " +
            " force_reset, password_changed_at) " +
            "VALUES (?, ?, ?, ?, ?, 0, ?, ?)";
        try {
            getJdbcTemplate().update(sql,
                user.getUsername(),
                user.getPasswordHash(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.isForceReset(),
                user.getPasswordChangedAt() == null ? null : new Timestamp(user.getPasswordChangedAt().getTime())
            );
        } catch (Exception e) {
            LOG.error("insert failed for username=" + user.getUsername(), e);
            throw new DAOException("Could not insert user", e);
        }
    }

    @Override
    public void update(User user) {
        String sql = "UPDATE users SET username = ?, full_name = ?, role = ?, active = ?, " +
            "force_reset = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.isActive(),
                user.isForceReset(),
                user.getId()
            );
        } catch (Exception e) {
            LOG.error("update failed for user id=" + user.getId(), e);
            throw new DAOException("Could not update user id=" + user.getId(), e);
        }
    }

    @Override
    public void updateFailedLoginCount(int userId, int count) {
        try {
            getJdbcTemplate().update(
                "UPDATE users SET failed_login_count = ? WHERE id = ?", count, userId);
        } catch (Exception e) {
            LOG.error("updateFailedLoginCount failed for user id=" + userId, e);
            throw new DAOException("Could not update failed_login_count for user id=" + userId, e);
        }
    }

    @Override
    public void updateLockedUntil(int userId, Date lockedUntil) {
        Timestamp ts = lockedUntil == null ? null : new Timestamp(lockedUntil.getTime());
        try {
            getJdbcTemplate().update(
                "UPDATE users SET locked_until = ? WHERE id = ?", ts, userId);
        } catch (Exception e) {
            LOG.error("updateLockedUntil failed for user id=" + userId, e);
            throw new DAOException("Could not update locked_until for user id=" + userId, e);
        }
    }

    @Override
    public void updateLastLoginAt(int userId, Date lastLoginAt) {
        Timestamp ts = lastLoginAt == null ? null : new Timestamp(lastLoginAt.getTime());
        try {
            getJdbcTemplate().update(
                "UPDATE users SET last_login_at = ? WHERE id = ?", ts, userId);
        } catch (Exception e) {
            LOG.error("updateLastLoginAt failed for user id=" + userId, e);
            throw new DAOException("Could not update last_login_at for user id=" + userId, e);
        }
    }

    @Override
    public void updatePasswordHash(int userId, String passwordHash, Date passwordChangedAt) {
        Timestamp ts = passwordChangedAt == null ? null : new Timestamp(passwordChangedAt.getTime());
        try {
            getJdbcTemplate().update(
                "UPDATE users SET password_hash = ?, password_changed_at = ?, " +
                "force_reset = FALSE, failed_login_count = 0, locked_until = NULL WHERE id = ?",
                passwordHash, ts, userId);
        } catch (Exception e) {
            LOG.error("updatePasswordHash failed for user id=" + userId, e);
            throw new DAOException("Could not update password for user id=" + userId, e);
        }
    }

    @Override
    public void updateForceReset(int userId, boolean forceReset) {
        try {
            getJdbcTemplate().update(
                "UPDATE users SET force_reset = ? WHERE id = ?", forceReset, userId);
        } catch (Exception e) {
            LOG.error("updateForceReset failed for user id=" + userId, e);
            throw new DAOException("Could not update force_reset for user id=" + userId, e);
        }
    }

    @Override
    public void setActive(int userId, boolean active) {
        try {
            getJdbcTemplate().update(
                "UPDATE users SET active = ? WHERE id = ?", active, userId);
        } catch (Exception e) {
            LOG.error("setActive failed for user id=" + userId, e);
            throw new DAOException("Could not set active=" + active + " for user id=" + userId, e);
        }
    }

    @Override
    public void insertSession(int userId, String sessionId, String ipAddress) {
        String sql = "INSERT INTO user_sessions (user_id, session_id, ip_address) VALUES (?, ?, ?)";
        try {
            getJdbcTemplate().update(sql, userId, sessionId, ipAddress);
        } catch (Exception e) {
            LOG.error("insertSession failed for user id=" + userId, e);
            throw new DAOException("Could not record session for user id=" + userId, e);
        }
    }

    @Override
    public void closeSession(String sessionId, Date logoutAt) {
        Timestamp ts = logoutAt == null ? null : new Timestamp(logoutAt.getTime());
        try {
            getJdbcTemplate().update(
                "UPDATE user_sessions SET logout_at = ? WHERE session_id = ? AND logout_at IS NULL",
                ts, sessionId);
        } catch (Exception e) {
            LOG.error("closeSession failed for sessionId=" + sessionId, e);
            throw new DAOException("Could not close session " + sessionId, e);
        }
    }

    private static final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User u = new User();
            u.setId(rs.getInt("id"));
            u.setUsername(rs.getString("username"));
            u.setPasswordHash(rs.getString("password_hash"));
            u.setFullName(rs.getString("full_name"));
            u.setRole(UserRole.valueOf(rs.getString("role")));
            u.setActive(rs.getBoolean("active"));
            u.setFailedLoginCount(rs.getInt("failed_login_count"));

            Timestamp lockedUntil = rs.getTimestamp("locked_until");
            if (lockedUntil != null) u.setLockedUntil(new Date(lockedUntil.getTime()));

            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            if (lastLogin != null) u.setLastLoginAt(new Date(lastLogin.getTime()));

            Timestamp pwChanged = rs.getTimestamp("password_changed_at");
            if (pwChanged != null) u.setPasswordChangedAt(new Date(pwChanged.getTime()));

            u.setForceReset(rs.getBoolean("force_reset"));

            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) u.setCreatedAt(new Date(createdAt.getTime()));

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) u.setUpdatedAt(new Date(updatedAt.getTime()));

            return u;
        }
    }
}
