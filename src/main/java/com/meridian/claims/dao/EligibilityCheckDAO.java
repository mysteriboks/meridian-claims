package com.meridian.claims.dao;

import com.meridian.claims.model.EligibilityCheck;

import java.util.List;

/** DAO for the eligibility-check event log ({@code eligibility_checks}, Phase 16). */
public interface EligibilityCheckDAO {

    int insert(EligibilityCheck check);

    /** Most recent checks for a member, newest first. */
    List<EligibilityCheck> findByMemberId(int memberId, int limit);
}
