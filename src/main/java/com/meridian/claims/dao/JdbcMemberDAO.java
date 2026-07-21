package com.meridian.claims.dao;

import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.util.LogMaskUtil;
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
public class JdbcMemberDAO extends BaseDAO implements MemberDAO {

    private static final Logger LOG = Logger.getLogger(JdbcMemberDAO.class);

    private static final String SELECT_COLS =
        "id, member_number, first_name, last_name, dob, address, phone, email, " +
        "status, deleted_at, created_at, updated_at";

    @Override
    public Member findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM members WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new MemberRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load member id=" + id, e);
        }
    }

    @Override
    public Member findByMemberNumber(String memberNumber) {
        String sql = "SELECT " + SELECT_COLS + " FROM members WHERE member_number = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new MemberRowMapper(), memberNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByMemberNumber failed", e);
            throw new DAOException("Could not load member " + memberNumber, e);
        }
    }

    @Override
    public Page<Member> search(String query, int pageNumber, int pageSize) {
        String trimmed = query == null ? "" : query.trim();
        String like = "%" + trimmed + "%";
        // If the query looks like an ISO date (yyyy-MM-dd), also match on dob.
        Date dob = parseIsoDate(trimmed);

        String matchClause = "(first_name ILIKE ? OR last_name ILIKE ? OR member_number ILIKE ?"
            + (dob != null ? " OR dob = ?" : "") + ")";
        String countSql = "SELECT COUNT(*) FROM members WHERE deleted_at IS NULL AND " + matchClause;
        String dataSql = "SELECT " + SELECT_COLS + " FROM members WHERE deleted_at IS NULL AND "
            + matchClause + " ORDER BY last_name, first_name LIMIT ? OFFSET ?";
        try {
            int offset = (pageNumber - 1) * pageSize;
            int total;
            List<Member> items;
            if (dob != null) {
                total = getJdbcTemplate().queryForObject(countSql, Integer.class, like, like, like, dob);
                items = getJdbcTemplate().query(dataSql, new MemberRowMapper(),
                    like, like, like, dob, pageSize, offset);
            } else {
                total = getJdbcTemplate().queryForObject(countSql, Integer.class, like, like, like);
                items = getJdbcTemplate().query(dataSql, new MemberRowMapper(),
                    like, like, like, pageSize, offset);
            }
            return new Page<Member>(items, pageNumber, pageSize, total);
        } catch (Exception e) {
            LOG.error("search failed query=" + query, e);
            throw new DAOException("Could not search members", e);
        }
    }

    /** Returns a java.sql.Date if the input is exactly yyyy-MM-dd, else null. */
    private Date parseIsoDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return Date.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<Member> findAll() {
        String sql = "SELECT " + SELECT_COLS + " FROM members ORDER BY last_name, first_name";
        try {
            return getJdbcTemplate().query(sql, new MemberRowMapper());
        } catch (Exception e) {
            LOG.error("findAll failed", e);
            throw new DAOException("Could not list members", e);
        }
    }

    @Override
    public List<Member> findAllActive() {
        String sql = "SELECT " + SELECT_COLS + " FROM members WHERE deleted_at IS NULL " +
            "AND status = 'ACTIVE' ORDER BY last_name, first_name";
        try {
            return getJdbcTemplate().query(sql, new MemberRowMapper());
        } catch (Exception e) {
            LOG.error("findAllActive failed", e);
            throw new DAOException("Could not list active members", e);
        }
    }

    @Override
    public void insert(Member member) {
        String sql = "INSERT INTO members " +
            "(member_number, first_name, last_name, dob, address, phone, email, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final Member m = member;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, m.getMemberNumber());
                ps.setString(2, m.getFirstName());
                ps.setString(3, m.getLastName());
                ps.setDate(4, m.getDob() == null ? null : new Date(m.getDob().getTime()));
                ps.setString(5, m.getAddress());
                ps.setString(6, m.getPhone());
                ps.setString(7, m.getEmail());
                ps.setString(8, m.getStatus() == null ? "ACTIVE" : m.getStatus().name());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                member.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert failed member=" + LogMaskUtil.maskMemberNumber(member.getMemberNumber()), e);
            throw new DAOException("Could not insert member", e);
        }
    }

    @Override
    public void update(Member member) {
        String sql = "UPDATE members SET first_name = ?, last_name = ?, dob = ?, " +
            "address = ?, phone = ?, email = ?, status = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                member.getFirstName(),
                member.getLastName(),
                member.getDob() == null ? null : new Date(member.getDob().getTime()),
                member.getAddress(),
                member.getPhone(),
                member.getEmail(),
                member.getStatus().name(),
                member.getId());
        } catch (Exception e) {
            LOG.error("update failed id=" + member.getId(), e);
            throw new DAOException("Could not update member id=" + member.getId(), e);
        }
    }

    @Override
    public void softDelete(int id) {
        try {
            getJdbcTemplate().update(
                "UPDATE members SET deleted_at = NOW(), status = 'INACTIVE' WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("softDelete failed id=" + id, e);
            throw new DAOException("Could not delete member id=" + id, e);
        }
    }

    @Override
    public void setStatus(int id, String status) {
        try {
            getJdbcTemplate().update("UPDATE members SET status = ? WHERE id = ?", status, id);
        } catch (Exception e) {
            LOG.error("setStatus failed id=" + id, e);
            throw new DAOException("Could not set status for member id=" + id, e);
        }
    }

    private static final class MemberRowMapper implements RowMapper<Member> {
        @Override
        public Member mapRow(ResultSet rs, int rowNum) throws SQLException {
            Member m = new Member();
            m.setId(rs.getInt("id"));
            m.setMemberNumber(rs.getString("member_number"));
            m.setFirstName(rs.getString("first_name"));
            m.setLastName(rs.getString("last_name"));
            Date dob = rs.getDate("dob");
            if (dob != null) m.setDob(new java.util.Date(dob.getTime()));
            m.setAddress(rs.getString("address"));
            m.setPhone(rs.getString("phone"));
            m.setEmail(rs.getString("email"));
            m.setStatus(MemberStatus.valueOf(rs.getString("status")));
            Timestamp deletedAt = rs.getTimestamp("deleted_at");
            if (deletedAt != null) m.setDeletedAt(new java.util.Date(deletedAt.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) m.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) m.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return m;
        }
    }
}
