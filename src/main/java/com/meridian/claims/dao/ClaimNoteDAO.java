package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimNote;
import java.util.List;

public interface ClaimNoteDAO {
    void insert(ClaimNote note);
    List<ClaimNote> findByClaimId(int claimId);
}
