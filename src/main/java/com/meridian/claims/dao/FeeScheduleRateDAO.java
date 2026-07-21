package com.meridian.claims.dao;

import com.meridian.claims.model.FeeScheduleRate;
import com.meridian.claims.util.Page;

import java.math.BigDecimal;
import java.util.Date;

public interface FeeScheduleRateDAO {

    FeeScheduleRate findById(int id);

    Page<FeeScheduleRate> findByPlanId(int planId, int pageNumber, int pageSize);

    /**
     * Resolve the allowed amount for a given plan, provider, procedure code and service date.
     * Fallback: provider-specific rate -> plan-wide rate -> null (no rate on file).
     */
    BigDecimal resolveAllowedAmount(int planId, Integer providerId, String procedureCode, Date serviceDate);

    void insert(FeeScheduleRate rate);

    void update(FeeScheduleRate rate);

    void expire(int id, Date terminationDate);
}
