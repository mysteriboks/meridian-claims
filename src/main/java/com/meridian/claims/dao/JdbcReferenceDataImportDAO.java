package com.meridian.claims.dao;

import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.ReferenceDataImportRow;
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
import java.util.Date;
import java.util.List;

@Repository
public class JdbcReferenceDataImportDAO extends BaseDAO implements ReferenceDataImportDAO {

    private static final Logger LOG = Logger.getLogger(JdbcReferenceDataImportDAO.class);

    private static final String BATCH_COLS =
        "id, feed_type, file_name, file_hash, status, total_records, added_count, changed_count, " +
        "flagged_count, error_message, applied_by_user_id, applied_at, created_at, updated_at";

    @Override
    public int insertBatch(ReferenceDataImportBatch batch) {
        String sql = "INSERT INTO reference_data_import_batches " +
            "(feed_type, file_name, file_hash, status, total_records, added_count, changed_count, flagged_count) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, batch.getFeedType());
                ps.setString(2, batch.getFileName());
                ps.setString(3, batch.getFileHash());
                ps.setString(4, batch.getStatus());
                ps.setInt(5, batch.getTotalRecords());
                ps.setInt(6, batch.getAddedCount());
                ps.setInt(7, batch.getChangedCount());
                ps.setInt(8, batch.getFlaggedCount());
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            batch.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insertBatch failed feedType=" + batch.getFeedType(), e);
            throw new DAOException("Could not insert reference data import batch", e);
        }
    }

    @Override
    public ReferenceDataImportBatch findBatchById(int id) {
        String sql = "SELECT " + BATCH_COLS + " FROM reference_data_import_batches WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new BatchRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findBatchById failed id=" + id, e);
            throw new DAOException("Could not load reference data import batch id=" + id, e);
        }
    }

    @Override
    public ReferenceDataImportBatch findBatchByFileHash(String fileHash) {
        String sql = "SELECT " + BATCH_COLS + " FROM reference_data_import_batches WHERE file_hash = ?";
        try {
            List<ReferenceDataImportBatch> results = getJdbcTemplate().query(sql, new BatchRowMapper(), fileHash);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            LOG.error("findBatchByFileHash failed hash=" + fileHash, e);
            throw new DAOException("Could not look up reference data import batch by hash", e);
        }
    }

    @Override
    public List<ReferenceDataImportBatch> findRecentBatches(int limit) {
        String sql = "SELECT " + BATCH_COLS + " FROM reference_data_import_batches ORDER BY created_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new BatchRowMapper(), limit);
        } catch (Exception e) {
            LOG.error("findRecentBatches failed", e);
            throw new DAOException("Could not load recent reference data import batches", e);
        }
    }

    @Override
    public void markBatchApplied(int id, int appliedByUserId, Date appliedAt) {
        String sql = "UPDATE reference_data_import_batches SET status = ?, applied_by_user_id = ?, applied_at = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, ReferenceDataImportBatch.STATUS_APPLIED, appliedByUserId,
                new Timestamp(appliedAt.getTime()), id);
        } catch (Exception e) {
            LOG.error("markBatchApplied failed id=" + id, e);
            throw new DAOException("Could not mark reference data import batch id=" + id + " applied", e);
        }
    }

    @Override
    public void markBatchFailed(int id, String errorMessage) {
        String sql = "UPDATE reference_data_import_batches SET status = ?, error_message = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, ReferenceDataImportBatch.STATUS_FAILED, errorMessage, id);
        } catch (Exception e) {
            LOG.error("markBatchFailed failed id=" + id, e);
            throw new DAOException("Could not mark reference data import batch id=" + id + " failed", e);
        }
    }

    @Override
    public int insertRow(ReferenceDataImportRow row) {
        String sql = "INSERT INTO reference_data_import_rows (batch_id, row_type, code, description, extra, applied) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, row.getBatchId());
                ps.setString(2, row.getRowType());
                ps.setString(3, row.getCode());
                ps.setString(4, row.getDescription());
                ps.setString(5, row.getExtra());
                ps.setBoolean(6, row.isApplied());
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            row.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insertRow failed batchId=" + row.getBatchId() + " code=" + row.getCode(), e);
            throw new DAOException("Could not insert reference data import row", e);
        }
    }

    @Override
    public List<ReferenceDataImportRow> findRowsByBatchId(int batchId) {
        String sql = "SELECT id, batch_id, row_type, code, description, extra, applied, created_at " +
            "FROM reference_data_import_rows WHERE batch_id = ? ORDER BY id";
        try {
            return getJdbcTemplate().query(sql, new RowRowMapper(), batchId);
        } catch (Exception e) {
            LOG.error("findRowsByBatchId failed batchId=" + batchId, e);
            throw new DAOException("Could not load reference data import rows for batch id=" + batchId, e);
        }
    }

    @Override
    public void markRowApplied(int id) {
        try {
            getJdbcTemplate().update("UPDATE reference_data_import_rows SET applied = TRUE WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("markRowApplied failed id=" + id, e);
            throw new DAOException("Could not mark reference data import row id=" + id + " applied", e);
        }
    }

    private static final class BatchRowMapper implements RowMapper<ReferenceDataImportBatch> {
        @Override
        public ReferenceDataImportBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReferenceDataImportBatch b = new ReferenceDataImportBatch();
            b.setId(rs.getInt("id"));
            b.setFeedType(rs.getString("feed_type"));
            b.setFileName(rs.getString("file_name"));
            b.setFileHash(rs.getString("file_hash"));
            b.setStatus(rs.getString("status"));
            b.setTotalRecords(rs.getInt("total_records"));
            b.setAddedCount(rs.getInt("added_count"));
            b.setChangedCount(rs.getInt("changed_count"));
            b.setFlaggedCount(rs.getInt("flagged_count"));
            b.setErrorMessage(rs.getString("error_message"));
            int appliedBy = rs.getInt("applied_by_user_id");
            if (!rs.wasNull()) b.setAppliedByUserId(appliedBy);
            Timestamp appliedAt = rs.getTimestamp("applied_at");
            if (appliedAt != null) b.setAppliedAt(new java.util.Date(appliedAt.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) b.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) b.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return b;
        }
    }

    private static final class RowRowMapper implements RowMapper<ReferenceDataImportRow> {
        @Override
        public ReferenceDataImportRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReferenceDataImportRow r = new ReferenceDataImportRow();
            r.setId(rs.getInt("id"));
            r.setBatchId(rs.getInt("batch_id"));
            r.setRowType(rs.getString("row_type"));
            r.setCode(rs.getString("code"));
            r.setDescription(rs.getString("description"));
            r.setExtra(rs.getString("extra"));
            r.setApplied(rs.getBoolean("applied"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) r.setCreatedAt(new java.util.Date(createdAt.getTime()));
            return r;
        }
    }
}
