package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.PaymentBatchDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.PaymentBatch;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.*;

public class PaymentBatchServiceTest {

    private PaymentBatchService batchService;
    private PaymentBatchDAO paymentBatchDAO;
    private PaymentDAO paymentDAO;
    private ClaimDAO claimDAO;
    private ProviderDAO providerDAO;
    private ClaimService claimService;
    private SubrogationService subrogationService;
    private RemittanceService remittanceService;
    private AuditService auditService;

    @Before
    public void setUp() {
        batchService       = new PaymentBatchService();
        paymentBatchDAO    = Mockito.mock(PaymentBatchDAO.class);
        paymentDAO         = Mockito.mock(PaymentDAO.class);
        claimDAO           = Mockito.mock(ClaimDAO.class);
        providerDAO        = Mockito.mock(ProviderDAO.class);
        claimService       = Mockito.mock(ClaimService.class);
        subrogationService = Mockito.mock(SubrogationService.class);
        remittanceService  = Mockito.mock(RemittanceService.class);
        auditService       = Mockito.mock(AuditService.class);

        ReflectionTestUtils.setField(batchService, "paymentBatchDAO",    paymentBatchDAO);
        ReflectionTestUtils.setField(batchService, "paymentDAO",         paymentDAO);
        ReflectionTestUtils.setField(batchService, "claimDAO",           claimDAO);
        ReflectionTestUtils.setField(batchService, "providerDAO",        providerDAO);
        ReflectionTestUtils.setField(batchService, "claimService",       claimService);
        ReflectionTestUtils.setField(batchService, "subrogationService", subrogationService);
        ReflectionTestUtils.setField(batchService, "remittanceService",  remittanceService);
        ReflectionTestUtils.setField(batchService, "auditService",       auditService);
    }

    @Test
    public void createBatch_groupsPendingPayments() {
        Payment p = new Payment();
        p.setId(1);
        p.setClaimId(100);
        p.setPlanPaidTotal(Money.of("144.00"));
        Mockito.when(paymentDAO.findByStatus("PENDING")).thenReturn(Arrays.asList(p));

        Claim claim = new Claim();
        claim.setId(100);
        claim.setStatus(ClaimStatus.PENDING_PAYMENT);
        claim.setVersion(1);
        Mockito.when(claimDAO.findById(100)).thenReturn(claim);

        Mockito.doAnswer(inv -> { ((PaymentBatch)inv.getArguments()[0]).setId(99); return null; })
            .when(paymentBatchDAO).insert(Mockito.any(PaymentBatch.class));

        batchService.createBatch(new Date(), 1);

        Mockito.verify(paymentDAO).assignToBatch(1, 99);
        Mockito.verify(claimDAO).updateStatus(100, "IN_BATCH", 1);
        Mockito.verify(paymentBatchDAO).updateTotalAmount(99, Money.of("144.00"));
    }

    @Test(expected = ServiceException.class)
    public void createBatch_noPendingPayments_throws() {
        Mockito.when(paymentDAO.findByStatus("PENDING")).thenReturn(Collections.<Payment>emptyList());
        batchService.createBatch(new Date(), 1);
    }

    @Test
    public void exportCsv_movesClaims_toPaid() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(5);
        batch.setBatchDate(new Date());
        batch.setStatus("PENDING");
        Mockito.when(paymentBatchDAO.findById(5)).thenReturn(batch);

        Payment p = new Payment();
        p.setId(1);
        p.setClaimId(100);
        p.setPlanPaidTotal(Money.of("144.00"));
        p.setBilledTotal(Money.of("200.00"));
        p.setAllowedTotal(Money.of("180.00"));
        p.setMemberResponsibility(Money.of("36.00"));
        Mockito.when(paymentDAO.findByBatchId(5)).thenReturn(Arrays.asList(p));

        Claim claim = new Claim();
        claim.setId(100);
        claim.setClaimNumber("CLM-20260101-000001");
        claim.setProviderId(5);
        claim.setStatus(ClaimStatus.IN_BATCH);
        claim.setVersion(2);
        Mockito.when(claimDAO.findById(100)).thenReturn(claim);
        Mockito.when(providerDAO.findById(5)).thenReturn(new com.meridian.claims.model.Provider());

        String csv = batchService.exportCsv(5, 1);

        Mockito.verify(claimDAO).updateStatus(100, "PAID", 2);
        // Remittance advice generated simultaneously on export (PHASES.md Phase 6)
        Mockito.verify(remittanceService).generateBatch(Mockito.anyList(), Mockito.any(java.util.Date.class));
        assertNotNull(csv);
        assertTrue(csv.contains("CLM-20260101-000001"));
    }

    @Test(expected = ServiceException.class)
    public void exportCsv_alreadyExported_throws() {
        PaymentBatch batch = new PaymentBatch();
        batch.setId(6);
        batch.setStatus("EXPORTED");
        Mockito.when(paymentBatchDAO.findById(6)).thenReturn(batch);
        batchService.exportCsv(6, 1);
    }
}
