package com.meridian.claims.dao;

import com.meridian.claims.model.SubrogationCase;
import java.math.BigDecimal;
import java.util.List;

public interface SubrogationCaseDAO {
    void insert(SubrogationCase sc);
    SubrogationCase findByClaimId(int claimId);
    SubrogationCase findById(int id);
    List<SubrogationCase> findByStatus(String status);
    void recordRecovery(int id, String liableParty, BigDecimal recoveryAmount, String notes);
    void close(int id);
}
