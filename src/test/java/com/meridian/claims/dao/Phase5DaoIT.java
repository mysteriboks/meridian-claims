package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimNote;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.InfoRequest;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.PhiAccessLog;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.model.SlaBreach;
import com.meridian.claims.service.OptimisticLockException;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Phase5DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcClaimDAO claimDAO;
    private JdbcInfoRequestDAO infoRequestDAO;
    private JdbcClaimNoteDAO claimNoteDAO;
    private JdbcSlaBreachDAO slaBreachDAO;
    private JdbcPhiAccessLogDAO phiAccessLogDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase5_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        memberDAO     = wire(new JdbcMemberDAO());
        providerDAO   = wire(new JdbcProviderDAO());
        planDAO       = wire(new JdbcPlanDAO());
        claimDAO      = wire(new JdbcClaimDAO());
        infoRequestDAO = wire(new JdbcInfoRequestDAO());
        claimNoteDAO  = wire(new JdbcClaimNoteDAO());
        slaBreachDAO  = wire(new JdbcSlaBreachDAO());
        phiAccessLogDAO = wire(new JdbcPhiAccessLogDAO());
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
    // InfoRequestDAO
    // -------------------------------------------------------------------------

    @Test
    public void infoRequest_insertAndFind() {
        Claim claim = newClaim();
        InfoRequest ir = new InfoRequest();
        ir.setClaimId(claim.getId());
        ir.setRequestedFrom("MEMBER");
        ir.setRequestedByUserId(newUserId());
        ir.setDueDate(date(2026, 8, 1));
        ir.setRequestNotes("Please provide prior EOB");
        infoRequestDAO.insert(ir);

        assertTrue(ir.getId() > 0);
        InfoRequest loaded = infoRequestDAO.findById(ir.getId());
        assertNotNull(loaded);
        assertEquals("OPEN", loaded.getStatus());
        assertEquals("MEMBER", loaded.getRequestedFrom());
    }

    @Test
    public void infoRequest_findOpenByClaimId_returnsOpenOnly() {
        Claim claim = newClaim();
        int userId = newUserId();

        InfoRequest ir1 = newInfoRequest(claim.getId(), userId);
        infoRequestDAO.markResponded(ir1.getId(), "Got the docs");

        InfoRequest ir2 = newInfoRequest(claim.getId(), userId);
        // ir2 is still OPEN

        InfoRequest open = infoRequestDAO.findOpenByClaimId(claim.getId());
        assertNotNull(open);
        assertEquals(ir2.getId(), open.getId());
    }

    @Test
    public void infoRequest_markResponded_updatesStatus() {
        Claim claim = newClaim();
        InfoRequest ir = newInfoRequest(claim.getId(), newUserId());

        infoRequestDAO.markResponded(ir.getId(), "Response text");

        InfoRequest updated = infoRequestDAO.findById(ir.getId());
        assertEquals("RESPONDED", updated.getStatus());
        assertEquals("Response text", updated.getResponseNotes());
    }

    @Test(expected = DAOException.class)
    public void infoRequest_markResponded_alreadyResolved_throws() {
        Claim claim = newClaim();
        InfoRequest ir = newInfoRequest(claim.getId(), newUserId());
        infoRequestDAO.markResponded(ir.getId(), "first");
        infoRequestDAO.markResponded(ir.getId(), "second"); // must throw — already RESPONDED
    }

    // -------------------------------------------------------------------------
    // ClaimNoteDAO
    // -------------------------------------------------------------------------

    @Test
    public void claimNote_insertAndFind() {
        Claim claim = newClaim();
        ClaimNote note = new ClaimNote();
        note.setClaimId(claim.getId());
        note.setAuthorUserId(newUserId());
        note.setNote("Reviewed with supervisor");
        claimNoteDAO.insert(note);

        assertTrue(note.getId() > 0);
        List<ClaimNote> notes = claimNoteDAO.findByClaimId(claim.getId());
        assertEquals(1, notes.size());
        assertEquals("Reviewed with supervisor", notes.get(0).getNote());
    }

    // -------------------------------------------------------------------------
    // SlaBreachDAO
    // -------------------------------------------------------------------------

    @Test
    public void slaBreach_insertAndExistsCheck() {
        Claim claim = newClaim();

        assertFalse(slaBreachDAO.existsForClaimStatus(claim.getId(), "SUBMITTED"));

        SlaBreach breach = new SlaBreach();
        breach.setClaimId(claim.getId());
        breach.setStatus("SUBMITTED");
        breach.setExpectedBy(date(2026, 6, 28));
        slaBreachDAO.insert(breach);

        assertTrue(slaBreachDAO.existsForClaimStatus(claim.getId(), "SUBMITTED"));
        assertFalse(slaBreachDAO.existsForClaimStatus(claim.getId(), "IN_REVIEW"));
    }

    // -------------------------------------------------------------------------
    // PhiAccessLogDAO
    // -------------------------------------------------------------------------

    @Test
    public void phiAccessLog_insertAndFindByClaim() {
        Claim claim = newClaim();
        PhiAccessLog log = new PhiAccessLog();
        log.setUserId(null); // anonymous
        log.setMemberId(claim.getMemberId());
        log.setClaimId(claim.getId());
        log.setAction("VIEW");
        phiAccessLogDAO.insert(log);

        List<PhiAccessLog> entries = phiAccessLogDAO.findByClaimId(claim.getId());
        assertEquals(1, entries.size());
        assertEquals("VIEW", entries.get(0).getAction());
        assertNull(entries.get(0).getUserId());
    }

    // -------------------------------------------------------------------------
    // ClaimDAO — updateAssignment optimistic lock
    // -------------------------------------------------------------------------

    @Test(expected = OptimisticLockException.class)
    public void claimDAO_updateAssignment_optimisticLockOnStaleVersion() {
        Claim claim = newClaim();
        claimDAO.updateAssignment(claim.getId(), null, 0);   // version 0 → 1
        claimDAO.updateAssignment(claim.getId(), null, 0);   // stale — must throw
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

    private int newUserId() {
        jdbc.update("INSERT INTO users (username, full_name, password_hash, role, active) VALUES (?,?,?,?,?)",
            "user_" + System.currentTimeMillis() % 100000, "Test User", "hash", "REVIEWER", true);
        return jdbc.queryForObject("SELECT id FROM users ORDER BY id DESC LIMIT 1", Integer.class);
    }

    private Member newMember() {
        Member m = new Member();
        m.setMemberNumber("M5-" + System.currentTimeMillis() % 100000);
        m.setFirstName("Test");
        m.setLastName("Member");
        m.setDob(date(1980, 1, 1));
        m.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(m);
        return m;
    }

    private Plan newPlan() {
        Plan p = new Plan();
        p.setPlanName("Test Plan");
        p.setPlanType(PlanType.PPO);
        p.setDeductibleAmount(Money.of("500.00"));
        p.setOopMax(Money.of("3000.00"));
        p.setCopayAmount(Money.of("30.00"));
        p.setCoveragePctInNetwork(Money.of("80.00"));
        p.setCoveragePctOutNetwork(Money.of("60.00"));
        p.setBenefitYearStart(date(2026, 1, 1));
        p.setTimelyFilingDays(180);
        planDAO.insert(p);
        return p;
    }

    private Provider newProvider() {
        Provider p = new Provider();
        p.setNpi("NP5" + System.currentTimeMillis() % 10000000);
        p.setName("Test Provider");
        p.setProviderType(ProviderType.INDIVIDUAL);
        p.setNetworkStatus(NetworkStatus.IN_NETWORK);
        providerDAO.insert(p);
        return p;
    }

    private Claim newClaim() {
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim c = new Claim();
        c.setClaimNumber("CLM-P5-" + System.currentTimeMillis() % 100000);
        c.setMemberId(member.getId());
        c.setProviderId(provider.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.IN_REVIEW);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        claimDAO.insert(c);
        return c;
    }

    private InfoRequest newInfoRequest(int claimId, int userId) {
        InfoRequest ir = new InfoRequest();
        ir.setClaimId(claimId);
        ir.setRequestedFrom("MEMBER");
        ir.setRequestedByUserId(userId);
        ir.setDueDate(date(2026, 8, 1));
        ir.setRequestNotes("Please provide documentation");
        infoRequestDAO.insert(ir);
        return ir;
    }
}
