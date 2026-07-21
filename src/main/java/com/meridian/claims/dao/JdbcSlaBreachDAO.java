package com.meridian.claims.dao;

import com.meridian.claims.model.SlaBreach;
import org.apache.log4j.Logger;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;

@Repository
public class JdbcSlaBreachDAO extends BaseDAO implements SlaBreachDAO {

    private static final Logger LOG = Logger.getLogger(JdbcSlaBreachDAO.class);

    @Override
    public void insert(SlaBreach breach) {
        String sql = "INSERT INTO sla_breaches (claim_id, status, expected_by) VALUES (?,?,?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final SlaBreach b = breach;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, b.getClaimId());
                ps.setString(2, b.getStatus());
                ps.setTimestamp(3, new Timestamp(b.getExpectedBy().getTime()));
                return ps;
            }, key);
            breach.setId(key.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insert sla_breach failed claimId=" + breach.getClaimId(), e);
            throw new DAOException("Could not insert SLA breach", e);
        }
    }

    @Override
    public boolean existsForClaimStatus(int claimId, String status) {
        String sql = "SELECT COUNT(*) FROM sla_breaches WHERE claim_id = ? AND status = ?";
        try {
            Integer count = getJdbcTemplate().queryForObject(sql, Integer.class, claimId, status);
            return count != null && count > 0;
        } catch (Exception e) {
            LOG.error("existsForClaimStatus failed claimId=" + claimId, e);
            throw new DAOException("Could not check SLA breach existence", e);
        }
    }

}
