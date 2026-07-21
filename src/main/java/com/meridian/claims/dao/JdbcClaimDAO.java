package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.service.OptimisticLockException;
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
public class JdbcClaimDAO extends BaseDAO implements ClaimDAO {

    private static final Logger LOG = Logger.getLogger(JdbcClaimDAO.class);

    private static final String SELECT_COLS =
        "id, claim_number, member_id, provider_id, claim_type, original_claim_id, " +
        "date_of_service, submission_date, status, plan_id, coverage_order, " +
        "prior_auth_number, referral_number, cob_primary_paid, " +
        "accident_indicator, accident_type, accident_date, " +
        "denial_reason_code, notes, external_reference, assigned_to_user_id, created_by_user_id, " +
        "status_entered_at, version, created_at, updated_at";

    @Override
    public void insert(Claim claim) {
        String sql = "INSERT INTO claims " +
            "(claim_number, member_id, provider_id, claim_type, original_claim_id, " +
            "date_of_service, submission_date, status, plan_id, coverage_order, " +
            "prior_auth_number, referral_number, cob_primary_paid, " +
            "accident_indicator, accident_type, accident_date, " +
            "notes, external_reference, assigned_to_user_id, created_by_user_id, status_entered_at, version) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),0)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            final Claim c = claim;
            getJdbcTemplate().update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
                ps.setString(1, c.getClaimNumber());
                ps.setInt(2, c.getMemberId());
                ps.setInt(3, c.getProviderId());
                ps.setString(4, c.getClaimType().name());
                if (c.getOriginalClaimId() != null) {
                    ps.setInt(5, c.getOriginalClaimId());
                } else {
                    ps.setNull(5, java.sql.Types.INTEGER);
                }
                ps.setDate(6, new Date(c.getDateOfService().getTime()));
                ps.setDate(7, new Date(c.getSubmissionDate().getTime()));
                ps.setString(8, c.getStatus().name());
                ps.setInt(9, c.getPlanId());
                ps.setString(10, c.getCoverageOrder());
                ps.setString(11, c.getPriorAuthNumber());
                ps.setString(12, c.getReferralNumber());
                ps.setBigDecimal(13, c.getCobPrimaryPaid());
                ps.setBoolean(14, c.isAccidentIndicator());
                ps.setString(15, c.getAccidentType());
                if (c.getAccidentDate() != null) {
                    ps.setDate(16, new Date(c.getAccidentDate().getTime()));
                } else {
                    ps.setNull(16, java.sql.Types.DATE);
                }
                ps.setString(17, c.getNotes());
                if (c.getExternalReference() != null) {
                    ps.setString(18, c.getExternalReference());
                } else {
                    ps.setNull(18, java.sql.Types.VARCHAR);
                }
                if (c.getAssignedToUserId() != null) {
                    ps.setInt(19, c.getAssignedToUserId());
                } else {
                    ps.setNull(19, java.sql.Types.INTEGER);
                }
                if (c.getCreatedByUserId() != null) {
                    ps.setInt(20, c.getCreatedByUserId());
                } else {
                    ps.setNull(20, java.sql.Types.INTEGER);
                }
                return ps;
            }, keyHolder);
            claim.setId(keyHolder.getKey().intValue());
            claim.setVersion(0);
        } catch (Exception e) {
            LOG.error("insert claim failed claimNumber=" + claim.getClaimNumber(), e);
            throw new DAOException("Could not insert claim", e);
        }
    }

    @Override
    public Claim findById(int id) {
        String sql = "SELECT " + SELECT_COLS + " FROM claims WHERE id = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ClaimRowMapper(), id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findById failed id=" + id, e);
            throw new DAOException("Could not load claim id=" + id, e);
        }
    }

    @Override
    public Claim findByClaimNumber(String claimNumber) {
        String sql = "SELECT " + SELECT_COLS + " FROM claims WHERE claim_number = ?";
        try {
            return getJdbcTemplate().queryForObject(sql, new ClaimRowMapper(), claimNumber);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            LOG.error("findByClaimNumber failed claimNumber=" + claimNumber, e);
            throw new DAOException("Could not load claim " + claimNumber, e);
        }
    }

    @Override
    public Claim findByMemberAndDOS(int memberId, int providerId, java.util.Date dos) {
        String sql = "SELECT " + SELECT_COLS +
            " FROM claims WHERE member_id = ? AND provider_id = ? AND date_of_service = ?" +
            " AND claim_type = 'ORIGINAL' AND status NOT IN ('VOIDED','REPLACED','ABANDONED')" +
            " ORDER BY created_at ASC LIMIT 1";
        try {
            List<Claim> results = getJdbcTemplate().query(sql, new ClaimRowMapper(),
                memberId, providerId, new Date(dos.getTime()));
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            LOG.error("findByMemberAndDOS failed memberId=" + memberId, e);
            throw new DAOException("Could not check for duplicate claim", e);
        }
    }

    @Override
    public List<Claim> findByMemberId(int memberId) {
        String sql = "SELECT " + SELECT_COLS + " FROM claims WHERE member_id = ? ORDER BY date_of_service DESC";
        try {
            return getJdbcTemplate().query(sql, new ClaimRowMapper(), memberId);
        } catch (Exception e) {
            LOG.error("findByMemberId failed memberId=" + memberId, e);
            throw new DAOException("Could not load claims for member " + memberId, e);
        }
    }

    @Override
    public Page<Claim> search(String query, String status, int page, int size) {
        String trimmed = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (!trimmed.isEmpty()) {
            where.append(" AND (c.claim_number ILIKE ? OR m.first_name ILIKE ? OR m.last_name ILIKE ?)");
        }
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND c.status = ?");
        }
        String countSqlFinal = "SELECT COUNT(*) FROM claims c JOIN members m ON c.member_id = m.id " + where;
        // Qualify every column explicitly to avoid ambiguity (id, created_at, updated_at exist in both tables)
        String dataSqlFinal = "SELECT c.id, c.claim_number, c.member_id, c.provider_id, c.claim_type, " +
            "c.original_claim_id, c.date_of_service, c.submission_date, c.status, c.plan_id, c.coverage_order, " +
            "c.prior_auth_number, c.referral_number, c.cob_primary_paid, " +
            "c.accident_indicator, c.accident_type, c.accident_date, " +
            "c.denial_reason_code, c.notes, c.external_reference, c.assigned_to_user_id, c.created_by_user_id, " +
            "c.status_entered_at, c.version, c.created_at, c.updated_at " +
            "FROM claims c JOIN members m ON c.member_id = m.id " + where +
            " ORDER BY c.created_at DESC LIMIT ? OFFSET ?";
        try {
            int offset = (page - 1) * size;
            Object[] baseParams = buildSearchParams(trimmed, status);
            Object[] dataParams = appendPaging(baseParams, size, offset);
            int total = getJdbcTemplate().queryForObject(countSqlFinal, Integer.class, baseParams);
            List<Claim> items = getJdbcTemplate().query(dataSqlFinal, new ClaimRowMapper(), dataParams);
            return new Page<Claim>(items, page, size, total);
        } catch (Exception e) {
            LOG.error("search failed query=" + query + " status=" + status, e);
            throw new DAOException("Could not search claims", e);
        }
    }

    private Object[] buildSearchParams(String trimmed, String status) {
        String like = "%" + trimmed + "%";
        boolean hasQuery = !trimmed.isEmpty();
        boolean hasStatus = status != null && !status.trim().isEmpty();
        if (hasQuery && hasStatus) {
            return new Object[]{like, like, like, status};
        } else if (hasQuery) {
            return new Object[]{like, like, like};
        } else if (hasStatus) {
            return new Object[]{status};
        } else {
            return new Object[]{};
        }
    }

    private Object[] appendPaging(Object[] base, int size, int offset) {
        Object[] result = new Object[base.length + 2];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = size;
        result[base.length + 1] = offset;
        return result;
    }

    @Override
    public Page<Claim> searchWorklist(String query, String status, Integer assignedToUserId,
                                      boolean unassignedOnly, boolean slaBreachedOnly,
                                      Integer scopeToUserId, int page, int size) {
        String trimmed = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (!trimmed.isEmpty()) {
            where.append(" AND (c.claim_number ILIKE ? OR m.first_name ILIKE ? OR m.last_name ILIKE ?)");
        }
        if (status != null && !status.trim().isEmpty()) {
            where.append(" AND c.status = ?");
        }
        if (assignedToUserId != null) {
            where.append(" AND c.assigned_to_user_id = ?");
        }
        if (unassignedOnly) {
            where.append(" AND c.assigned_to_user_id IS NULL");
        }
        if (slaBreachedOnly) {
            where.append(" AND EXISTS (SELECT 1 FROM sla_breaches sb WHERE sb.claim_id = c.id AND sb.status = c.status)");
        }
        // HIPAA minimum-necessary: non-reviewer/admin callers see only their own claims.
        if (scopeToUserId != null) {
            where.append(" AND (c.created_by_user_id = ? OR c.assigned_to_user_id = ?)");
        }
        String cols = "c.id, c.claim_number, c.member_id, c.provider_id, c.claim_type, " +
            "c.original_claim_id, c.date_of_service, c.submission_date, c.status, c.plan_id, c.coverage_order, " +
            "c.prior_auth_number, c.referral_number, c.cob_primary_paid, " +
            "c.accident_indicator, c.accident_type, c.accident_date, " +
            "c.denial_reason_code, c.notes, c.external_reference, c.assigned_to_user_id, c.created_by_user_id, " +
            "c.status_entered_at, c.version, c.created_at, c.updated_at";
        String from = " FROM claims c JOIN members m ON c.member_id = m.id ";
        String countSql = "SELECT COUNT(*) " + from + where;
        String dataSql = "SELECT " + cols + from + where + " ORDER BY c.created_at DESC LIMIT ? OFFSET ?";
        try {
            int offset = (page - 1) * size;
            java.util.List<Object> baseList = new java.util.ArrayList<Object>();
            if (!trimmed.isEmpty()) {
                String like = "%" + trimmed + "%";
                baseList.add(like);
                baseList.add(like);
                baseList.add(like);
            }
            if (status != null && !status.trim().isEmpty()) {
                baseList.add(status);
            }
            if (assignedToUserId != null) {
                baseList.add(assignedToUserId);
            }
            if (scopeToUserId != null) {
                baseList.add(scopeToUserId);
                baseList.add(scopeToUserId);
            }
            Object[] base = baseList.toArray();
            Object[] data = appendPaging(base, size, offset);
            int total = getJdbcTemplate().queryForObject(countSql, Integer.class, base);
            List<Claim> items = getJdbcTemplate().query(dataSql, new ClaimRowMapper(), data);
            return new Page<Claim>(items, page, size, total);
        } catch (Exception e) {
            LOG.error("searchWorklist failed", e);
            throw new DAOException("Could not search claims worklist", e);
        }
    }

    @Override
    public void updateAssignment(int id, Integer reviewerId, int currentVersion) {
        String sql = "UPDATE claims SET assigned_to_user_id = ?, version = version + 1 WHERE id = ? AND version = ?";
        try {
            int rows = getJdbcTemplate().update(sql, reviewerId, id, currentVersion);
            if (rows == 0) {
                throw new OptimisticLockException("Claim id=" + id + " was modified concurrently. Please refresh.");
            }
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("updateAssignment failed id=" + id, e);
            throw new DAOException("Could not update claim assignment id=" + id, e);
        }
    }

    @Override
    public void update(Claim claim) {
        String sql = "UPDATE claims SET status = ?, denial_reason_code = ?, notes = ?, " +
            "assigned_to_user_id = ?, status_entered_at = ?, version = version + 1 " +
            "WHERE id = ? AND version = ?";
        try {
            int rows = getJdbcTemplate().update(sql,
                claim.getStatus().name(),
                claim.getDenialReasonCode(),
                claim.getNotes(),
                claim.getAssignedToUserId(),
                new Timestamp(System.currentTimeMillis()),
                claim.getId(),
                claim.getVersion());
            if (rows == 0) {
                throw new OptimisticLockException("Claim id=" + claim.getId() + " was modified by another user. Please refresh and try again.");
            }
            claim.setVersion(claim.getVersion() + 1);
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("update claim failed id=" + claim.getId(), e);
            throw new DAOException("Could not update claim id=" + claim.getId(), e);
        }
    }

    @Override
    public void updateStatus(int id, String newStatus, int currentVersion) {
        String sql = "UPDATE claims SET status = ?, status_entered_at = NOW(), version = version + 1 " +
            "WHERE id = ? AND version = ?";
        try {
            int rows = getJdbcTemplate().update(sql, newStatus, id, currentVersion);
            if (rows == 0) {
                throw new OptimisticLockException("Claim id=" + id + " was modified concurrently. Please refresh.");
            }
        } catch (OptimisticLockException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("updateStatus failed id=" + id, e);
            throw new DAOException("Could not update claim status id=" + id, e);
        }
    }

    private static final class ClaimRowMapper implements RowMapper<Claim> {
        @Override
        public Claim mapRow(ResultSet rs, int rowNum) throws SQLException {
            Claim c = new Claim();
            c.setId(rs.getInt("id"));
            c.setClaimNumber(rs.getString("claim_number"));
            c.setMemberId(rs.getInt("member_id"));
            c.setProviderId(rs.getInt("provider_id"));
            c.setClaimType(ClaimType.valueOf(rs.getString("claim_type")));
            int origId = rs.getInt("original_claim_id");
            if (!rs.wasNull()) {
                c.setOriginalClaimId(origId);
            }
            c.setDateOfService(rs.getDate("date_of_service"));
            c.setSubmissionDate(rs.getDate("submission_date"));
            c.setStatus(ClaimStatus.valueOf(rs.getString("status")));
            c.setPlanId(rs.getInt("plan_id"));
            c.setCoverageOrder(rs.getString("coverage_order"));
            c.setPriorAuthNumber(rs.getString("prior_auth_number"));
            c.setReferralNumber(rs.getString("referral_number"));
            c.setCobPrimaryPaid(rs.getBigDecimal("cob_primary_paid"));
            c.setAccidentIndicator(rs.getBoolean("accident_indicator"));
            c.setAccidentType(rs.getString("accident_type"));
            c.setAccidentDate(rs.getDate("accident_date"));
            c.setDenialReasonCode(rs.getString("denial_reason_code"));
            c.setNotes(rs.getString("notes"));
            c.setExternalReference(rs.getString("external_reference"));
            int assignedTo = rs.getInt("assigned_to_user_id");
            if (!rs.wasNull()) { c.setAssignedToUserId(assignedTo); }
            int createdBy = rs.getInt("created_by_user_id");
            if (!rs.wasNull()) { c.setCreatedByUserId(createdBy); }
            Timestamp sat = rs.getTimestamp("status_entered_at");
            if (sat != null) { c.setStatusEnteredAt(new java.util.Date(sat.getTime())); }
            c.setVersion(rs.getInt("version"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) { c.setCreatedAt(new java.util.Date(createdAt.getTime())); }
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) { c.setUpdatedAt(new java.util.Date(updatedAt.getTime())); }
            return c;
        }
    }
}
