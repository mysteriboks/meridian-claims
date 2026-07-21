package com.meridian.claims.dao;

import com.meridian.claims.model.DeductibleAccumulator;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcDeductibleAccumulatorDAO extends BaseDAO implements DeductibleAccumulatorDAO {

    private static final Logger LOG = Logger.getLogger(JdbcDeductibleAccumulatorDAO.class);

    @Override
    public DeductibleAccumulator findOrCreateForUpdate(int memberId, int planId, java.util.Date benefitYearStart) {
        Date bys = new Date(benefitYearStart.getTime());
        // Insert a zero row if none exists (idempotent: unique constraint prevents duplicates).
        String insertIfAbsent = "INSERT INTO deductible_accumulators " +
            "(member_id, plan_id, benefit_year_start, deductible_accumulated, oop_accumulated) " +
            "SELECT ?, ?, ?, 0.00, 0.00 " +
            "WHERE NOT EXISTS (" +
            "  SELECT 1 FROM deductible_accumulators WHERE member_id = ? AND plan_id = ? AND benefit_year_start = ?" +
            ")";
        try {
            getJdbcTemplate().update(insertIfAbsent, memberId, planId, bys, memberId, planId, bys);
        } catch (Exception e) {
            // Row may have been inserted by a concurrent thread — ignore and proceed to SELECT FOR UPDATE.
            LOG.debug("insertIfAbsent deductible_accumulators — row may already exist: " + e.getMessage());
        }
        String selectForUpdate = "SELECT id, member_id, plan_id, benefit_year_start, " +
            "deductible_accumulated, oop_accumulated, updated_at " +
            "FROM deductible_accumulators WHERE member_id = ? AND plan_id = ? AND benefit_year_start = ? " +
            "FOR UPDATE";
        try {
            List<DeductibleAccumulator> rows = getJdbcTemplate().query(
                selectForUpdate, new AccumulatorRowMapper(), memberId, planId, bys);
            if (rows.isEmpty()) {
                throw new DAOException("deductible_accumulators row missing after insert for memberId=" + memberId);
            }
            return rows.get(0);
        } catch (DAOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("findOrCreateForUpdate failed memberId=" + memberId, e);
            throw new DAOException("Could not lock deductible accumulator", e);
        }
    }

    @Override
    public void update(DeductibleAccumulator acc) {
        String sql = "UPDATE deductible_accumulators SET " +
            "deductible_accumulated = ?, oop_accumulated = ? " +
            "WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                acc.getDeductibleAccumulated(),
                acc.getOopAccumulated(),
                acc.getId());
        } catch (Exception e) {
            LOG.error("update accumulator failed id=" + acc.getId(), e);
            throw new DAOException("Could not update deductible accumulator id=" + acc.getId(), e);
        }
    }

    @Override
    public List<DeductibleAccumulator> findByPlanId(int planId) {
        String sql = "SELECT id, member_id, plan_id, benefit_year_start, " +
            "deductible_accumulated, oop_accumulated, updated_at " +
            "FROM deductible_accumulators WHERE plan_id = ? ORDER BY benefit_year_start DESC";
        try {
            return getJdbcTemplate().query(sql, new AccumulatorRowMapper(), planId);
        } catch (Exception e) {
            LOG.error("findByPlanId failed planId=" + planId, e);
            throw new DAOException("Could not load accumulators for plan " + planId, e);
        }
    }

    private static final class AccumulatorRowMapper implements RowMapper<DeductibleAccumulator> {
        @Override
        public DeductibleAccumulator mapRow(ResultSet rs, int rowNum) throws SQLException {
            DeductibleAccumulator a = new DeductibleAccumulator();
            a.setId(rs.getInt("id"));
            a.setMemberId(rs.getInt("member_id"));
            a.setPlanId(rs.getInt("plan_id"));
            a.setBenefitYearStart(rs.getDate("benefit_year_start"));
            a.setDeductibleAccumulated(rs.getBigDecimal("deductible_accumulated"));
            a.setOopAccumulated(rs.getBigDecimal("oop_accumulated"));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { a.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return a;
        }
    }
}
