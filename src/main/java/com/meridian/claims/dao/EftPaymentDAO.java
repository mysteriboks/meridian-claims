package com.meridian.claims.dao;

import com.meridian.claims.model.EftPayment;

import java.util.List;

/** DAO for EFT/ACH payment file generation events (Phase 18). */
public interface EftPaymentDAO {

    int insert(EftPayment eftPayment);

    EftPayment findById(int id);

    /** One EFT payment per payment batch (UNIQUE constraint); null if none generated yet. */
    EftPayment findByPaymentBatchId(int paymentBatchId);

    /** Looked up from the 835-download endpoint, which only has the remittance batch id. Null if none. */
    EftPayment findByRemittanceBatchId(int remittanceBatchId);

    void updateSettlementStatus(int id, String settlementStatus);

    List<EftPayment> findRecent(int limit);
}
