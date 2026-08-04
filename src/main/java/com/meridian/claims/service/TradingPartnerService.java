package com.meridian.claims.service;

import com.meridian.claims.dao.TradingPartnerDAO;
import com.meridian.claims.model.TradingPartner;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for the trading-partner master ({@code trading_partners}, Phase 13). */
@Service
public class TradingPartnerService {

    private static final Logger LOG = Logger.getLogger(TradingPartnerService.class);

    private final TradingPartnerDAO tradingPartnerDAO;
    private final AuditService auditService;

    @Autowired
    public TradingPartnerService(TradingPartnerDAO tradingPartnerDAO, AuditService auditService) {
        this.tradingPartnerDAO = tradingPartnerDAO;
        this.auditService = auditService;
    }

    public TradingPartner findById(int id) {
        TradingPartner p = tradingPartnerDAO.findById(id);
        if (p == null) {
            throw new ServiceException("Trading partner id=" + id + " not found");
        }
        return p;
    }

    public List<TradingPartner> listAll() {
        return tradingPartnerDAO.findAll();
    }

    public List<TradingPartner> listAllActive() {
        return tradingPartnerDAO.findAllActive();
    }

    @Transactional
    public TradingPartner create(TradingPartner partner) {
        validate(partner);
        tradingPartnerDAO.insert(partner);
        LOG.info("Created trading partner id=" + partner.getId() + " name=" + partner.getPartnerName());
        auditService.record("TRADING_PARTNER_CREATED", "TRADING_PARTNER", (long) partner.getId(),
            "Created trading partner " + partner.getPartnerName());
        return partner;
    }

    @Transactional
    public void update(TradingPartner partner) {
        findById(partner.getId());
        validate(partner);
        tradingPartnerDAO.update(partner);
        auditService.record("TRADING_PARTNER_UPDATED", "TRADING_PARTNER", (long) partner.getId(),
            "Updated trading partner " + partner.getPartnerName());
    }

    @Transactional
    public void deactivate(int id) {
        TradingPartner p = findById(id);
        p.setActive(false);
        tradingPartnerDAO.update(p);
        LOG.info("Deactivated trading partner id=" + id);
        auditService.record("TRADING_PARTNER_DEACTIVATED", "TRADING_PARTNER", (long) id,
            "Deactivated trading partner " + p.getPartnerName());
    }

    private void validate(TradingPartner partner) {
        if (partner.getPartnerName() == null || partner.getPartnerName().trim().isEmpty()) {
            throw new ServiceException("Partner name is required");
        }
        if (partner.getIsaQualifier() == null || partner.getIsaQualifier().trim().isEmpty()) {
            throw new ServiceException("ISA qualifier is required");
        }
        if (partner.getIsaId() == null || partner.getIsaId().trim().isEmpty()) {
            throw new ServiceException("ISA ID is required");
        }
        if (partner.getGsId() == null || partner.getGsId().trim().isEmpty()) {
            throw new ServiceException("GS ID is required");
        }
        if (!TradingPartner.TRANSPORT_LOCAL.equals(partner.getTransportType())
                && !TradingPartner.TRANSPORT_SFTP.equals(partner.getTransportType())) {
            throw new ServiceException("Transport type must be LOCAL or SFTP");
        }
        if (TradingPartner.TRANSPORT_SFTP.equals(partner.getTransportType())) {
            if (partner.getTransportHost() == null || partner.getTransportHost().trim().isEmpty()) {
                throw new ServiceException("Transport host is required for SFTP partners");
            }
            if (partner.getTransportUsername() == null || partner.getTransportUsername().trim().isEmpty()) {
                throw new ServiceException("Transport username is required for SFTP partners");
            }
            if (partner.getTransportCredentialRef() == null || partner.getTransportCredentialRef().trim().isEmpty()) {
                throw new ServiceException("Transport credential reference is required for SFTP partners "
                    + "(the actual secret is configured separately in the external prod overlay)");
            }
        }
        if (partner.getInboundPath() == null || partner.getInboundPath().trim().isEmpty()) {
            throw new ServiceException("Inbound path is required");
        }
    }
}
