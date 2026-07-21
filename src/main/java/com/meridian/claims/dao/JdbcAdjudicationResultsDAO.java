package com.meridian.claims.dao;

import com.meridian.claims.model.AdjudicationResult;
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
public class JdbcAdjudicationResultsDAO extends BaseDAO implements AdjudicationResultsDAO {

    private static final Logger LOG = Logger.getLogger(JdbcAdjudicationResultsDAO.class);

    @Override
    public void insertBatch(List<AdjudicationResult> results) {
        String sql = "INSERT INTO adjudication_results " +
            "(claim_id, run_id, step_number, rule_name, rule_type, passed, reason) " +
            "VALUES (?,?,?,?,?,?,?)";
        try {
            for (AdjudicationResult r : results) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                final AdjudicationResult result = r;
                getJdbcTemplate().update(con -> {
                    PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                    ps.setInt(1, result.getClaimId());
                    ps.setInt(2, result.getRunId());
                    ps.setInt(3, result.getStepNumber());
                    ps.setString(4, result.getRuleName());
                    ps.setString(5, result.getRuleType());
                    ps.setBoolean(6, result.isPassed());
                    ps.setString(7, result.getReason());
                    return ps;
                }, keyHolder);
                r.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insertBatch adjudication results failed", e);
            throw new DAOException("Could not insert adjudication results", e);
        }
    }

    @Override
    public List<AdjudicationResult> findByClaimId(int claimId) {
        String sql = "SELECT id, claim_id, run_id, step_number, rule_name, rule_type, passed, reason, evaluated_at " +
            "FROM adjudication_results WHERE claim_id = ? ORDER BY run_id, step_number";
        try {
            return getJdbcTemplate().query(sql, new ResultRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load adjudication results for claim id=" + claimId, e);
        }
    }

    @Override
    public int maxRunId(int claimId) {
        String sql = "SELECT COALESCE(MAX(run_id), 0) FROM adjudication_results WHERE claim_id = ?";
        try {
            Integer max = getJdbcTemplate().queryForObject(sql, Integer.class, claimId);
            return max == null ? 0 : max.intValue();
        } catch (Exception e) {
            LOG.error("maxRunId failed claimId=" + claimId, e);
            throw new DAOException("Could not determine max run_id for claim id=" + claimId, e);
        }
    }

    @Override
    public void deleteByClaimId(int claimId) {
        try {
            getJdbcTemplate().update("DELETE FROM adjudication_results WHERE claim_id = ?", claimId);
        } catch (Exception e) {
            LOG.error("deleteByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not delete adjudication results for claim id=" + claimId, e);
        }
    }

    private static final class ResultRowMapper implements RowMapper<AdjudicationResult> {
        @Override
        public AdjudicationResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            AdjudicationResult r = new AdjudicationResult();
            r.setId(rs.getInt("id"));
            r.setClaimId(rs.getInt("claim_id"));
            r.setRunId(rs.getInt("run_id"));
            r.setStepNumber(rs.getInt("step_number"));
            r.setRuleName(rs.getString("rule_name"));
            r.setRuleType(rs.getString("rule_type"));
            r.setPassed(rs.getBoolean("passed"));
            r.setReason(rs.getString("reason"));
            Timestamp evaluatedAt = rs.getTimestamp("evaluated_at");
            if (evaluatedAt != null) { r.setEvaluatedAt(new java.util.Date(evaluatedAt.getTime())); }
            return r;
        }
    }
}
