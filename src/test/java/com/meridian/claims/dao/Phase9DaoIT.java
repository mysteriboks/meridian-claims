package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimIntakeBatch;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Phase 9 DAO integration tests against H2 (PostgreSQL mode).
 * Verifies the claim_intake_batches table: insert, find-by-hash,
 * update-completion, and the UNIQUE(file_hash) idempotency constraint.
 */
public class Phase9DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcClaimIntakeBatchDAO batchDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase9_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        batchDAO = new JdbcClaimIntakeBatchDAO();
        batchDAO.setJdbcTemplate(jdbc);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insertAndFindByHash() {
        ClaimIntakeBatch batch = newBatch("file1.json", "abc123hash");
        int id = batchDAO.insert(batch);

        ClaimIntakeBatch found = batchDAO.findByFileHash("abc123hash");
        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals("file1.json", found.getFileName());
        assertEquals("abc123hash", found.getFileHash());
        assertEquals("PROCESSING", found.getStatus());
        assertEquals(0, found.getTotalRecords());
    }

    @Test
    public void findByHash_notFound_returnsNull() {
        assertNull(batchDAO.findByFileHash("nonexistent-hash"));
    }

    @Test
    public void updateCompletion_setsCountsAndStatus() {
        int id = batchDAO.insert(newBatch("file2.json", "hash2"));

        batchDAO.updateCompletion(id, "COMPLETED", 10, 8, 2, null);

        ClaimIntakeBatch updated = batchDAO.findByFileHash("hash2");
        assertEquals("COMPLETED", updated.getStatus());
        assertEquals(10, updated.getTotalRecords());
        assertEquals(8, updated.getSucceeded());
        assertEquals(2, updated.getQuarantined());
        assertNull(updated.getErrorMessage());
    }

    @Test
    public void updateCompletion_storesErrorMessage() {
        int id = batchDAO.insert(newBatch("file3.json", "hash3"));

        batchDAO.updateCompletion(id, "FAILED", 0, 0, 0, "File-level parse error");

        ClaimIntakeBatch updated = batchDAO.findByFileHash("hash3");
        assertEquals("FAILED", updated.getStatus());
        assertEquals("File-level parse error", updated.getErrorMessage());
    }

    @Test(expected = DAOException.class)
    public void uniqueFileHash_enforced() {
        batchDAO.insert(newBatch("file4a.json", "duplicate-hash"));
        // Second insert with the same hash must violate UNIQUE(file_hash) —
        // the DAO wraps DuplicateKeyException in DAOException.
        batchDAO.insert(newBatch("file4b.json", "duplicate-hash"));
    }

    @Test
    public void findRecent_limitsAndOrdersByCreatedAtDesc() {
        int id1 = batchDAO.insert(newBatch("oldest.json", "h-old"));
        int id2 = batchDAO.insert(newBatch("middle.json", "h-mid"));
        int id3 = batchDAO.insert(newBatch("newest.json", "h-new"));

        // H2's CURRENT_TIMESTAMP can be identical across these fast inserts, which
        // would make the DESC ordering assertion flaky. Stamp created_at explicitly
        // so "newest" is unambiguously the most recent.
        jdbc.update("UPDATE claim_intake_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 09:00:00"), id1);
        jdbc.update("UPDATE claim_intake_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 10:00:00"), id2);
        jdbc.update("UPDATE claim_intake_batches SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 11:00:00"), id3);

        java.util.List<ClaimIntakeBatch> recent = batchDAO.findRecent(2);
        assertEquals(2, recent.size());
        assertEquals("newest.json", recent.get(0).getFileName());
        assertEquals("middle.json", recent.get(1).getFileName());
    }

    private ClaimIntakeBatch newBatch(String fileName, String fileHash) {
        ClaimIntakeBatch b = new ClaimIntakeBatch();
        b.setFileName(fileName);
        b.setFileHash(fileHash);
        b.setStatus("PROCESSING");
        return b;
    }
}
