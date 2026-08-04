package com.meridian.claims.service;

import com.meridian.claims.dao.EftPaymentDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.EftPayment;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EftPaymentServiceTest {

    private EftPaymentService service;
    private EftPaymentDAO eftPaymentDAO;
    private ProviderDAO providerDAO;

    private PaymentBatch paymentBatch;
    private RemittanceBatch remittanceBatch;

    @Before
    public void setUp() {
        eftPaymentDAO = mock(EftPaymentDAO.class);
        providerDAO = mock(ProviderDAO.class);
        service = new EftPaymentService(eftPaymentDAO, providerDAO);
        ReflectionTestUtils.setField(service, "originRoutingNumber", "123456789");
        ReflectionTestUtils.setField(service, "originName", "MERIDIAN CLAIMS");
        ReflectionTestUtils.setField(service, "companyId", "1234567890");
        ReflectionTestUtils.setField(service, "outputPath", "");

        paymentBatch = new PaymentBatch();
        paymentBatch.setId(42);
        paymentBatch.setBatchDate(new Date());

        remittanceBatch = new RemittanceBatch();
        remittanceBatch.setId(99);
    }

    private Provider providerWithBanking(int id, String name) {
        Provider p = new Provider();
        p.setId(id);
        p.setNpi("NPI" + id);
        p.setName(name);
        p.setAchRoutingNumber("021000021");
        p.setAchAccountNumber("00012345678");
        p.setAchAccountType("CHECKING");
        return p;
    }

    private RemittanceBatchItem item(int providerId, String planPaid) {
        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setProviderId(providerId);
        item.setPlanPaid(new BigDecimal(planPaid));
        return item;
    }

    @Test
    public void generateForBatch_providerWithBanking_createsEftPayment() {
        when(providerDAO.findById(5)).thenReturn(providerWithBanking(5, "Acme Clinic"));
        List<RemittanceBatchItem> items = Arrays.asList(item(5, "100.00"), item(5, "44.00"));

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, items);

        assertEquals(1, result.getEntryCount());
        assertEquals(0, result.getSkippedProviderCount());
        assertEquals(new BigDecimal("144.00"), result.getAmount());
        assertEquals("EFT000000042", result.getTrnReassociationNumber());
        assertEquals(EftPayment.STATUS_GENERATED, result.getSettlementStatus());
        verify(eftPaymentDAO).insert(result);
    }

    @Test
    public void generateForBatch_providerWithoutBanking_returnsNullAndInsertsNothing() {
        Provider unconfigured = new Provider();
        unconfigured.setId(5);
        when(providerDAO.findById(5)).thenReturn(unconfigured);
        List<RemittanceBatchItem> items = Arrays.asList(item(5, "100.00"));

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, items);

        assertNull(result);
        verify(eftPaymentDAO, never()).insert(any(EftPayment.class));
    }

    @Test
    public void generateForBatch_mixedProviders_skipsUnconfiguredOnesButProceeds() {
        when(providerDAO.findById(5)).thenReturn(providerWithBanking(5, "Configured Clinic"));
        Provider unconfigured = new Provider();
        unconfigured.setId(6);
        when(providerDAO.findById(6)).thenReturn(unconfigured);

        List<RemittanceBatchItem> items = Arrays.asList(item(5, "100.00"), item(6, "50.00"));

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, items);

        assertEquals(1, result.getEntryCount());
        assertEquals(1, result.getSkippedProviderCount());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
    }

    @Test
    public void generateForBatch_invalidRoutingNumber_treatedAsUnconfigured() {
        Provider badRouting = providerWithBanking(5, "Acme Clinic");
        badRouting.setAchRoutingNumber("12345"); // not 9 digits
        when(providerDAO.findById(5)).thenReturn(badRouting);

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, Arrays.asList(item(5, "100.00")));

        assertNull(result);
    }

    @Test
    public void generateForBatch_sumsMultipleLineItemsPerProvider() {
        when(providerDAO.findById(5)).thenReturn(providerWithBanking(5, "Acme Clinic"));
        List<RemittanceBatchItem> items = Arrays.asList(item(5, "10.00"), item(5, "20.00"), item(5, "30.00"));

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, items);

        assertEquals(1, result.getEntryCount());
        assertEquals(new BigDecimal("60.00"), result.getAmount());
    }

    @Test
    public void generateForBatch_multipleProviders_oneEntryEach() {
        when(providerDAO.findById(5)).thenReturn(providerWithBanking(5, "Provider Five"));
        when(providerDAO.findById(6)).thenReturn(providerWithBanking(6, "Provider Six"));
        List<RemittanceBatchItem> items = Arrays.asList(item(5, "100.00"), item(6, "50.00"));

        EftPayment result = service.generateForBatch(paymentBatch, remittanceBatch, items);

        assertEquals(2, result.getEntryCount());
        assertEquals(new BigDecimal("150.00"), result.getAmount());
    }

    @Test
    public void markSettled_updatesStatus() {
        EftPayment existing = new EftPayment();
        existing.setId(7);
        when(eftPaymentDAO.findById(7)).thenReturn(existing);

        service.markSettled(7);

        verify(eftPaymentDAO).updateSettlementStatus(7, EftPayment.STATUS_SETTLED);
    }

    @Test(expected = ServiceException.class)
    public void markSettled_unknownId_throws() {
        when(eftPaymentDAO.findById(999)).thenReturn(null);
        service.markSettled(999);
    }

    @Test
    public void regenerateAchText_reusesStoredTrnReassociationNumber() throws Exception {
        when(providerDAO.findById(5)).thenReturn(providerWithBanking(5, "Acme Clinic"));
        EftPayment eftPayment = new EftPayment();
        eftPayment.setTrnReassociationNumber("EFT000000042");

        String achText = service.regenerateAchText(paymentBatch, Arrays.asList(item(5, "100.00")), eftPayment);

        assertTrue(achText.contains("TRN*1*EFT000000042"));
    }

    @Test
    public void findByPaymentBatchId_delegatesToDao() {
        service.findByPaymentBatchId(42);
        verify(eftPaymentDAO).findByPaymentBatchId(42);
    }

    @Test
    public void findByRemittanceBatchId_delegatesToDao() {
        service.findByRemittanceBatchId(99);
        verify(eftPaymentDAO).findByRemittanceBatchId(99);
    }
}
