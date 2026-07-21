package com.meridian.claims.dao;

import com.meridian.claims.model.Referral;
import com.meridian.claims.model.ReferralStatus;
import com.meridian.claims.util.Page;
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
public class JdbcReferralDAO extends BaseDAO implements ReferralDAO {

    private static final Logger LOG = Logger.getLogger(JdbcReferralDAO.class);

    private static final String SELECT_COLS =
        "r.id, r.member_id, (m.first_name || ' ' || m.last_name) AS member_name, " +
        "r.referring_provider_id, rp.name AS referring_provider_name, " +
        "r.referred_to_provider_id, tp.name AS referred_to_provider_name, " +
        "r.service_type, r.valid_from, r.valid_to, r.referral_number, " +
        "r.status, r.notes, r.created_at, r.updated_at";

    private static final String BASE_FROM =
        " FROM referrals r " +
        " JOIN members m ON m.id = r.member_id " +
        " JOIN providers rp ON rp.id = r.referring_provider_id " +
        " JOIN providers tp ON tp.id = r.referred_to_provider_id ";

    @Override
    public Referral findById(int id) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE r.id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ReferralRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load referral id=" + id, e);
        }
    }

    @Override
    public Referral findByReferralNumber(String referralNumber) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE r.referral_number = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ReferralRowMapper(), referralNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByReferralNumber failed", e);
            throw new DAOException("Could not load referral " + referralNumber, e);
        }
    }

    @Override
    public Referral findValid(int memberId, String serviceType, java.util.Date serviceDate) {
        Date dos = new Date(serviceDate.getTime());
        String sql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE r.member_id = ? AND r.service_type = ? AND r.status = 'ACTIVE'" +
            " AND r.valid_from <= ? AND r.valid_to >= ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ReferralRowMapper(), memberId, serviceType, dos, dos);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findValid failed memberId=" + memberId, e);
            throw new DAOException("Could not find valid referral", e);
        }
    }

    @Override
    public Page<Referral> findByMemberId(int memberId, int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM referrals WHERE member_id = ?";
        String dataSql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE r.member_id = ? ORDER BY r.valid_from DESC LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, memberId);
            int offset = (pageNumber - 1) * pageSize;
            List<Referral> items = getJdbcTemplate().query(dataSql, new ReferralRowMapper(),
                memberId, pageSize, offset);
            return new Page<Referral>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not list referrals for member id=" + memberId, e);
        }
    }

    @Override
    public Page<Referral> findAll(int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM referrals";
        String dataSql = "SELECT " + SELECT_COLS + BASE_FROM +
            " ORDER BY r.valid_from DESC LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class);
            int offset = (pageNumber - 1) * pageSize;
            List<Referral> items = getJdbcTemplate().query(dataSql, new ReferralRowMapper(),
                pageSize, offset);
            return new Page<Referral>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list referrals", e);
        }
    }

    @Override
    public void insert(Referral referral) {
        String sql = "INSERT INTO referrals " +
            "(member_id, referring_provider_id, referred_to_provider_id, service_type, " +
            " valid_from, valid_to, referral_number, status, notes) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final Referral ref = referral;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, ref.getMemberId());
                ps.setInt(2, ref.getReferringProviderId());
                ps.setInt(3, ref.getReferredToProviderId());
                ps.setString(4, ref.getServiceType());
                ps.setDate(5, new Date(ref.getValidFrom().getTime()));
                ps.setDate(6, new Date(ref.getValidTo().getTime()));
                ps.setString(7, ref.getReferralNumber());
                ps.setString(8, ref.getStatus() == null ? "ACTIVE" : ref.getStatus().name());
                ps.setString(9, ref.getNotes());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                referral.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed", e);
            throw new DAOException("Could not insert referral", e);
        }
    }

    @Override
    public void update(Referral referral) {
        String sql = "UPDATE referrals SET service_type = ?, valid_from = ?, valid_to = ?, " +
            "status = ?, notes = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                referral.getServiceType(),
                new Date(referral.getValidFrom().getTime()),
                new Date(referral.getValidTo().getTime()),
                referral.getStatus().name(),
                referral.getNotes(),
                referral.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + referral.getId(), e);
            throw new DAOException("Could not update referral id=" + referral.getId(), e);
        }
    }

    @Override
    public void expire(int id) {
        try {
            getJdbcTemplate().update("UPDATE referrals SET status = 'EXPIRED' WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("expire failed id=" + id, e);
            throw new DAOException("Could not expire referral id=" + id, e);
        }
    }

    private static final class ReferralRowMapper implements RowMapper<Referral> {
        @Override
        public Referral mapRow(ResultSet rs, int rowNum) throws SQLException {
            Referral r = new Referral();
            r.setId(rs.getInt("id"));
            r.setMemberId(rs.getInt("member_id"));
            r.setMemberName(rs.getString("member_name"));
            r.setReferringProviderId(rs.getInt("referring_provider_id"));
            r.setReferringProviderName(rs.getString("referring_provider_name"));
            r.setReferredToProviderId(rs.getInt("referred_to_provider_id"));
            r.setReferredToProviderName(rs.getString("referred_to_provider_name"));
            r.setServiceType(rs.getString("service_type"));
            Date vf = rs.getDate("valid_from");
            if (vf != null) r.setValidFrom(new java.util.Date(vf.getTime()));
            Date vt = rs.getDate("valid_to");
            if (vt != null) r.setValidTo(new java.util.Date(vt.getTime()));
            r.setReferralNumber(rs.getString("referral_number"));
            r.setStatus(ReferralStatus.valueOf(rs.getString("status")));
            r.setNotes(rs.getString("notes"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) r.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) r.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return r;
        }
    }
}
