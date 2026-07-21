package com.meridian.claims.dao;

import com.meridian.claims.model.Payment;
import java.util.List;

public interface PaymentDAO {
    void insert(Payment payment);
    Payment findById(int id);
    Payment findByClaimId(int claimId);
    List<Payment> findByStatus(String status);
    void updatePaid(int id, String referenceNumber, java.util.Date paymentDate,
                    java.math.BigDecimal amountPaid, java.math.BigDecimal remainingBalance,
                    boolean partial);
    void assignToBatch(int paymentId, int batchId);
    List<Payment> findByBatchId(int batchId);
}
