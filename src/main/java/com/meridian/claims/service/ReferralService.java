package com.meridian.claims.service;

import com.meridian.claims.dao.ReferralDAO;
import com.meridian.claims.model.Referral;
import com.meridian.claims.model.ReferralStatus;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class ReferralService {

    private static final Logger LOG = Logger.getLogger(ReferralService.class);

    private final ReferralDAO referralDAO;
    private final AuditService auditService;

    @Autowired
    public ReferralService(ReferralDAO referralDAO, AuditService auditService) {
        this.referralDAO = referralDAO;
        this.auditService = auditService;
    }

    public Referral findById(int id) {
        Referral r = referralDAO.findById(id);
        if (r == null) {
            throw new ServiceException("Referral id=" + id + " not found");
        }
        return r;
    }

    public Referral findByReferralNumber(String referralNumber) {
        return referralDAO.findByReferralNumber(referralNumber);
    }

    public Referral findValid(int memberId, String serviceType, Date serviceDate) {
        return referralDAO.findValid(memberId, serviceType, serviceDate);
    }

    public Page<Referral> listByMember(int memberId, int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return referralDAO.findByMemberId(memberId, page, size);
    }

    public Page<Referral> listAll(int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return referralDAO.findAll(page, size);
    }

    @Transactional
    public Referral createReferral(int memberId, int referringProviderId, int referredToProviderId,
                                    String serviceType, Date validFrom, Date validTo, String notes) {
        if (serviceType == null || serviceType.trim().isEmpty()) {
            throw new ServiceException("Service type is required");
        }
        if (validFrom == null || validTo == null) {
            throw new ServiceException("Validity date range is required");
        }
        if (validTo.before(validFrom)) {
            throw new ServiceException("Valid-to date must be on or after valid-from date");
        }
        String referralNumber = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Referral ref = new Referral();
        ref.setMemberId(memberId);
        ref.setReferringProviderId(referringProviderId);
        ref.setReferredToProviderId(referredToProviderId);
        ref.setServiceType(serviceType.trim());
        ref.setValidFrom(validFrom);
        ref.setValidTo(validTo);
        ref.setReferralNumber(referralNumber);
        ref.setStatus(ReferralStatus.ACTIVE);
        ref.setNotes(notes);
        referralDAO.insert(ref);
        LOG.info("Created referral id=" + ref.getId() + " referralNumber=" + referralNumber);
        auditService.record("REFERRAL_CREATED", "REFERRAL", (long) ref.getId(),
            "Created referral " + referralNumber);
        return ref;
    }

    @Transactional
    public void updateReferral(int id, String serviceType, Date validFrom, Date validTo,
                                String status, String notes) {
        Referral ref = findById(id);
        ref.setServiceType(serviceType);
        ref.setValidFrom(validFrom);
        ref.setValidTo(validTo);
        ref.setStatus(ReferralStatus.valueOf(status));
        ref.setNotes(notes);
        referralDAO.update(ref);
    }

    @Transactional
    public void expireReferral(int id) {
        Referral ref = findById(id);
        referralDAO.expire(id);
        auditService.record("REFERRAL_EXPIRED", "REFERRAL", (long) id,
            "Expired referral " + ref.getReferralNumber());
    }
}
