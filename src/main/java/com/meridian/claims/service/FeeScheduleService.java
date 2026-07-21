package com.meridian.claims.service;

import com.meridian.claims.dao.FeeScheduleRateDAO;
import com.meridian.claims.model.FeeScheduleRate;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class FeeScheduleService {

    private static final Logger LOG = Logger.getLogger(FeeScheduleService.class);

    private final FeeScheduleRateDAO feeScheduleRateDAO;
    private final AuditService auditService;

    @Autowired
    public FeeScheduleService(FeeScheduleRateDAO feeScheduleRateDAO, AuditService auditService) {
        this.feeScheduleRateDAO = feeScheduleRateDAO;
        this.auditService = auditService;
    }

    public FeeScheduleRate findById(int id) {
        FeeScheduleRate r = feeScheduleRateDAO.findById(id);
        if (r == null) {
            throw new ServiceException("Fee schedule rate id=" + id + " not found");
        }
        return r;
    }

    public Page<FeeScheduleRate> findByPlan(int planId, int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return feeScheduleRateDAO.findByPlanId(planId, page, size);
    }

    /**
     * Resolve allowed amount using fallback: provider-specific → plan-wide → null.
     * Returns null if no rate is on file (caller should flag NO_RATE).
     */
    public BigDecimal resolveAllowedAmount(int planId, Integer providerId, String procedureCode, Date serviceDate) {
        return feeScheduleRateDAO.resolveAllowedAmount(planId, providerId, procedureCode, serviceDate);
    }

    @Transactional
    public FeeScheduleRate createRate(int planId, Integer providerId, String procedureCode,
                                       BigDecimal allowedAmount, Date effectiveDate, Date terminationDate) {
        if (procedureCode == null || procedureCode.trim().isEmpty()) {
            throw new ServiceException("Procedure code is required");
        }
        if (allowedAmount == null || allowedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ServiceException("Allowed amount must be zero or greater");
        }
        if (effectiveDate == null) {
            throw new ServiceException("Effective date is required");
        }
        FeeScheduleRate r = new FeeScheduleRate();
        r.setPlanId(planId);
        r.setProviderId(providerId);
        r.setProcedureCode(procedureCode.trim().toUpperCase());
        r.setAllowedAmount(allowedAmount);
        r.setEffectiveDate(effectiveDate);
        r.setTerminationDate(terminationDate);
        feeScheduleRateDAO.insert(r);
        LOG.info("Created fee schedule rate id=" + r.getId() + " planId=" + planId + " code=" + procedureCode);
        auditService.record("FEE_RATE_CREATED", "FEE_SCHEDULE_RATE", (long) r.getId(),
            "Created rate for plan id=" + planId + " procedure " + r.getProcedureCode());
        return r;
    }

    @Transactional
    public void updateRate(int id, BigDecimal allowedAmount, Date effectiveDate, Date terminationDate) {
        FeeScheduleRate r = findById(id);
        r.setAllowedAmount(allowedAmount);
        r.setEffectiveDate(effectiveDate);
        r.setTerminationDate(terminationDate);
        feeScheduleRateDAO.update(r);
    }

    @Transactional
    public void expireRate(int id, Date terminationDate) {
        findById(id);
        feeScheduleRateDAO.expire(id, terminationDate);
        auditService.record("FEE_RATE_EXPIRED", "FEE_SCHEDULE_RATE", (long) id,
            "Expired fee schedule rate id=" + id);
    }
}
