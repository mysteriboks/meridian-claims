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

    void softDelete(int id);
}
