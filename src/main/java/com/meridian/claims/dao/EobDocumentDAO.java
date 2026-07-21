package com.meridian.claims.dao;

import com.meridian.claims.model.EobDocument;
import java.util.List;

public interface EobDocumentDAO {
    void insert(EobDocument doc);
    EobDocument findById(int id);
    EobDocument findByClaimId(int claimId);
    List<EobDocument> findByMemberId(int memberId);
    void markMailed(int id, int mailedByUserId);
}
