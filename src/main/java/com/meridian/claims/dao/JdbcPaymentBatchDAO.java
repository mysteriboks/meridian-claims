package com.meridian.claims.dao;

import com.meridian.claims.model.PaymentBatch;
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
public class JdbcPaymentBatchDAO extends BaseDAO implements PaymentBatchDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPaymentBatchDAO.class);

    private static final String COLS =
        "id, batch_date, total_amount, status, exported_at, file_reference, created_by, created_at, updated_at";

    @Override
    public void insert(PaymentBatch batch) {
        String sql = "INSERT INTO payment_batches (batch_date, total_amount, status, created_by) VALUES (?,0.00,'PENDING',?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final PaymentBatch b = batch;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setDate(1, new Date(b.getBatchDate().getTime()));
                if (b.getCreatedBy() != null) {
                    ps.setInt(2, b.getCreatedBy());
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
                return ps;
            }, key);
            batch.setId(key.getKey().intValue());
            batch.setStatus("PENDING");
        } catch (Exception e) {
            LOG.error("insert payment_batch failed", e);
            throw new DAOException("Could not insert payment batch", e);
        }
    }

    @Override
    public PaymentBatch findById(int id) {
        String sql = "SELECT " + COLS + " FROM payment_batches WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new BatchRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load payment batch id=" + id, e);
        }
    }

    @Override
    public List<PaymentBatch> findAll() {
        String sql = "SELECT " + COLS + " FROM payment_batches ORDER BY batch_date DESC";
        try {
            return getJdbcTemplate().query(sql, new BatchRowMapper());
        } catch (Exception e) {
            LOG.error("findAll payment_batches failed", e);
            throw new DAOException("Could not load payment batches", e);
        }
    }

    @Override
    public void updateTotalAmount(int id, BigDecimal totalAmount) {
        String sql = "UPDATE payment_batches SET total_amount = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, totalAmount, id);
        } catch (Exception e) {
            LOG.error("updateTotalAmount failed id=" + id, e);
            throw new DAOException("Could not update payment batch total id=" + id, e);
        }
    }

    @Override
    public void updateStatus(int id, String status, String fileReference) {
        String sql = "UPDATE payment_batches SET status = ?, file_reference = ?, " +
            "exported_at = CASE WHEN ? = 'EXPORTED' THEN NOW() ELSE exported_at END WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, status, fileReference, status, id);
        } catch (Exception e) {
            LOG.error("updateStatus failed id=" + id, e);
            throw new DAOException("Could not update payment batch status id=" + id, e);
        }
    }

    private static final class BatchRowMapper implements RowMapper<PaymentBatch> {
        @Override
        public PaymentBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            PaymentBatch b = new PaymentBatch();
            b.setId(rs.getInt("id"));
            b.setBatchDate(rs.getDate("batch_date"));
            b.setTotalAmount(rs.getBigDecimal("total_amount"));
            b.setStatus(rs.getString("status"));
            Timestamp exp = rs.getTimestamp("exported_at");
            if (exp != null) { b.setExportedAt(new java.util.Date(exp.getTime())); }
            b.setFileReference(rs.getString("file_reference"));
            int cb = rs.getInt("created_by");
            if (!rs.wasNull()) { b.setCreatedBy(cb); }
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { b.setCreatedAt(new java.util.Date(ca.getTime())); }
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) { b.setUpdatedAt(new java.util.Date(ua.getTime())); }
            return b;
        }
    }
}
