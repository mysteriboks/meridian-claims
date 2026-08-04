package com.meridian.claims.dao;

import com.meridian.claims.model.CarcRarcCode;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.ReferenceDataImportRow;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 19 DAO integration tests against H2 (PostgreSQL mode). Verifies the
 * reference-data import ledger (batches + rows), the new carc_rarc_codes
 * lookup table, and the providers.npi_validation_status/npi_validated_at
 * columns added by the same migration.
 */
public class Phase19DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcReferenceDataImportDAO importDAO;
    private JdbcLookupDAO lookupDAO;
    private JdbcProviderDAO providerDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase19_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        importDAO = new JdbcReferenceDataImportDAO();
        importDAO.setJdbcTemplate(jdbc);
        lookupDAO = new JdbcLookupDAO();
        lookupDAO.setJdbcTemplate(jdbc);
        providerDAO = new JdbcProviderDAO();
        providerDAO.setJdbcTemplate(jdbc);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insertBatch_assignsIdAndRoundTripsFields() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setFeedType(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES);
        batch.setFileName("icd10.csv");
        batch.setFileHash("hash-1");
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        batch.setTotalRecords(3);
        batch.setAddedCount(2);
        batch.setChangedCount(1);

        int id = importDAO.insertBatch(batch);
        assertTrue(id > 0);

        ReferenceDataImportBatch found = importDAO.findBatchById(id);
        assertEquals(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES, found.getFeedType());
        assertEquals("icd10.csv", found.getFileName());
        assertEquals(ReferenceDataImportBatch.STATUS_STAGED, found.getStatus());
        assertEquals(3, found.getTotalRecords());
        assertEquals(2, found.getAddedCount());
        assertEquals(1, found.getChangedCount());
    }

    @Test
    public void findBatchByFileHash_unknownHash_returnsNull() {
        assertNull(importDAO.findBatchByFileHash("does-not-exist"));
    }

    @Test
    public void markBatchApplied_persistsStatusAndAppliedBy() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setFeedType(ReferenceDataImportBatch.FEED_CARC_RARC);
        batch.setFileName("carc.csv");
        batch.setFileHash("hash-2");
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        int id = importDAO.insertBatch(batch);

        jdbc.update("INSERT INTO users (id, username, full_name, password_hash, role, active) " +
            "VALUES (1, 'admin1', 'Admin One', 'h', 'ADMIN', TRUE)");
        importDAO.markBatchApplied(id, 1, new Date());

        ReferenceDataImportBatch found = importDAO.findBatchById(id);
        assertEquals(ReferenceDataImportBatch.STATUS_APPLIED, found.getStatus());
        assertEquals(Integer.valueOf(1), found.getAppliedByUserId());
    }

    @Test
    public void insertRow_andFindByBatchId_roundTrips() {
        ReferenceDataImportBatch batch = new ReferenceDataImportBatch();
        batch.setFeedType(ReferenceDataImportBatch.FEED_DIAGNOSIS_CODES);
        batch.setFileName("icd10.csv");
        batch.setFileHash("hash-3");
        batch.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        int batchId = importDAO.insertBatch(batch);

        ReferenceDataImportRow row = new ReferenceDataImportRow();
        row.setBatchId(batchId);
        row.setRowType(ReferenceDataImportRow.TYPE_ADD);
        row.setCode("E11.9");
        row.setDescription("Type 2 diabetes mellitus");
        int rowId = importDAO.insertRow(row);
        assertTrue(rowId > 0);

        List<ReferenceDataImportRow> rows = importDAO.findRowsByBatchId(batchId);
        assertEquals(1, rows.size());
        assertEquals("E11.9", rows.get(0).getCode());
        assertTrue(!rows.get(0).isApplied());

        importDAO.markRowApplied(rowId);
        assertTrue(importDAO.findRowsByBatchId(batchId).get(0).isApplied());
    }

    @Test(expected = DAOException.class)
    public void duplicateFileHash_violatesUniqueConstraint() {
        ReferenceDataImportBatch first = new ReferenceDataImportBatch();
        first.setFeedType(ReferenceDataImportBatch.FEED_NPPES);
        first.setFileName("a.csv");
        first.setFileHash("dup-hash");
        first.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        importDAO.insertBatch(first);

        ReferenceDataImportBatch second = new ReferenceDataImportBatch();
        second.setFeedType(ReferenceDataImportBatch.FEED_NPPES);
        second.setFileName("b.csv");
        second.setFileHash("dup-hash");
        second.setStatus(ReferenceDataImportBatch.STATUS_STAGED);
        importDAO.insertBatch(second);
    }

    @Test
    public void carcRarcCode_insertFindUpdate_roundTrips() {
        CarcRarcCode c = new CarcRarcCode();
        c.setCode("45");
        c.setCodeType(CarcRarcCode.TYPE_CARC);
        c.setDescription("Charge exceeds fee schedule");
        c.setActive(true);
        lookupDAO.insertCarcRarcCode(c);

        CarcRarcCode found = lookupDAO.findCarcRarcCode("45");
        assertEquals("CARC", found.getCodeType());
        assertEquals("Charge exceeds fee schedule", found.getDescription());

        found.setDescription("Updated description");
        lookupDAO.updateCarcRarcCode(found);
        assertEquals("Updated description", lookupDAO.findCarcRarcCode("45").getDescription());

        assertEquals(1, lookupDAO.findAllCarcRarcCodes().size());
    }

    @Test
    public void providerNpiValidation_defaultsUnverifiedAndUpdates() {
        Provider provider = new Provider();
        provider.setNpi("1234567893");
        provider.setName("Acme Clinic");
        provider.setProviderType(com.meridian.claims.model.ProviderType.INDIVIDUAL);
        provider.setNetworkStatus(com.meridian.claims.model.NetworkStatus.IN_NETWORK);
        providerDAO.insert(provider);

        Provider found = providerDAO.findById(provider.getId());
        assertEquals("UNVERIFIED", found.getNpiValidationStatus());
        assertNull(found.getNpiValidatedAt());

        Date now = new Date();
        providerDAO.updateNpiValidation(provider.getId(), "VALID", now);
        Provider updated = providerDAO.findById(provider.getId());
        assertEquals("VALID", updated.getNpiValidationStatus());
        assertTrue(updated.getNpiValidatedAt() != null);
    }
}
