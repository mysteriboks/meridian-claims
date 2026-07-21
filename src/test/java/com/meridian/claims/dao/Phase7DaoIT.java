package com.meridian.claims.dao;

import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 7 reporting integration tests against H2 (PostgreSQL mode).
 *
 * Purpose: prove that every one of the 20 ReportDAO query statements actually
 * EXECUTES against a database. The unit-level ReportServiceTest mocks the DAO,
 * so the hand-written SQL (EXTRACT, INTERVAL arithmetic, DATE_TRUNC, NULLS LAST,
 * date subtraction, multi-branch UNION ALL) is otherwise never run until prod.
 *
 * CAVEAT: H2-in-PostgreSQL-mode is not a perfect PostgreSQL oracle — it can
 * accept or reject dialect edge cases differently. A manual verification against
 * real PostgreSQL (load samples/sample_data.sql, open each /reports page) is documented
 * in docs/operations.md and remains the authoritative dialect check.
 */
public class Phase7DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcReportDAO reportDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_p7_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=0");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        reportDAO = new JdbcReportDAO();
        reportDAO.setJdbcTemplate(jdbc);

        seedFixture();
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) {
            new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
            dataSource.close();
        }
    }

    /**
     * Seeds one coherent end-to-end scenario: a user, member, provider, plan,
     * two claims (one PAID with a payment + appeal + subrogation + sla_breach +
     * adjudication results + audit; one DENIED), line items, an EOB.
     */
    private void seedFixture() {
        jdbc.update("INSERT INTO users (id, username, full_name, password_hash, role, active) " +
            "VALUES (1, 'rev', 'Rev Iewer', 'h', 'REVIEWER', TRUE)");
        jdbc.update("INSERT INTO members (id, member_number, first_name, last_name, dob, status) " +
            "VALUES (1, 'M-1', 'Jane', 'Doe', DATE '1980-01-01', 'ACTIVE')");
        jdbc.update("INSERT INTO providers (id, npi, name, provider_type, network_status) " +
            "VALUES (1, 'NPI0000001', 'Clinic', 'INDIVIDUAL', 'IN_NETWORK')");
        jdbc.update("INSERT INTO plans (id, plan_name, plan_type, deductible_amount, oop_max, copay_amount, " +
            "coverage_pct_in_network, coverage_pct_out_network, benefit_year_start, timely_filing_days) " +
            "VALUES (1, 'Gold', 'PPO', 500.00, 3000.00, 30.00, 80.00, 60.00, DATE '2026-01-01', 180)");

        // Claim 1 — PAID, secondary coverage (exercises COB), accident (subrogation)
        jdbc.update("INSERT INTO claims (id, claim_number, member_id, provider_id, claim_type, " +
            "date_of_service, submission_date, status, plan_id, coverage_order, cob_primary_paid, " +
            "accident_indicator, assigned_to_user_id, status_entered_at, version) " +
            "VALUES (1, 'CLM-1', 1, 1, 'ORIGINAL', DATE '2026-02-15', CURRENT_DATE, 'PAID', 1, " +
            "'SECONDARY', 50.00, TRUE, 1, NOW(), 0)");
        // Claim 2 — DENIED (exercises denial report)
        jdbc.update("INSERT INTO claims (id, claim_number, member_id, provider_id, claim_type, " +
            "date_of_service, submission_date, status, plan_id, coverage_order, denial_reason_code, " +
            "status_entered_at, version) " +
            "VALUES (2, 'CLM-2', 1, 1, 'ORIGINAL', DATE '2026-02-20', CURRENT_DATE, 'DENIED', 1, " +
            "'PRIMARY', 'TIMELY_FILING', NOW(), 0)");

        jdbc.update("INSERT INTO claim_line_items (claim_id, procedure_code, billed_amount, allowed_amount, " +
            "plan_paid_amount, member_responsibility, deductible_applied, copay_applied, rate_source) " +
            "VALUES (1, '99213', 110.00, 85.00, 52.00, 33.00, 0.00, 30.00, 'PLAN_WIDE')");
        // A NO_RATE line (exercises fee-schedule-coverage report)
        jdbc.update("INSERT INTO claim_line_items (claim_id, procedure_code, billed_amount, allowed_amount, " +
            "plan_paid_amount, member_responsibility, deductible_applied, copay_applied, rate_source) " +
            "VALUES (2, '99999', 200.00, NULL, NULL, NULL, 0.00, 0.00, 'NO_RATE')");

        jdbc.update("INSERT INTO payments (claim_id, billed_total, allowed_total, plan_paid_total, " +
            "member_responsibility, status, payment_date) " +
            "VALUES (1, 110.00, 85.00, 52.00, 33.00, 'PAID', CURRENT_DATE)");

        jdbc.update("INSERT INTO eob_documents (claim_id, member_id, content, delivery_method) " +
            "VALUES (1, 1, '<html>eob</html>', 'MAILED')");

        // Appeal RESOLVED (exercises appeals report avg-days-to-resolve date math)
        jdbc.update("INSERT INTO appeals (claim_id, member_id, appeal_type, submitted_date, deadline_date, " +
            "status, outcome, resolved_date) " +
            "VALUES (2, 1, 'INTERNAL', DATE '2026-03-01', DATE '2026-03-31', 'DENIED', 'DENIED', DATE '2026-03-10')");

        jdbc.update("INSERT INTO subrogation_cases (claim_id, opened_date, status, liable_party, recovery_amount) " +
            "VALUES (1, CURRENT_DATE, 'RECOVERED', 'StateAuto', 5000.00)");

        jdbc.update("INSERT INTO sla_breaches (claim_id, status, expected_by) " +
            "VALUES (2, 'DENIED', NOW())");

        jdbc.update("INSERT INTO adjudication_results (claim_id, run_id, step_number, rule_name, rule_type, passed, reason) " +
            "VALUES (2, 1, 1, 'TimelyFilingRule', 'HARD', FALSE, 'past window')");
        jdbc.update("INSERT INTO adjudication_results (claim_id, run_id, step_number, rule_name, rule_type, passed, reason) " +
            "VALUES (1, 1, 1, 'TimelyFilingRule', 'HARD', TRUE, 'ok')");

        jdbc.update("INSERT INTO claim_audit (claim_id, event_type, old_status, new_status, changed_by_user_id) " +
            "VALUES (1, 'APPROVED', 'IN_REVIEW', 'APPROVED', 1)");
    }

    // -------------------------------------------------------------------------
    // Dashboard widgets — must all execute
    // -------------------------------------------------------------------------

    @Test
    public void dashboardWidgets_allExecute() {
        assertNotNull(reportDAO.claimCountsByStatus());
        assertTrue(reportDAO.claimCountsByStatus().containsKey("PAID"));
        reportDAO.claimsSubmittedToday();
        reportDAO.claimsSubmittedThisWeek();
        assertTrue(reportDAO.myQueueSize(1) >= 0);
        assertTrue(reportDAO.slaBreachCount() >= 1);
        assertNotNull(reportDAO.recentActivity(20));
        assertNotNull(reportDAO.topDenialReasons(30, 5));
        assertNotNull(reportDAO.paymentTotals());
    }

    // -------------------------------------------------------------------------
    // Reports — each must execute and return rows for the seeded data
    // -------------------------------------------------------------------------

    @Test
    public void claimsSummary_executes() {
        List<Map<String, Object>> rows = reportDAO.claimsSummary(d("2026-01-01"), d("2026-12-31"), null);
        assertFalse(rows.isEmpty());
    }

    @Test
    public void claimsDetail_executesAndCounts() {
        List<Map<String, Object>> rows = reportDAO.claimsDetail(d("2026-01-01"), d("2026-12-31"), null, null, null, 1, 50);
        assertEquals2(rows.size());
        assertTrue(reportDAO.claimsDetailCount(d("2026-01-01"), d("2026-12-31"), null, null, null) >= 2);
    }

    @Test
    public void denialReport_executes() {
        List<Map<String, Object>> rows = reportDAO.denialReport(d("2026-01-01"), d("2026-12-31"));
        assertFalse(rows.isEmpty()); // CLM-2 is DENIED with TIMELY_FILING
    }

    @Test
    public void paymentReport_executes() {
        List<Map<String, Object>> rows = reportDAO.paymentReport(d("2026-01-01"), d("2026-12-31"), null, null);
        assertFalse(rows.isEmpty());
    }

    @Test
    public void memberActivityReport_executes() {
        // UNION ALL across CLAIM/PAYMENT/APPEAL/EOB — must not throw on type mismatch
        List<Map<String, Object>> rows = reportDAO.memberActivityReport(1);
        assertFalse(rows.isEmpty());
    }

    @Test
    public void providerActivityReport_executes() {
        List<Map<String, Object>> rows = reportDAO.providerActivityReport(1);
        assertFalse(rows.isEmpty());
    }

    @Test
    public void slaPerformanceReport_executes() {
        assertNotNull(reportDAO.slaPerformanceReport(d("2026-01-01"), d("2026-12-31")));
    }

    @Test
    public void adjudicationRuleReport_executes() {
        List<Map<String, Object>> rows = reportDAO.adjudicationRuleReport(d("2026-01-01"), d("2026-12-31"));
        assertFalse(rows.isEmpty());
    }

    @Test
    public void appealsReport_executes_dateMathValid() {
        // Regression for the EXTRACT(DAY FROM date-date) bug — must not throw.
        List<Map<String, Object>> rows = reportDAO.appealsReport(d("2026-01-01"), d("2026-12-31"));
        assertFalse(rows.isEmpty());
    }

    @Test
    public void cobReport_executes() {
        // CLM-1 is SECONDARY coverage
        List<Map<String, Object>> rows = reportDAO.cobReport(d("2026-01-01"), d("2026-12-31"));
        assertFalse(rows.isEmpty());
    }

    @Test
    public void subrogationReport_executes() {
        List<Map<String, Object>> rows = reportDAO.subrogationReport();
        assertFalse(rows.isEmpty());
    }

    @Test
    public void feeScheduleCoverageReport_executes() {
        // CLM-2 line item is NO_RATE
        List<Map<String, Object>> rows = reportDAO.feeScheduleCoverageReport();
        assertFalse(rows.isEmpty());
    }

    private void assertEquals2(int actual) {
        assertTrue("expected at least 2 claims, got " + actual, actual >= 2);
    }

    private java.util.Date d(String iso) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(iso);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
