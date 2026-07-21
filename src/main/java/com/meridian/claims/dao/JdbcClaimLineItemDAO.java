package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimLineItem;
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
public class JdbcClaimLineItemDAO extends BaseDAO implements ClaimLineItemDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimLineItemDAO.class);

    private static final String SELECT_COLS =
        "id, claim_id, procedure_code, description, billed_amount, allowed_amount, " +
        "plan_paid_amount, member_responsibility, deductible_applied, copay_applied, " +
        "rate_source, adjustment_reason_code, created_at";

    @Override
    public void insertBatch(List<ClaimLineItem> items) {
        String sql = "INSERT INTO claim_line_items " +
            "(claim_id, procedure_code, description, billed_amount, deductible_applied, copay_applied) " +
            "VALUES (?,?,?,?,0,0)";
        try {
            for (ClaimLineItem item : items) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                final ClaimLineItem li = item;
                getJdbcTemplate().update(con -> {
                    PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                    ps.setInt(1, li.getClaimId());
                    ps.setString(2, li.getProcedureCode());
                    ps.setString(3, li.getDescription());
                    ps.setBigDecimal(4, li.getBilledAmount());
                    return ps;
                }, keyHolder);
                item.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insertBatch failed", e);
            throw new DAOException("Could not insert claim line items", e);
        }
    }

    @Override
    public List<ClaimLineItem> findByClaimId(int claimId) {
        String sql = "SELECT " + SELECT_COLS + " FROM claim_line_items WHERE claim_id = ? ORDER BY id";
        try {
            return getJdbcTemplate().query(sql, new LineItemRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load line items for claim id=" + claimId, e);
        }
    }

    @Override
    public void updateAmounts(ClaimLineItem item) {
        String sql = "UPDATE claim_line_items SET " +
            "allowed_amount = ?, plan_paid_amount = ?, member_responsibility = ?, " +
            "deductible_applied = ?, copay_applied = ?, rate_source = ?, adjustment_reason_code = ? " +
            "WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                item.getAllowedAmount(),
                item.getPlanPaidAmount(),
                item.getMemberResponsibility(),
                item.getDeductibleApplied(),
                item.getCopayApplied(),
                item.getRateSource(),
                item.getAdjustmentReasonCode(),
                item.getId());
        } catch (Exception e) {
            LOG.error("updateAmounts failed id=" + item.getId(), e);
            throw new DAOException("Could not update line item amounts id=" + item.getId(), e);
        }
    }

    private static final class LineItemRowMapper implements RowMapper<ClaimLineItem> {
        @Override
        public ClaimLineItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimLineItem li = new ClaimLineItem();
            li.setId(rs.getInt("id"));
            li.setClaimId(rs.getInt("claim_id"));
            li.setProcedureCode(rs.getString("procedure_code"));
            li.setDescription(rs.getString("description"));
            li.setBilledAmount(rs.getBigDecimal("billed_amount"));
            li.setAllowedAmount(rs.getBigDecimal("allowed_amount"));
            li.setPlanPaidAmount(rs.getBigDecimal("plan_paid_amount"));
            li.setMemberResponsibility(rs.getBigDecimal("member_responsibility"));
            li.setDeductibleApplied(rs.getBigDecimal("deductible_applied"));
            li.setCopayApplied(rs.getBigDecimal("copay_applied"));
            li.setRateSource(rs.getString("rate_source"));
            li.setAdjustmentReasonCode(rs.getString("adjustment_reason_code"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { li.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            return li;
        }
    }
}
