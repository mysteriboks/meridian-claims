package com.meridian.claims.dao;

import com.meridian.claims.model.FeeScheduleRate;
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
public class JdbcFeeScheduleRateDAO extends BaseDAO implements FeeScheduleRateDAO {

    private static final Logger LOG = Logger.getLogger(JdbcFeeScheduleRateDAO.class);

    private static final String SELECT_COLS =
        "f.id, f.plan_id, p.plan_name, f.provider_id, pr.name AS provider_name, " +
        "f.procedure_code, f.allowed_amount, f.effective_date, f.termination_date, " +
        "f.created_at, f.updated_at";

    private static final String BASE_FROM =
        " FROM fee_schedule_rates f " +
        " JOIN plans p ON p.id = f.plan_id " +
        " LEFT JOIN providers pr ON pr.id = f.provider_id ";

    @Override
    public FeeScheduleRate findById(int id) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE f.id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new RateRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load fee schedule rate id=" + id, e);
        }
    }

    @Override
    public Page<FeeScheduleRate> findByPlanId(int planId, int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM fee_schedule_rates WHERE plan_id = ?";
        String dataSql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE f.plan_id = ? ORDER BY f.procedure_code, f.effective_date LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, planId);
            int offset = (pageNumber - 1) * pageSize;
            List<FeeScheduleRate> items = getJdbcTemplate().query(dataSql, new RateRowMapper(),
                planId, pageSize, offset);
            return new Page<FeeScheduleRate>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findByPlanId failed planId=" + planId, e);
            throw new DAOException("Could not list fee schedule rates for plan id=" + planId, e);
        }
    }

    @Override
    public BigDecimal resolveAllowedAmount(int planId, Integer providerId, String procedureCode, java.util.Date serviceDate) {
        Date dos = new Date(serviceDate.getTime());
        // provider-specific rate first
        if (providerId != null) {
            String sql = "SELECT allowed_amount FROM fee_schedule_rates " +
                "WHERE plan_id = ? AND provider_id = ? AND procedure_code = ? " +
                "AND effective_date <= ? AND (termination_date IS NULL OR termination_date >= ?) " +
                "ORDER BY effective_date DESC LIMIT 1";
            try {
                List<BigDecimal> rows = getJdbcTemplate().queryForList(sql, BigDecimal.class,
                    planId, providerId, procedureCode, dos, dos);
                if (!rows.isEmpty()) {
                    return rows.get(0);
                }
            } catch (Exception e) {
                LOG.error("resolveAllowedAmount (provider-specific) failed", e);
                throw new DAOException("Could not resolve fee schedule rate", e);
            }
        }
        // fall back to plan-wide rate (provider_id IS NULL)
        String sql = "SELECT allowed_amount FROM fee_schedule_rates " +
            "WHERE plan_id = ? AND provider_id IS NULL AND procedure_code = ? " +
            "AND effective_date <= ? AND (termination_date IS NULL OR termination_date >= ?) " +
            "ORDER BY effective_date DESC LIMIT 1";
        try {
            List<BigDecimal> rows = getJdbcTemplate().queryForList(sql, BigDecimal.class,
                planId, procedureCode, dos, dos);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception e) {
            LOG.error("resolveAllowedAmount (plan-wide) failed", e);
            throw new DAOException("Could not resolve fee schedule rate", e);
        }
        return null; // no rate on file — caller must flag NO_RATE
    }

    @Override
    public void insert(FeeScheduleRate rate) {
        String sql = "INSERT INTO fee_schedule_rates " +
            "(plan_id, provider_id, procedure_code, allowed_amount, effective_date, termination_date) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final FeeScheduleRate r = rate;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, r.getPlanId());
                if (r.getProviderId() == null) {
                    ps.setNull(2, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(2, r.getProviderId());
                }
                ps.setString(3, r.getProcedureCode());
                ps.setBigDecimal(4, r.getAllowedAmount());
                ps.setDate(5, new Date(r.getEffectiveDate().getTime()));
                ps.setDate(6, r.getTerminationDate() == null ? null : new Date(r.getTerminationDate().getTime()));
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                rate.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed", e);
            throw new DAOException("Could not insert fee schedule rate", e);
        }
    }

    @Override
    public void update(FeeScheduleRate rate) {
        String sql = "UPDATE fee_schedule_rates SET allowed_amount = ?, " +
            "effective_date = ?, termination_date = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                rate.getAllowedAmount(),
                new Date(rate.getEffectiveDate().getTime()),
                rate.getTerminationDate() == null ? null : new Date(rate.getTerminationDate().getTime()),
                rate.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + rate.getId(), e);
            throw new DAOException("Could not update fee schedule rate id=" + rate.getId(), e);
        }
    }

    @Override
    public void expire(int id, java.util.Date terminationDate) {
        try {
            getJdbcTemplate().update(
                "UPDATE fee_schedule_rates SET termination_date = ? WHERE id = ?",
                new Date(terminationDate.getTime()), id);
        } catch (Exception e) {
            LOG.error("expire failed id=" + id, e);
            throw new DAOException("Could not expire fee schedule rate id=" + id, e);
        }
    }

    private static final class RateRowMapper implements RowMapper<FeeScheduleRate> {
        @Override
        public FeeScheduleRate mapRow(ResultSet rs, int rowNum) throws SQLException {
            FeeScheduleRate r = new FeeScheduleRate();
            r.setId(rs.getInt("id"));
            r.setPlanId(rs.getInt("plan_id"));
            r.setPlanName(rs.getString("plan_name"));
            int pid = rs.getInt("provider_id");
            if (!rs.wasNull()) r.setProviderId(pid);
            r.setProviderName(rs.getString("provider_name"));
            r.setProcedureCode(rs.getString("procedure_code"));
            r.setAllowedAmount(rs.getBigDecimal("allowed_amount"));
            Date eff = rs.getDate("effective_date");
            if (eff != null) r.setEffectiveDate(new java.util.Date(eff.getTime()));
            Date term = rs.getDate("termination_date");
            if (term != null) r.setTerminationDate(new java.util.Date(term.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) r.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) r.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return r;
        }
    }
}
