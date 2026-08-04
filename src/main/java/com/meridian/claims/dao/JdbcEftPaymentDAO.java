package com.meridian.claims.dao;

import com.meridian.claims.model.EftPayment;
import org.apache.log4j.Logger;
import org.springframework.dao.EmptyResultDataAccessException;
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
public class JdbcEftPaymentDAO extends BaseDAO implements EftPaymentDAO {

    private static final Logger LOG = Logger.getLogger(JdbcEftPaymentDAO.class);

    private static final String SELECT_COLS =
        "id, payment_batch_id, remittance_batch_id, trn_reassociation_number, amount, " +
        "settlement_status, ach_file_reference, entry_count, skipped_provider_count, " +
        "created_at, updated_at";

    @Override
    public int insert(EftPayment eftPayment) {
        String sql = "INSERT INTO eft_payments " +
            "(payment_batch_id, remittance_batch_id, trn_reassociation_number, amount, " +
            "settlement_status, ach_file_reference, entry_count, skipped_provider_count) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, eftPayment.getPaymentBatchId());
                ps.setInt(2, eftPayment.getRemittanceBatchId());
                ps.setString(3, eftPayment.getTrnReassociationNumber());
                ps.setBigDecimal(4, eftPayment.getAmount());
                ps.setString(5, eftPayment.getSettlementStatus());
                ps.setString(6, eftPayment.getAchFileReference());
                ps.setInt(7, eftPayment.getEntryCount());
                ps.setInt(8, eftPayment.getSkippedProviderCount());
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            eftPayment.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insert eft payment failed paymentBatchId=" + eftPayment.getPaymentBatchId(), e);
            throw new DAOException("Could not insert eft payment", e);
        }
    }

    @Override
    public EftPayment findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM eft_payments WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new EftPaymentRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load eft payment id=" + id, e);
        }
    }

    @Override
    public EftPayment findByPaymentBatchId(int paymentBatchId) {
        String sql = "SELECT " + SELECT_COLS + " FROM eft_payments WHERE payment_batch_id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new EftPaymentRowMapper(), paymentBatchId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByPaymentBatchId failed paymentBatchId=" + paymentBatchId, e);
            throw new DAOException("Could not load eft payment for payment batch id=" + paymentBatchId, e);
        }
    }

    @Override
    public EftPayment findByRemittanceBatchId(int remittanceBatchId) {
        String sql = "SELECT " + SELECT_COLS + " FROM eft_payments WHERE remittance_batch_id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new EftPaymentRowMapper(), remittanceBatchId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByRemittanceBatchId failed remittanceBatchId=" + remittanceBatchId, e);
            throw new DAOException("Could not load eft payment for remittance batch id=" + remittanceBatchId, e);
        }
    }

    @Override
    public void updateSettlementStatus(int id, String settlementStatus) {
        try {
            getJdbcTemplate().update("UPDATE eft_payments SET settlement_status = ? WHERE id = ?",
                settlementStatus, id);
        } catch (Exception e) {
            LOG.error("updateSettlementStatus failed id=" + id, e);
            throw new DAOException("Could not update settlement status for eft payment id=" + id, e);
        }
    }

    @Override
    public List<EftPayment> findRecent(int limit) {
        String sql = "SELECT " + SELECT_COLS + " FROM eft_payments ORDER BY created_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new EftPaymentRowMapper(), limit);
        } catch (Exception e) {
            LOG.error("findRecent eft payments failed", e);
            throw new DAOException("Could not load recent eft payments", e);
        }
    }

    private static final class EftPaymentRowMapper implements RowMapper<EftPayment> {
        @Override
        public EftPayment mapRow(ResultSet rs, int rowNum) throws SQLException {
            EftPayment e = new EftPayment();
            e.setId(rs.getInt("id"));
            e.setPaymentBatchId(rs.getInt("payment_batch_id"));
            e.setRemittanceBatchId(rs.getInt("remittance_batch_id"));
            e.setTrnReassociationNumber(rs.getString("trn_reassociation_number"));
            e.setAmount(rs.getBigDecimal("amount"));
            e.setSettlementStatus(rs.getString("settlement_status"));
            e.setAchFileReference(rs.getString("ach_file_reference"));
            e.setEntryCount(rs.getInt("entry_count"));
            e.setSkippedProviderCount(rs.getInt("skipped_provider_count"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) e.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) e.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return e;
        }
    }
}
