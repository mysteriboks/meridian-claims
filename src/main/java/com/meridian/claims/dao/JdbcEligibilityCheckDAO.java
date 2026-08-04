package com.meridian.claims.dao;

import com.meridian.claims.model.EligibilityCheck;
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
public class JdbcEligibilityCheckDAO extends BaseDAO implements EligibilityCheckDAO {

    private static final Logger LOG = Logger.getLogger(JdbcEligibilityCheckDAO.class);

    private static final String COLS = "id, member_id, provider_id, service_type, inquiry_at, " +
        "response_at, result_status, coverage_snapshot, checked_by_user_id, created_at, updated_at";

    @Override
    public int insert(EligibilityCheck check) {
        String sql = "INSERT INTO eligibility_checks " +
            "(member_id, provider_id, service_type, inquiry_at, response_at, result_status, " +
            "coverage_snapshot, checked_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, check.getMemberId());
                if (check.getProviderId() != null) {
                    ps.setInt(2, check.getProviderId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, check.getServiceType());
                ps.setTimestamp(4, check.getInquiryAt() != null
                    ? new Timestamp(check.getInquiryAt().getTime()) : new Timestamp(System.currentTimeMillis()));
                ps.setTimestamp(5, check.getResponseAt() != null ? new Timestamp(check.getResponseAt().getTime()) : null);
                ps.setString(6, check.getResultStatus());
                ps.setString(7, check.getCoverageSnapshot());
                if (check.getCheckedByUserId() != null) {
                    ps.setInt(8, check.getCheckedByUserId());
                } else {
                    ps.setNull(8, Types.INTEGER);
                }
                return ps;
            }, keyHolder);
            int id = keyHolder.getKey().intValue();
            check.setId(id);
            return id;
        } catch (Exception e) {
            LOG.error("insert eligibility check failed memberId=" + check.getMemberId(), e);
            throw new DAOException("Could not insert eligibility check", e);
        }
    }

    @Override
    public List<EligibilityCheck> findByMemberId(int memberId, int limit) {
        String sql = "SELECT " + COLS + " FROM eligibility_checks WHERE member_id = ? " +
            "ORDER BY inquiry_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().query(sql, new EligibilityCheckRowMapper(), memberId, limit);
        } catch (Exception e) {
            LOG.error("findByMemberId eligibility checks failed memberId=" + memberId, e);
            throw new DAOException("Could not load eligibility checks for member " + memberId, e);
        }
    }

    private static final class EligibilityCheckRowMapper implements RowMapper<EligibilityCheck> {
        @Override
        public EligibilityCheck mapRow(ResultSet rs, int rowNum) throws SQLException {
            EligibilityCheck c = new EligibilityCheck();
            c.setId(rs.getInt("id"));
            c.setMemberId(rs.getInt("member_id"));
            int providerId = rs.getInt("provider_id");
            if (!rs.wasNull()) { c.setProviderId(providerId); }
            c.setServiceType(rs.getString("service_type"));
            Timestamp inquiryAt = rs.getTimestamp("inquiry_at");
            if (inquiryAt != null) { c.setInquiryAt(new java.util.Date(inquiryAt.getTime())); }
            Timestamp responseAt = rs.getTimestamp("response_at");
            if (responseAt != null) { c.setResponseAt(new java.util.Date(responseAt.getTime())); }
            c.setResultStatus(rs.getString("result_status"));
            c.setCoverageSnapshot(rs.getString("coverage_snapshot"));
            int checkedBy = rs.getInt("checked_by_user_id");
            if (!rs.wasNull()) { c.setCheckedByUserId(checkedBy); }
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { c.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { c.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return c;
        }
    }
}
