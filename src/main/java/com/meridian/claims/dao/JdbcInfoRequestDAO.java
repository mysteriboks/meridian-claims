package com.meridian.claims.dao;

import com.meridian.claims.model.InfoRequest;
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
public class JdbcInfoRequestDAO extends BaseDAO implements InfoRequestDAO {

    private static final Logger LOG = Logger.getLogger(JdbcInfoRequestDAO.class);

    private static final String SELECT_COLS =
        "id, claim_id, requested_from, requested_by_user_id, requested_at, due_date, " +
        "request_notes, response_received_at, response_notes, status, created_at, updated_at";

    @Override
    public void insert(InfoRequest r) {
        String sql = "INSERT INTO info_requests " +
            "(claim_id, requested_from, requested_by_user_id, due_date, request_notes, status) " +
            "VALUES (?,?,?,?,?,'OPEN')";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final InfoRequest ir = r;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, ir.getClaimId());
                ps.setString(2, ir.getRequestedFrom());
                ps.setInt(3, ir.getRequestedByUserId());
                ps.setDate(4, new Date(ir.getDueDate().getTime()));
                ps.setString(5, ir.getRequestNotes());
                return ps;
            }, key);
            r.setId(key.getKey().intValue());
            r.setStatus("OPEN");
        } catch (Exception e) {
            LOG.error("insert info_request failed claimId=" + r.getClaimId(), e);
            throw new DAOException("Could not insert info_request", e);
        }
    }

    @Override
    public InfoRequest findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM info_requests WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new InfoRequestRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load info_request id=" + id, e);
        }
    }

    @Override
    public List<InfoRequest> findByClaimId(int claimId) {
        String sql = "SELECT " + SELECT_COLS +
            " FROM info_requests WHERE claim_id = ? ORDER BY created_at DESC";
        try {
            return getJdbcTemplate().query(sql, new InfoRequestRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load info_requests for claim " + claimId, e);
        }
    }

    @Override
    public InfoRequest findOpenByClaimId(int claimId) {
        String sql = "SELECT " + SELECT_COLS +
            " FROM info_requests WHERE claim_id = ? AND status = 'OPEN' ORDER BY created_at DESC LIMIT 1";
        try {
            List<InfoRequest> rows = getJdbcTemplate().query(sql, new InfoRequestRowMapper(), claimId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            LOG.error("findOpenByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load open info_request for claim " + claimId, e);
        }
    }

    @Override
    public void markResponded(int id, String responseNotes) {
        String sql = "UPDATE info_requests SET status = 'RESPONDED', response_notes = ?, " +
            "response_received_at = NOW() WHERE id = ? AND status = 'OPEN'";
        try {
            int rows = getJdbcTemplate().update(sql, responseNotes, id);
            if (rows == 0) {
                throw new DAOException("Info request id=" + id + " is not OPEN or does not exist");
            }
        } catch (DAOException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("markResponded failed id=" + id, e);
            throw new DAOException("Could not mark info_request responded id=" + id, e);
        }
    }

    private static final class InfoRequestRowMapper implements RowMapper<InfoRequest> {
        @Override
        public InfoRequest mapRow(ResultSet rs, int rowNum) throws SQLException {
            InfoRequest r = new InfoRequest();
            r.setId(rs.getInt("id"));
            r.setClaimId(rs.getInt("claim_id"));
            r.setRequestedFrom(rs.getString("requested_from"));
            r.setRequestedByUserId(rs.getInt("requested_by_user_id"));
            Timestamp reqAt = rs.getTimestamp("requested_at");
            if (reqAt != null) { r.setRequestedAt(new java.util.Date(reqAt.getTime())); }
            r.setDueDate(rs.getDate("due_date"));
            r.setRequestNotes(rs.getString("request_notes"));
            Timestamp respAt = rs.getTimestamp("response_received_at");
            if (respAt != null) { r.setResponseReceivedAt(new java.util.Date(respAt.getTime())); }
            r.setResponseNotes(rs.getString("response_notes"));
            r.setStatus(rs.getString("status"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { r.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { r.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return r;
        }
    }
}
