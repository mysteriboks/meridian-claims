package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimLineItem;

import java.util.List;

public interface ClaimLineItemDAO {
    void insertBatch(List<ClaimLineItem> items);
    List<ClaimLineItem> findByClaimId(int claimId);
    void updateAmounts(ClaimLineItem item);
}
