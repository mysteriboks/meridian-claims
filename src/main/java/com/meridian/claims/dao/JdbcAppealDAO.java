package com.meridian.claims.dao;

import com.meridian.claims.model.Appeal;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcAppealDAO extends BaseDAO implements AppealDAO {

    private static final Logger LOG = Logger.getLogger(JdbcAppealDAO.class);

    private static final String COLS =
        "id, claim_id, member_id, appeal_type, submitted_date, deadline_date, status, " +
        "assigned_to, resolved_date, outcome, outcome_notes, submitted_by_user_id, created_at, updated_at";

    @Override
    public void insert(Appeal appeal) {
        String sql = "INSERT INTO appeals " +
            "(claim_id, member_id, appeal_type, submitted_date, deadline_date, status, submitted_by_user_id) " +
            "VALUES (?,?,?,?,?,'OPEN',?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final Appeal a = appeal;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, a.getClaimId());
                ps.setInt(2, a.getMemberId());
                ps.setString(3, a.getAppealType());
                ps.setDate(4, new Date(a.getSubmittedDate().getTime()));
                ps.setDate(5, new Date(a.getDeadlineDate().getTime()));
                if (a.getSubmittedByUserId() != null) {
                    ps.setInt(6, a.getSubmittedByUserId());
                } else {
                    ps.setNull(6, java.sql.Types.INTEGER);
                }
                return ps;
            }, key);
            appeal.setId(key.getKey().intValue());
            appeal.setStatus("OPEN");
        } catch (Exception e) {
            LOG.error("insert appeal failed claimId=" + appeal.getClaimId(), e);
            throw new DAOException("Could not insert appeal", e);
        }
    }

    @Override
    public Appeal findById(int id) {
        String sql = "SELECT " + COLS + " FROM appeals WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new AppealRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load appeal id=" + id, e);
        }
    }

    @Override
    public List<Appeal> findByClaimId(int claimId) {
        String sql = "SELECT " + COLS + " FROM appeals WHERE claim_id = ? ORDER BY created_at DESC";
        try {
            return getJdbcTemplate().query(sql, new AppealRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load appeals for claim " + claimId, e);
        }
    }

    @Override
    public List<Appeal> findByStatus(String status) {
        String sql = "SELECT " + COLS + " FROM appeals WHERE status = ? ORDER BY deadline_date ASC";
        try {
            return getJdbcTemplate().query(sql, new AppealRowMapper(), status);
        } catch (Exception e) {
            LOG.error("findByStatus failed status=" + status, e);
            throw new DAOException("Could not load appeals by status", e);
        }
    }

    @Override
    public void updateStatus(int id, String status, String outcome, String outcomeNotes) {
        String sql = "UPDATE appeals SET status = ?, outcome = ?, outcome_notes = ?, " +
            "resolved_date = CURRENT_DATE WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, status, outcome, outcomeNotes, id);
        } catch (Exception e) {
            LOG.error("updateStatus failed id=" + id, e);
            throw new DAOException("Could not update appeal status id=" + id, e);
        }
    }

    @Override
    public void updateAssignment(int id, Integer assignedTo) {
        String sql = "UPDATE appeals SET assigned_to = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, assignedTo, id);
        } catch (Exception e) {
            LOG.error("updateAssignment failed id=" + id, e);
            throw new DAOException("Could not update appeal assignment id=" + id, e);
        }
    }

    private static final class AppealRowMapper implements RowMapper<Appeal> {
        @Override
        public Appeal mapRow(ResultSet rs, int rowNum) throws SQLException {
            Appeal a = new Appeal();
            a.setId(rs.getInt("id"));
            a.setClaimId(rs.getInt("claim_id"));
            a.setMemberId(rs.getInt("member_id"));
            a.setAppealType(rs.getString("appeal_type"));
            a.setSubmittedDate(rs.getDate("submitted_date"));
            a.setDeadlineDate(rs.getDate("deadline_date"));
            a.setStatus(rs.getString("status"));
            int at = rs.getInt("assigned_to");
            if (!rs.wasNull()) { a.setAssignedTo(at); }
            a.setResolvedDate(rs.getDate("resolved_date"));
            a.setOutcome(rs.getString("outcome"));
            a.setOutcomeNotes(rs.getString("outcome_notes"));
            int sub = rs.getInt("submitted_by_user_id");
            if (!rs.wasNull()) { a.setSubmittedByUserId(sub); }
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { a.setCreatedAt(new java.util.Date(ca.getTime())); }
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) { a.setUpdatedAt(new java.util.Date(ua.getTime())); }
            return a;
        }
    }
}
