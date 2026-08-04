package com.meridian.claims.dao;

import com.meridian.claims.model.Plan;
import com.meridian.claims.util.Page;

import java.util.List;

public interface PlanDAO {

    Plan findById(int id);

    /** Case-insensitive exact match on plan_name among active (non-deleted) plans. Returns null if not found. */
    Plan findByName(String planName);

    Page<Plan> findAll(int pageNumber, int pageSize);

    List<Plan> findAllActive();

    void insert(Plan plan);

    void update(Plan plan);

    void softDelete(int id);
}
