package com.meridian.claims.dao;

import com.meridian.claims.model.EobDocument;
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
import java.util.List;

@Repository
public class JdbcEobDocumentDAO extends BaseDAO implements EobDocumentDAO {

    private static final Logger LOG = Logger.getLogger(JdbcEobDocumentDAO.class);

    private static final String COLS =
        "id, claim_id, member_id, content, delivery_method, delivered_at, " +
        "mailed_by_user_id, generated_at, created_at";

    @Override
    public void insert(EobDocument doc) {
        String sql = "INSERT INTO eob_documents (claim_id, member_id, content, delivery_method) VALUES (?,?,?,'PENDING')";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final EobDocument d = doc;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, d.getClaimId());
                ps.setInt(2, d.getMemberId());
                ps.setString(3, d.getContent());
                return ps;
            }, key);
            doc.setId(key.getKey().intValue());
            doc.setDeliveryMethod("PENDING");
        } catch (Exception e) {
            LOG.error("insert eob_document failed claimId=" + doc.getClaimId(), e);
            throw new DAOException("Could not insert EOB document", e);
        }
    }

    @Override
    public EobDocument findById(int id) {
        String sql = "SELECT " + COLS + " FROM eob_documents WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new EobRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load EOB id=" + id, e);
        }
    }

    @Override
    public EobDocument findByClaimId(int claimId) {
        String sql = "SELECT " + COLS + " FROM eob_documents WHERE claim_id = ? ORDER BY created_at DESC LIMIT 1";
        try {
            List<EobDocument> rows = getJdbcTemplate().query(sql, new EobRowMapper(), claimId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load EOB for claim " + claimId, e);
        }
    }

    @Override
    public List<EobDocument> findByMemberId(int memberId) {
        String sql = "SELECT " + COLS + " FROM eob_documents WHERE member_id = ? ORDER BY created_at DESC";
        try {
            return getJdbcTemplate().query(sql, new EobRowMapper(), memberId);
        } catch (Exception e) {
            LOG.error("findByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not load EOBs for member " + memberId, e);
        }
    }

    @Override
    public void markMailed(int id, int mailedByUserId) {
        String sql = "UPDATE eob_documents SET delivery_method = 'MAILED', delivered_at = NOW(), " +
            "mailed_by_user_id = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql, mailedByUserId, id);
        } catch (Exception e) {
            LOG.error("markMailed failed id=" + id, e);
            throw new DAOException("Could not mark EOB mailed id=" + id, e);
        }
    }

    private static final class EobRowMapper implements RowMapper<EobDocument> {
        @Override
        public EobDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            EobDocument d = new EobDocument();
            d.setId(rs.getInt("id"));
            d.setClaimId(rs.getInt("claim_id"));
            d.setMemberId(rs.getInt("member_id"));
            d.setContent(rs.getString("content"));
            d.setDeliveryMethod(rs.getString("delivery_method"));
            Timestamp del = rs.getTimestamp("delivered_at");
            if (del != null) { d.setDeliveredAt(new java.util.Date(del.getTime())); }
            int mbu = rs.getInt("mailed_by_user_id");
            if (!rs.wasNull()) { d.setMailedByUserId(mbu); }
            Timestamp gen = rs.getTimestamp("generated_at");
            if (gen != null) { d.setGeneratedAt(new java.util.Date(gen.getTime())); }
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) { d.setCreatedAt(new java.util.Date(ca.getTime())); }
            return d;
        }
    }
}
