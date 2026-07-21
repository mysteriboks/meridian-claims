package com.meridian.claims.dao;

import com.meridian.claims.model.DeductibleAccumulator;

import java.util.Date;
import java.util.List;

public interface DeductibleAccumulatorDAO {
    /** Inserts a zero-row if absent, then SELECT ... FOR UPDATE. Must be called inside an active transaction. */
    DeductibleAccumulator findOrCreateForUpdate(int memberId, int planId, Date benefitYearStart);
    void update(DeductibleAccumulator accumulator);
    List<DeductibleAccumulator> findByPlanId(int planId);
}
