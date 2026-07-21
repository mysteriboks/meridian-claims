package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PaymentServiceTest {

    private PaymentService paymentService;
    private ClaimDAO claimDAO;
    private ClaimLineItemDAO lineItemDAO;
    private PaymentDAO paymentDAO;

    @Before
    public void setUp() {
        paymentService = new PaymentService();
        claimDAO       = Mockito.mock(ClaimDAO.class);
        lineItemDAO    = Mockito.mock(ClaimLineItemDAO.class);
        paymentDAO     = Mockito.mock(PaymentDAO.class);

        ReflectionTestUtils.setField(paymentService, "claimDAO",    claimDAO);
        ReflectionTestUtils.setField(paymentService, "lineItemDAO", lineItemDAO);
        ReflectionTestUtils.setField(paymentService, "paymentDAO",  paymentDAO);
    }

    @Test
    public void generatePayment_approved_insertsPaymentWithCorrectTotals() {
        Claim claim = approvedClaim(1);
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);
        Mockito.when(paymentDAO.findByClaimId(1)).thenReturn(null);

        ClaimLineItem li = new ClaimLineItem();
        li.setBilledAmount(Money.of("200.00"));
        li.setAllowedAmount(Money.of("180.00"));
        li.setPlanPaidAmount(Money.of("144.00"));
        li.setMemberResponsibility(Money.of("36.00"));
        Mockito.when(lineItemDAO.findByClaimId(1)).thenReturn(Arrays.asList(li));

        Payment payment = paymentService.generatePayment(1);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        Mockito.verify(paymentDAO).insert(captor.capture());
        assertNotNull(payment);
        Payment captured = captor.getValue();
        assertEquals("planPaidTotal should be 144.00", Money.of("144.00"), captured.getPlanPaidTotal());
        assertEquals("billedTotal should be 200.00",   Money.of("200.00"), captured.getBilledTotal());
    }

    @Test
    public void generatePayment_existingPayment_returnsExistingWithoutInsert() {
        Claim claim = approvedClaim(2);
        Mockito.when(claimDAO.findById(2)).thenReturn(claim);
        Payment existing = new Payment();
        existing.setId(99);
        Mockito.when(paymentDAO.findByClaimId(2)).thenReturn(existing);

        Payment result = paymentService.generatePayment(2);

        Mockito.verify(paymentDAO, Mockito.never()).insert(Mockito.any());
        assertNotNull(result);
    }

    @Test(expected = ServiceException.class)
    public void generatePayment_notApproved_throws() {
        Claim claim = approvedClaim(3);
        claim.setStatus(ClaimStatus.IN_REVIEW);
        Mockito.when(claimDAO.findById(3)).thenReturn(claim);
        paymentService.generatePayment(3);
    }

    @Test
    public void markPaid_fullPayment_callsUpdatePaid() {
        Payment payment = new Payment();
        payment.setId(10);
        payment.setStatus("PENDING");
        payment.setPlanPaidTotal(Money.of("144.00"));
        Mockito.when(paymentDAO.findById(10)).thenReturn(payment);

        paymentService.markPaid(10, "REF-001", new Date(), Money.of("144.00"), 1);

        Mockito.verify(paymentDAO).updatePaid(
            Mockito.eq(10),
            Mockito.eq("REF-001"),
            Mockito.any(Date.class),
            Mockito.eq(Money.of("144.00")),
            Mockito.any(),
            Mockito.eq(false));
    }

    @Test(expected = ServiceException.class)
    public void markPaid_missingReferenceNumber_throws() {
        Payment payment = new Payment();
        payment.setId(11);
        payment.setStatus("PENDING");
        payment.setPlanPaidTotal(Money.of("100.00"));
        Mockito.when(paymentDAO.findById(11)).thenReturn(payment);
        paymentService.markPaid(11, "", new Date(), Money.of("100.00"), 1);
    }

    @Test(expected = ServiceException.class)
    public void markPaid_alreadyPaid_throws() {
        Payment payment = new Payment();
        payment.setId(12);
        payment.setStatus("PAID");
        Mockito.when(paymentDAO.findById(12)).thenReturn(payment);
        paymentService.markPaid(12, "REF", new Date(), Money.of("50.00"), 1);
    }

    @Test(expected = ServiceException.class)
    public void markPaid_overPayment_throws() {
        Payment payment = new Payment();
        payment.setId(13);
        payment.setStatus("PENDING");
        payment.setPlanPaidTotal(Money.of("100.00"));
        Mockito.when(paymentDAO.findById(13)).thenReturn(payment);
        // amountPaid > planPaidTotal must be rejected
        paymentService.markPaid(13, "REF", new Date(), Money.of("150.00"), 1);
    }

    @Test
    public void findById_returnsRow() {
        Payment payment = new Payment();
        payment.setId(20);
        Mockito.when(paymentDAO.findById(20)).thenReturn(payment);
        Payment result = paymentService.findById(20);
        Mockito.verify(paymentDAO).findById(20);
        org.junit.Assert.assertEquals(20, result.getId());
    }

    private Claim approvedClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setStatus(ClaimStatus.APPROVED);
        return c;
    }
}
