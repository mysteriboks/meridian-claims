package com.meridian.claims.dao;

import com.meridian.claims.model.PlanCoverageRule;

import java.util.List;

public interface PlanCoverageRuleDAO {

    List<PlanCoverageRule> findByPlanId(int planId);

    PlanCoverageRule findByPlanAndServiceType(int planId, String serviceType);

    void insert(PlanCoverageRule rule);

    void update(PlanCoverageRule rule);

    void delete(int id);

    void deleteByPlanId(int planId);
}
