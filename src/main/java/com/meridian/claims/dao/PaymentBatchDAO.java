package com.meridian.claims.dao;

import com.meridian.claims.model.PaymentBatch;
import java.math.BigDecimal;
import java.util.List;

public interface PaymentBatchDAO {
    void insert(PaymentBatch batch);
    PaymentBatch findById(int id);
    List<PaymentBatch> findAll();
    void updateTotalAmount(int id, BigDecimal totalAmount);
    void updateStatus(int id, String status, String fileReference);
}
