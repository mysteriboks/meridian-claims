package com.meridian.claims.dao;

import com.meridian.claims.model.EftPayment;
import com.meridian.claims.model.Provider;
import com.meridian.claims.util.StartupValidator;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Phase 18 DAO integration tests against H2 (PostgreSQL mode). Verifies
 * eft_payments insert/round-trip and the providers.ach_* banking columns
 * added by the same migration.
 */
public class Phase18DaoIT {

    private BasicDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcEftPaymentDAO eftPaymentDAO;
    private JdbcProviderDAO providerDAO;
    private int paymentBatchId;
    private int remittanceBatchId;

    @Before
    public void setUp() throws Exception {
        dataSource = new BasicDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:meridian_phase18_" + System.nanoTime()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setInitialSize(2);
        dataSource.setMaxTotal(5);

        new StartupValidator(dataSource, "classpath:db/migration").afterPropertiesSet();

        jdbc = new JdbcTemplate(dataSource);
        eftPaymentDAO = new JdbcEftPaymentDAO();
        eftPaymentDAO.setJdbcTemplate(jdbc);
        providerDAO = new JdbcProviderDAO();
        providerDAO.setJdbcTemplate(jdbc);

        jdbc.update("INSERT INTO payment_batches (id, batch_date, total_amount, status) " +
            "VALUES (1, CURRENT_DATE, 144.00, 'EXPORTED')");
        paymentBatchId = 1;
        jdbc.update("INSERT INTO remittance_batches (id, payment_date, total_paid, status) " +
            "VALUES (1, CURRENT_DATE, 144.00, 'GENERATED')");
        remittanceBatchId = 1;
    }

    @After
    public void tearDown() throws Exception {
        if (dataSource != null) dataSource.close();
    }

    @Test
    public void insert_assignsIdAndRoundTripsFields() {
        EftPayment eftPayment = new EftPayment();
        eftPayment.setPaymentBatchId(paymentBatchId);
        eftPayment.setRemittanceBatchId(remittanceBatchId);
        eftPayment.setTrnReassociationNumber("EFT000000001");
        eftPayment.setAmount(new BigDecimal("144.00"));
        eftPayment.setSettlementStatus(EftPayment.STATUS_GENERATED);
        eftPayment.setAchFileReference("/tmp/eft-1.ach");
        eftPayment.setEntryCount(1);
        eftPayment.setSkippedProviderCount(0);

        int id = eftPaymentDAO.insert(eftPayment);
        assertTrue(id > 0);

        EftPayment found = eftPaymentDAO.findById(id);
        assertEquals(paymentBatchId, found.getPaymentBatchId());
        assertEquals(remittanceBatchId, found.getRemittanceBatchId());
        assertEquals("EFT000000001", found.getTrnReassociationNumber());
        assertEquals(new BigDecimal("144.00"), found.getAmount());
        assertEquals(EftPayment.STATUS_GENERATED, found.getSettlementStatus());
        assertEquals(1, found.getEntryCount());
    }

    @Test
    public void findByPaymentBatchId_andByRemittanceBatchId_bothResolve() {
        EftPayment eftPayment = new EftPayment();
        eftPayment.setPaymentBatchId(paymentBatchId);
        eftPayment.setRemittanceBatchId(remittanceBatchId);
        eftPayment.setTrnReassociationNumber("EFT000000001");
        eftPayment.setAmount(new BigDecimal("144.00"));
        eftPayment.setSettlementStatus(EftPayment.STATUS_GENERATED);
        int id = eftPaymentDAO.insert(eftPayment);

        assertEquals(id, eftPaymentDAO.findByPaymentBatchId(paymentBatchId).getId());
        assertEquals(id, eftPaymentDAO.findByRemittanceBatchId(remittanceBatchId).getId());
    }

    @Test
    public void findByPaymentBatchId_none_returnsNull() {
        assertNull(eftPaymentDAO.findByPaymentBatchId(999));
    }

    @Test
    public void updateSettlementStatus_persists() {
        EftPayment eftPayment = new EftPayment();
        eftPayment.setPaymentBatchId(paymentBatchId);
        eftPayment.setRemittanceBatchId(remittanceBatchId);
        eftPayment.setTrnReassociationNumber("EFT000000001");
        eftPayment.setAmount(new BigDecimal("144.00"));
        eftPayment.setSettlementStatus(EftPayment.STATUS_GENERATED);
        int id = eftPaymentDAO.insert(eftPayment);

        eftPaymentDAO.updateSettlementStatus(id, EftPayment.STATUS_SETTLED);

        assertEquals(EftPayment.STATUS_SETTLED, eftPaymentDAO.findById(id).getSettlementStatus());
    }

    @Test(expected = DAOException.class)
    public void duplicatePaymentBatchId_violatesUniqueConstraint() {
        EftPayment first = new EftPayment();
        first.setPaymentBatchId(paymentBatchId);
        first.setRemittanceBatchId(remittanceBatchId);
        first.setTrnReassociationNumber("EFT000000001");
        first.setAmount(new BigDecimal("144.00"));
        first.setSettlementStatus(EftPayment.STATUS_GENERATED);
        eftPaymentDAO.insert(first);

        jdbc.update("INSERT INTO remittance_batches (id, payment_date, total_paid, status) " +
            "VALUES (2, CURRENT_DATE, 50.00, 'GENERATED')");
        EftPayment second = new EftPayment();
        second.setPaymentBatchId(paymentBatchId); // same payment batch — must violate UNIQUE
        second.setRemittanceBatchId(2);
        second.setTrnReassociationNumber("EFT000000002");
        second.setAmount(new BigDecimal("50.00"));
        second.setSettlementStatus(EftPayment.STATUS_GENERATED);
        eftPaymentDAO.insert(second);
    }

    @Test
    public void providerBankingColumns_roundTrip() {
        Provider provider = new Provider();
        provider.setNpi("1234567893");
        provider.setName("Acme Clinic");
        provider.setProviderType(com.meridian.claims.model.ProviderType.INDIVIDUAL);
        provider.setNetworkStatus(com.meridian.claims.model.NetworkStatus.IN_NETWORK);
        providerDAO.insert(provider);

        providerDAO.updateBankingInfo(provider.getId(), "021000021", "00012345678", "CHECKING");

        Provider found = providerDAO.findById(provider.getId());
        assertEquals("021000021", found.getAchRoutingNumber());
        assertEquals("00012345678", found.getAchAccountNumber());
        assertEquals("CHECKING", found.getAchAccountType());
    }

    @Test
    public void providerBankingColumns_defaultNull() {
        Provider provider = new Provider();
        provider.setNpi("1234567894");
        provider.setName("Beta Clinic");
        provider.setProviderType(com.meridian.claims.model.ProviderType.INDIVIDUAL);
        provider.setNetworkStatus(com.meridian.claims.model.NetworkStatus.IN_NETWORK);
        providerDAO.insert(provider);

        Provider found = providerDAO.findById(provider.getId());
        assertNull(found.getAchRoutingNumber());
        assertNull(found.getAchAccountNumber());
        assertNull(found.getAchAccountType());
    }
}
