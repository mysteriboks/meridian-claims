package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimNote;
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
public class JdbcClaimNoteDAO extends BaseDAO implements ClaimNoteDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimNoteDAO.class);

    @Override
    public void insert(ClaimNote note) {
        String sql = "INSERT INTO claim_notes (claim_id, author_user_id, note) VALUES (?,?,?)";
        KeyHolder key = new GeneratedKeyHolder();
        try {
            final ClaimNote n = note;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, n.getClaimId());
                ps.setInt(2, n.getAuthorUserId());
                ps.setString(3, n.getNote());
                return ps;
            }, key);
            note.setId(key.getKey().intValue());
        } catch (Exception e) {
            LOG.error("insert claim_note failed claimId=" + note.getClaimId(), e);
            throw new DAOException("Could not insert claim note", e);
        }
    }

    @Override
    public List<ClaimNote> findByClaimId(int claimId) {
        String sql = "SELECT id, claim_id, author_user_id, note, created_at " +
            "FROM claim_notes WHERE claim_id = ? ORDER BY created_at ASC";
        try {
            return getJdbcTemplate().query(sql, new ClaimNoteRowMapper(), claimId);
        } catch (Exception e) {
            LOG.error("findByClaimId failed claimId=" + claimId, e);
            throw new DAOException("Could not load notes for claim " + claimId, e);
        }
    }

    private static final class ClaimNoteRowMapper implements RowMapper<ClaimNote> {
        @Override
        public ClaimNote mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClaimNote n = new ClaimNote();
            n.setId(rs.getInt("id"));
            n.setClaimId(rs.getInt("claim_id"));
            n.setAuthorUserId(rs.getInt("author_user_id"));
            n.setNote(rs.getString("note"));
            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) { n.setCreatedAt(new java.util.Date(ts.getTime())); }
            return n;
        }
    }
}
