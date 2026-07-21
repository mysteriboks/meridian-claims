package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.EobDocument;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class Phase5Slice2DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcClaimDAO claimDAO;
    private JdbcPaymentDAO paymentDAO;
    private JdbcEobDocumentDAO eobDocumentDAO;
    private JdbcRemittanceBatchDAO remittanceBatchDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase5s2_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        memberDAO          = wire(new JdbcMemberDAO());
        providerDAO        = wire(new JdbcProviderDAO());
        planDAO            = wire(new JdbcPlanDAO());
        claimDAO           = wire(new JdbcClaimDAO());
        paymentDAO         = wire(new JdbcPaymentDAO());
        eobDocumentDAO     = wire(new JdbcEobDocumentDAO());
        remittanceBatchDAO = wire(new JdbcRemittanceBatchDAO());
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
    // PaymentDAO
    // -------------------------------------------------------------------------

    @Test
    public void payment_insertAndFind() {
        Claim claim = newClaim();

        Payment p = new Payment();
        p.setClaimId(claim.getId());
        p.setBilledTotal(Money.of("200.00"));
        p.setAllowedTotal(Money.of("180.00"));
        p.setPlanPaidTotal(Money.of("144.00"));
        p.setMemberResponsibility(Money.of("36.00"));
        paymentDAO.insert(p);

        assertTrue(p.getId() > 0);
        assertEquals("PENDING", p.getStatus());

        Payment loaded = paymentDAO.findByClaimId(claim.getId());
        assertNotNull(loaded);
        assertEquals(0, loaded.getPlanPaidTotal().compareTo(Money.of("144.00")));
    }

    @Test
    public void payment_updatePaid_fullPayment_statusPaid() {
        Claim claim = newClaim();
        Payment p = new Payment();
        p.setClaimId(claim.getId());
        p.setBilledTotal(Money.of("200.00"));
        p.setAllowedTotal(Money.of("180.00"));
        p.setPlanPaidTotal(Money.of("144.00"));
        p.setMemberResponsibility(Money.of("36.00"));
        paymentDAO.insert(p);

        paymentDAO.updatePaid(p.getId(), "REF-001", new Date(), Money.of("144.00"), null, false);

        Payment updated = paymentDAO.findById(p.getId());
        assertEquals("PAID", updated.getStatus());
        assertEquals("REF-001", updated.getReferenceNumber());
    }

    @Test
    public void payment_updatePaid_partial_remainsInStatus() {
        Claim claim = newClaim();
        Payment p = new Payment();
        p.setClaimId(claim.getId());
        p.setBilledTotal(Money.of("200.00"));
        p.setAllowedTotal(Money.of("180.00"));
        p.setPlanPaidTotal(Money.of("144.00"));
        p.setMemberResponsibility(Money.of("36.00"));
        paymentDAO.insert(p);

        paymentDAO.updatePaid(p.getId(), "REF-PART", new Date(),
            Money.of("72.00"), Money.of("72.00"), true);

        Payment updated = paymentDAO.findById(p.getId());
        // partial: remaining > 0 → status stays PENDING
        assertEquals("PENDING", updated.getStatus());
        assertTrue(updated.isPartialPaymentFlag());
    }

    // -------------------------------------------------------------------------
    // EobDocumentDAO
    // -------------------------------------------------------------------------

    @Test
    public void eobDocument_insertAndFindByClaim() {
        Claim claim = newClaim();

        EobDocument doc = new EobDocument();
        doc.setClaimId(claim.getId());
        doc.setMemberId(claim.getMemberId());
        doc.setContent("<html>EOB content</html>");
        eobDocumentDAO.insert(doc);

        assertTrue(doc.getId() > 0);
        EobDocument loaded = eobDocumentDAO.findByClaimId(claim.getId());
        assertNotNull(loaded);
        assertEquals("PENDING", loaded.getDeliveryMethod());
        assertTrue(loaded.getContent().contains("EOB content"));
    }

    @Test
    public void eobDocument_findById_returnsCorrectRow() {
        Claim claim = newClaim();
        EobDocument doc = new EobDocument();
        doc.setClaimId(claim.getId());
        doc.setMemberId(claim.getMemberId());
        doc.setContent("<html>by-id</html>");
        eobDocumentDAO.insert(doc);

        EobDocument byId = eobDocumentDAO.findById(doc.getId());
        assertNotNull(byId);
        assertEquals(doc.getId(), byId.getId());
        assertTrue(byId.getContent().contains("by-id"));
    }

    @Test
    public void eobDocument_markMailed_updatesDeliveryMethod() {
        Claim claim = newClaim();
        EobDocument doc = new EobDocument();
        doc.setClaimId(claim.getId());
        doc.setMemberId(claim.getMemberId());
        doc.setContent("<html>test</html>");
        eobDocumentDAO.insert(doc);

        int userId = newUserId();
        eobDocumentDAO.markMailed(doc.getId(), userId);

        EobDocument updated = eobDocumentDAO.findByClaimId(claim.getId());
        assertEquals("MAILED", updated.getDeliveryMethod());
        assertNotNull(updated.getDeliveredAt());
    }

    // -------------------------------------------------------------------------
    // RemittanceBatchDAO
    // -------------------------------------------------------------------------

    @Test
    public void remittanceBatch_insertAndFindItems() {
        Claim claim = newClaim();

        RemittanceBatch batch = new RemittanceBatch();
        batch.setPaymentDate(date(2026, 6, 30));
        batch.setTotalPaid(Money.of("144.00"));
        remittanceBatchDAO.insertBatch(batch);
        assertTrue(batch.getId() > 0);

        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setBatchId(batch.getId());
        item.setClaimId(claim.getId());
        item.setProviderId(claim.getProviderId());
        item.setBilled(Money.of("200.00"));
        item.setAllowed(Money.of("180.00"));
        item.setPlanPaid(Money.of("144.00"));
        item.setAdjustmentReasonCode("45");
        remittanceBatchDAO.insertItem(item);

        List<RemittanceBatchItem> items = remittanceBatchDAO.findItemsByBatchId(batch.getId());
        assertEquals(1, items.size());
        assertEquals(0, items.get(0).getPlanPaid().compareTo(Money.of("144.00")));
        assertEquals("45", items.get(0).getAdjustmentReasonCode());
    }

    @Test
    public void remittanceBatch_updateTotalPaid_persistsAndReloads() {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setPaymentDate(date(2026, 6, 30));
        batch.setTotalPaid(Money.ZERO);   // inserted as zero, like generateBatch does
        remittanceBatchDAO.insertBatch(batch);

        remittanceBatchDAO.updateTotalPaid(batch.getId(), Money.of("288.00"));

        RemittanceBatch reloaded = remittanceBatchDAO.findById(batch.getId());
        assertEquals(0, reloaded.getTotalPaid().compareTo(Money.of("288.00")));
    }

    @Test
    public void remittanceBatch_markSent_updatesStatus() {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setPaymentDate(date(2026, 6, 30));
        batch.setTotalPaid(Money.of("500.00"));
        remittanceBatchDAO.insertBatch(batch);

        remittanceBatchDAO.markSent(batch.getId());

        RemittanceBatch updated = remittanceBatchDAO.findById(batch.getId());
        assertEquals("SENT", updated.getStatus());
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
            "fin_" + System.currentTimeMillis() % 100000, "Finance User", "hash", "FINANCE", true);
        return jdbc.queryForObject("SELECT id FROM users ORDER BY id DESC LIMIT 1", Integer.class);
    }

    private Claim newClaim() {
        Member member = new Member();
        member.setMemberNumber("MF-" + System.currentTimeMillis() % 100000);
        member.setFirstName("Test");
        member.setLastName("Member");
        member.setDob(date(1980, 1, 1));
        member.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(member);

        Provider provider = new Provider();
        provider.setNpi("NF" + System.currentTimeMillis() % 10000000);
        provider.setName("Test Provider");
        provider.setProviderType(ProviderType.INDIVIDUAL);
        provider.setNetworkStatus(NetworkStatus.IN_NETWORK);
        providerDAO.insert(provider);

        Plan plan = new Plan();
        plan.setPlanName("Test Plan");
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
        c.setClaimNumber("CLM-S2-" + System.currentTimeMillis() % 100000);
        c.setMemberId(member.getId());
        c.setProviderId(provider.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.APPROVED);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        claimDAO.insert(c);
        return c;
    }
}
