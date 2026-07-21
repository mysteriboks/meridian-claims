package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimAuditEntry;

import java.util.List;

public interface ClaimAuditDAO {
    void insert(ClaimAuditEntry entry);
    List<ClaimAuditEntry> findByClaimId(int claimId);
}
