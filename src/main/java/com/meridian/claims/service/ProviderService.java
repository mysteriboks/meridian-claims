package com.meridian.claims.service;

import com.meridian.claims.dao.ProviderDAO;
import com.meridian.claims.model.NetworkStatus;
import com.meridian.claims.model.Provider;
import com.meridian.claims.model.ProviderType;
import com.meridian.claims.util.Page;
import com.meridian.claims.util.ValidationUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProviderService {

    private static final Logger LOG = Logger.getLogger(ProviderService.class);

    private final ProviderDAO providerDAO;
    private final AuditService auditService;

    @Autowired
    public ProviderService(ProviderDAO providerDAO, AuditService auditService) {
        this.providerDAO = providerDAO;
        this.auditService = auditService;
    }

    public Provider findById(int id) {
        Provider p = providerDAO.findById(id);
        if (p == null) {
            throw new ServiceException("Provider id=" + id + " not found");
        }
        return p;
    }

    public Provider findByNpi(String npi) {
        return providerDAO.findByNpi(npi);
    }

    public Page<Provider> search(String query, int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return providerDAO.search(query, page, size);
    }

    public List<Provider> listAllActive() {
        return providerDAO.findAllActive();
    }

    @Transactional
    public Provider createProvider(String npi, String name, String providerType,
                                    String specialty, String networkStatus,
                                    String phone, String address) {
        if (npi == null || npi.trim().isEmpty()) {
            throw new ServiceException("NPI is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ServiceException("Provider name is required");
        }
        if (providerDAO.findByNpi(npi.trim()) != null) {
            throw new ServiceException("NPI " + npi + " is already registered");
        }
        Provider p = new Provider();
        p.setNpi(npi.trim());
        p.setName(name.trim());
        p.setProviderType(ProviderType.valueOf(providerType));
        p.setSpecialty(specialty);
        p.setNetworkStatus(NetworkStatus.valueOf(networkStatus));
        p.setPhone(phone);
        p.setAddress(address);
        providerDAO.insert(p);
        LOG.info("Created provider id=" + p.getId() + " npi=" + p.getNpi());
        auditService.record("PROVIDER_CREATED", "PROVIDER", (long) p.getId(),
            "Created provider " + p.getName() + " (NPI " + p.getNpi() + ")");
        return p;
    }

    @Transactional
    public void updateProvider(int id, String npi, String name, String providerType,
                                String specialty, String networkStatus,
                                String phone, String address) {
        Provider p = findById(id);
        if (npi == null || npi.trim().isEmpty()) {
            throw new ServiceException("NPI is required");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ServiceException("Provider name is required");
        }
        Provider existing = providerDAO.findByNpi(npi.trim());
        if (existing != null && existing.getId() != id) {
            throw new ServiceException("NPI " + npi + " is already in use by another provider");
        }
        p.setNpi(npi.trim());
        p.setName(name.trim());
        p.setProviderType(ProviderType.valueOf(providerType));
        p.setSpecialty(specialty);
        p.setNetworkStatus(NetworkStatus.valueOf(networkStatus));
        p.setPhone(phone);
        p.setAddress(address);
        providerDAO.update(p);
    }

    /**
     * Sets or clears a provider's ACH disbursement banking details (Phase 18).
     * Passing all three args blank/null clears the provider's banking info —
     * that provider is then simply skipped (not failed) when an EFT/ACH batch
     * file is generated, the same "missing config is inert, not fatal" pattern
     * {@code TransportAdapterResolver} uses for an unconfigured trading partner.
     */
    @Transactional
    public void updateBankingInfo(int id, String routingNumber, String accountNumber, String accountType) {
        findById(id);
        boolean anyProvided = ValidationUtil.required(routingNumber)
            || ValidationUtil.required(accountNumber) || ValidationUtil.required(accountType);
        if (!anyProvided) {
            providerDAO.updateBankingInfo(id, null, null, null);
            auditService.record("PROVIDER_BANKING_CLEARED", "PROVIDER", (long) id, "Cleared ACH banking info");
            return;
        }
        if (!ValidationUtil.achRoutingNumber(routingNumber)) {
            throw new ServiceException("ACH routing number must be exactly 9 digits");
        }
        if (!ValidationUtil.required(accountNumber)) {
            throw new ServiceException("ACH account number is required");
        }
        if (!"CHECKING".equals(accountType) && !"SAVINGS".equals(accountType)) {
            throw new ServiceException("ACH account type must be CHECKING or SAVINGS");
        }
        providerDAO.updateBankingInfo(id, routingNumber.trim(), accountNumber.trim(), accountType);
        auditService.record("PROVIDER_BANKING_UPDATED", "PROVIDER", (long) id, "Updated ACH banking info");
    }

    @Transactional
    public void deactivateProvider(int id) {
        Provider p = findById(id);
        providerDAO.softDelete(id);
        LOG.info("Deactivated (soft-deleted) provider id=" + id);
        auditService.record("PROVIDER_DEACTIVATED", "PROVIDER", (long) id,
            "Deactivated provider " + p.getName() + " (NPI " + p.getNpi() + ")");
    }
}
