package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import com.meridian.claims.util.Page;

import java.util.Date;
import java.util.List;

public interface ClaimDAO {
    void insert(Claim claim);
    Claim findById(int id);
    Claim findByClaimNumber(String claimNumber);
    Claim findByMemberAndDOS(int memberId, int providerId, Date dos);
    List<Claim> findByMemberId(int memberId);
    Page<Claim> search(String query, String status, int page, int size);
    /**
     * @param scopeToUserId when non-null, restricts results to claims where
     *     created_by_user_id = scopeToUserId OR assigned_to_user_id = scopeToUserId.
     *     Pass null for REVIEWER / ADMIN (see all claims).
     */
    Page<Claim> searchWorklist(String query, String status, Integer assignedToUserId,
                               boolean unassignedOnly, boolean slaBreachedOnly,
                               Integer scopeToUserId, int page, int size);
    void update(Claim claim);
    void updateStatus(int id, String newStatus, int currentVersion);
    void updateAssignment(int id, Integer reviewerId, int currentVersion);
}
