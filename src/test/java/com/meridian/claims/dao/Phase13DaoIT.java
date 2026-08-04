package com.meridian.claims.dao;

import com.meridian.claims.model.EdiTransaction;
import com.meridian.claims.model.TradingPartner;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 13 DAO integration tests against H2 (PostgreSQL mode).
 * Verifies trading_partners CRUD/active-filtering and the Phase 13 additions to
 * edi_transactions (trading_partner_id attribution, findByFileReference).
 */
public class Phase13DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcTradingPartnerDAO tradingPartnerDAO;
    private JdbcEdiTransactionDAO ediTransactionDAO;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase13_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        tradingPartnerDAO = new JdbcTradingPartnerDAO();
        tradingPartnerDAO.setJdbcTemplate(jdbc);
        ediTransactionDAO = new JdbcEdiTransactionDAO();
        ediTransactionDAO.setJdbcTemplate(jdbc);
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insertAndFindById_roundTripsAllFields() {
        TradingPartner p = sftpPartner("Acme Health", true);
        int id = tradingPartnerDAO.insert(p);

        TradingPartner found = tradingPartnerDAO.findById(id);
        assertEquals("Acme Health", found.getPartnerName());
        assertEquals("ZZ", found.getIsaQualifier());
        assertEquals("ACMEHEALTH", found.getIsaId());
        assertEquals("ACMEGS", found.getGsId());
        assertEquals("837,999,277CA", found.getEnabledTransactions());
        assertEquals(TradingPartner.TRANSPORT_SFTP, found.getTransportType());
        assertEquals("sftp.acme.example.com", found.getTransportHost());
        assertEquals(Integer.valueOf(2222), found.getTransportPort());
        assertEquals("meridian-svc", found.getTransportUsername());
        assertEquals("acme-primary", found.getTransportCredentialRef());
        assertEquals("/inbound", found.getInboundPath());
        assertEquals("/outbound", found.getOutboundPath());
        assertTrue(found.isActive());
    }

    @Test
    public void update_persistsChanges() {
        TradingPartner p = localPartner("Beta Clinic", true);
        int id = tradingPartnerDAO.insert(p);

        TradingPartner toUpdate = tradingPartnerDAO.findById(id);
        toUpdate.setPartnerName("Beta Clinic Group");
        toUpdate.setInboundPath("/new/inbound");
        tradingPartnerDAO.update(toUpdate);

        TradingPartner found = tradingPartnerDAO.findById(id);
        assertEquals("Beta Clinic Group", found.getPartnerName());
        assertEquals("/new/inbound", found.getInboundPath());
    }

    @Test
    public void findAllActive_excludesInactivePartners() {
        tradingPartnerDAO.insert(localPartner("Active Partner", true));
        int inactiveId = tradingPartnerDAO.insert(localPartner("Inactive Partner", false));

        List<TradingPartner> active = tradingPartnerDAO.findAllActive();
        assertEquals(1, active.size());
        assertEquals("Active Partner", active.get(0).getPartnerName());

        List<TradingPartner> all = tradingPartnerDAO.findAll();
        assertEquals(2, all.size());
        assertFalse(findById(all, inactiveId).isActive());
    }

    @Test
    public void ediTransaction_tradingPartnerId_roundTrips() {
        TradingPartner p = localPartner("Gamma Provider", true);
        int partnerId = tradingPartnerDAO.insert(p);

        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("837");
        inbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        inbound.setFileReference("partner-file.edi");
        inbound.setTradingPartnerId(partnerId);
        int inboundId = ediTransactionDAO.insert(inbound);

        EdiTransaction found = ediTransactionDAO.findRecent(10).get(0);
        assertEquals(inboundId, found.getId());
        assertEquals(Integer.valueOf(partnerId), found.getTradingPartnerId());
    }

    @Test
    public void ediTransaction_globalRow_hasNullTradingPartnerId() {
        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("837");
        inbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        inbound.setFileReference("global-file.edi");
        ediTransactionDAO.insert(inbound);

        EdiTransaction found = ediTransactionDAO.findRecent(10).get(0);
        assertNull(found.getTradingPartnerId());
    }

    @Test
    public void findByFileReference_returnsInboundAndAllAcksForThatFile() {
        EdiTransaction inbound = new EdiTransaction();
        inbound.setDirection(EdiTransaction.DIRECTION_INBOUND);
        inbound.setTransactionType("837");
        inbound.setStatus(EdiTransaction.STATUS_ACCEPTED);
        inbound.setFileReference("claims-batch.edi");
        int inboundId = ediTransactionDAO.insert(inbound);

        EdiTransaction ack999 = new EdiTransaction();
        ack999.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack999.setTransactionType("999");
        ack999.setStatus(EdiTransaction.STATUS_ACCEPTED);
        ack999.setRelatedTransactionId(inboundId);
        ack999.setFileReference("claims-batch.edi");
        ack999.setDetail("ISA*00*999-content");
        ediTransactionDAO.insert(ack999);

        EdiTransaction ack277 = new EdiTransaction();
        ack277.setDirection(EdiTransaction.DIRECTION_OUTBOUND);
        ack277.setTransactionType("277CA");
        ack277.setStatus(EdiTransaction.STATUS_ACCEPTED);
        ack277.setRelatedTransactionId(inboundId);
        ack277.setFileReference("claims-batch.edi");
        ack277.setDetail("ISA*00*277-content");
        ediTransactionDAO.insert(ack277);

        // Unrelated file — must not be returned.
        EdiTransaction other = new EdiTransaction();
        other.setDirection(EdiTransaction.DIRECTION_INBOUND);
        other.setTransactionType("837");
        other.setStatus(EdiTransaction.STATUS_ACCEPTED);
        other.setFileReference("other-file.edi");
        ediTransactionDAO.insert(other);

        List<EdiTransaction> forFile = ediTransactionDAO.findByFileReference("claims-batch.edi");
        assertEquals(3, forFile.size());
        assertEquals("837", forFile.get(0).getTransactionType());
        assertEquals("999", forFile.get(1).getTransactionType());
        assertEquals("277CA", forFile.get(2).getTransactionType());
    }

    private TradingPartner sftpPartner(String name, boolean active) {
        TradingPartner p = localPartner(name, active);
        p.setTransportType(TradingPartner.TRANSPORT_SFTP);
        p.setTransportHost("sftp.acme.example.com");
        p.setTransportPort(2222);
        p.setTransportUsername("meridian-svc");
        p.setTransportCredentialRef("acme-primary");
        p.setIsaId("ACMEHEALTH");
        p.setGsId("ACMEGS");
        p.setEnabledTransactions("837,999,277CA");
        return p;
    }

    private TradingPartner localPartner(String name, boolean active) {
        TradingPartner p = new TradingPartner();
        p.setPartnerName(name);
        p.setIsaQualifier("ZZ");
        p.setIsaId("PARTNERID");
        p.setGsId("PARTNERGS");
        p.setTransportType(TradingPartner.TRANSPORT_LOCAL);
        p.setInboundPath("/inbound");
        p.setOutboundPath("/outbound");
        p.setActive(active);
        return p;
    }

    private TradingPartner findById(List<TradingPartner> list, int id) {
        for (TradingPartner p : list) {
            if (p.getId() == id) return p;
        }
        throw new AssertionError("No trading partner with id=" + id);
    }
}
