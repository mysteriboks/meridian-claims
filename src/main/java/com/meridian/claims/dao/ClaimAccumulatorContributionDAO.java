package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimAccumulatorContribution;

public interface ClaimAccumulatorContributionDAO {
    void insert(ClaimAccumulatorContribution contribution);
    ClaimAccumulatorContribution findByClaimId(int claimId);
    void markReversed(int claimId);
}
