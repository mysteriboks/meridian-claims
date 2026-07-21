package com.meridian.claims.dao;

import com.meridian.claims.model.AdjudicationResult;

import java.util.List;

public interface AdjudicationResultsDAO {
    void insertBatch(List<AdjudicationResult> results);
    List<AdjudicationResult> findByClaimId(int claimId);
    void deleteByClaimId(int claimId);

    /** Highest run_id recorded for a claim, or 0 if none. Used to append the next run. */
    int maxRunId(int claimId);
}
