package com.meridian.claims.dao;

import com.meridian.claims.model.PlanCoverageRule;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class JdbcPlanCoverageRuleDAO extends BaseDAO implements PlanCoverageRuleDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPlanCoverageRuleDAO.class);

    private static final String SELECT_COLS =
        "id, plan_id, service_type, coverage_pct, requires_referral, requires_prior_auth";

    @Override
    public List<PlanCoverageRule> findByPlanId(int planId) {
        String sql = "SELECT " + SELECT_COLS + " FROM plan_coverage_rules WHERE plan_id = ? ORDER BY service_type";
        try {
            return getJdbcTemplate().query(sql, new RuleRowMapper(), planId);
        } catch (Exception e) {
            LOG.error("findByPlanId failed planId=" + planId, e);
            throw new DAOException("Could not load coverage rules for plan id=" + planId, e);
        }
    }

    @Override
    public PlanCoverageRule findByPlanAndServiceType(int planId, String serviceType) {
        String sql = "SELECT " + SELECT_COLS + " FROM plan_coverage_rules WHERE plan_id = ? AND service_type = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new RuleRowMapper(), planId, serviceType);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByPlanAndServiceType failed planId=" + planId + " serviceType=" + serviceType, e);
            throw new DAOException("Could not load coverage rule", e);
        }
    }

    @Override
    public void insert(PlanCoverageRule rule) {
        String sql = "INSERT INTO plan_coverage_rules " +
            "(plan_id, service_type, coverage_pct, requires_referral, requires_prior_auth) " +
            "VALUES (?, ?, ?, ?, ?)";
        try {
            getJdbcTemplate().update(sql,
                rule.getPlanId(),
                rule.getServiceType(),
                rule.getCoveragePct(),
                rule.isRequiresReferral(),
                rule.isRequiresPriorAuth());
        } catch (Exception e) {
            LOG.error("insert failed planId=" + rule.getPlanId(), e);
            throw new DAOException("Could not insert coverage rule", e);
        }
    }

    @Override
    public void update(PlanCoverageRule rule) {
        String sql = "UPDATE plan_coverage_rules SET coverage_pct = ?, " +
            "requires_referral = ?, requires_prior_auth = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                rule.getCoveragePct(),
                rule.isRequiresReferral(),
                rule.isRequiresPriorAuth(),
                rule.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + rule.getId(), e);
            throw new DAOException("Could not update coverage rule id=" + rule.getId(), e);
        }
    }

    @Override
    public void delete(int id) {
        try {
            getJdbcTemplate().update("DELETE FROM plan_coverage_rules WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("delete failed id=" + id, e);
            throw new DAOException("Could not delete coverage rule id=" + id, e);
        }
    }

    @Override
    public void deleteByPlanId(int planId) {
        try {
            getJdbcTemplate().update("DELETE FROM plan_coverage_rules WHERE plan_id = ?", planId);
        } catch (Exception e) {
            LOG.error("deleteByPlanId failed planId=" + planId, e);
            throw new DAOException("Could not delete coverage rules for plan id=" + planId, e);
        }
    }

    private static final class RuleRowMapper implements RowMapper<PlanCoverageRule> {
        @Override
        public PlanCoverageRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            PlanCoverageRule r = new PlanCoverageRule();
            r.setId(rs.getInt("id"));
            r.setPlanId(rs.getInt("plan_id"));
            r.setServiceType(rs.getString("service_type"));
            r.setCoveragePct(rs.getBigDecimal("coverage_pct"));
            r.setRequiresReferral(rs.getBoolean("requires_referral"));
            r.setRequiresPriorAuth(rs.getBoolean("requires_prior_auth"));
            return r;
        }
    }
}
