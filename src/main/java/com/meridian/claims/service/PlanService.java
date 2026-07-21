package com.meridian.claims.service;

import com.meridian.claims.dao.PlanCoverageRuleDAO;
import com.meridian.claims.dao.PlanDAO;
import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class PlanService {

    private static final Logger LOG = Logger.getLogger(PlanService.class);

    private final PlanDAO planDAO;
    private final PlanCoverageRuleDAO planCoverageRuleDAO;
    private final AuditService auditService;

    @Autowired
    public PlanService(PlanDAO planDAO, PlanCoverageRuleDAO planCoverageRuleDAO,
                        AuditService auditService) {
        this.planDAO = planDAO;
        this.planCoverageRuleDAO = planCoverageRuleDAO;
        this.auditService = auditService;
    }

    public Plan findById(int id) {
        Plan p = planDAO.findById(id);
        if (p == null) {
            throw new ServiceException("Plan id=" + id + " not found");
        }
        return p;
    }

    public Page<Plan> listAll(int pageNumber, int pageSize) {
        int page = pageNumber < 1 ? 1 : pageNumber;
        int size = pageSize < 1 ? 20 : pageSize;
        return planDAO.findAll(page, size);
    }

    public List<Plan> listAllActive() {
        return planDAO.findAllActive();
    }

    public List<PlanCoverageRule> getCoverageRules(int planId) {
        return planCoverageRuleDAO.findByPlanId(planId);
    }

    public PlanCoverageRule getCoverageRule(int planId, String serviceType) {
        return planCoverageRuleDAO.findByPlanAndServiceType(planId, serviceType);
    }

    @Transactional
    public Plan createPlan(String planName, String planType,
                            BigDecimal deductibleAmount, BigDecimal oopMax, BigDecimal copayAmount,
                            BigDecimal coveragePctIn, BigDecimal coveragePctOut,
                            Date benefitYearStart, int timelyFilingDays) {
        if (planName == null || planName.trim().isEmpty()) {
            throw new ServiceException("Plan name is required");
        }
        Plan p = new Plan();
        p.setPlanName(planName.trim());
        p.setPlanType(PlanType.valueOf(planType));
        p.setDeductibleAmount(deductibleAmount);
        p.setOopMax(oopMax);
        p.setCopayAmount(copayAmount);
        p.setCoveragePctInNetwork(coveragePctIn);
        p.setCoveragePctOutNetwork(coveragePctOut);
        p.setBenefitYearStart(benefitYearStart);
        p.setTimelyFilingDays(timelyFilingDays);
        planDAO.insert(p);
        LOG.info("Created plan id=" + p.getId() + " name=" + p.getPlanName());
        auditService.record("PLAN_CREATED", "PLAN", (long) p.getId(),
            "Created plan " + p.getPlanName());
        return p;
    }

    @Transactional
    public void updatePlan(int id, String planName, String planType,
                            BigDecimal deductibleAmount, BigDecimal oopMax, BigDecimal copayAmount,
                            BigDecimal coveragePctIn, BigDecimal coveragePctOut,
                            Date benefitYearStart, int timelyFilingDays) {
        Plan p = findById(id);
        if (planName == null || planName.trim().isEmpty()) {
            throw new ServiceException("Plan name is required");
        }
        p.setPlanName(planName.trim());
        p.setPlanType(PlanType.valueOf(planType));
        p.setDeductibleAmount(deductibleAmount);
        p.setOopMax(oopMax);
        p.setCopayAmount(copayAmount);
        p.setCoveragePctInNetwork(coveragePctIn);
        p.setCoveragePctOutNetwork(coveragePctOut);
        p.setBenefitYearStart(benefitYearStart);
        p.setTimelyFilingDays(timelyFilingDays);
        planDAO.update(p);
    }

    @Transactional
    public void deactivatePlan(int id) {
        Plan p = findById(id);
        planDAO.softDelete(id);
        LOG.info("Deactivated (soft-deleted) plan id=" + id);
        auditService.record("PLAN_DEACTIVATED", "PLAN", (long) id,
            "Deactivated plan " + p.getPlanName());
    }

    @Transactional
    public void saveCoverageRule(int planId, String serviceType, BigDecimal coveragePct,
                                  boolean requiresReferral, boolean requiresPriorAuth) {
        findById(planId);
        PlanCoverageRule existing = planCoverageRuleDAO.findByPlanAndServiceType(planId, serviceType);
        if (existing != null) {
            existing.setCoveragePct(coveragePct);
            existing.setRequiresReferral(requiresReferral);
            existing.setRequiresPriorAuth(requiresPriorAuth);
            planCoverageRuleDAO.update(existing);
        } else {
            PlanCoverageRule rule = new PlanCoverageRule();
            rule.setPlanId(planId);
            rule.setServiceType(serviceType);
            rule.setCoveragePct(coveragePct);
            rule.setRequiresReferral(requiresReferral);
            rule.setRequiresPriorAuth(requiresPriorAuth);
            planCoverageRuleDAO.insert(rule);
        }
    }

    @Transactional
    public void deleteCoverageRule(int ruleId) {
        planCoverageRuleDAO.delete(ruleId);
    }
}
