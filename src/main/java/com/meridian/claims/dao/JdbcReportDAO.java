package com.meridian.claims.dao;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting queries. All use parameterised SQL; no string-concatenated user input.
 * Returns List&lt;Map&gt; so the service/controller can pass data straight to JSTL.
 *
 * Date-window boundaries are computed in Java and passed as bound parameters rather
 * than expressed with database-specific date arithmetic (INTERVAL, DATE_TRUNC), so
 * the same SQL runs on both PostgreSQL (prod) and H2 (tests).
 */
@Repository
public class JdbcReportDAO extends BaseDAO implements ReportDAO {

    /** Today minus N days, as a java.sql.Date for binding. */
    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return new Date(cal.getTimeInMillis());
    }

    /** First day of the current month, midnight. */
    private static Date firstOfThisMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(cal);
        return new Date(cal.getTimeInMillis());
    }

    /** First day of last month, midnight. */
    private static Date firstOfLastMonth() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.add(Calendar.MONTH, -1);
        zeroTime(cal);
        return new Date(cal.getTimeInMillis());
    }

    private static void zeroTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static final Logger LOG = Logger.getLogger(JdbcReportDAO.class);

    // =========================================================================
    // Dashboard widgets
    // =========================================================================

    @Override
    public Map<String, Long> claimCountsByStatus() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM claims " +
            "WHERE status NOT IN ('VOIDED','REPLACED','ABANDONED') " +
            "GROUP BY status ORDER BY status";
        Map<String, Long> result = new LinkedHashMap<String, Long>();
        try {
            List<Map<String, Object>> rows = getJdbcTemplate().queryForList(sql);
            for (Map<String, Object> row : rows) {
                result.put((String) row.get("status"), toLong(row.get("cnt")));
            }
        } catch (Exception e) {
            LOG.error("claimCountsByStatus failed", e);
        }
        return result;
    }

    @Override
    public long claimsSubmittedToday() {
        try {
            Long n = getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM claims WHERE submission_date = CURRENT_DATE", Long.class);
            return n != null ? n : 0L;
        } catch (Exception e) { return 0L; }
    }

    @Override
    public long claimsSubmittedThisWeek() {
        try {
            Long n = getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM claims WHERE submission_date >= ?",
                Long.class, daysAgo(7));
            return n != null ? n : 0L;
        } catch (Exception e) { return 0L; }
    }

    @Override
    public long myQueueSize(int reviewerUserId) {
        try {
            Long n = getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM claims WHERE assigned_to_user_id = ? AND status = 'IN_REVIEW'",
                Long.class, reviewerUserId);
            return n != null ? n : 0L;
        } catch (Exception e) { return 0L; }
    }

    @Override
    public long slaBreachCount() {
        try {
            Long n = getJdbcTemplate().queryForObject(
                "SELECT COUNT(DISTINCT claim_id) FROM sla_breaches", Long.class);
            return n != null ? n : 0L;
        } catch (Exception e) { return 0L; }
    }

    @Override
    public List<Map<String, Object>> recentActivity(int limit) {
        String sql = "SELECT ca.claim_id, c.claim_number, ca.event_type, ca.old_status, " +
            "ca.new_status, ca.changed_by_user_id, ca.changed_at " +
            "FROM claim_audit ca JOIN claims c ON ca.claim_id = c.id " +
            "ORDER BY ca.changed_at DESC LIMIT ?";
        try {
            return getJdbcTemplate().queryForList(sql, limit);
        } catch (Exception e) {
            LOG.error("recentActivity failed", e);
            return new ArrayList<Map<String, Object>>();
        }
    }

    @Override
    public List<Map<String, Object>> topDenialReasons(int days, int topN) {
        String sql = "SELECT c.denial_reason_code, d.description, COUNT(*) AS cnt " +
            "FROM claims c LEFT JOIN denial_reason_codes d ON c.denial_reason_code = d.code " +
            "WHERE c.status = 'DENIED' AND c.submission_date >= ? " +
            "AND c.denial_reason_code IS NOT NULL " +
            "GROUP BY c.denial_reason_code, d.description ORDER BY cnt DESC LIMIT ?";
        try {
            return getJdbcTemplate().queryForList(sql, daysAgo(days), topN);
        } catch (Exception e) {
            LOG.error("topDenialReasons failed", e);
            return new ArrayList<Map<String, Object>>();
        }
    }

    @Override
    public Map<String, Object> paymentTotals() {
        // Month boundaries computed in Java and bound, so the SQL is dialect-portable.
        Date thisMonthStart = firstOfThisMonth();
        Date lastMonthStart = firstOfLastMonth();
        String sql = "SELECT " +
            "SUM(CASE WHEN payment_date >= ? THEN plan_paid_total ELSE 0 END) AS this_month, " +
            "SUM(CASE WHEN payment_date >= ? AND payment_date < ? THEN plan_paid_total ELSE 0 END) AS last_month " +
            "FROM payments WHERE status = 'PAID'";
        try {
            List<Map<String, Object>> rows = getJdbcTemplate().queryForList(
                sql, thisMonthStart, lastMonthStart, thisMonthStart);
            return rows.isEmpty() ? new LinkedHashMap<String, Object>() : rows.get(0);
        } catch (Exception e) {
            LOG.error("paymentTotals failed", e);
            return new LinkedHashMap<String, Object>();
        }
    }

    // =========================================================================
    // Standard reports
    //
    // Unlike the dashboard widgets above (which return empty/zero on failure so a
    // single broken tile never blanks the whole dashboard), a report is something
    // the user explicitly requested — a failed query must surface as an error, not
    // a silently-empty table. These methods therefore rethrow DAOException.
    // =========================================================================

    @Override
    public List<Map<String, Object>> claimsSummary(java.util.Date from, java.util.Date to,
                                                    Integer planId) {
        StringBuilder sql = new StringBuilder(
            "SELECT c.status, COUNT(*) AS claim_count, " +
            "SUM(li.billed_amount) AS total_billed, SUM(li.plan_paid_amount) AS total_plan_paid " +
            "FROM claims c LEFT JOIN claim_line_items li ON c.id = li.claim_id " +
            "WHERE c.submission_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<Object>();
        params.add(new Date(from.getTime()));
        params.add(new Date(to.getTime()));
        if (planId != null) { sql.append(" AND c.plan_id = ?"); params.add(planId); }
        sql.append(" GROUP BY c.status ORDER BY c.status");
        try {
            return getJdbcTemplate().queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            LOG.error("claimsSummary failed", e);
            throw new DAOException("Could not run Claims Summary report", e);
        }
    }

    @Override
    public List<Map<String, Object>> claimsDetail(java.util.Date from, java.util.Date to,
                                                    String status, Integer planId,
                                                    Integer providerId, int page, int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT c.id, c.claim_number, c.claim_type, c.date_of_service, c.submission_date, " +
            "c.status, c.coverage_order, c.denial_reason_code, m.first_name, m.last_name, " +
            "p.name AS provider_name " +
            "FROM claims c " +
            "JOIN members m ON c.member_id = m.id " +
            "JOIN providers p ON c.provider_id = p.id " +
            "WHERE c.submission_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<Object>();
        params.add(new Date(from.getTime()));
        params.add(new Date(to.getTime()));
        if (status != null && !status.isEmpty()) { sql.append(" AND c.status = ?"); params.add(status); }
        if (planId != null) { sql.append(" AND c.plan_id = ?"); params.add(planId); }
        if (providerId != null) { sql.append(" AND c.provider_id = ?"); params.add(providerId); }
        sql.append(" ORDER BY c.submission_date DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add((page - 1) * size);
        try {
            return getJdbcTemplate().queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            LOG.error("claimsDetail failed", e);
            throw new DAOException("Could not run Claims Detail report", e);
        }
    }

    @Override
    public long claimsDetailCount(java.util.Date from, java.util.Date to, String status,
                                   Integer planId, Integer providerId) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM claims c WHERE c.submission_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<Object>();
        params.add(new Date(from.getTime()));
        params.add(new Date(to.getTime()));
        if (status != null && !status.isEmpty()) { sql.append(" AND c.status = ?"); params.add(status); }
        if (planId != null) { sql.append(" AND c.plan_id = ?"); params.add(planId); }
        if (providerId != null) { sql.append(" AND c.provider_id = ?"); params.add(providerId); }
        try {
            Long n = getJdbcTemplate().queryForObject(sql.toString(), Long.class, params.toArray());
            return n != null ? n : 0L;
        } catch (Exception e) {
            LOG.error("claimsDetailCount failed", e);
            throw new DAOException("Could not run Claims Detail report", e);
        }
    }

    @Override
    public List<Map<String, Object>> denialReport(java.util.Date from, java.util.Date to) {
        String sql = "SELECT c.denial_reason_code, d.description, d.carc_code, " +
            "COUNT(*) AS denial_count, " +
            "EXTRACT(MONTH FROM c.submission_date) AS month, " +
            "EXTRACT(YEAR FROM c.submission_date) AS year " +
            "FROM claims c LEFT JOIN denial_reason_codes d ON c.denial_reason_code = d.code " +
            "WHERE c.status = 'DENIED' AND c.submission_date BETWEEN ? AND ? " +
            "AND c.denial_reason_code IS NOT NULL " +
            "GROUP BY c.denial_reason_code, d.description, d.carc_code, " +
            "EXTRACT(MONTH FROM c.submission_date), EXTRACT(YEAR FROM c.submission_date) " +
            "ORDER BY year, month, denial_count DESC";
        try {
            return getJdbcTemplate().queryForList(sql, new Date(from.getTime()), new Date(to.getTime()));
        } catch (Exception e) {
            LOG.error("denialReport failed", e);
            throw new DAOException("Could not run Denial report", e);
        }
    }

    @Override
    public List<Map<String, Object>> paymentReport(java.util.Date from, java.util.Date to,
                                                    Integer planId, Integer providerId) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.id AS payment_id, c.claim_number, prov.name AS provider_name, prov.npi, " +
            "p.billed_total, p.allowed_total, p.plan_paid_total, p.member_responsibility, " +
            "p.status, p.payment_date, pb.id AS batch_id " +
            "FROM payments p " +
            "JOIN claims c ON p.claim_id = c.id " +
            "JOIN providers prov ON c.provider_id = prov.id " +
            "LEFT JOIN payment_batches pb ON p.batch_id = pb.id " +
            "WHERE p.payment_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<Object>();
        params.add(new Date(from.getTime()));
        params.add(new Date(to.getTime()));
        if (planId != null) { sql.append(" AND c.plan_id = ?"); params.add(planId); }
        if (providerId != null) { sql.append(" AND c.provider_id = ?"); params.add(providerId); }
        sql.append(" ORDER BY p.payment_date DESC");
        try {
            return getJdbcTemplate().queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            LOG.error("paymentReport failed", e);
            throw new DAOException("Could not run Payment report", e);
        }
    }

    @Override
    public List<Map<String, Object>> memberActivityReport(int memberId) {
        String sql = "SELECT 'CLAIM' AS rec_type, c.id AS rec_id, c.claim_number AS ref, " +
            "c.date_of_service AS event_date, c.status AS status_or_outcome, " +
            "c.denial_reason_code AS notes " +
            "FROM claims c WHERE c.member_id = ? " +
            "UNION ALL " +
            "SELECT 'PAYMENT', p.id, c.claim_number, p.payment_date, p.status, " +
            "CAST(p.plan_paid_total AS VARCHAR) " +
            "FROM payments p JOIN claims c ON p.claim_id = c.id WHERE c.member_id = ? " +
            "UNION ALL " +
            "SELECT 'APPEAL', a.id, CAST(a.claim_id AS VARCHAR), a.submitted_date, a.status, " +
            "a.outcome_notes " +
            "FROM appeals a WHERE a.member_id = ? " +
            "UNION ALL " +
            "SELECT 'EOB', e.id, CAST(e.claim_id AS VARCHAR), CAST(e.generated_at AS DATE), " +
            "e.delivery_method, NULL " +
            "FROM eob_documents e WHERE e.member_id = ? " +
            "ORDER BY event_date DESC NULLS LAST";
        try {
            return getJdbcTemplate().queryForList(sql, memberId, memberId, memberId, memberId);
        } catch (Exception e) {
            LOG.error("memberActivityReport failed memberId=" + memberId, e);
            throw new DAOException("Could not run Member Activity report", e);
        }
    }

    @Override
    public List<Map<String, Object>> providerActivityReport(int providerId) {
        String sql = "SELECT c.claim_number, c.date_of_service, c.status, " +
            "SUM(li.billed_amount) AS billed, SUM(li.plan_paid_amount) AS plan_paid, " +
            "p.payment_date, rb.id AS remit_batch_id " +
            "FROM claims c " +
            "LEFT JOIN claim_line_items li ON c.id = li.claim_id " +
            "LEFT JOIN payments p ON c.id = p.claim_id " +
            "LEFT JOIN remittance_batch_items rbi ON c.id = rbi.claim_id " +
            "LEFT JOIN remittance_batches rb ON rbi.batch_id = rb.id " +
            "WHERE c.provider_id = ? " +
            "GROUP BY c.claim_number, c.date_of_service, c.status, p.payment_date, rb.id " +
            "ORDER BY c.date_of_service DESC";
        try {
            return getJdbcTemplate().queryForList(sql, providerId);
        } catch (Exception e) {
            LOG.error("providerActivityReport failed providerId=" + providerId, e);
            throw new DAOException("Could not run Provider Activity report", e);
        }
    }

    @Override
    public List<Map<String, Object>> slaPerformanceReport(java.util.Date from, java.util.Date to) {
        String sql = "SELECT c.status, " +
            "COUNT(*) AS claim_count, " +
            "COUNT(sb.id) AS breach_count, " +
            "ROUND(100.0 * COUNT(sb.id) / NULLIF(COUNT(*), 0), 1) AS breach_pct, " +
            "c.assigned_to_user_id AS reviewer_id " +
            "FROM claims c " +
            "LEFT JOIN sla_breaches sb ON c.id = sb.claim_id AND sb.status = c.status " +
            "WHERE c.submission_date BETWEEN ? AND ? " +
            "GROUP BY c.status, c.assigned_to_user_id " +
            "ORDER BY breach_pct DESC NULLS LAST";
        try {
            return getJdbcTemplate().queryForList(sql, new Date(from.getTime()), new Date(to.getTime()));
        } catch (Exception e) {
            LOG.error("slaPerformanceReport failed", e);
            throw new DAOException("Could not run SLA Performance report", e);
        }
    }

    @Override
    public List<Map<String, Object>> adjudicationRuleReport(java.util.Date from, java.util.Date to) {
        String sql = "SELECT ar.rule_name, ar.rule_type, " +
            "COUNT(*) AS total_evaluations, " +
            "SUM(CASE WHEN ar.passed THEN 1 ELSE 0 END) AS passed_count, " +
            "SUM(CASE WHEN NOT ar.passed THEN 1 ELSE 0 END) AS failed_count, " +
            "ROUND(100.0 * SUM(CASE WHEN NOT ar.passed THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 1) AS deny_pct " +
            "FROM adjudication_results ar " +
            "JOIN claims c ON ar.claim_id = c.id " +
            "WHERE c.submission_date BETWEEN ? AND ? " +
            "GROUP BY ar.rule_name, ar.rule_type ORDER BY ar.rule_name";
        try {
            return getJdbcTemplate().queryForList(sql, new Date(from.getTime()), new Date(to.getTime()));
        } catch (Exception e) {
            LOG.error("adjudicationRuleReport failed", e);
            throw new DAOException("Could not run Adjudication Rule report", e);
        }
    }

    @Override
    public List<Map<String, Object>> appealsReport(java.util.Date from, java.util.Date to) {
        // Pull raw rows; aggregate counts and average days-to-resolve in Java so we avoid
        // database-specific date-subtraction semantics (PostgreSQL date−date = integer days;
        // H2 treats it as an INTERVAL). Grouping key: appeal_type | status | outcome.
        String sql = "SELECT a.appeal_type, a.status, a.outcome, a.submitted_date, a.resolved_date " +
            "FROM appeals a WHERE a.submitted_date BETWEEN ? AND ?";
        try {
            List<Map<String, Object>> raw = getJdbcTemplate().queryForList(
                sql, new Date(from.getTime()), new Date(to.getTime()));

            // group -> [count, sumDays, resolvedCount]
            LinkedHashMap<String, long[]> agg = new LinkedHashMap<String, long[]>();
            LinkedHashMap<String, Map<String, Object>> keyParts = new LinkedHashMap<String, Map<String, Object>>();
            for (Map<String, Object> row : raw) {
                String type = str(row.get("appeal_type"));
                String status = str(row.get("status"));
                String outcome = str(row.get("outcome"));
                String key = type + "|" + status + "|" + outcome;
                long[] a = agg.get(key);
                if (a == null) {
                    a = new long[]{0, 0, 0};
                    agg.put(key, a);
                    Map<String, Object> kp = new LinkedHashMap<String, Object>();
                    kp.put("appeal_type", type);
                    kp.put("status", status);
                    kp.put("outcome", outcome);
                    keyParts.put(key, kp);
                }
                a[0]++; // count
                java.util.Date submitted = (java.util.Date) row.get("submitted_date");
                java.util.Date resolved = (java.util.Date) row.get("resolved_date");
                if (submitted != null && resolved != null) {
                    long days = (resolved.getTime() - submitted.getTime()) / (1000L * 60 * 60 * 24);
                    a[1] += days;
                    a[2]++;
                }
            }

            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            for (Map.Entry<String, long[]> e : agg.entrySet()) {
                long[] a = e.getValue();
                Map<String, Object> out = keyParts.get(e.getKey());
                out.put("appeal_count", a[0]);
                out.put("avg_days_to_resolve", a[2] > 0
                    ? Math.round((double) a[1] / a[2] * 10.0) / 10.0
                    : null);
                result.add(out);
            }
            // Sort by appeal_count descending (mirrors prior ORDER BY)
            result.sort((x, y) -> Long.compare(
                ((Number) y.get("appeal_count")).longValue(),
                ((Number) x.get("appeal_count")).longValue()));
            return result;
        } catch (Exception e) {
            LOG.error("appealsReport failed", e);
            throw new DAOException("Could not run Appeals report", e);
        }
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    @Override
    public List<Map<String, Object>> cobReport(java.util.Date from, java.util.Date to) {
        String sql = "SELECT c.claim_number, c.date_of_service, c.coverage_order, " +
            "c.cob_primary_paid, " +
            "SUM(li.plan_paid_amount) AS secondary_plan_paid, " +
            "SUM(li.member_responsibility) AS member_resp, " +
            "SUM(li.allowed_amount) AS total_allowed " +
            "FROM claims c " +
            "JOIN claim_line_items li ON c.id = li.claim_id " +
            "WHERE c.coverage_order = 'SECONDARY' AND c.submission_date BETWEEN ? AND ? " +
            "GROUP BY c.claim_number, c.date_of_service, c.coverage_order, c.cob_primary_paid " +
            "ORDER BY c.date_of_service DESC";
        try {
            return getJdbcTemplate().queryForList(sql, new Date(from.getTime()), new Date(to.getTime()));
        } catch (Exception e) {
            LOG.error("cobReport failed", e);
            throw new DAOException("Could not run COB report", e);
        }
    }

    @Override
    public List<Map<String, Object>> subrogationReport() {
        String sql = "SELECT sc.id, sc.opened_date, sc.status, sc.liable_party, " +
            "sc.recovery_amount, sc.notes, " +
            "c.claim_number, c.date_of_service, " +
            "SUM(li.plan_paid_amount) AS plan_paid_on_claim " +
            "FROM subrogation_cases sc " +
            "JOIN claims c ON sc.claim_id = c.id " +
            "LEFT JOIN claim_line_items li ON c.id = li.claim_id " +
            "GROUP BY sc.id, sc.opened_date, sc.status, sc.liable_party, sc.recovery_amount, " +
            "sc.notes, c.claim_number, c.date_of_service " +
            "ORDER BY sc.opened_date DESC";
        try {
            return getJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            LOG.error("subrogationReport failed", e);
            throw new DAOException("Could not run Subrogation report", e);
        }
    }

    @Override
    public List<Map<String, Object>> feeScheduleCoverageReport() {
        String sql = "SELECT li.procedure_code, COUNT(*) AS no_rate_count, " +
            "MAX(c.date_of_service) AS latest_dos " +
            "FROM claim_line_items li " +
            "JOIN claims c ON li.claim_id = c.id " +
            "WHERE li.rate_source = 'NO_RATE' " +
            "GROUP BY li.procedure_code ORDER BY no_rate_count DESC";
        try {
            return getJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            LOG.error("feeScheduleCoverageReport failed", e);
            throw new DAOException("Could not run Fee Schedule Coverage report", e);
        }
    }

    private long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        return 0L;
    }
}
