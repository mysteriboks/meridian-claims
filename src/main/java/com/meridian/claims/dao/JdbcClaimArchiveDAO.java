package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcClaimArchiveDAO extends BaseDAO implements ClaimArchiveDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimArchiveDAO.class);

    private static final String COLS =
        "id, claim_number, member_id, provider_id, claim_type, original_claim_id, " +
        "date_of_service, submission_date, status, plan_id, coverage_order, " +
        "denial_reason_code, notes";

    // Only terminal-state claims may be archived — active claims must stay in the live table.
    private static final String TERMINAL_STATES =
        "('PAID','VOIDED','REPLACED','ABANDONED')";

    @Override
    public int archiveClaimsOlderThan(java.util.Date cutoff) {
        Date cut = new Date(cutoff.getTime());
        String copy = "INSERT INTO claims_archive (" + COLS + ") " +
            "SELECT " + COLS + " FROM claims " +
            "WHERE date_of_service < ? AND status IN " + TERMINAL_STATES +
            " AND id NOT IN (SELECT id FROM claims_archive)";
        String delete = "DELETE FROM claims " +
            "WHERE date_of_service < ? AND status IN " + TERMINAL_STATES;
        try {
            int copied = getJdbcTemplate().update(copy, cut);
            // Only delete what we just archived (same predicate).
            getJdbcTemplate().update(delete, cut);
            return copied;
        } catch (Exception e) {
            LOG.error("archiveClaimsOlderThan failed cutoff=" + cutoff, e);
            throw new DAOException("Could not archive old claims", e);
        }
    }

    @Override
    public List<Claim> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        String sql;
        Object[] params;
        if (trimmed.isEmpty()) {
            sql = "SELECT " + COLS + " FROM claims_archive ORDER BY date_of_service DESC LIMIT 200";
            params = new Object[]{};
        } else {
            sql = "SELECT " + COLS + " FROM claims_archive " +
                "WHERE claim_number ILIKE ? ORDER BY date_of_service DESC LIMIT 200";
            params = new Object[]{"%" + trimmed + "%"};
        }
        try {
            return getJdbcTemplate().query(sql, new ArchiveRowMapper(), params);
        } catch (Exception e) {
            LOG.error("search archive failed query=" + query, e);
            throw new DAOException("Could not search claims archive", e);
        }
    }

    @Override
    public Claim findById(int id) {
        String sql = "SELECT " + COLS + " FROM claims_archive WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ArchiveRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById archive failed id=" + id, e);
            throw new DAOException("Could not load archived claim id=" + id, e);
        }
    }

    private static final class ArchiveRowMapper implements RowMapper<Claim> {
        @Override
        public Claim mapRow(ResultSet rs, int rowNum) throws SQLException {
            Claim c = new Claim();
            c.setId(rs.getInt("id"));
            c.setClaimNumber(rs.getString("claim_number"));
            c.setMemberId(rs.getInt("member_id"));
            c.setProviderId(rs.getInt("provider_id"));
            c.setClaimType(ClaimType.valueOf(rs.getString("claim_type")));
            int orig = rs.getInt("original_claim_id");
            if (!rs.wasNull()) { c.setOriginalClaimId(orig); }
            c.setDateOfService(rs.getDate("date_of_service"));
            c.setSubmissionDate(rs.getDate("submission_date"));
            c.setStatus(ClaimStatus.valueOf(rs.getString("status")));
            c.setPlanId(rs.getInt("plan_id"));
            c.setCoverageOrder(rs.getString("coverage_order"));
            c.setDenialReasonCode(rs.getString("denial_reason_code"));
            c.setNotes(rs.getString("notes"));
            return c;
        }
    }
}
