package com.meridian.claims.dao;

import com.meridian.claims.model.SubrogationCase;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcSubrogationCaseDAO extends BaseDAO implements SubrogationCaseDAO {

    private static final Logger LOG = Logger.getLogger(JdbcSubrogationCaseDAO.class);

    private static final String COLS =
        "id, claim_id, opened_date, status, liable_party, recovery_amount, notes, created_at, updated_at";

    @Override
    public void insert(SubrogationCase sc) {
        String sql = "INSERT INTO subrogation_cases (claim_id, opened_date, status) VALUES (?,?,'OPEN')";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final SubrogationCase s = sc;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, s.getClaimId());
                ps.setDate(2, new Date(s.getOpenedDate().getTime()));
                return ps;
            }, key);
            sc.setId(key.getKey().intValue());
            sc.setStatus("OPEN");
        } catch (Exception e) {
            LOG.error("insert subrogation_case failed claimId=" + sc.getClaimId(), e);
            throw new DAOException("Could not insert subrogation case", e);
        }
    }

    @Override
    public SubrogationCase findByClaimId(int claimId) {
        String sql = "SELECT " + COLS + " FROM subrogation_cases WHERE claim_id = ?";
        try {
            List<SubrogationCase> rows = getJdbcTemplate().query(sql, new Subro(), claimId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load subrogation case for claim " + claimId, e);
        }
    }

    @Override
    public SubrogationCase findById(int id) {
        String sql = "SELECT " + COLS + " FROM subrogation_cases WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new Subro(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load subrogation case id=" + id, e);
        }
    }

    @Override
    public List<SubrogationCase> findByStatus(String status) {
        String sql = "SELECT " + COLS + " FROM subrogation_cases WHERE status = ? ORDER BY opened_date ASC";
        try {
            return getJdbcTemplate().query(sql, new Subro(), status);
        } catch (Exception e) {
            LOG.error("findByStatus failed status=" + status, e);
            throw new DAOException("Could not load subrogation cases by status", e);
        }
    }

    @Override
    public void recordRecovery(int id, String liableParty, BigDecimal recoveryAmount, String notes) {
        String sql = "UPDATE subrogation_cases SET status = 'RECOVERED', liable_party = ?, " +
            "recovery_amount = ?, notes = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, liableParty, recoveryAmount, notes, id);
        } catch (Exception e) {
            LOG.error("recordRecovery failed id=" + id, e);
            throw new DAOException("Could not record recovery id=" + id, e);
        }
    }

    @Override
    public void close(int id) {
        String sql = "UPDATE subrogation_cases SET status = 'CLOSED' WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, id);
        } catch (Exception e) {
            LOG.error("close subrogation_case failed id=" + id, e);
            throw new DAOException("Could not close subrogation case id=" + id, e);
        }
    }

    private static final class Subro implements RowMapper<SubrogationCase> {
        @Override
        public SubrogationCase mapRow(ResultSet rs, int rowNum) throws SQLException {
            SubrogationCase sc = new SubrogationCase();
            sc.setId(rs.getInt("id"));
            sc.setClaimId(rs.getInt("claim_id"));
            sc.setOpenedDate(rs.getDate("opened_date"));
            sc.setStatus(rs.getString("status"));
            sc.setLiableParty(rs.getString("liable_party"));
            sc.setRecoveryAmount(rs.getBigDecimal("recovery_amount"));
            sc.setNotes(rs.getString("notes"));
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { sc.setCreatedAt(new java.util.Date(ca.getTime())); }
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) { sc.setUpdatedAt(new java.util.Date(ua.getTime())); }
            return sc;
        }
    }
}
