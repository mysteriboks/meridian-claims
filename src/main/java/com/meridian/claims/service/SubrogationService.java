package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.SubrogationCaseDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.SubrogationCase;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class SubrogationService {

    private static final Logger LOG = Logger.getLogger(SubrogationService.class);

    @Autowired private SubrogationCaseDAO subrogationCaseDAO;
    @Autowired private ClaimDAO claimDAO;
    @Autowired private AuditService auditService;

    /**
     * Auto-creates a subrogation case for a PAID accident-flagged claim.
     * Called from ClaimService when a claim moves to PAID and accidentIndicator=true.
     */
    @Transactional
    public SubrogationCase openIfAccident(int claimId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null || !claim.isAccidentIndicator()) {
            return null;
        }
        // Idempotent: don't create a second case if one already exists
        if (subrogationCaseDAO.findByClaimId(claimId) != null) {
            LOG.warn("Subrogation case already exists for claimId=" + claimId);
            return subrogationCaseDAO.findByClaimId(claimId);
        }
        SubrogationCase sc = new SubrogationCase();
        sc.setClaimId(claimId);
        sc.setOpenedDate(new Date());
        subrogationCaseDAO.insert(sc);

        auditService.record("SUBROGATION_OPENED", "CLAIM", (long) claimId,
            "Subrogation case id=" + sc.getId() + " opened for accident claim");
        LOG.info("Subrogation case opened claimId=" + claimId + " caseId=" + sc.getId());
        return sc;
    }

    /**
     * Records a third-party recovery. Recovery is plan income only — it does NOT
     * alter member deductible/OOP accumulators (member paid what they paid).
     */
    @Transactional
    public void recordRecovery(int caseId, String liableParty, BigDecimal recoveryAmount,
                                String notes, int userId) {
        if (recoveryAmount == null || recoveryAmount.signum() <= 0) {
            throw new ServiceException("Recovery amount must be positive");
        }
        SubrogationCase sc = requireCase(caseId);
        if (!"OPEN".equals(sc.getStatus())) {
            throw new ServiceException("Subrogation case id=" + caseId + " is already " + sc.getStatus());
        }
        subrogationCaseDAO.recordRecovery(caseId, liableParty, recoveryAmount, notes);
        auditService.record("SUBROGATION_RECOVERED", "CLAIM", (long) sc.getClaimId(),
            "Recovery $" + recoveryAmount + " from " + liableParty);
        LOG.info("Recovery recorded caseId=" + caseId + " amount=" + recoveryAmount);
    }

    @Transactional
    public void closeCase(int caseId, int userId) {
        SubrogationCase sc = requireCase(caseId);
        subrogationCaseDAO.close(caseId);
        auditService.record("SUBROGATION_CLOSED", "CLAIM", (long) sc.getClaimId(),
            "Subrogation case id=" + caseId + " closed by user id=" + userId);
    }

    public SubrogationCase findById(int id) {
        return subrogationCaseDAO.findById(id);
    }

    public List<SubrogationCase> findOpen() {
        return subrogationCaseDAO.findByStatus("OPEN");
    }

    private SubrogationCase requireCase(int id) {
        SubrogationCase sc = subrogationCaseDAO.findById(id);
        if (sc == null) {
            throw new ServiceException("Subrogation case not found: id=" + id);
        }
        return sc;
    }
}
