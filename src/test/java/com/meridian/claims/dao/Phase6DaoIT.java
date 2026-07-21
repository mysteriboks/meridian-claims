package com.meridian.claims.dao;

import com.meridian.claims.model.Appeal;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.model.ScheduledJobLog;
import com.meridian.claims.model.SubrogationCase;
import com.meridian.claims.util.Money;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class Phase6DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcClaimDAO claimDAO;
    private JdbcAppealDAO appealDAO;
    private JdbcSubrogationCaseDAO subrogationDAO;
    private JdbcPaymentBatchDAO paymentBatchDAO;
    private JdbcScheduledJobLogDAO jobLogDAO;
    private JdbcClaimArchiveDAO claimArchiveDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_p6_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=0");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc             = new JdbcTemplate(dataSource);
        memberDAO        = wire(new JdbcMemberDAO());
        providerDAO      = wire(new JdbcProviderDAO());
        planDAO          = wire(new JdbcPlanDAO());
        claimDAO         = wire(new JdbcClaimDAO());
        appealDAO        = wire(new JdbcAppealDAO());
        subrogationDAO   = wire(new JdbcSubrogationCaseDAO());
        paymentBatchDAO  = wire(new JdbcPaymentBatchDAO());
        jobLogDAO        = wire(new JdbcScheduledJobLogDAO());
        claimArchiveDAO  = wire(new JdbcClaimArchiveDAO());
    }

    private <T extends BaseDAO> T wire(T dao) {
        dao.setJdbcTemplate(jdbc);
        return dao;
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) {
            new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
            dataSource.close();
        }
    }

    // -------------------------------------------------------------------------
    // AppealDAO
    // -------------------------------------------------------------------------

    @Test
    public void appeal_insertAndFind() {
        Claim claim = newClaim();
        int userId = newUserId();

        Appeal appeal = new Appeal();
        appeal.setClaimId(claim.getId());
        appeal.setMemberId(claim.getMemberId());
        appeal.setAppealType("INTERNAL");
        appeal.setSubmittedDate(date(2026, 7, 1));
        appeal.setDeadlineDate(date(2026, 7, 31));
        appeal.setSubmittedByUserId(userId);
        appealDAO.insert(appeal);

        assertTrue(appeal.getId() > 0);
        Appeal loaded = appealDAO.findById(appeal.getId());
        assertNotNull(loaded);
        assertEquals("OPEN", loaded.getStatus());
        assertEquals("INTERNAL", loaded.getAppealType());
    }

    @Test
    public void appeal_updateStatus_setsOutcome() {
        Claim claim = newClaim();
        Appeal appeal = newAppeal(claim);

        appealDAO.updateStatus(appeal.getId(), "APPROVED", "APPROVED", "Evidence upheld");

        Appeal updated = appealDAO.findById(appeal.getId());
        assertEquals("APPROVED", updated.getStatus());
        assertEquals("Evidence upheld", updated.getOutcomeNotes());
    }

    @Test
    public void appeal_findByStatus_returnsOnlyMatchingStatus() {
        Claim c1 = newClaim();
        Claim c2 = newClaim();
        Appeal a1 = newAppeal(c1);
        Appeal a2 = newAppeal(c2);
        appealDAO.updateStatus(a1.getId(), "DENIED", "DENIED", "no merit");

        List<Appeal> open = appealDAO.findByStatus("OPEN");
        assertEquals(1, open.size());
        assertEquals(a2.getId(), open.get(0).getId());
    }

    // -------------------------------------------------------------------------
    // SubrogationCaseDAO
    // -------------------------------------------------------------------------

    @Test
    public void subrogation_insertAndFindByClaimId() {
        Claim claim = newClaim();

        SubrogationCase sc = new SubrogationCase();
        sc.setClaimId(claim.getId());
        sc.setOpenedDate(new Date());
        subrogationDAO.insert(sc);

        assertTrue(sc.getId() > 0);
        SubrogationCase loaded = subrogationDAO.findByClaimId(claim.getId());
        assertNotNull(loaded);
        assertEquals("OPEN", loaded.getStatus());
    }

    @Test
    public void subrogation_recordRecovery_updatesStatus() {
        Claim claim = newClaim();
        SubrogationCase sc = new SubrogationCase();
        sc.setClaimId(claim.getId());
        sc.setOpenedDate(new Date());
        subrogationDAO.insert(sc);

        subrogationDAO.recordRecovery(sc.getId(), "ABC Insurance", Money.of("5000.00"), "Settled");

        SubrogationCase updated = subrogationDAO.findById(sc.getId());
        assertEquals("RECOVERED", updated.getStatus());
        assertEquals(0, updated.getRecoveryAmount().compareTo(Money.of("5000.00")));
    }

    // -------------------------------------------------------------------------
    // PaymentBatchDAO
    // -------------------------------------------------------------------------

    @Test
    public void paymentBatch_insertAndFindById() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchDate(date(2026, 6, 30));
        paymentBatchDAO.insert(batch);

        assertTrue(batch.getId() > 0);
        assertEquals("PENDING", batch.getStatus());

        PaymentBatch loaded = paymentBatchDAO.findById(batch.getId());
        assertEquals(0, loaded.getTotalAmount().compareTo(Money.ZERO));
    }

    @Test
    public void paymentBatch_updateStatus_setsExported() {
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchDate(date(2026, 6, 30));
        paymentBatchDAO.insert(batch);

        paymentBatchDAO.updateStatus(batch.getId(), "EXPORTED", "BATCH-1-2026-06-30.csv");

        PaymentBatch updated = paymentBatchDAO.findById(batch.getId());
        assertEquals("EXPORTED", updated.getStatus());
        assertEquals("BATCH-1-2026-06-30.csv", updated.getFileReference());
    }

    // -------------------------------------------------------------------------
    // ScheduledJobLogDAO
    // -------------------------------------------------------------------------

    @Test
    public void jobLog_startCompleteRoundTrip() {
        int logId = jobLogDAO.start("TestJob");
        assertTrue(logId > 0);

        jobLogDAO.complete(logId, 42);

        List<ScheduledJobLog> history = jobLogDAO.findByJobName("TestJob");
        assertEquals(1, history.size());
        assertEquals("SUCCESS", history.get(0).getStatus());
        assertEquals(42, history.get(0).getRecordsProcessed());
    }

    @Test
    public void jobLog_fail_setsErrorMessage() {
        int logId = jobLogDAO.start("FailingJob");
        jobLogDAO.fail(logId, "Connection timeout");

        List<ScheduledJobLog> history = jobLogDAO.findByJobName("FailingJob");
        assertEquals("FAILED", history.get(0).getStatus());
        assertEquals("Connection timeout", history.get(0).getErrorMessage());
    }

    // -------------------------------------------------------------------------
    // ClaimDAO.findByMemberId
    // -------------------------------------------------------------------------

    @Test
    public void claimDAO_findByMemberId_returnsAllClaims() {
        Claim c1 = newClaim();
        newClaim(c1.getMemberId()); // second claim for same member

        List<Claim> claims = claimDAO.findByMemberId(c1.getMemberId());
        assertEquals(2, claims.size());
    }

    // -------------------------------------------------------------------------
    // ClaimArchiveDAO
    // -------------------------------------------------------------------------

    @Test
    public void claimArchive_archivesOldTerminalClaim_andRemovesFromLive() {
        Claim claim = newClaim();
        // Make it old + terminal: DOS 2015, status PAID
        jdbc.update("UPDATE claims SET date_of_service = ?, status = 'PAID' WHERE id = ?",
            date(2015, 1, 1), claim.getId());

        int archived = claimArchiveDAO.archiveClaimsOlderThan(date(2020, 1, 1));
        assertEquals(1, archived);

        // Gone from live table
        assertNull(claimDAO.findById(claim.getId()));
        // Present in archive
        Claim fromArchive = claimArchiveDAO.findById(claim.getId());
        assertNotNull(fromArchive);
        assertEquals(claim.getClaimNumber(), fromArchive.getClaimNumber());
    }

    @Test
    public void claimArchive_leavesActiveClaims_untouched() {
        Claim claim = newClaim();  // status DENIED (not terminal-archivable), recent DOS
        int archived = claimArchiveDAO.archiveClaimsOlderThan(date(2020, 1, 1));
        assertEquals(0, archived);
        assertNotNull(claimDAO.findById(claim.getId()));
    }

    @Test
    public void claimArchive_search_findsByClaimNumber() {
        Claim claim = newClaim();
        jdbc.update("UPDATE claims SET date_of_service = ?, status = 'PAID' WHERE id = ?",
            date(2015, 1, 1), claim.getId());
        claimArchiveDAO.archiveClaimsOlderThan(date(2020, 1, 1));

        List<Claim> found = claimArchiveDAO.search(claim.getClaimNumber());
        assertEquals(1, found.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    private static final java.util.concurrent.atomic.AtomicInteger USER_SEQ =
        new java.util.concurrent.atomic.AtomicInteger();

    private int newUserId() {
        // Unique per call — a timestamp-based name collides when two users are
        // created within the same millisecond (multiple appeals in one test).
        String username = "p6user_" + USER_SEQ.incrementAndGet();
        jdbc.update("INSERT INTO users (username, full_name, password_hash, role, active) VALUES (?,?,?,?,?)",
            username, "Test User", "hash", "REVIEWER", true);
        return jdbc.queryForObject("SELECT id FROM users ORDER BY id DESC LIMIT 1", Integer.class);
    }

    private Claim newClaim() {
        Member m = new Member();
        m.setMemberNumber("P6M-" + System.currentTimeMillis() % 100000);
        m.setFirstName("Test");
        m.setLastName("Member");
        m.setDob(date(1980, 1, 1));
        m.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(m);
        return newClaim(m.getId());
    }

    private Claim newClaim(int memberId) {
        Provider p = new Provider();
        p.setNpi("NPI6" + System.currentTimeMillis() % 100000);
        p.setName("Provider");
        p.setProviderType(ProviderType.INDIVIDUAL);
        p.setNetworkStatus(NetworkStatus.IN_NETWORK);
        providerDAO.insert(p);

        Plan plan = new Plan();
        plan.setPlanName("Test");
        plan.setPlanType(PlanType.PPO);
        plan.setDeductibleAmount(Money.of("500.00"));
        plan.setOopMax(Money.of("3000.00"));
        plan.setCopayAmount(Money.of("30.00"));
        plan.setCoveragePctInNetwork(Money.of("80.00"));
        plan.setCoveragePctOutNetwork(Money.of("60.00"));
        plan.setBenefitYearStart(date(2026, 1, 1));
        plan.setTimelyFilingDays(180);
        planDAO.insert(plan);

        Claim c = new Claim();
        c.setClaimNumber("P6CLM-" + System.nanoTime() % 10000000);
        c.setMemberId(memberId);
        c.setProviderId(p.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.DENIED);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        claimDAO.insert(c);
        return c;
    }

    private Appeal newAppeal(Claim claim) {
        int userId = newUserId();
        Appeal a = new Appeal();
        a.setClaimId(claim.getId());
        a.setMemberId(claim.getMemberId());
        a.setAppealType("INTERNAL");
        a.setSubmittedDate(date(2026, 7, 1));
        a.setDeadlineDate(date(2026, 7, 31));
        a.setSubmittedByUserId(userId);
        appealDAO.insert(a);
        return a;
    }
}
