package com.meridian.claims.dao;

import com.meridian.claims.model.DenialReasonCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.ServiceTypeCategory;

import java.util.List;

public interface LookupDAO {

    // --- Denial Reason Codes ---
    List<DenialReasonCode> findAllDenialReasonCodes();
    DenialReasonCode findDenialReasonCode(String code);
    void insertDenialReasonCode(DenialReasonCode c);
    void updateDenialReasonCode(DenialReasonCode c);

    // --- Procedure Codes (CPT — admin-populated only, never seeded) ---
    List<ProcedureCode> findAllProcedureCodes();
    ProcedureCode findProcedureCode(String code);
    void insertProcedureCode(ProcedureCode c);
    void updateProcedureCode(ProcedureCode c);

    // --- Diagnosis Codes (ICD-10) ---
    List<DiagnosisCode> findAllDiagnosisCodes();
    DiagnosisCode findDiagnosisCode(String code);
    void insertDiagnosisCode(DiagnosisCode c);
    void updateDiagnosisCode(DiagnosisCode c);

    // --- Service Type Categories ---
    List<ServiceTypeCategory> findAllServiceTypeCategories();
    ServiceTypeCategory findServiceTypeCategory(String code);
    void insertServiceTypeCategory(ServiceTypeCategory c);
    void updateServiceTypeCategory(ServiceTypeCategory c);
}
