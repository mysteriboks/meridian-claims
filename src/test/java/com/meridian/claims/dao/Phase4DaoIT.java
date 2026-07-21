package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimAccumulatorContribution;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 4 DAO integration tests against H2 (PostgreSQL mode).
 *
 * NOTE: SELECT ... FOR UPDATE serialization cannot be proven here — H2 runs
 * single-threaded in-memory. These tests verify that:
 *   (a) the SQL is syntactically valid and returns the correct row,
 *   (b) the reversal arithmetic is exact (no double-count).
 * The concurrent-serialization exit criterion must be verified manually with
 * two parallel psql sessions against PostgreSQL.
 */
public class Phase4DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcMemberDAO memberDAO;
    private JdbcProviderDAO providerDAO;
    private JdbcPlanDAO planDAO;
    private JdbcClaimDAO claimDAO;
    private JdbcDeductibleAccumulatorDAO accumulatorDAO;
    private JdbcClaimAccumulatorContributionDAO contributionDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase4_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        memberDAO = wire(new JdbcMemberDAO());
        providerDAO = wire(new JdbcProviderDAO());
        planDAO = wire(new JdbcPlanDAO());
        claimDAO = wire(new JdbcClaimDAO());
        accumulatorDAO = wire(new JdbcDeductibleAccumulatorDAO());
        contributionDAO = wire(new JdbcClaimAccumulatorContributionDAO());
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

    private Date date(int y, int m, int d) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(y, m - 1, d);
        return cal.getTime();
    }

    // -------------------------------------------------------------------------
    // DeductibleAccumulatorDAO
    // -------------------------------------------------------------------------

    @Test
    public void accumulator_createsRowWhenAbsent() {
        Member member = newMember();
        Plan plan = newPlan();

        DeductibleAccumulator acc = accumulatorDAO.findOrCreateForUpdate(
            member.getId(), plan.getId(), date(2026, 1, 1));

        assertNotNull(acc);
        assertEquals(0, acc.getDeductibleAccumulated().compareTo(Money.ZERO));
        assertEquals(0, acc.getOopAccumulated().compareTo(Money.ZERO));
    }

    @Test
    public void accumulator_idempotent_secondCallReturnsSameRow() {
        Member member = newMember();
        Plan plan = newPlan();
        Date bys = date(2026, 1, 1);

        DeductibleAccumulator first  = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);
        DeductibleAccumulator second = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);

        assertEquals(first.getId(), second.getId());
    }

    // -------------------------------------------------------------------------
    // ClaimAccumulatorContributionDAO — insert + markReversed
    // -------------------------------------------------------------------------

    @Test
    public void contribution_insertAndMarkReversed() {
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim claim = newClaim(member, provider, plan);

        ClaimAccumulatorContribution contrib = new ClaimAccumulatorContribution();
        contrib.setClaimId(claim.getId());
        contrib.setBenefitYearStart(date(2026, 1, 1));
        contrib.setDeductibleContributed(Money.of("200.00"));
        contrib.setOopContributed(Money.of("384.00"));
        contrib.setReversed(false);
        contributionDAO.insert(contrib);

        ClaimAccumulatorContribution loaded = contributionDAO.findByClaimId(claim.getId());
        assertNotNull(loaded);
        assertFalse(loaded.isReversed());
        assertEquals(0, loaded.getDeductibleContributed().compareTo(Money.of("200.00")));

        contributionDAO.markReversed(claim.getId());
        ClaimAccumulatorContribution reversed = contributionDAO.findByClaimId(claim.getId());
        assertTrue(reversed.isReversed());
    }

    @Test(expected = DAOException.class)
    public void contribution_markReversed_idempotent_secondReverseThrows() {
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim claim = newClaim(member, provider, plan);

        ClaimAccumulatorContribution contrib = new ClaimAccumulatorContribution();
        contrib.setClaimId(claim.getId());
        contrib.setBenefitYearStart(date(2026, 1, 1));
        contrib.setDeductibleContributed(Money.of("100.00"));
        contrib.setOopContributed(Money.of("100.00"));
        contrib.setReversed(false);
        contributionDAO.insert(contrib);

        contributionDAO.markReversed(claim.getId());   // first reverse succeeds
        contributionDAO.markReversed(claim.getId());   // second reverse must throw (0 rows affected)
    }

    @Test
    public void contribution_multipleRowsPerClaim_findReturnsLatest() {
        // Re-adjudication retains history: a claim may have several contribution rows.
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim claim = newClaim(member, provider, plan);

        ClaimAccumulatorContribution run1 = new ClaimAccumulatorContribution();
        run1.setClaimId(claim.getId());
        run1.setBenefitYearStart(date(2026, 1, 1));
        run1.setDeductibleContributed(Money.of("200.00"));
        run1.setOopContributed(Money.of("200.00"));
        run1.setReversed(false);
        contributionDAO.insert(run1);
        contributionDAO.markReversed(claim.getId());   // prior run reversed before the next run inserts

        ClaimAccumulatorContribution run2 = new ClaimAccumulatorContribution();
        run2.setClaimId(claim.getId());
        run2.setBenefitYearStart(date(2026, 1, 1));
        run2.setDeductibleContributed(Money.of("350.00"));
        run2.setOopContributed(Money.of("350.00"));
        run2.setReversed(false);  // current run
        contributionDAO.insert(run2);

        // findByClaimId returns the most recent (highest id) row
        ClaimAccumulatorContribution latest = contributionDAO.findByClaimId(claim.getId());
        assertFalse(latest.isReversed());
        assertEquals(0, latest.getDeductibleContributed().compareTo(Money.of("350.00")));
    }

    // -------------------------------------------------------------------------
    // ClaimDAO — optimistic lock
    // -------------------------------------------------------------------------

    @Test(expected = OptimisticLockException.class)
    public void claimDAO_updateStatus_optimisticLockOnStaleVersion() {
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim claim = newClaim(member, provider, plan);

        // First update succeeds (version=0 → 1)
        claimDAO.updateStatus(claim.getId(), "IN_REVIEW", 0);

        // Second update with stale version=0 must throw
        claimDAO.updateStatus(claim.getId(), "APPROVED", 0);
    }

    // -------------------------------------------------------------------------
    // Full accumulator round-trip: apply → verify → reverse → verify
    // -------------------------------------------------------------------------

    @Test
    public void accumulatorRoundTrip_applyThenReverse_exactArithmetic() {
        Member member = newMember();
        Plan plan = newPlan();
        Provider provider = newProvider();
        Claim claim = newClaim(member, provider, plan);
        Date bys = date(2026, 1, 1);

        // Apply contribution
        DeductibleAccumulator acc = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);
        acc.setDeductibleAccumulated(Money.add(acc.getDeductibleAccumulated(), Money.of("200.00")));
        acc.setOopAccumulated(Money.add(acc.getOopAccumulated(), Money.of("384.00")));
        accumulatorDAO.update(acc);

        ClaimAccumulatorContribution contrib = new ClaimAccumulatorContribution();
        contrib.setClaimId(claim.getId());
        contrib.setBenefitYearStart(bys);
        contrib.setDeductibleContributed(Money.of("200.00"));
        contrib.setOopContributed(Money.of("384.00"));
        contrib.setReversed(false);
        contributionDAO.insert(contrib);

        // Verify applied
        DeductibleAccumulator applied = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);
        assertEquals(0, applied.getDeductibleAccumulated().compareTo(Money.of("200.00")));
        assertEquals(0, applied.getOopAccumulated().compareTo(Money.of("384.00")));

        // Reverse
        ClaimAccumulatorContribution loaded = contributionDAO.findByClaimId(claim.getId());
        DeductibleAccumulator forUpdate = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);
        forUpdate.setDeductibleAccumulated(
            Money.max(Money.ZERO, Money.subtract(forUpdate.getDeductibleAccumulated(), loaded.getDeductibleContributed())));
        forUpdate.setOopAccumulated(
            Money.max(Money.ZERO, Money.subtract(forUpdate.getOopAccumulated(), loaded.getOopContributed())));
        accumulatorDAO.update(forUpdate);
        contributionDAO.markReversed(claim.getId());

        // Verify reversed to zero (no double-count)
        DeductibleAccumulator afterReversal = accumulatorDAO.findOrCreateForUpdate(member.getId(), plan.getId(), bys);
        assertEquals(0, afterReversal.getDeductibleAccumulated().compareTo(Money.ZERO));
        assertEquals(0, afterReversal.getOopAccumulated().compareTo(Money.ZERO));

        assertTrue(contributionDAO.findByClaimId(claim.getId()).isReversed());
    }

    // -------------------------------------------------------------------------
    // findByClaimId returns null for non-existent contribution
    // -------------------------------------------------------------------------

    @Test
    public void contribution_findByClaimId_nullWhenAbsent() {
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();
        Claim claim = newClaim(member, provider, plan);

        assertNull(contributionDAO.findByClaimId(claim.getId()));
    }

    // -------------------------------------------------------------------------
    // FK constraint: claims.created_by_user_id REFERENCES users(id)
    // -------------------------------------------------------------------------

    @Test(expected = Exception.class)
    public void claim_insert_withBogusCreatedByUserId_rejectsByFkConstraint() {
        // Verifies that the restored FK constraint in test V6 is enforced by H2.
        Member member = newMember();
        Provider provider = newProvider();
        Plan plan = newPlan();

        Claim c = new Claim();
        c.setClaimNumber("CLM-FK-TEST-001");
        c.setMemberId(member.getId());
        c.setProviderId(provider.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.SUBMITTED);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        c.setCreatedByUserId(99999);  // user id 99999 does not exist
        claimDAO.insert(c);  // must throw FK violation
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private Claim newClaim(Member member, Provider provider, Plan plan) {
        Claim c = new Claim();
        c.setClaimNumber("CLM-TEST-" + System.currentTimeMillis() % 1000000);
        c.setMemberId(member.getId());
        c.setProviderId(provider.getId());
        c.setClaimType(ClaimType.ORIGINAL);
        c.setDateOfService(date(2026, 2, 15));
        c.setSubmissionDate(date(2026, 3, 1));
        c.setStatus(ClaimStatus.SUBMITTED);
        c.setPlanId(plan.getId());
        c.setCoverageOrder("PRIMARY");
        claimDAO.insert(c);
        return c;
    }
}
