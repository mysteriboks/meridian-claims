package com.meridian.claims.dao;

import com.meridian.claims.model.PhiAccessLog;
import java.util.List;

public interface PhiAccessLogDAO {
    void insert(PhiAccessLog log);
    List<PhiAccessLog> findByClaimId(int claimId);
}
