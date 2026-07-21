package com.meridian.claims.dao;

import com.meridian.claims.model.CoverageOrder;
import com.meridian.claims.model.FeeScheduleRate;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberCoverage;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.PriorAuthStatus;
import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.model.Referral;
import com.meridian.claims.model.ReferralStatus;
import com.meridian.claims.util.Page;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 3 DAO integration tests against H2 (PostgreSQL mode) using the real
 * test migrations (classpath:db/migration). Exercises the actual hand-written
 * SQL — including the fee-schedule resolution fallback, the highest-risk
 * Phase 3 exit criterion — which the mock-based service tests cannot cover.
 */
public class Phase3DaoIT {

    private BasicDataSource dataSource;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcMemberCoverageDAO coverageDAO;
    private JdbcFeeScheduleRateDAO feeDAO;
    private JdbcPriorAuthorizationDAO priorAuthDAO;
    private JdbcReferralDAO referralDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        // Unique DB name per run keeps tests isolated.
        dataSource.setUrl("jdbc:h2:mem:meridian_phase3_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        memberDAO = wire(new JdbcMemberDAO(), jdbc);
        providerDAO = wire(new JdbcProviderDAO(), jdbc);
        planDAO = wire(new JdbcPlanDAO(), jdbc);
        coverageDAO = wire(new JdbcMemberCoverageDAO(), jdbc);
        feeDAO = wire(new JdbcFeeScheduleRateDAO(), jdbc);
        priorAuthDAO = wire(new JdbcPriorAuthorizationDAO(), jdbc);
        referralDAO = wire(new JdbcReferralDAO(), jdbc);
    }

    private <T extends BaseDAO> T wire(T dao, JdbcTemplate jdbc) {
        dao.setJdbcTemplate(jdbc);
        return dao;
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) {
            // Drop everything so the next test's migration runs against a clean DB.
            new JdbcTemplate(dataSource).execute("DROP ALL OBJECTS");
            dataSource.close();
        }
    }

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    private Member newMember(String number, String first, String last, Date dob) {
        Member m = new Member();
        m.setMemberNumber(number);
        m.setFirstName(first);
        m.setLastName(last);
        m.setDob(dob);
        m.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(m);
        return m;
    }

    private Plan newPlan(String name) {
        Plan p = new Plan();
        p.setPlanName(name);
        p.setPlanType(PlanType.PPO);
        p.setDeductibleAmount(new BigDecimal("500.00"));
        p.setOopMax(new BigDecimal("5000.00"));
        p.setCopayAmount(new BigDecimal("20.00"));
        p.setCoveragePctInNetwork(new BigDecimal("80.00"));
        p.setCoveragePctOutNetwork(new BigDecimal("60.00"));
        p.setBenefitYearStart(date(2026, 1, 1));
        p.setTimelyFilingDays(180);
        planDAO.insert(p);
        return p;
    }

    private Provider newProvider(String npi, String name) {
        Provider pr = new Provider();
        pr.setNpi(npi);
        pr.setName(name);
        pr.setProviderType(ProviderType.INDIVIDUAL);
        pr.setNetworkStatus(NetworkStatus.IN_NETWORK);
        providerDAO.insert(pr);
        return pr;
    }

    // ---------- Member search + soft delete ----------

    @Test
    public void memberSearchByNameNumberAndDob() {
        newMember("M001", "Jane", "Doe", date(1990, 5, 15));
        newMember("M002", "John", "Smith", date(1985, 3, 20));

        assertEquals(1, memberDAO.search("Jane", 1, 20).getTotalItems());
        assertEquals(1, memberDAO.search("M002", 1, 20).getTotalItems());
        // DOB search — the gap this fix closed
        Page<Member> byDob = memberDAO.search("1990-05-15", 1, 20);
        assertEquals(1, byDob.getTotalItems());
        assertEquals("M001", byDob.getItems().get(0).getMemberNumber());
    }

    @Test
    public void softDeleteHidesFromActiveButFindByIdStillReturns() {
        Member m = newMember("M003", "Soft", "Delete", date(2000, 1, 1));
        memberDAO.softDelete(m.getId());

        // Not in search (active list)
        assertEquals(0, memberDAO.search("Soft", 1, 20).getTotalItems());
        // But still retrievable by id (history preserved)
        Member reloaded = memberDAO.findById(m.getId());
        assertNotNull(reloaded);
        assertNotNull(reloaded.getDeletedAt());
    }

    // ---------- Fee schedule resolution (provider -> plan -> null) ----------

    @Test
    public void feeScheduleResolutionFollowsFallbackHierarchy() {
        Plan plan = newPlan("PPO Gold");
        Provider provider = newProvider("1111111111", "Dr. Who");

        // plan-wide rate
        FeeScheduleRate planWide = new FeeScheduleRate();
        planWide.setPlanId(plan.getId());
        planWide.setProviderId(null);
        planWide.setProcedureCode("99213");
        planWide.setAllowedAmount(new BigDecimal("100.00"));
        planWide.setEffectiveDate(date(2026, 1, 1));
        feeDAO.insert(planWide);

        // provider-specific rate (should win)
        FeeScheduleRate providerRate = new FeeScheduleRate();
        providerRate.setPlanId(plan.getId());
        providerRate.setProviderId(provider.getId());
        providerRate.setProcedureCode("99213");
        providerRate.setAllowedAmount(new BigDecimal("120.00"));
        providerRate.setEffectiveDate(date(2026, 1, 1));
        feeDAO.insert(providerRate);

        Date dos = date(2026, 6, 1);

        // Provider-specific wins
        assertEquals(0, new BigDecimal("120.00").compareTo(
            feeDAO.resolveAllowedAmount(plan.getId(), provider.getId(), "99213", dos)));

        // No provider rate for a different provider -> falls back to plan-wide
        Provider other = newProvider("2222222222", "Dr. Strange");
        assertEquals(0, new BigDecimal("100.00").compareTo(
            feeDAO.resolveAllowedAmount(plan.getId(), other.getId(), "99213", dos)));

        // Unknown procedure -> null (NO_RATE)
        assertNull(feeDAO.resolveAllowedAmount(plan.getId(), provider.getId(), "00000", dos));

        // Date before effective -> null
        assertNull(feeDAO.resolveAllowedAmount(plan.getId(), provider.getId(), "99213", date(2025, 1, 1)));
    }

    @Test
    public void feeScheduleRespectsTerminationWindow() {
        Plan plan = newPlan("PPO Silver");
        FeeScheduleRate rate = new FeeScheduleRate();
        rate.setPlanId(plan.getId());
        rate.setProviderId(null);
        rate.setProcedureCode("70450");
        rate.setAllowedAmount(new BigDecimal("250.00"));
        rate.setEffectiveDate(date(2026, 1, 1));
        rate.setTerminationDate(date(2026, 3, 31));
        feeDAO.insert(rate);

        assertNotNull(feeDAO.resolveAllowedAmount(plan.getId(), null, "70450", date(2026, 2, 1)));
        // after termination -> null
        assertNull(feeDAO.resolveAllowedAmount(plan.getId(), null, "70450", date(2026, 4, 1)));
    }

    // ---------- Member coverage window resolution ----------

    @Test
    public void coverageFindByMemberAndDateResolvesWindow() {
        Member m = newMember("M010", "Cob", "Member", date(1970, 1, 1));
        Plan plan = newPlan("PPO Coverage");

        MemberCoverage c = new MemberCoverage();
        c.setMemberId(m.getId());
        c.setPlanId(plan.getId());
        c.setCoverageOrder(CoverageOrder.PRIMARY);
        c.setEffectiveDate(date(2026, 1, 1));
        c.setTerminationDate(date(2026, 12, 31));
        coverageDAO.insert(c);

        assertNotNull(coverageDAO.findByMemberAndDate(m.getId(), "PRIMARY", date(2026, 6, 1)));
        // Out of window
        assertNull(coverageDAO.findByMemberAndDate(m.getId(), "PRIMARY", date(2025, 6, 1)));
        // Returned by member list
        List<MemberCoverage> all = coverageDAO.findByMemberId(m.getId());
        assertEquals(1, all.size());
        assertEquals("PPO Coverage", all.get(0).getPlanName());
    }

    // ---------- Prior auth & referral lookup + validity ----------

    @Test
    public void priorAuthLookupByNumberAndValidity() {
        Member m = newMember("M020", "PA", "Member", date(1980, 2, 2));
        Provider pr = newProvider("3333333333", "Auth Provider");

        PriorAuthorization a = new PriorAuthorization();
        a.setMemberId(m.getId());
        a.setProviderId(pr.getId());
        a.setProcedureCode("99214");
        a.setAuthorizedFrom(date(2026, 1, 1));
        a.setAuthorizedTo(date(2026, 6, 30));
        a.setAuthNumber("PA-TEST01");
        a.setStatus(PriorAuthStatus.ACTIVE);
        a.setApprovedUnits(3);
        priorAuthDAO.insert(a);

        assertNotNull(priorAuthDAO.findByAuthNumber("PA-TEST01"));
        assertNotNull(priorAuthDAO.findValid(m.getId(), "99214", date(2026, 3, 1)));
        // out of window
        assertNull(priorAuthDAO.findValid(m.getId(), "99214", date(2026, 9, 1)));
    }

    @Test
    public void referralLookupByNumberAndValidity() {
        Member m = newMember("M030", "Ref", "Member", date(1975, 7, 7));
        Provider pcp = newProvider("4444444444", "PCP");
        Provider spec = newProvider("5555555555", "Specialist");

        Referral r = new Referral();
        r.setMemberId(m.getId());
        r.setReferringProviderId(pcp.getId());
        r.setReferredToProviderId(spec.getId());
        r.setServiceType("SPECIALIST");
        r.setValidFrom(date(2026, 1, 1));
        r.setValidTo(date(2026, 6, 30));
        r.setReferralNumber("REF-TEST01");
        r.setStatus(ReferralStatus.ACTIVE);
        referralDAO.insert(r);

        assertNotNull(referralDAO.findByReferralNumber("REF-TEST01"));
        assertNotNull(referralDAO.findValid(m.getId(), "SPECIALIST", date(2026, 2, 1)));
        assertNull(referralDAO.findValid(m.getId(), "SPECIALIST", date(2026, 8, 1)));
    }

    // ---------- sanity: provider search ----------

    @Test
    public void providerSearchAndSoftDelete() {
        Provider pr = newProvider("6666666666", "Cardiology Clinic");
        pr.setSpecialty("Cardiology");
        providerDAO.update(pr);

        assertTrue(providerDAO.search("Cardiology", 1, 20).getTotalItems() >= 1);
        providerDAO.softDelete(pr.getId());
        assertEquals(0, providerDAO.search("Cardiology Clinic", 1, 20).getTotalItems());
        assertFalse(providerDAO.findById(pr.getId()) == null);
    }
}
