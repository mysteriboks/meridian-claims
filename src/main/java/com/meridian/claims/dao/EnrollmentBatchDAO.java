package com.meridian.claims.dao;

import com.meridian.claims.model.EnrollmentBatch;

import java.util.List;

/** DAO for the X12 834 enrollment file idempotency ledger (Phase 17). Mirrors {@link ClaimIntakeBatchDAO}. */
public interface EnrollmentBatchDAO {

    /** Insert a new batch row with status PROCESSING; returns the generated id. */
    int insert(EnrollmentBatch batch);

    /** Look up a batch by SHA-256 file hash (idempotency check). Returns null if not found. */
    EnrollmentBatch findByFileHash(String fileHash);

    /** Update counts and status once processing is complete or has failed. */
    void updateCompletion(int id, String status, int totalRecords, int succeeded, int quarantined, String errorMessage);

    /** Return the most recent N batch rows ordered by created_at DESC. */
    List<EnrollmentBatch> findRecent(int limit);
}
