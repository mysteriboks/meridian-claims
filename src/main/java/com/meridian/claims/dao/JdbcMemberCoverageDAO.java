package com.meridian.claims.dao;

import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.MemberCoverage;
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
public class JdbcMemberCoverageDAO extends BaseDAO implements MemberCoverageDAO {

    private static final Logger LOG = Logger.getLogger(JdbcMemberCoverageDAO.class);

    private static final String SELECT_COLS =
        "mc.id, mc.member_id, mc.plan_id, p.plan_name, mc.coverage_order, " +
        "mc.effective_date, mc.termination_date, mc.created_at, mc.updated_at";

    private static final String BASE_FROM =
        " FROM member_coverage mc JOIN plans p ON p.id = mc.plan_id ";

    @Override
    public MemberCoverage findById(int id) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM + " WHERE mc.id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new CoverageRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load coverage id=" + id, e);
        }
    }

    @Override
    public List<MemberCoverage> findByMemberId(int memberId) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE mc.member_id = ? ORDER BY mc.coverage_order, mc.effective_date";
        try {
            return getJdbcTemplate().query(sql, new CoverageRowMapper(), memberId);
        } catch (Exception e) {
            LOG.error("findByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not load coverage for member id=" + memberId, e);
        }
    }

    @Override
    public List<MemberCoverage> findActiveByMemberId(int memberId) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE mc.member_id = ? AND (mc.termination_date IS NULL OR mc.termination_date >= CURRENT_DATE)" +
            " ORDER BY mc.coverage_order, mc.effective_date";
        try {
            return getJdbcTemplate().query(sql, new CoverageRowMapper(), memberId);
        } catch (Exception e) {
            LOG.error("findActiveByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not load active coverage for member id=" + memberId, e);
        }
    }

    @Override
    public MemberCoverage findByMemberAndDate(int memberId, String coverageOrder, java.util.Date dateOfService) {
        String sql = "SELECT " + SELECT_COLS + BASE_FROM +
            " WHERE mc.member_id = ? AND mc.coverage_order = ?" +
            " AND mc.effective_date <= ? AND (mc.termination_date IS NULL OR mc.termination_date >= ?)";
        Date dos = new Date(dateOfService.getTime());
        try {
            return getJdbcTemplate().queryForObject(sql, new CoverageRowMapper(), memberId, coverageOrder, dos, dos);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByMemberAndDate failed memberId=" + memberId, e);
            throw new DAOException("Could not find coverage for member id=" + memberId, e);
        }
    }

    @Override
    public void insert(MemberCoverage coverage) {
        String sql = "INSERT INTO member_coverage " +
            "(member_id, plan_id, coverage_order, effective_date, termination_date) " +
            "VALUES (?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final MemberCoverage c = coverage;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setInt(1, c.getMemberId());
                ps.setInt(2, c.getPlanId());
                ps.setString(3, c.getCoverageOrder().name());
                ps.setDate(4, new Date(c.getEffectiveDate().getTime()));
                ps.setDate(5, c.getTerminationDate() == null ? null : new Date(c.getTerminationDate().getTime()));
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                coverage.setId(keyHolder.getKey().intValue());
            }
        } catch (Exception e) {
            LOG.error("insert coverage failed memberId=" + coverage.getMemberId(), e);
            throw new DAOException("Could not insert coverage", e);
        }
    }

    @Override
    public void update(MemberCoverage coverage) {
        String sql = "UPDATE member_coverage SET plan_id = ?, coverage_order = ?, " +
            "effective_date = ?, termination_date = ? WHERE id = ?";
        try {
            getJdbcTemplate().update(sql,
                coverage.getPlanId(),
                coverage.getCoverageOrder().name(),
                new Date(coverage.getEffectiveDate().getTime()),
                coverage.getTerminationDate() == null ? null : new Date(coverage.getTerminationDate().getTime()),
                coverage.getId());
        } catch (Exception e) {
            LOG.error("update coverage failed id=" + coverage.getId(), e);
            throw new DAOException("Could not update coverage id=" + coverage.getId(), e);
        }
    }

    @Override
    public void delete(int id) {
        try {
            getJdbcTemplate().update("DELETE FROM member_coverage WHERE id = ?", id);
        } catch (Exception e) {
            LOG.error("delete coverage failed id=" + id, e);
            throw new DAOException("Could not delete coverage id=" + id, e);
        }
    }

    private static final class CoverageRowMapper implements RowMapper<MemberCoverage> {
        @Override
        public MemberCoverage mapRow(ResultSet rs, int rowNum) throws SQLException {
            MemberCoverage c = new MemberCoverage();
            c.setId(rs.getInt("id"));
            c.setMemberId(rs.getInt("member_id"));
            c.setPlanId(rs.getInt("plan_id"));
            c.setPlanName(rs.getString("plan_name"));
            c.setCoverageOrder(CoverageOrder.valueOf(rs.getString("coverage_order")));
            Date eff = rs.getDate("effective_date");
            if (eff != null) c.setEffectiveDate(new java.util.Date(eff.getTime()));
            Date term = rs.getDate("termination_date");
            if (term != null) c.setTerminationDate(new java.util.Date(term.getTime()));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) c.setCreatedAt(new java.util.Date(createdAt.getTime()));
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) c.setUpdatedAt(new java.util.Date(updatedAt.getTime()));
            return c;
        }
    }
}
