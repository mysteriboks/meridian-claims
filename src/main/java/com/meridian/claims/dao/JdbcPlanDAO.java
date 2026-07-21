package com.meridian.claims.dao;

import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.util.Page;
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
public class JdbcPlanDAO extends BaseDAO implements PlanDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPlanDAO.class);

    private static final String SELECT_COLS =
        "id, plan_name, plan_type, deductible_amount, oop_max, copay_amount, " +
        "coverage_pct_in_network, coverage_pct_out_network, benefit_year_start, " +
        "timely_filing_days, deleted_at, created_at, updated_at";

    @Override
    public Plan findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM plans WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new PlanRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load plan id=" + id, e);
        }
    }

    @Override
    public Page<Plan> findAll(int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM plans WHERE deleted_at IS NULL";
        String dataSql = "SELECT " + SELECT_COLS + " FROM plans WHERE deleted_at IS NULL " +
            "ORDER BY plan_name LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class);
            int offset = (pageNumber - 1) * pageSize;
            List<Plan> items = getJdbcTemplate().query(dataSql, new PlanRowMapper(), pageSize, offset);
            return new Page<Plan>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list plans", e);
        }
    }

    @Override
    public List<Plan> findAllActive() {
        String sql = "SELECT " + SELECT_COLS + " FROM plans WHERE deleted_at IS NULL ORDER BY plan_name";
        try {
            return getJdbcTemplate().query(sql, new PlanRowMapper());
        } catch (Exception e) {
            LOG.error("findAllActive failed", e);
            throw new DAOException("Could not list active plans", e);
        }
    }

    @Override
    public void insert(Plan plan) {
        String sql = "INSERT INTO plans " +
            "(plan_name, plan_type, deductible_amount, oop_max, copay_amount, " +
            " coverage_pct_in_network, coverage_pct_out_network, benefit_year_start, timely_filing_days) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final Plan p = plan;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, p.getPlanName());
                ps.setString(2, p.getPlanType().name());
                ps.setBigDecimal(3, p.getDeductibleAmount());
                ps.setBigDecimal(4, p.getOopMax());
                ps.setBigDecimal(5, p.getCopayAmount());
                ps.setBigDecimal(6, p.getCoveragePctInNetwork());
                ps.setBigDecimal(7, p.getCoveragePctOutNetwork());
                ps.setDate(8, p.getBenefitYearStart() == null ? null : new Date(p.getBenefitYearStart().getTime()));
                ps.setInt(9, p.getTimelyFilingDays());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                plan.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed plan=" + plan.getPlanName(), e);
            throw new DAOException("Could not insert plan", e);
        }
    }

    @Override
    public void update(Plan plan) {
        String sql = "UPDATE plans SET plan_name = ?, plan_type = ?, deductible_amount = ?, " +
            "oop_max = ?, copay_amount = ?, coverage_pct_in_network = ?, " +
            "coverage_pct_out_network = ?, benefit_year_start = ?, timely_filing_days = ? " +
            "WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                plan.getPlanName(),
                plan.getPlanType().name(),
                plan.getDeductibleAmount(),
                plan.getOopMax(),
                plan.getCopayAmount(),
                plan.getCoveragePctInNetwork(),
                plan.getCoveragePctOutNetwork(),
                plan.getBenefitYearStart() == null ? null : new Date(plan.getBenefitYearStart().getTime()),
                plan.getTimelyFilingDays(),
                plan.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + plan.getId(), e);
            throw new DAOException("Could not update plan id=" + plan.getId(), e);
        }
    }

    @Override
    public void softDelete(int id) {
        try {
            getJdbcTemplate().update("UPDATE plans SET deleted_at = NOW() WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("softDelete failed id=" + id, e);
            throw new DAOException("Could not delete plan id=" + id, e);
        }
    }

    private static final class PlanRowMapper implements RowMapper<Plan> {
        @Override
        public Plan mapRow(ResultSet rs, int rowNum) throws SQLException {
            Plan p = new Plan();
            p.setId(rs.getInt("id"));
            p.setPlanName(rs.getString("plan_name"));
            p.setPlanType(PlanType.valueOf(rs.getString("plan_type")));
            p.setDeductibleAmount(rs.getBigDecimal("deductible_amount"));
            p.setOopMax(rs.getBigDecimal("oop_max"));
            p.setCopayAmount(rs.getBigDecimal("copay_amount"));
            BigDecimal inNet = rs.getBigDecimal("coverage_pct_in_network");
            p.setCoveragePctInNetwork(inNet);
            BigDecimal outNet = rs.getBigDecimal("coverage_pct_out_network");
            p.setCoveragePctOutNetwork(outNet);
            Date bys = rs.getDate("benefit_year_start");
            if (bys != null) p.setBenefitYearStart(new java.util.Date(bys.getTime()));
            p.setTimelyFilingDays(rs.getInt("timely_filing_days"));
            Timestamp deletedAt = rs.getTimestamp("deleted_at");
            if (deletedAt != null) p.setDeletedAt(new java.util.Date(deletedAt.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) p.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) p.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return p;
        }
    }
}
