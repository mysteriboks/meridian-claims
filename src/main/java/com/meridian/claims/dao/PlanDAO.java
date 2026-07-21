package com.meridian.claims.dao;

import com.meridian.claims.model.Plan;
import com.meridian.claims.util.Page;

import java.util.List;

public interface PlanDAO {

    Plan findById(int id);

    Page<Plan> findAll(int pageNumber, int pageSize);

    List<Plan> findAllActive();

    void insert(Plan plan);

    void update(Plan plan);

    void softDelete(int id);
}
