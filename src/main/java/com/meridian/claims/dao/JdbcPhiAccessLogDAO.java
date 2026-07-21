package com.meridian.claims.dao;

import com.meridian.claims.model.PhiAccessLog;
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
public class JdbcPhiAccessLogDAO extends BaseDAO implements PhiAccessLogDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPhiAccessLogDAO.class);

    @Override
    public void insert(PhiAccessLog log) {
        String sql = "INSERT INTO phi_access_log (user_id, member_id, claim_id, action) VALUES (?,?,?,?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final PhiAccessLog l = log;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                if (l.getUserId() != null) {
                    ps.setInt(1, l.getUserId());
                } else {
                    ps.setNull(1, java.sql.Types.INTEGER);
                }
                ps.setInt(2, l.getMemberId());
                ps.setInt(3, l.getClaimId());
                ps.setString(4, l.getAction());
                return ps;
            }, key);
            log.setId(key.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insert phi_access_log failed claimId=" + log.getClaimId(), e);
            throw new DAOException("Could not insert PHI access log", e);
        }
    }

    @Override
    public List<PhiAccessLog> findByClaimId(int claimId) {
        String sql = "SELECT id, user_id, member_id, claim_id, action, accessed_at " +
            "FROM phi_access_log WHERE claim_id = ? ORDER BY accessed_at DESC";
        try {
            return getJdbcTemplate().query(sql, new PhiRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load PHI access log for claim " + claimId, e);
        }
    }

    private static final class PhiRowMapper implements RowMapper<PhiAccessLog> {
        @Override
        public PhiAccessLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            PhiAccessLog l = new PhiAccessLog();
            l.setId(rs.getInt("id"));
            int uid = rs.getInt("user_id");
            if (!rs.wasNull()) { l.setUserId(uid); }
            l.setMemberId(rs.getInt("member_id"));
            l.setClaimId(rs.getInt("claim_id"));
            l.setAction(rs.getString("action"));
            Timestamp ts = rs.getTimestamp("accessed_at");
            if (ts != null) { l.setAccessedAt(new java.util.Date(ts.getTime())); }
            return l;
        }
    }
}
