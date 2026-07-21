package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimAuditEntry;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcClaimAuditDAO extends BaseDAO implements ClaimAuditDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimAuditDAO.class);

    @Override
    public void insert(ClaimAuditEntry entry) {
        String sql = "INSERT INTO claim_audit " +
            "(claim_id, event_type, old_status, new_status, changed_by_user_id, change_reason, notes) " +
            "VALUES (?,?,?,?,?,?,?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final ClaimAuditEntry e = entry;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, e.getClaimId());
                ps.setString(2, e.getEventType());
                ps.setString(3, e.getOldStatus());
                ps.setString(4, e.getNewStatus());
                if (e.getChangedByUserId() != null) {
                    ps.setInt(5, e.getChangedByUserId());
                } else {
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
                ps.setString(6, e.getChangeReason());
                ps.setString(7, e.getNotes());
                return ps;
            }, keyHolder);
            entry.setId(keyHolder.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insert claim audit failed claimId=" + entry.getClaimId(), e);
            throw new DAOException("Could not insert claim audit entry", e);
        }
    }

    @Override
    public List<ClaimAuditEntry> findByClaimId(int claimId) {
        String sql = "SELECT id, claim_id, event_type, old_status, new_status, " +
            "changed_by_user_id, change_reason, notes, changed_at " +
            "FROM claim_audit WHERE claim_id = ? ORDER BY changed_at";
        try {
            return getJdbcTemplate().query(sql, new AuditRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load audit trail for claim id=" + claimId, e);
        }
    }

    private static final class AuditRowMapper implements RowMapper<ClaimAuditEntry> {
        @Override
        public ClaimAuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimAuditEntry e = new ClaimAuditEntry();
            e.setId(rs.getInt("id"));
            e.setClaimId(rs.getInt("claim_id"));
            e.setEventType(rs.getString("event_type"));
            e.setOldStatus(rs.getString("old_status"));
            e.setNewStatus(rs.getString("new_status"));
            int changedBy = rs.getInt("changed_by_user_id");
            if (!rs.wasNull()) { e.setChangedByUserId(changedBy); }
            e.setChangeReason(rs.getString("change_reason"));
            e.setNotes(rs.getString("notes"));
            Timestamp changedAt = rs.getTimestamp("changed_at");
            if (changedAt != null) { e.setChangedAt(new java.util.Date(changedAt.getTime())); }
            return e;
        }
    }
}
