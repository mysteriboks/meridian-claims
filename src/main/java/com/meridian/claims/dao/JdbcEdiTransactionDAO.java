package com.meridian.claims.dao;

import com.meridian.claims.model.EdiTransaction;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

@Repository
public class JdbcEdiTransactionDAO extends BaseDAO implements EdiTransactionDAO {

    private static final Logger LOG = Logger.getLogger(JdbcEdiTransactionDAO.class);

    @Override
    public int insert(EdiTransaction txn) {
        String sql = "INSERT INTO edi_transactions " +
            "(direction, transaction_type, isa_control_number, gs_control_number, st_control_number, " +
            "status, related_transaction_id, file_reference, detail, trading_partner_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, txn.getDirection());
                ps.setString(2, txn.getTransactionType());
                ps.setString(3, txn.getIsaControlNumber());
                ps.setString(4, txn.getGsControlNumber());
                ps.setString(5, txn.getStControlNumber());
                ps.setString(6, txn.getStatus());
                if (txn.getRelatedTransactionId() != null) {
                    ps.setInt(7, txn.getRelatedTransactionId());
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                ps.setString(8, txn.getFileReference());
                ps.setString(9, txn.getDetail());
                if (txn.getTradingPartnerId() != null) {
                    ps.setInt(10, txn.getTradingPartnerId());
                } else {
                    ps.setNull(10, Types.INTEGER);
                }
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            txn.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insert edi transaction failed type=" + txn.getTransactionType(), e);
            throw new DAOException("Could not insert EDI transaction", e);
        }
    }

    @Override
    public List<EdiTransaction> findRecent(int limit) {
        String sql = "SELECT " + COLS + " FROM edi_transactions ORDER BY created_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new EdiTransactionRowMapper(), limit);
        } catch (Exception e) {
            LOG.error("findRecent edi transactions failed", e);
            throw new DAOException("Could not load recent EDI transactions", e);
        }
    }

    @Override
    public List<EdiTransaction> findByFileReference(String fileReference) {
        String sql = "SELECT " + COLS + " FROM edi_transactions WHERE file_reference = ? ORDER BY created_at";
        try {
            return getJdbcTemplate().query(sql, new EdiTransactionRowMapper(), fileReference);
        } catch (Exception e) {
            LOG.error("findByFileReference edi transactions failed fileReference=" + fileReference, e);
            throw new DAOException("Could not load EDI transactions for file " + fileReference, e);
        }
    }

    private static final String COLS = "id, direction, transaction_type, isa_control_number, " +
        "gs_control_number, st_control_number, status, related_transaction_id, file_reference, " +
        "detail, trading_partner_id, created_at, updated_at";

    private static final class EdiTransactionRowMapper implements RowMapper<EdiTransaction> {
        @Override
        public EdiTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            EdiTransaction t = new EdiTransaction();
            t.setId(rs.getInt("id"));
            t.setDirection(rs.getString("direction"));
            t.setTransactionType(rs.getString("transaction_type"));
            t.setIsaControlNumber(rs.getString("isa_control_number"));
            t.setGsControlNumber(rs.getString("gs_control_number"));
            t.setStControlNumber(rs.getString("st_control_number"));
            t.setStatus(rs.getString("status"));
            int relatedId = rs.getInt("related_transaction_id");
            if (!rs.wasNull()) { t.setRelatedTransactionId(relatedId); }
            t.setFileReference(rs.getString("file_reference"));
            t.setDetail(rs.getString("detail"));
            int tradingPartnerId = rs.getInt("trading_partner_id");
            if (!rs.wasNull()) { t.setTradingPartnerId(tradingPartnerId); }
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { t.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { t.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return t;
        }
    }
}
