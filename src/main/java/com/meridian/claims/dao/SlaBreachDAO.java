package com.meridian.claims.dao;

import com.meridian.claims.model.SlaBreach;

public interface SlaBreachDAO {
    void insert(SlaBreach breach);
    boolean existsForClaimStatus(int claimId, String status);
}
