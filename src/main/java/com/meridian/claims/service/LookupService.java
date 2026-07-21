package com.meridian.claims.service;

import com.meridian.claims.dao.LookupDAO;
import com.meridian.claims.model.DenialReasonCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.ServiceTypeCategory;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

/**
 * Lookup tables are small and read on nearly every screen, so they are cached
 * in memory at startup and served from the cache. Writes go through to the DB
 * and then refresh the affected slice. An Admin can force a full reload via
 * {@link #refreshAll()} (wired to POST /admin/lookups/refresh) without a
 * redeploy — satisfying the Phase 3 "cached on startup, refreshable via Admin
 * action" deliverable.
 *
 * The cached lists are replaced wholesale (copy-on-write) under a lock, and the
 * volatile references are handed out as unmodifiable views, so concurrent
 * readers always see a consistent snapshot without locking.
 */
@Service
public class LookupService {

    private static final Logger LOG = Logger.getLogger(LookupService.class);

    private final LookupDAO lookupDAO;
    private final Object refreshLock = new Object();

    private volatile List<DenialReasonCode> denialReasonCodes = Collections.emptyList();
    private volatile List<ProcedureCode> procedureCodes = Collections.emptyList();
    private volatile List<DiagnosisCode> diagnosisCodes = Collections.emptyList();
    private volatile List<ServiceTypeCategory> serviceTypeCategories = Collections.emptyList();

    @Autowired
    public LookupService(LookupDAO lookupDAO) {
        this.lookupDAO = lookupDAO;
    }

    @PostConstruct
    public void refreshAll() {
        synchronized (refreshLock) {
            denialReasonCodes = Collections.unmodifiableList(lookupDAO.findAllDenialReasonCodes());
            procedureCodes = Collections.unmodifiableList(lookupDAO.findAllProcedureCodes());
            diagnosisCodes = Collections.unmodifiableList(lookupDAO.findAllDiagnosisCodes());
            serviceTypeCategories = Collections.unmodifiableList(lookupDAO.findAllServiceTypeCategories());
        }
        LOG.info("Lookup caches refreshed: " + denialReasonCodes.size() + " denial reasons, "
            + procedureCodes.size() + " procedure codes, " + diagnosisCodes.size() + " diagnosis codes, "
            + serviceTypeCategories.size() + " service types");
    }

    // --- Denial Reason Codes ---

    public List<DenialReasonCode> listDenialReasonCodes() {
        return denialReasonCodes;
    }

    public DenialReasonCode getDenialReasonCode(String code) {
        for (DenialReasonCode c : denialReasonCodes) {
            if (c.getCode().equals(code)) {
                return c;
            }
        }
        return null;
    }

    public void saveDenialReasonCode(DenialReasonCode c) {
        if (lookupDAO.findDenialReasonCode(c.getCode()) == null) {
            lookupDAO.insertDenialReasonCode(c);
        } else {
            lookupDAO.updateDenialReasonCode(c);
        }
        synchronized (refreshLock) {
            denialReasonCodes = Collections.unmodifiableList(lookupDAO.findAllDenialReasonCodes());
        }
    }

    // --- Procedure Codes ---

    public List<ProcedureCode> listProcedureCodes() {
        return procedureCodes;
    }

    public ProcedureCode getProcedureCode(String code) {
        for (ProcedureCode c : procedureCodes) {
            if (c.getCode().equals(code)) {
                return c;
            }
        }
        return null;
    }

    public void saveProcedureCode(ProcedureCode c) {
        if (c.getCode() == null || c.getCode().trim().isEmpty()) {
            throw new ServiceException("Procedure code is required");
        }
        if (lookupDAO.findProcedureCode(c.getCode()) == null) {
            lookupDAO.insertProcedureCode(c);
        } else {
            lookupDAO.updateProcedureCode(c);
        }
        synchronized (refreshLock) {
            procedureCodes = Collections.unmodifiableList(lookupDAO.findAllProcedureCodes());
        }
    }

    // --- Diagnosis Codes ---

    public List<DiagnosisCode> listDiagnosisCodes() {
        return diagnosisCodes;
    }

    public DiagnosisCode getDiagnosisCode(String code) {
        for (DiagnosisCode c : diagnosisCodes) {
            if (c.getCode().equals(code)) {
                return c;
            }
        }
        return null;
    }

    public void saveDiagnosisCode(DiagnosisCode c) {
        if (c.getCode() == null || c.getCode().trim().isEmpty()) {
            throw new ServiceException("Diagnosis code is required");
        }
        if (lookupDAO.findDiagnosisCode(c.getCode()) == null) {
            lookupDAO.insertDiagnosisCode(c);
        } else {
            lookupDAO.updateDiagnosisCode(c);
        }
        synchronized (refreshLock) {
            diagnosisCodes = Collections.unmodifiableList(lookupDAO.findAllDiagnosisCodes());
        }
    }

    // --- Service Type Categories ---

    public List<ServiceTypeCategory> listServiceTypeCategories() {
        return serviceTypeCategories;
    }

    public ServiceTypeCategory getServiceTypeCategory(String code) {
        for (ServiceTypeCategory c : serviceTypeCategories) {
            if (c.getCode().equals(code)) {
                return c;
            }
        }
        return null;
    }

    public void saveServiceTypeCategory(ServiceTypeCategory c) {
        if (c.getCode() == null || c.getCode().trim().isEmpty()) {
            throw new ServiceException("Category code is required");
        }
        if (lookupDAO.findServiceTypeCategory(c.getCode()) == null) {
            lookupDAO.insertServiceTypeCategory(c);
        } else {
            lookupDAO.updateServiceTypeCategory(c);
        }
        synchronized (refreshLock) {
            serviceTypeCategories = Collections.unmodifiableList(lookupDAO.findAllServiceTypeCategories());
        }
    }
}
