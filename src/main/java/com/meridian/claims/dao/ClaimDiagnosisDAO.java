package com.meridian.claims.dao;

import com.meridian.claims.model.ClaimDiagnosis;

import java.util.List;

public interface ClaimDiagnosisDAO {
    void insertBatch(List<ClaimDiagnosis> diagnoses);
    List<ClaimDiagnosis> findByClaimId(int claimId);
}
