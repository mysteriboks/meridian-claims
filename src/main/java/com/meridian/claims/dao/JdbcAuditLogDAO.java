package com.meridian.claims.dao;

import com.meridian.claims.model.AuditLogEntry;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class JdbcAuditLogDAO extends BaseDAO implements AuditLogDAO {

    private static final Logger LOG = Logger.getLogger(JdbcAuditLogDAO.class);

    @Override
    public void insert(AuditLogEntry entry) {
        String sql = "INSERT INTO audit_log (event_type, entity_type, entity_id, user_id, description) " +
            "VALUES (?, ?, ?, ?, ?)";
        try {
            getJdbcTemplate().update(sql,
                entry.getEventType(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getUserId(),
                entry.getDescription());
        } catch (Exception e) {
            LOG.error("insert audit entry failed eventType=" + entry.getEventType(), e);
            throw new DAOException("Could not write audit log entry", e);
        }
    }

    @Override
    public List<AuditLogEntry> findByEntity(String entityType, long entityId) {
        String sql = "SELECT id, event_type, entity_type, entity_id, user_id, description, created_at " +
            "FROM audit_log WHERE entity_type = ? AND entity_id = ? ORDER BY created_at DESC, id DESC";
        try {
            return getJdbcTemplate().query(sql, new AuditRowMapper(), entityType, entityId);
        } catch (Exception e) {
            LOG.error("findByEntity failed entityType=" + entityType + " entityId=" + entityId, e);
            throw new DAOException("Could not load audit entries", e);
        }
    }

    @Override
    public Page<AuditLogEntry> search(String username, String eventType, String entityType,
                                      java.util.Date from, java.util.Date to,
                                      int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<Object>();

        if (username != null && !username.trim().isEmpty()) {
            where.append(" AND u.username ILIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (eventType != null && !eventType.trim().isEmpty()) {
            where.append(" AND a.event_type = ?");
            params.add(eventType.trim());
        }
        if (entityType != null && !entityType.trim().isEmpty()) {
            where.append(" AND a.entity_type = ?");
            params.add(entityType.trim());
        }
        if (from != null) {
            where.append(" AND a.created_at >= ?");
            params.add(new Timestamp(from.getTime()));
        }
        if (to != null) {
            // Inclusive of the selected day: the picker sends midnight at the start
            // of the 'to' date, so advance 24h and use a strict upper bound to cover
            // every entry within that calendar day.
            where.append(" AND a.created_at < ?");
            params.add(new Timestamp(to.getTime() + 24L * 60L * 60L * 1000L));
        }

        String countSql = "SELECT COUNT(*) FROM audit_log a"
                + " LEFT JOIN users u ON a.user_id = u.id"
                + where;
        String dataSql  = "SELECT a.id, a.event_type, a.entity_type, a.entity_id,"
                + " a.user_id, a.description, a.created_at, u.username"
                + " FROM audit_log a"
                + " LEFT JOIN users u ON a.user_id = u.id"
                + where
                + " ORDER BY a.created_at DESC, a.id DESC"
                + " LIMIT ? OFFSET ?";

        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, params.toArray());

            List<Object> dataParams = new ArrayList<Object>(params);
            dataParams.add(pageSize);
            dataParams.add((page - 1) * pageSize);

            List<AuditLogEntry> entries = getJdbcTemplate().query(
                    dataSql, new AuditRowMapper(), dataParams.toArray());

            return new Page<AuditLogEntry>(entries, page, pageSize, total);
        } catch (Exception e) {
            LOG.error("audit_log search failed", e);
            throw new DAOException("Could not search audit log", e);
        }
    }

    private static final class AuditRowMapper implements RowMapper<AuditLogEntry> {
        @Override
        public AuditLogEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            AuditLogEntry e = new AuditLogEntry();
            e.setId(rs.getLong("id"));
            e.setEventType(rs.getString("event_type"));
            e.setEntityType(rs.getString("entity_type"));
            long entityId = rs.getLong("entity_id");
            if (!rs.wasNull()) e.setEntityId(entityId);
            int userId = rs.getInt("user_id");
            if (!rs.wasNull()) e.setUserId(userId);
            e.setDescription(rs.getString("description"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) e.setCreatedAt(new java.util.Date(createdAt.getTime()));
            try {
                e.setUsername(rs.getString("username"));
            } catch (SQLException ignore) {
                // username column only present in search queries (LEFT JOIN users)
            }
            return e;
        }
    }
}
