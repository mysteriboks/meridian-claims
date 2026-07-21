package com.meridian.claims.dao;

import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.model.PriorAuthStatus;
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
public class JdbcPriorAuthorizationDAO extends BaseDAO implements PriorAuthorizationDAO {

    private static final Logger LOG = Logger.getLogger(JdbcPriorAuthorizationDAO.class);

    private static final String SELECT_COLS =
        "pa.id, pa.member_id, (m.first_name || ' ' || m.last_name) AS member_name, " +
        "pa.provider_id, pr.name AS provider_name, pa.procedure_code, pa.service_type, " +
        "pa.authorized_from, pa.authorized_to, pa.auth_number, pa.status, " +
        "pa.approved_units, pa.notes, pa.created_at, pa.updated_at";

    private static final String BASE_FROM =
        " FROM prior_authorizations pa " +
        " JOIN members m ON m.id = pa.member_id " +
        " JOIN providers pr ON pr.id = pa.provider_id ";

    @Override
    public PriorAuthorization findById(int id) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE pa.id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new PaRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load prior auth id=" + id, e);
        }
    }

    @Override
    public PriorAuthorization findByAuthNumber(String authNumber) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE pa.auth_number = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new PaRowMapper(), authNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByAuthNumber failed", e);
            throw new DAOException("Could not load prior auth " + authNumber, e);
        }
    }

    @Override
    public PriorAuthorization findValid(int memberId, String procedureCode, java.util.Date serviceDate) {
        Date dos = new Date(serviceDate.getTime());
        String sql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE pa.member_id = ? AND pa.procedure_code = ? AND pa.status = 'ACTIVE'" +
            " AND pa.authorized_from <= ? AND pa.authorized_to >= ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new PaRowMapper(), memberId, procedureCode, dos, dos);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findValid failed memberId=" + memberId, e);
            throw new DAOException("Could not find valid prior auth", e);
        }
    }

    @Override
    public Page<PriorAuthorization> findByMemberId(int memberId, int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM prior_authorizations WHERE member_id = ?";
        String dataSql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE pa.member_id = ? ORDER BY pa.authorized_from DESC LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, memberId);
            int offset = (pageNumber - 1) * pageSize;
            List<PriorAuthorization> items = getJdbcTemplate().query(dataSql, new PaRowMapper(),
                memberId, pageSize, offset);
            return new Page<PriorAuthorization>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not list prior auths for member id=" + memberId, e);
        }
    }

    @Override
    public Page<PriorAuthorization> findAll(int pageNumber, int pageSize) {
        String countSql = "SELECT COUNT(*) FROM prior_authorizations";
        String dataSql = "SELECT " + SELECT_COLS + BASE_FROM +
            " ORDER BY pa.authorized_from DESC LIMIT ? OFFSET ?";
        try {
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class);
            int offset = (pageNumber - 1) * pageSize;
            List<PriorAuthorization> items = getJdbcTemplate().query(dataSql, new PaRowMapper(),
                pageSize, offset);
            return new Page<PriorAuthorization>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list prior auths", e);
        }
    }

    @Override
    public void insert(PriorAuthorization auth) {
        String sql = "INSERT INTO prior_authorizations " +
            "(member_id, provider_id, procedure_code, service_type, authorized_from, " +
            " authorized_to, auth_number, status, approved_units, notes) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final PriorAuthorization a = auth;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, a.getMemberId());
                ps.setInt(2, a.getProviderId());
                ps.setString(3, a.getProcedureCode());
                ps.setString(4, a.getServiceType());
                ps.setDate(5, new Date(a.getAuthorizedFrom().getTime()));
                ps.setDate(6, new Date(a.getAuthorizedTo().getTime()));
                ps.setString(7, a.getAuthNumber());
                ps.setString(8, a.getStatus() == null ? "ACTIVE" : a.getStatus().name());
                ps.setInt(9, a.getApprovedUnits());
                ps.setString(10, a.getNotes());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                auth.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed", e);
            throw new DAOException("Could not insert prior authorization", e);
        }
    }

    @Override
    public void update(PriorAuthorization auth) {
        String sql = "UPDATE prior_authorizations SET procedure_code = ?, service_type = ?, " +
            "authorized_from = ?, authorized_to = ?, status = ?, approved_units = ?, notes = ? " +
            "WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                auth.getProcedureCode(),
                auth.getServiceType(),
                new Date(auth.getAuthorizedFrom().getTime()),
                new Date(auth.getAuthorizedTo().getTime()),
                auth.getStatus().name(),
                auth.getApprovedUnits(),
                auth.getNotes(),
                auth.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + auth.getId(), e);
            throw new DAOException("Could not update prior auth id=" + auth.getId(), e);
        }
    }

    @Override
    public void expire(int id) {
        try {
            getJdbcTemplate().update(
                "UPDATE prior_authorizations SET status = 'EXPIRED' WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("expire failed id=" + id, e);
            throw new DAOException("Could not expire prior auth id=" + id, e);
        }
    }

    private static final class PaRowMapper implements RowMapper<PriorAuthorization> {
        @Override
        public PriorAuthorization mapRow(ResultSet rs, int rowNum) throws SQLException {
            PriorAuthorization a = new PriorAuthorization();
            a.setId(rs.getInt("id"));
            a.setMemberId(rs.getInt("member_id"));
            a.setMemberName(rs.getString("member_name"));
            a.setProviderId(rs.getInt("provider_id"));
            a.setProviderName(rs.getString("provider_name"));
            a.setProcedureCode(rs.getString("procedure_code"));
            a.setServiceType(rs.getString("service_type"));
            Date from = rs.getDate("authorized_from");
            if (from != null) a.setAuthorizedFrom(new java.util.Date(from.getTime()));
            Date to = rs.getDate("authorized_to");
            if (to != null) a.setAuthorizedTo(new java.util.Date(to.getTime()));
            a.setAuthNumber(rs.getString("auth_number"));
            a.setStatus(PriorAuthStatus.valueOf(rs.getString("status")));
            a.setApprovedUnits(rs.getInt("approved_units"));
            a.setNotes(rs.getString("notes"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) a.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) a.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return a;
        }
    }
}
