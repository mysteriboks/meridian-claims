package com.meridian.claims.dao;

import com.meridian.claims.model.Claim;
import java.util.Date;
import java.util.List;

public interface ClaimArchiveDAO {
    /**
     * Moves claims with date_of_service older than `cutoff` into claims_archive
     * (copy then delete from claims), returning the count archived.
     * Only terminal-state claims are archived (PAID/VOIDED/REPLACED/ABANDONED).
     */
    int archiveClaimsOlderThan(Date cutoff);

    /** Searches archived claims by claim number or member id. */
    List<Claim> search(String query);

    /** Loads a single archived claim by its original id. */
    Claim findById(int id);
}
