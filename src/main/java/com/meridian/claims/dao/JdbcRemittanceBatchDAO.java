package com.meridian.claims.dao;

import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
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
import java.util.List;

@Repository
public class JdbcRemittanceBatchDAO extends BaseDAO implements RemittanceBatchDAO {

    private static final Logger LOG = Logger.getLogger(JdbcRemittanceBatchDAO.class);

    @Override
    public void insertBatch(RemittanceBatch batch) {
        String sql = "INSERT INTO remittance_batches (payment_date, total_paid, status) VALUES (?,?,'GENERATED')";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final RemittanceBatch b = batch;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setDate(1, new Date(b.getPaymentDate().getTime()));
                ps.setBigDecimal(2, b.getTotalPaid());
                return ps;
            }, key);
            batch.setId(key.getKey().intValue());
            batch.setStatus("GENERATED");
        } catch (Exception e) {
            LOG.error("insertBatch failed", e);
            throw new DAOException("Could not insert remittance batch", e);
        }
    }

    @Override
    public void insertItem(RemittanceBatchItem item) {
        String sql = "INSERT INTO remittance_batch_items " +
            "(batch_id, claim_id, provider_id, billed, allowed, plan_paid, adjustment_reason_code, procedure_code) " +
            "VALUES (?,?,?,?,?,?,?,?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final RemittanceBatchItem it = item;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, it.getBatchId());
                ps.setInt(2, it.getClaimId());
                ps.setInt(3, it.getProviderId());
                ps.setBigDecimal(4, it.getBilled());
                ps.setBigDecimal(5, it.getAllowed());
                ps.setBigDecimal(6, it.getPlanPaid());
                ps.setString(7, it.getAdjustmentReasonCode());
                ps.setString(8, it.getProcedureCode());
                return ps;
            }, key);
            item.setId(key.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insertItem failed batchId=" + item.getBatchId(), e);
            throw new DAOException("Could not insert remittance batch item", e);
        }
    }

    @Override
    public void updateTotalPaid(int batchId, java.math.BigDecimal totalPaid) {
        String sql = "UPDATE remittance_batches SET total_paid = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, totalPaid, batchId);
        } catch (Exception e) {
            LOG.error("updateTotalPaid failed batchId=" + batchId, e);
            throw new DAOException("Could not update remittance batch total id=" + batchId, e);
        }
    }

    @Override
    public RemittanceBatch findById(int id) {
        String sql = "SELECT id, payment_date, total_paid, status, generated_at, created_at FROM remittance_batches WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new BatchRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load remittance batch id=" + id, e);
        }
    }

    @Override
    public List<RemittanceBatch> findAll() {
        String sql = "SELECT id, payment_date, total_paid, status, generated_at, created_at FROM remittance_batches ORDER BY id DESC LIMIT 500";
        try {
            return getJdbcTemplate().query(sql, new BatchRowMapper());
        } catch (Exception e) {
            LOG.error("findAll batches failed", e);
            throw new DAOException("Could not load remittance batches", e);
        }
    }

    @Override
    public List<RemittanceBatchItem> findItemsByBatchId(int batchId) {
        String sql = "SELECT id, batch_id, claim_id, provider_id, billed, allowed, plan_paid, adjustment_reason_code, procedure_code, created_at " +
            "FROM remittance_batch_items WHERE batch_id = ? ORDER BY provider_id, claim_id";
        try {
            return getJdbcTemplate().query(sql, new ItemRowMapper(), batchId);
        } catch (Exception e) {
            LOG.error("findItemsByBatchId failed batchId=" + batchId, e);
            throw new DAOException("Could not load remittance items for batch " + batchId, e);
        }
    }

    @Override
    public void markSent(int batchId) {
        String sql = "UPDATE remittance_batches SET status = 'SENT' WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, batchId);
        } catch (Exception e) {
            LOG.error("markSent failed batchId=" + batchId, e);
            throw new DAOException("Could not mark batch sent id=" + batchId, e);
        }
    }

    private static final class BatchRowMapper implements RowMapper<RemittanceBatch> {
        @Override
        public RemittanceBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            RemittanceBatch b = new RemittanceBatch();
            b.setId(rs.getInt("id"));
            java.sql.Date pd = rs.getDate("payment_date");
            if (pd != null) { b.setPaymentDate(new java.util.Date(pd.getTime())); }
            b.setTotalPaid(rs.getBigDecimal("total_paid"));
            b.setStatus(rs.getString("status"));
            Timestamp gen = rs.getTimestamp("generated_at");
            if (gen != null) { b.setGeneratedAt(new java.util.Date(gen.getTime())); }
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { b.setCreatedAt(new java.util.Date(ca.getTime())); }
            return b;
        }
    }

    private static final class ItemRowMapper implements RowMapper<RemittanceBatchItem> {
        @Override
        public RemittanceBatchItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            RemittanceBatchItem it = new RemittanceBatchItem();
            it.setId(rs.getInt("id"));
            it.setBatchId(rs.getInt("batch_id"));
            it.setClaimId(rs.getInt("claim_id"));
            it.setProviderId(rs.getInt("provider_id"));
            it.setBilled(rs.getBigDecimal("billed"));
            it.setAllowed(rs.getBigDecimal("allowed"));
            it.setPlanPaid(rs.getBigDecimal("plan_paid"));
            it.setAdjustmentReasonCode(rs.getString("adjustment_reason_code"));
            it.setProcedureCode(rs.getString("procedure_code"));
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { it.setCreatedAt(new java.util.Date(ca.getTime())); }
            return it;
        }
    }
}
