package com.meridian.claims.service.adjudication;

import com.meridian.claims.model.ClaimLineItem;
import com.meridian.claims.model.PlanCoverageRule;
import com.meridian.claims.model.ProcedureCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoverageRule implements AdjudicationRule {

    private final Map<String, ProcedureCode> procedureCodeMap;

    public CoverageRule(List<ProcedureCode> allProcedureCodes) {
        this.procedureCodeMap = new HashMap<String, ProcedureCode>();
        for (ProcedureCode pc : allProcedureCodes) {
            if (pc != null && pc.getCode() != null) {
                procedureCodeMap.put(pc.getCode(), pc);
            }
        }
    }

    @Override
    public String getRuleName() { return "CoverageRule"; }

    @Override
    public String getRuleType() { return "HARD"; }

    @Override
    public AdjudicationRuleResult evaluate(AdjudicationContext ctx) {
        int planId = ctx.getPlan().getId();
        // Load coverage rules for the plan keyed by service type
        List<PlanCoverageRule> rules = ctx.getPlanCoverageRuleDAO().findByPlanId(planId);
        Map<String, PlanCoverageRule> ruleByServiceType = new HashMap<String, PlanCoverageRule>();
        for (PlanCoverageRule r : rules) {
            ruleByServiceType.put(r.getServiceType(), r);
        }

        for (ClaimLineItem item : ctx.getLineItems()) {
            // Resolve service type from procedure code lookup
            ProcedureCode pc = procedureCodeMap.get(item.getProcedureCode());
            String serviceType = (pc != null) ? pc.getServiceType() : null;
            item.setServiceType(serviceType);

            PlanCoverageRule matchedRule = (serviceType != null) ? ruleByServiceType.get(serviceType) : null;
            item.setCoverageRule(matchedRule);

            if (matchedRule == null) {
                return AdjudicationRuleResult.failed(
                    "Procedure " + item.getProcedureCode() + " (service type: " +
                    (serviceType != null ? serviceType : "unknown") + ") is not covered under this plan");
            }
        }
        return AdjudicationRuleResult.passed("All line items covered under plan");
    }
}
