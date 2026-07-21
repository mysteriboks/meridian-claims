package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.dao.RemittanceBatchDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.model.RemittanceBatch;
import com.meridian.claims.model.RemittanceBatchItem;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertTrue;

public class RemittanceServiceTest {

    private RemittanceService remittanceService;
    private PaymentDAO paymentDAO;
    private ClaimDAO claimDAO;
    private ClaimLineItemDAO lineItemDAO;
    private RemittanceBatchDAO remittanceBatchDAO;

    @Before
    public void setUp() {
        remittanceService  = new RemittanceService();
        paymentDAO         = Mockito.mock(PaymentDAO.class);
        claimDAO           = Mockito.mock(ClaimDAO.class);
        lineItemDAO        = Mockito.mock(ClaimLineItemDAO.class);
        remittanceBatchDAO = Mockito.mock(RemittanceBatchDAO.class);

        ReflectionTestUtils.setField(remittanceService, "paymentDAO",         paymentDAO);
        ReflectionTestUtils.setField(remittanceService, "claimDAO",           claimDAO);
        ReflectionTestUtils.setField(remittanceService, "lineItemDAO",        lineItemDAO);
        ReflectionTestUtils.setField(remittanceService, "remittanceBatchDAO", remittanceBatchDAO);
    }

    @Test
    public void generateBatch_insertsItemsForEachLineItem() {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setClaimId(100);
        Mockito.when(paymentDAO.findById(1)).thenReturn(payment);

        Claim claim = new Claim();
        claim.setId(100);
        claim.setProviderId(5);
        claim.setStatus(ClaimStatus.APPROVED);
        Mockito.when(claimDAO.findById(100)).thenReturn(claim);

        ClaimLineItem li = new ClaimLineItem();
        li.setBilledAmount(Money.of("200.00"));
        li.setAllowedAmount(Money.of("180.00"));
        li.setPlanPaidAmount(Money.of("144.00"));
        li.setAdjustmentReasonCode("45");
        Mockito.when(lineItemDAO.findByClaimId(100)).thenReturn(Arrays.asList(li));

        // insertBatch sets id on the batch object
        Mockito.doAnswer(inv -> {
            ((RemittanceBatch) inv.getArguments()[0]).setId(99);
            return null;
        }).when(remittanceBatchDAO).insertBatch(Mockito.any(RemittanceBatch.class));

        remittanceService.generateBatch(Arrays.asList(1), new Date());

        Mockito.verify(remittanceBatchDAO).insertBatch(Mockito.any(RemittanceBatch.class));
        Mockito.verify(remittanceBatchDAO).insertItem(Mockito.any(RemittanceBatchItem.class));
    }

    @Test(expected = ServiceException.class)
    public void generateBatch_emptyList_throws() {
        remittanceService.generateBatch(Collections.<Integer>emptyList(), new Date());
    }

    @Test
    public void buildHtml_containsProviderAndAmounts() {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(1);
        batch.setPaymentDate(new Date());
        batch.setTotalPaid(Money.of("144.00"));
        Mockito.when(remittanceBatchDAO.findById(1)).thenReturn(batch);

        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setId(1);
        item.setBatchId(1);
        item.setClaimId(100);
        item.setProviderId(5);
        item.setBilled(Money.of("200.00"));
        item.setAllowed(Money.of("180.00"));
        item.setPlanPaid(Money.of("144.00"));
        item.setAdjustmentReasonCode("45");
        Mockito.when(remittanceBatchDAO.findItemsByBatchId(1)).thenReturn(Arrays.asList(item));

        String html = remittanceService.buildHtml(1);

        assertTrue(html.contains("144.00"));
        assertTrue(html.contains("45"));
        assertTrue(html.contains("Provider ID: 5"));
    }

    @Test
    public void buildHtml_escapesCarcWithAngleBrackets() {
        RemittanceBatch batch = new RemittanceBatch();
        batch.setId(2);
        batch.setPaymentDate(new Date());
        batch.setTotalPaid(Money.of("10.00"));
        Mockito.when(remittanceBatchDAO.findById(2)).thenReturn(batch);

        RemittanceBatchItem item = new RemittanceBatchItem();
        item.setId(2);
        item.setBatchId(2);
        item.setClaimId(200);
        item.setProviderId(9);
        item.setBilled(Money.of("10.00"));
        item.setAllowed(Money.of("10.00"));
        item.setPlanPaid(Money.of("10.00"));
        item.setAdjustmentReasonCode("<script>");
        Mockito.when(remittanceBatchDAO.findItemsByBatchId(2)).thenReturn(Arrays.asList(item));

        String html = remittanceService.buildHtml(2);

        assertTrue(html.contains("&lt;script&gt;"));
        org.junit.Assert.assertFalse(html.contains("<script>"));
    }
}
