package com.meridian.claims.dao;

import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 12 DAO integration tests against H2 (PostgreSQL mode).
 * Verifies the edi_transactions table: insert, findRecent ordering/limit,
 * and the related_transaction_id ack-to-inbound linkage.
 */
public class Phase12DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcEdiTransactionDAO ediTransactionDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase12_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        ediTransactionDAO = new JdbcEdiTransactionDAO();
        ediTransactionDAO.setJdbcTemplate(jdbc);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insertAndFindRecent_inboundTransaction() {
        int id = ediTransactionDAO.insert(inbound("ISA-1", "1", EdiTransaction.STATUS_ACCEPTED, "file1.edi"));

        java.util.List<EdiTransaction> recent = ediTransactionDAO.findRecent(10);
        assertEquals(1, recent.size());
        EdiTransaction found = recent.get(0);
        assertEquals(id, found.getId());
        assertEquals("INBOUND", found.getDirection());
        assertEquals("837", found.getTransactionType());
        assertEquals("ISA-1", found.getIsaControlNumber());
        assertEquals("1", found.getGsControlNumber());
        assertEquals("ACCEPTED", found.getStatus());
        assertNull("no related transaction for the inbound row itself", found.getRelatedTransactionId());
    }

    @Test
    public void ackTransaction_linksBackToInboundViaRelatedTransactionId() {
        int inboundId = ediTransactionDAO.insert(inbound("ISA-2", "1", EdiTransaction.STATUS_PARTIAL, "file2.edi"));

        EdiTransaction ack = new EdiTransaction();
        ack.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack.setTransactionType("999");
        ack.setIsaControlNumber("ISA-2");
        ack.setGsControlNumber("1");
        ack.setStatus(EdiTransaction.STATUS_PARTIAL);
        ack.setRelatedTransactionId(inboundId);
        ack.setFileReference("file2.edi");
        ack.setDetail("ISA*00*...");
        int ackId = ediTransactionDAO.insert(ack);

        java.util.List<EdiTransaction> recent = ediTransactionDAO.findRecent(10);
        EdiTransaction found = findById(recent, ackId);
        assertEquals(Integer.valueOf(inboundId), found.getRelatedTransactionId());
        assertEquals("999", found.getTransactionType());
        assertTrue("detail (EDI text) is persisted", found.getDetail().startsWith("ISA*00*"));
    }

    @Test
    public void findRecent_limitsAndOrdersByCreatedAtDesc() {
        int id1 = ediTransactionDAO.insert(inbound("ISA-old", "1", EdiTransaction.STATUS_ACCEPTED, "oldest.edi"));
        int id2 = ediTransactionDAO.insert(inbound("ISA-mid", "1", EdiTransaction.STATUS_ACCEPTED, "middle.edi"));
        int id3 = ediTransactionDAO.insert(inbound("ISA-new", "1", EdiTransaction.STATUS_ACCEPTED, "newest.edi"));

        // H2's CURRENT_TIMESTAMP can collide across fast inserts; stamp explicitly (Phase9DaoIT precedent).
        jdbc.update("UPDATE edi_transactions SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 09:00:00"), id1);
        jdbc.update("UPDATE edi_transactions SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 10:00:00"), id2);
        jdbc.update("UPDATE edi_transactions SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.valueOf("2026-01-01 11:00:00"), id3);

        java.util.List<EdiTransaction> recent = ediTransactionDAO.findRecent(2);
        assertEquals(2, recent.size());
        assertEquals("newest.edi", recent.get(0).getFileReference());
        assertEquals("middle.edi", recent.get(1).getFileReference());
    }

    @Test
    public void ta1Transaction_hasNoGsOrStControlNumber() {
        EdiTransaction ta1 = new EdiTransaction();
        ta1.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ta1.setTransactionType("TA1");
        ta1.setIsaControlNumber(null); // ISA13 unrecoverable
        ta1.setStatus(EdiTransaction.STATUS_REJECTED);
        ta1.setFileReference("garbled.edi");
        int id = ediTransactionDAO.insert(ta1);

        EdiTransaction found = findById(ediTransactionDAO.findRecent(10), id);
        assertNull(found.getIsaControlNumber());
        assertNull(found.getGsControlNumber());
        assertNull(found.getStControlNumber());
        assertEquals("REJECTED", found.getStatus());
    }

    private EdiTransaction inbound(String isa, String gs, String status, String fileRef) {
        EdiTransaction t = new EdiTransaction();
        t.setDirection(EdiTransaction.DIRECTION_INBOUND);
        t.setTransactionType("837");
        t.setIsaControlNumber(isa);
        t.setGsControlNumber(gs);
        t.setStatus(status);
        t.setFileReference(fileRef);
        return t;
    }

    private EdiTransaction findById(java.util.List<EdiTransaction> list, int id) {
        for (EdiTransaction t : list) {
            if (t.getId() == id) return t;
        }
        throw new AssertionError("No transaction with id=" + id);
    }
}
