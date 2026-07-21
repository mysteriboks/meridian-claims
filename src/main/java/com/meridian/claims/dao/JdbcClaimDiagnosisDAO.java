package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimDiagnosis;
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
public class JdbcClaimDiagnosisDAO extends BaseDAO implements ClaimDiagnosisDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimDiagnosisDAO.class);

    @Override
    public void insertBatch(List<ClaimDiagnosis> diagnoses) {
        String sql = "INSERT INTO claim_diagnoses (claim_id, diagnosis_code, sequence_number, diagnosis_type) VALUES (?,?,?,?)";
        try {
            for (ClaimDiagnosis diag : diagnoses) {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                final ClaimDiagnosis d = diag;
                getJdbcTemplate().update(con -> {
                    PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                    ps.setInt(1, d.getClaimId());
                    ps.setString(2, d.getDiagnosisCode());
                    ps.setInt(3, d.getSequenceNumber());
                    ps.setString(4, d.getDiagnosisType());
                    return ps;
                }, keyHolder);
                diag.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insertBatch diagnoses failed", e);
            throw new DAOException("Could not insert claim diagnoses", e);
        }
    }

    @Override
    public List<ClaimDiagnosis> findByClaimId(int claimId) {
        String sql = "SELECT id, claim_id, diagnosis_code, sequence_number, diagnosis_type, created_at " +
            "FROM claim_diagnoses WHERE claim_id = ? ORDER BY sequence_number";
        try {
            return getJdbcTemplate().query(sql, new DiagnosisRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load diagnoses for claim id=" + claimId, e);
        }
    }

    private static final class DiagnosisRowMapper implements RowMapper<ClaimDiagnosis> {
        @Override
        public ClaimDiagnosis mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimDiagnosis d = new ClaimDiagnosis();
            d.setId(rs.getInt("id"));
            d.setClaimId(rs.getInt("claim_id"));
            d.setDiagnosisCode(rs.getString("diagnosis_code"));
            d.setSequenceNumber(rs.getInt("sequence_number"));
            d.setDiagnosisType(rs.getString("diagnosis_type"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { d.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            return d;
        }
    }
}
