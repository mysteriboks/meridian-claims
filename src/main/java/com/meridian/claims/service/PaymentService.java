package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.ClaimLineItemDAO;
import com.meridian.claims.dao.PaymentDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.Payment;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger LOG = Logger.getLogger(PaymentService.class);

    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimLineItemDAO lineItemDAO;
    @Autowired private PaymentDAO paymentDAO;

    /**
     * Creates a PENDING Payment record from an APPROVED claim's adjudicated line-item totals.
     * Called automatically when a claim is approved.
     */
    @Transactional
    public Payment generatePayment(int claimId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null) {
            throw new ServiceException("Cannot generate payment — claim not found: id=" + claimId);
        }
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ServiceException("Payment can only be generated for APPROVED claims; " +
                "current status=" + claim.getStatus());
        }
        // Guard against duplicate
        Payment existing = paymentDAO.findByClaimId(claimId);
        if (existing != null) {
            LOG.warn("Payment already exists for claimId=" + claimId + " paymentId=" + existing.getId());
            return existing;
        }

        List<ClaimLineItem> lineItems = lineItemDAO.findByClaimId(claimId);
        BigDecimal billedTotal  = Money.ZERO;
        BigDecimal allowedTotal = Money.ZERO;
        BigDecimal planTotal    = Money.ZERO;
        BigDecimal memberTotal  = Money.ZERO;

        for (ClaimLineItem li : lineItems) {
            if (li.getBilledAmount() != null)       billedTotal  = Money.add(billedTotal,  li.getBilledAmount());
            if (li.getAllowedAmount() != null)       allowedTotal = Money.add(allowedTotal, li.getAllowedAmount());
            if (li.getPlanPaidAmount() != null)      planTotal    = Money.add(planTotal,    li.getPlanPaidAmount());
            if (li.getMemberResponsibility() != null) memberTotal = Money.add(memberTotal,  li.getMemberResponsibility());
        }

        Payment payment = new Payment();
        payment.setClaimId(claimId);
        payment.setBilledTotal(billedTotal);
        payment.setAllowedTotal(allowedTotal);
        payment.setPlanPaidTotal(planTotal);
        payment.setMemberResponsibility(memberTotal);
        paymentDAO.insert(payment);

        // APPROVED → PENDING_PAYMENT per state machine (PHASES.md)
        claimDAO.updateStatus(claimId, ClaimStatus.PENDING_PAYMENT.name(), claim.getVersion());

        LOG.info("Payment generated claimId=" + claimId + " paymentId=" + payment.getId() +
            " planTotal=" + planTotal);
        return payment;
    }

    /**
     * Finance marks a payment as paid (full or partial).
     * On full payment the claim transitions to PENDING_PAYMENT → caller handles claim status.
     */
    @Transactional
    public void markPaid(int paymentId, String referenceNumber, Date paymentDate,
                         BigDecimal amountPaid, int actingUserId) {
        if (referenceNumber == null || referenceNumber.trim().isEmpty()) {
            throw new ServiceException("Reference number is required to mark a payment paid");
        }
        if (amountPaid == null || amountPaid.signum() <= 0) {
            throw new ServiceException("Amount paid must be positive");
        }
        Payment payment = paymentDAO.findById(paymentId);
        if (payment == null) {
            throw new ServiceException("Payment not found: id=" + paymentId);
        }
        if ("PAID".equals(payment.getStatus()) || "VOIDED".equals(payment.getStatus())) {
            throw new ServiceException("Payment id=" + paymentId + " is already " + payment.getStatus());
        }

        BigDecimal planTotal = payment.getPlanPaidTotal();
        if (amountPaid.compareTo(planTotal) > 0) {
            throw new ServiceException("Amount paid (" + amountPaid + ") exceeds plan paid total (" + planTotal + ")");
        }
        BigDecimal remaining = Money.max(Money.ZERO, Money.subtract(planTotal, amountPaid));
        boolean partial = remaining.compareTo(Money.ZERO) > 0;

        paymentDAO.updatePaid(paymentId, referenceNumber.trim(), paymentDate,
            amountPaid, partial ? remaining : null, partial);

        LOG.info("Payment marked paid id=" + paymentId + " amount=" + amountPaid +
            " partial=" + partial + " ref=" + referenceNumber);
    }

    public Payment findById(int paymentId) {
        return paymentDAO.findById(paymentId);
    }

    public Payment findByClaimId(int claimId) {
        return paymentDAO.findByClaimId(claimId);
    }

    public List<Payment> findPending() {
        return paymentDAO.findByStatus("PENDING");
    }
}
