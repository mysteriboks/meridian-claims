package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimIntakeBatch;

import java.util.List;

public interface ClaimIntakeBatchDAO {

    /** Insert a new batch row with status PROCESSING; returns the generated id. */
    int insert(ClaimIntakeBatch batch);

    /** Look up a batch by SHA-256 file hash (idempotency check). Returns null if not found. */
    ClaimIntakeBatch findByFileHash(String fileHash);

    /** Update counts and status once processing is complete or has failed. */
    void updateCompletion(int id, String status, int totalRecords, int succeeded, int quarantined, String errorMessage);

    /** Return the most recent N batch rows ordered by created_at DESC. */
    List<ClaimIntakeBatch> findRecent(int limit);
}
