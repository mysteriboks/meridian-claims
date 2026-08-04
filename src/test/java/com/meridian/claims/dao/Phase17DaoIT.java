package com.meridian.claims.dao;

import com.meridian.claims.model.EnrollmentBatch;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 17 DAO integration tests against H2 (PostgreSQL mode). Verifies
 * enrollment_batches insert/round-trip, idempotency lookup by file hash, and
 * recent-batch retrieval — mirroring Phase 9's claim_intake_batches coverage.
 */
public class Phase17DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcEnrollmentBatchDAO enrollmentBatchDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase17_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        enrollmentBatchDAO = new JdbcEnrollmentBatchDAO();
        enrollmentBatchDAO.setJdbcTemplate(jdbc);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insert_assignsIdAndDefaultsToProcessing() {
        EnrollmentBatch batch = new EnrollmentBatch();
        batch.setFileName("enroll.834");
        batch.setFileHash("abc123");
        batch.setStatus("PROCESSING");

        int id = enrollmentBatchDAO.insert(batch);
        assertTrue(id > 0);

        EnrollmentBatch found = enrollmentBatchDAO.findByFileHash("abc123");
        assertEquals(id, found.getId());
        assertEquals("enroll.834", found.getFileName());
        assertEquals("PROCESSING", found.getStatus());
        assertEquals(0, found.getTotalRecords());
        assertNull(found.getErrorMessage());
    }

    @Test
    public void findByFileHash_unknownHash_returnsNull() {
        assertNull(enrollmentBatchDAO.findByFileHash("does-not-exist"));
    }

    @Test
    public void updateCompletion_persistsCountsAndStatus() {
        EnrollmentBatch batch = new EnrollmentBatch();
        batch.setFileName("enroll.834");
        batch.setFileHash("hash-1");
        batch.setStatus("PROCESSING");
        int id = enrollmentBatchDAO.insert(batch);

        enrollmentBatchDAO.updateCompletion(id, "COMPLETED", 5, 4, 1, "Record 3: plan not found: Bogus");

        EnrollmentBatch found = enrollmentBatchDAO.findByFileHash("hash-1");
        assertEquals("COMPLETED", found.getStatus());
        assertEquals(5, found.getTotalRecords());
        assertEquals(4, found.getSucceeded());
        assertEquals(1, found.getQuarantined());
        assertEquals("Record 3: plan not found: Bogus", found.getErrorMessage());
    }

    @Test
    public void findRecent_ordersNewestFirstAndRespectsLimit() {
        int id0 = enrollmentBatchDAO.insert(newBatch("enroll-0.834", "hash-0"));
        int id1 = enrollmentBatchDAO.insert(newBatch("enroll-1.834", "hash-1"));
        int id2 = enrollmentBatchDAO.insert(newBatch("enroll-2.834", "hash-2"));

        // H2's CURRENT_TIMESTAMP can be identical across these fast inserts, which
        // would make the DESC ordering assertion flaky. Stamp created_at explicitly
        // so "enroll-2" is unambiguously the most recent — same fix as Phase9DaoIT.
        jdbc.update("UPDATE enrollment_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 09:00:00"), id0);
        jdbc.update("UPDATE enrollment_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 10:00:00"), id1);
        jdbc.update("UPDATE enrollment_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 11:00:00"), id2);

        List<EnrollmentBatch> recent = enrollmentBatchDAO.findRecent(2);
        assertEquals(2, recent.size());
        assertEquals("enroll-2.834", recent.get(0).getFileName());
        assertEquals("enroll-1.834", recent.get(1).getFileName());
    }

    private EnrollmentBatch newBatch(String fileName, String fileHash) {
        EnrollmentBatch b = new EnrollmentBatch();
        b.setFileName(fileName);
        b.setFileHash(fileHash);
        b.setStatus("COMPLETED");
        return b;
    }

    @Test(expected = DAOException.class)
    public void duplicateFileHash_violatesUniqueConstraint() {
        EnrollmentBatch first = new EnrollmentBatch();
        first.setFileName("a.834");
        first.setFileHash("dup-hash");
        first.setStatus("COMPLETED");
        enrollmentBatchDAO.insert(first);

        EnrollmentBatch second = new EnrollmentBatch();
        second.setFileName("b.834");
        second.setFileHash("dup-hash");
        second.setStatus("PROCESSING");
        enrollmentBatchDAO.insert(second);
    }
}
