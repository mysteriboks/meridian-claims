package com.meridian.claims.service;

import com.meridian.claims.dao.AppealDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Appeal;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class AppealService {

    private static final Logger LOG = Logger.getLogger(AppealService.class);

    @Autowired private AppealDAO appealDAO;
    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimService claimService;
    @Autowired private AuditService auditService;

    @Value("${claims.appeal.internal.days:30}")
    private int internalDays;

    @Value("${claims.appeal.external.days:72}")
    private int externalDays;

    /** Submit an appeal against a DENIED claim. */
    @Transactional
    public Appeal submitAppeal(int claimId, String appealType, int submittedByUserId) {
        Claim claim = claimDAO.findById(claimId);
        if (claim == null) {
            throw new ServiceException("Claim not found: id=" + claimId);
        }
        if (claim.getStatus() != ClaimStatus.DENIED) {
            throw new ServiceException("Appeals can only be submitted against DENIED claims; " +
                "current status=" + claim.getStatus());
        }
        if (!"INTERNAL".equals(appealType) && !"EXTERNAL".equals(appealType)) {
            throw new ServiceException("Appeal type must be INTERNAL or EXTERNAL");
        }

        Date today = new Date();
        Date deadline = addDays(today, "INTERNAL".equals(appealType) ? internalDays : externalDays);

        Appeal appeal = new Appeal();
        appeal.setClaimId(claimId);
        appeal.setMemberId(claim.getMemberId());
        appeal.setAppealType(appealType);
        appeal.setSubmittedDate(today);
        appeal.setDeadlineDate(deadline);
        appeal.setSubmittedByUserId(submittedByUserId);
        appealDAO.insert(appeal);

        auditService.record("APPEAL_SUBMITTED", "CLAIM", (long) claimId,
            "Appeal id=" + appeal.getId() + " type=" + appealType);
        LOG.info("Appeal submitted claimId=" + claimId + " appealId=" + appeal.getId());
        return appeal;
    }

    /** Approve an appeal — reverses the denial, triggers re-adjudication. */
    @Transactional
    public void approveAppeal(int appealId, String outcomeNotes, int userId) {
        Appeal appeal = requireAppeal(appealId);
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new ServiceException("Appeal id=" + appealId + " is already " + appeal.getStatus());
        }
        appealDAO.updateStatus(appealId, "APPROVED", "APPROVED", outcomeNotes);
        auditService.record("APPEAL_APPROVED", "CLAIM", (long) appeal.getClaimId(),
            "Appeal id=" + appealId + " approved");

        // DENIED → IN_REVIEW → re-adjudication (state machine: DENIED only allows IN_REVIEW)
        claimService.reAdjudicate(appeal.getClaimId(), userId);
        LOG.info("Appeal approved appealId=" + appealId + " claimId=" + appeal.getClaimId());
    }

    /** Deny an appeal — claim remains DENIED. */
    @Transactional
    public void denyAppeal(int appealId, String outcomeNotes, int userId) {
        Appeal appeal = requireAppeal(appealId);
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new ServiceException("Appeal id=" + appealId + " is already " + appeal.getStatus());
        }
        appealDAO.updateStatus(appealId, "DENIED", "DENIED", outcomeNotes);
        auditService.record("APPEAL_DENIED", "CLAIM", (long) appeal.getClaimId(),
            "Appeal id=" + appealId + " denied; notes=" + outcomeNotes);
        LOG.info("Appeal denied appealId=" + appealId);
    }

    /** Withdraw an appeal. */
    @Transactional
    public void withdrawAppeal(int appealId, int userId) {
        Appeal appeal = requireAppeal(appealId);
        if (!"OPEN".equals(appeal.getStatus())) {
            throw new ServiceException("Appeal id=" + appealId + " is already " + appeal.getStatus());
        }
        appealDAO.updateStatus(appealId, "WITHDRAWN", "WITHDRAWN", null);
        auditService.record("APPEAL_WITHDRAWN", "CLAIM", (long) appeal.getClaimId(),
            "Appeal id=" + appealId + " withdrawn by user id=" + userId);
    }

    public Appeal findById(int id) {
        return appealDAO.findById(id);
    }

    public List<Appeal> findByClaimId(int claimId) {
        return appealDAO.findByClaimId(claimId);
    }

    public List<Appeal> findOpen() {
        return appealDAO.findByStatus("OPEN");
    }

    private Appeal requireAppeal(int id) {
        Appeal a = appealDAO.findById(id);
        if (a == null) {
            throw new ServiceException("Appeal not found: id=" + id);
        }
        return a;
    }

    private Date addDays(Date from, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        cal.add(Calendar.DAY_OF_YEAR, days);
        return cal.getTime();
    }
}
