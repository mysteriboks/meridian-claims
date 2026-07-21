package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimAccumulatorContribution;
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

@Repository
public class JdbcClaimAccumulatorContributionDAO extends BaseDAO implements ClaimAccumulatorContributionDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimAccumulatorContributionDAO.class);

    @Override
    public void insert(ClaimAccumulatorContribution contribution) {
        String sql = "INSERT INTO claim_accumulator_contributions " +
            "(claim_id, benefit_year_start, deductible_contributed, oop_contributed, reversed) " +
            "VALUES (?,?,?,?,FALSE)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final ClaimAccumulatorContribution c = contribution;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, c.getClaimId());
                ps.setDate(2, new Date(c.getBenefitYearStart().getTime()));
                ps.setBigDecimal(3, c.getDeductibleContributed());
                ps.setBigDecimal(4, c.getOopContributed());
                return ps;
            }, keyHolder);
            contribution.setId(keyHolder.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insert contribution failed claimId=" + contribution.getClaimId(), e);
            throw new DAOException("Could not insert accumulator contribution", e);
        }
    }

    @Override
    public ClaimAccumulatorContribution findByClaimId(int claimId) {
        // Re-adjudication retains history, so a claim can have several contribution rows
        // (one per run); the latest row is the current/active contribution.
        String sql = "SELECT id, claim_id, benefit_year_start, deductible_contributed, " +
            "oop_contributed, reversed, applied_at " +
            "FROM claim_accumulator_contributions WHERE claim_id = ? ORDER BY id DESC LIMIT 1";
        try {
            return getJdbcTemplate().queryForObject(sql, new ContributionRowMapper(), claimId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load accumulator contribution for claim id=" + claimId, e);
        }
    }

    @Override
    public void markReversed(int claimId) {
        // Idempotent: only flip rows that are still un-reversed. If nothing was un-reversed,
        // the caller is double-reversing — signal that rather than silently succeeding.
        String sql = "UPDATE claim_accumulator_contributions SET reversed = TRUE " +
            "WHERE claim_id = ? AND reversed = FALSE";
        try {
            int rows = getJdbcTemplate().update(sql, claimId);
            if (rows == 0) {
                throw new DAOException("No un-reversed accumulator contribution to reverse for claim id=" + claimId);
            }
        } catch (DAOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("markReversed failed claimId=" + claimId, e);
            throw new DAOException("Could not mark contribution reversed for claim id=" + claimId, e);
        }
    }

    private static final class ContributionRowMapper implements RowMapper<ClaimAccumulatorContribution> {
        @Override
        public ClaimAccumulatorContribution mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimAccumulatorContribution c = new ClaimAccumulatorContribution();
            c.setId(rs.getInt("id"));
            c.setClaimId(rs.getInt("claim_id"));
            c.setBenefitYearStart(rs.getDate("benefit_year_start"));
            c.setDeductibleContributed(rs.getBigDecimal("deductible_contributed"));
            c.setOopContributed(rs.getBigDecimal("oop_contributed"));
            c.setReversed(rs.getBoolean("reversed"));
            Timestamp appliedAt = rs.getTimestamp("applied_at");
            if (appliedAt != null) { c.setAppliedAt(new java.util.Date(appliedAt.getTime())); }
            return c;
        }
    }
}
