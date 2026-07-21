package com.meridian.claims.dao;

import com.meridian.claims.model.InfoRequest;
import java.util.List;

public interface InfoRequestDAO {
    void insert(InfoRequest infoRequest);
    InfoRequest findById(int id);
    List<InfoRequest> findByClaimId(int claimId);
    InfoRequest findOpenByClaimId(int claimId);
    void markResponded(int id, String responseNotes);
}
