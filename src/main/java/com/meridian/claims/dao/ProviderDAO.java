package com.meridian.claims.dao;

import com.meridian.claims.model.Provider;
import com.meridian.claims.util.Page;

import java.util.List;

public interface ProviderDAO {

    Provider findById(int id);

    Provider findByNpi(String npi);

    Page<Provider> search(String query, int pageNumber, int pageSize);

    List<Provider> findAll();

    List<Provider> findAllActive();

    void insert(Provider provider);

    void update(Provider provider);

    /** Sets (or clears, when all args are null) the provider's ACH disbursement banking details (Phase 18). */
    void updateBankingInfo(int id, String routingNumber, String accountNumber, String accountType);

    /** Records the result of the most recent NPPES NPI-validation check (Phase 19). */
    void updateNpiValidation(int id, String status, java.util.Date validatedAt);

    void softDelete(int id);
}
