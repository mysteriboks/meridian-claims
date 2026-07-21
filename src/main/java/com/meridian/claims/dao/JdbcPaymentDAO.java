package com.meridian.claims.dao;

import com.meridian.claims.model.Payment;
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
public class JdbcPaymentDAO extends BaseDAO implements PaymentDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPaymentDAO.class);

    private static final String COLS =
        "id, claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility, " +
        "partial_payment_flag, amount_paid, remaining_balance, reference_number, " +
        "status, payment_date, created_at, updated_at";

    @Override
    public void insert(Payment p) {
        String sql = "INSERT INTO payments " +
            "(claim_id, billed_total, allowed_total, plan_paid_total, member_responsibility, status) " +
            "VALUES (?,?,?,?,?,'PENDING')";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final Payment pay = p;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, pay.getClaimId());
                ps.setBigDecimal(2, pay.getBilledTotal());
                ps.setBigDecimal(3, pay.getAllowedTotal());
                ps.setBigDecimal(4, pay.getPlanPaidTotal());
                ps.setBigDecimal(5, pay.getMemberResponsibility());
                return ps;
            }, key);
            p.setId(key.getKey().intValue());
            p.setStatus("PENDING");
        } catch (Exception e) {
            LOG.error("insert payment failed claimId=" + p.getClaimId(), e);
            throw new DAOException("Could not insert payment", e);
        }
    }

    @Override
    public Payment findById(int id) {
        String sql = "SELECT " + COLS + " FROM payments WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new PaymentRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load payment id=" + id, e);
        }
    }

    @Override
    public Payment findByClaimId(int claimId) {
        String sql = "SELECT " + COLS + " FROM payments WHERE claim_id = ? ORDER BY created_at DESC LIMIT 1";
        try {
            List<Payment> rows = getJdbcTemplate().query(sql, new PaymentRowMapper(), claimId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load payment for claim " + claimId, e);
        }
    }

    @Override
    public List<Payment> findByStatus(String status) {
        String sql = "SELECT " + COLS + " FROM payments WHERE status = ? ORDER BY created_at DESC LIMIT 500";
        try {
            return getJdbcTemplate().query(sql, new PaymentRowMapper(), status);
        } catch (Exception e) {
            LOG.error("findByStatus failed status=" + status, e);
            throw new DAOException("Could not load payments by status", e);
        }
    }

    @Override
    public void updatePaid(int id, String referenceNumber, java.util.Date paymentDate,
                           BigDecimal amountPaid, BigDecimal remainingBalance, boolean partial) {
        String status = (remainingBalance != null && remainingBalance.signum() > 0) ? "PENDING" : "PAID";
        String sql = "UPDATE payments SET status = ?, reference_number = ?, payment_date = ?, " +
            "amount_paid = ?, remaining_balance = ?, partial_payment_flag = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, status, referenceNumber,
                paymentDate != null ? new Date(paymentDate.getTime()) : null,
                amountPaid, remainingBalance, partial, id);
        } catch (Exception e) {
            LOG.error("updatePaid failed id=" + id, e);
            throw new DAOException("Could not update payment id=" + id, e);
        }
    }

    @Override
    public void assignToBatch(int paymentId, int batchId) {
        String sql = "UPDATE payments SET batch_id = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, batchId, paymentId);
        } catch (Exception e) {
            LOG.error("assignToBatch failed paymentId=" + paymentId, e);
            throw new DAOException("Could not assign payment to batch", e);
        }
    }

    @Override
    public List<Payment> findByBatchId(int batchId) {
        String sql = "SELECT " + COLS + " FROM payments WHERE batch_id = ? ORDER BY created_at ASC";
        try {
            return getJdbcTemplate().query(sql, new PaymentRowMapper(), batchId);
        } catch (Exception e) {
            LOG.error("findByBatchId failed batchId=" + batchId, e);
            throw new DAOException("Could not load payments for batch " + batchId, e);
        }
    }

    private static final class PaymentRowMapper implements RowMapper<Payment> {
        @Override
        public Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
            Payment p = new Payment();
            p.setId(rs.getInt("id"));
            p.setClaimId(rs.getInt("claim_id"));
            p.setBilledTotal(rs.getBigDecimal("billed_total"));
            p.setAllowedTotal(rs.getBigDecimal("allowed_total"));
            p.setPlanPaidTotal(rs.getBigDecimal("plan_paid_total"));
            p.setMemberResponsibility(rs.getBigDecimal("member_responsibility"));
            p.setPartialPaymentFlag(rs.getBoolean("partial_payment_flag"));
            p.setAmountPaid(rs.getBigDecimal("amount_paid"));
            p.setRemainingBalance(rs.getBigDecimal("remaining_balance"));
            p.setReferenceNumber(rs.getString("reference_number"));
            p.setStatus(rs.getString("status"));
            java.sql.Date pd = rs.getDate("payment_date");
            if (pd != null) { p.setPaymentDate(new java.util.Date(pd.getTime())); }
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { p.setCreatedAt(new java.util.Date(ca.getTime())); }
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) { p.setUpdatedAt(new java.util.Date(ua.getTime())); }
            return p;
        }
    }
}
