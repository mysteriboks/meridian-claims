package com.meridian.claims.dao;

import com.meridian.claims.model.EligibilityCheck;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 16 DAO integration tests against H2 (PostgreSQL mode). Verifies
 * eligibility_checks insert/round-trip and member-scoped history retrieval.
 */
public class Phase16DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcEligibilityCheckDAO eligibilityCheckDAO;
    private int memberId;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase16_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        eligibilityCheckDAO = new JdbcEligibilityCheckDAO();
        eligibilityCheckDAO.setJdbcTemplate(jdbc);

        memberId = 1;
        jdbc.update("INSERT INTO members (id, member_number, first_name, last_name, dob) " +
            "VALUES (1, 'M100001', 'JOHN', 'DOE', DATE '1980-01-01')");
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insert_assignsIdAndRoundTripsFields() {
        EligibilityCheck check = new EligibilityCheck();
        check.setMemberId(memberId);
        check.setServiceType("30");
        check.setInquiryAt(new Date());
        check.setResponseAt(new Date());
        check.setResultStatus(EligibilityCheck.STATUS_ACTIVE);
        check.setCoverageSnapshot("Gold PPO");

        int id = eligibilityCheckDAO.insert(check);
        assertTrue(id > 0);
        assertEquals(id, check.getId());

        List<EligibilityCheck> found = eligibilityCheckDAO.findByMemberId(memberId, 10);
        assertEquals(1, found.size());
        EligibilityCheck loaded = found.get(0);
        assertEquals(memberId, loaded.getMemberId());
        assertEquals("30", loaded.getServiceType());
        assertEquals(EligibilityCheck.STATUS_ACTIVE, loaded.getResultStatus());
        assertEquals("Gold PPO", loaded.getCoverageSnapshot());
        assertNull("no provider on a member-only check", loaded.getProviderId());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    public void findByMemberId_ordersNewestFirstAndRespectsLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            EligibilityCheck check = new EligibilityCheck();
            check.setMemberId(memberId);
            check.setServiceType("30");
            check.setInquiryAt(new Date(System.currentTimeMillis() + i * 1000L));
            check.setResultStatus(EligibilityCheck.STATUS_ACTIVE);
            eligibilityCheckDAO.insert(check);
        }

        List<EligibilityCheck> found = eligibilityCheckDAO.findByMemberId(memberId, 2);
        assertEquals(2, found.size());
        assertTrue("newest first", found.get(0).getInquiryAt().getTime() >= found.get(1).getInquiryAt().getTime());
    }

    @Test
    public void findByMemberId_scopedToOneMember() {
        EligibilityCheck check = new EligibilityCheck();
        check.setMemberId(memberId);
        check.setServiceType("30");
        check.setInquiryAt(new Date());
        check.setResultStatus(EligibilityCheck.STATUS_ERROR);
        eligibilityCheckDAO.insert(check);

        List<EligibilityCheck> found = eligibilityCheckDAO.findByMemberId(999999, 10);
        assertTrue(found.isEmpty());
    }
}
