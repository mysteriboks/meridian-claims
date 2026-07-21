package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.util.Money;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Phase 10 DAO integration tests against H2 (PostgreSQL mode).
 *
 * Verifies the external_reference column added to claims in V13:
 *   - externalReference round-trips correctly when set.
 *   - externalReference is null when not set (nullable column).
 */
public class Phase10DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcClaimDAO claimDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase10_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        memberDAO  = wire(new JdbcMemberDAO());
        providerDAO = wire(new JdbcProviderDAO());
        planDAO    = wire(new JdbcPlanDAO());
        claimDAO   = wire(new JdbcClaimDAO());
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
    // externalReference persists and loads correctly
    // -------------------------------------------------------------------------

    @Test
    public void externalReference_persistsAndLoads() {
        Member member   = newMember();
        Provider provider = newProvider();
        Plan plan       = newPlan();

        Claim claim = buildClaim(member, provider, plan, "CLM-EXT-001");
        claim.setExternalReference("ISA-001");
        claimDAO.insert(claim);

        Claim loaded = claimDAO.findById(claim.getId());
        assertNotNull("findById must return the inserted claim", loaded);
        assertEquals("externalReference must round-trip via insert/findById",
            "ISA-001", loaded.getExternalReference());
    }

    // -------------------------------------------------------------------------
    // externalReference is null when not set (nullable column)
    // -------------------------------------------------------------------------

    @Test
    public void externalReference_nullByDefault() {
        Member member   = newMember();
        Provider provider = newProvider();
        Plan plan       = newPlan();

        Claim claim = buildClaim(member, provider, plan, "CLM-EXT-002");
        // externalReference intentionally not set — remains Java null
        claimDAO.insert(claim);

        Claim loaded = claimDAO.findById(claim.getId());
        assertNotNull("findById must return the inserted claim", loaded);
        assertNull("externalReference must be null when not set",
            loaded.getExternalReference());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Claim buildClaim(Member member, Provider provider, Plan plan,
                              String claimNumber) {
        Claim c = new Claim();
        c.setClaimNumber(claimNumber);
        c.setMemberId(member.getId());
        c.setProviderId(provider.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.SUBMITTED);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        return c;
    }

    private Member newMember() {
        Member m = new Member();
        m.setMemberNumber("M-" + System.currentTimeMillis());
        m.setFirstName("Test");
        m.setLastName("Member");
        m.setDob(date(1980, 1, 1));
        m.setStatus(MemberStatus.ACTIVE);
        memberDAO.insert(m);
        return m;
    }

    private Provider newProvider() {
        Provider p = new Provider();
        p.setNpi("NPI" + System.currentTimeMillis() % 10000000);
        p.setName("Test Provider");
        p.setProviderType(ProviderType.INDIVIDUAL);
        p.setNetworkStatus(NetworkStatus.IN_NETWORK);
        providerDAO.insert(p);
        return p;
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

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }
}
