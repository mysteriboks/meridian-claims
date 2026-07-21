package com.meridian.claims.service;

import com.meridian.claims.dao.PriorAuthorizationDAO;
import com.meridian.claims.model.PriorAuthorization;
import com.meridian.claims.model.PriorAuthStatus;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class PriorAuthorizationService {

    private static final Logger LOG = Logger.getLogger(PriorAuthorizationService.class);

    private final PriorAuthorizationDAO priorAuthorizationDAO;
    private final AuditService auditService;

    @Autowired
    public PriorAuthorizationService(PriorAuthorizationDAO priorAuthorizationDAO, AuditService auditService) {
        this.priorAuthorizationDAO = priorAuthorizationDAO;
        this.auditService = auditService;
    }

    public PriorAuthorization findById(int id) {
        PriorAuthorization a = priorAuthorizationDAO.findById(id);
        if (a == null) {
            throw new ServiceException("Prior authorization id=" + id + " not found");
        }
        return a;
    }

    public PriorAuthorization findByAuthNumber(String authNumber) {
        return priorAuthorizationDAO.findByAuthNumber(authNumber);
    }

    public PriorAuthorization findValid(int memberId, String procedureCode, Date serviceDate) {
        return priorAuthorizationDAO.findValid(memberId, procedureCode, serviceDate);
    }

    public Page<PriorAuthorization> listByMember(int memberId, int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return priorAuthorizationDAO.findByMemberId(memberId, page, size);
    }

    public Page<PriorAuthorization> listAll(int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return priorAuthorizationDAO.findAll(page, size);
    }

    @Transactional
    public PriorAuthorization createAuthorization(int memberId, int providerId,
                                                   String procedureCode, String serviceType,
                                                   Date authorizedFrom, Date authorizedTo,
                                                   int approvedUnits, String notes) {
        if (procedureCode == null || procedureCode.trim().isEmpty()) {
            throw new ServiceException("Procedure code is required");
        }
        if (authorizedFrom == null || authorizedTo == null) {
            throw new ServiceException("Authorization date range is required");
        }
        if (authorizedTo.before(authorizedFrom)) {
            throw new ServiceException("Authorized-to date must be on or after authorized-from date");
        }
        String authNumber = "PA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PriorAuthorization a = new PriorAuthorization();
        a.setMemberId(memberId);
        a.setProviderId(providerId);
        a.setProcedureCode(procedureCode.trim().toUpperCase());
        a.setServiceType(serviceType);
        a.setAuthorizedFrom(authorizedFrom);
        a.setAuthorizedTo(authorizedTo);
        a.setAuthNumber(authNumber);
        a.setStatus(PriorAuthStatus.ACTIVE);
        a.setApprovedUnits(approvedUnits < 1 ? 1 : approvedUnits);
        a.setNotes(notes);
        priorAuthorizationDAO.insert(a);
        LOG.info("Created prior auth id=" + a.getId() + " authNumber=" + authNumber);
        auditService.record("PRIOR_AUTH_CREATED", "PRIOR_AUTHORIZATION", (long) a.getId(),
            "Created prior authorization " + authNumber);
        return a;
    }

    @Transactional
    public void updateAuthorization(int id, String procedureCode, String serviceType,
                                     Date authorizedFrom, Date authorizedTo,
                                     String status, int approvedUnits, String notes) {
        PriorAuthorization a = findById(id);
        a.setProcedureCode(procedureCode.trim().toUpperCase());
        a.setServiceType(serviceType);
        a.setAuthorizedFrom(authorizedFrom);
        a.setAuthorizedTo(authorizedTo);
        a.setStatus(PriorAuthStatus.valueOf(status));
        a.setApprovedUnits(approvedUnits);
        a.setNotes(notes);
        priorAuthorizationDAO.update(a);
    }

    @Transactional
    public void expireAuthorization(int id) {
        PriorAuthorization a = findById(id);
        priorAuthorizationDAO.expire(id);
        auditService.record("PRIOR_AUTH_EXPIRED", "PRIOR_AUTHORIZATION", (long) id,
            "Expired prior authorization " + a.getAuthNumber());
    }
}
