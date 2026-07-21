package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimIntakeBatch;
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
public class JdbcClaimIntakeBatchDAO extends BaseDAO implements ClaimIntakeBatchDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimIntakeBatchDAO.class);

    @Override
    public int insert(ClaimIntakeBatch batch) {
        String sql = "INSERT INTO claim_intake_batches " +
            "(file_name, file_hash, status, total_records, succeeded, quarantined) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, batch.getFileName());
                ps.setString(2, batch.getFileHash());
                ps.setString(3, batch.getStatus());
                ps.setInt(4, batch.getTotalRecords());
                ps.setInt(5, batch.getSucceeded());
                ps.setInt(6, batch.getQuarantined());
                return ps;
            }, keyHolder);
            return keyHolder.getKey().intValue();
        } catch (Exception e) {
            LOG.error("insert intake batch failed fileName=" + batch.getFileName(), e);
            throw new DAOException("Could not insert claim intake batch", e);
        }
    }

    @Override
    public ClaimIntakeBatch findByFileHash(String fileHash) {
        String sql = "SELECT id, file_name, file_hash, status, total_records, succeeded, " +
            "quarantined, error_message, created_at, updated_at " +
            "FROM claim_intake_batches WHERE file_hash = ?";
        try {
            List<ClaimIntakeBatch> results = getJdbcTemplate().query(sql, new BatchRowMapper(), fileHash);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            LOG.error("findByFileHash failed hash=" + fileHash, e);
            throw new DAOException("Could not look up claim intake batch by hash", e);
        }
    }

    @Override
    public void updateCompletion(int id, String status, int totalRecords,
                                 int succeeded, int quarantined, String errorMessage) {
        String sql = "UPDATE claim_intake_batches " +
            "SET status = ?, total_records = ?, succeeded = ?, quarantined = ?, error_message = ? " +
            "WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, status, totalRecords, succeeded, quarantined, errorMessage, id);
        } catch (Exception e) {
            LOG.error("updateCompletion failed id=" + id, e);
            throw new DAOException("Could not update claim intake batch id=" + id, e);
        }
    }

    @Override
    public List<ClaimIntakeBatch> findRecent(int limit) {
        String sql = "SELECT id, file_name, file_hash, status, total_records, succeeded, " +
            "quarantined, error_message, created_at, updated_at " +
            "FROM claim_intake_batches ORDER BY created_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new BatchRowMapper(), limit);
        } catch (Exception e) {
            LOG.error("findRecent intake batches failed", e);
            throw new DAOException("Could not load recent claim intake batches", e);
        }
    }

    private static final class BatchRowMapper implements RowMapper<ClaimIntakeBatch> {
        @Override
        public ClaimIntakeBatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimIntakeBatch b = new ClaimIntakeBatch();
            b.setId(rs.getInt("id"));
            b.setFileName(rs.getString("file_name"));
            b.setFileHash(rs.getString("file_hash"));
            b.setStatus(rs.getString("status"));
            b.setTotalRecords(rs.getInt("total_records"));
            b.setSucceeded(rs.getInt("succeeded"));
            b.setQuarantined(rs.getInt("quarantined"));
            b.setErrorMessage(rs.getString("error_message"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) b.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) b.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return b;
        }
    }
}
