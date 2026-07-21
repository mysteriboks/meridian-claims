package com.meridian.claims.controller;

import com.meridian.claims.model.Plan;
import com.meridian.claims.model.PlanType;
import com.meridian.claims.service.LookupService;
import com.meridian.claims.service.PlanService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.util.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Date;

@Controller
@RequestMapping("/admin/plans")
public class PlanController {

    private final PlanService planService;
    private final LookupService lookupService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public PlanController(PlanService planService, LookupService lookupService,
                          com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.planService = planService;
        this.lookupService = lookupService;
        this.paginationConfig = paginationConfig;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(
            @RequestParam(value = "page", defaultValue = "1") int pageNumber,
            Model model) {
        Page<Plan> page = planService.listAll(pageNumber, paginationConfig.getListPageSize());
        model.addAttribute("page", page);
        return "admin/plans/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(Model model) {
        model.addAttribute("planTypes", PlanType.values());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        return "admin/plans/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("planName") String planName,
            @RequestParam("planType") String planType,
            @RequestParam("deductibleAmount") BigDecimal deductibleAmount,
            @RequestParam("oopMax") BigDecimal oopMax,
            @RequestParam("copayAmount") BigDecimal copayAmount,
            @RequestParam("coveragePctInNetwork") BigDecimal coveragePctIn,
            @RequestParam("coveragePctOutNetwork") BigDecimal coveragePctOut,
            @RequestParam("benefitYearStart") @DateTimeFormat(pattern = "yyyy-MM-dd") Date benefitYearStart,
            @RequestParam("timelyFilingDays") int timelyFilingDays,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            Plan p = planService.createPlan(planName, planType, deductibleAmount, oopMax, copayAmount,
                coveragePctIn, coveragePctOut, benefitYearStart, timelyFilingDays);
            redirectAttrs.addFlashAttribute("success", "Plan created.");
            return "redirect:/admin/plans/" + p.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("planTypes", PlanType.values());
            model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
            return "admin/plans/form";
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, Model model) {
        model.addAttribute("plan", planService.findById(id));
        model.addAttribute("coverageRules", planService.getCoverageRules(id));
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        return "admin/plans/view";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("plan", planService.findById(id));
        model.addAttribute("planTypes", PlanType.values());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        return "admin/plans/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("planName") String planName,
            @RequestParam("planType") String planType,
            @RequestParam("deductibleAmount") BigDecimal deductibleAmount,
            @RequestParam("oopMax") BigDecimal oopMax,
            @RequestParam("copayAmount") BigDecimal copayAmount,
            @RequestParam("coveragePctInNetwork") BigDecimal coveragePctIn,
            @RequestParam("coveragePctOutNetwork") BigDecimal coveragePctOut,
            @RequestParam("benefitYearStart") @DateTimeFormat(pattern = "yyyy-MM-dd") Date benefitYearStart,
            @RequestParam("timelyFilingDays") int timelyFilingDays,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            planService.updatePlan(id, planName, planType, deductibleAmount, oopMax, copayAmount,
                coveragePctIn, coveragePctOut, benefitYearStart, timelyFilingDays);
            redirectAttrs.addFlashAttribute("success", "Plan updated.");
            return "redirect:/admin/plans/" + id;
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("plan", planService.findById(id));
            model.addAttribute("planTypes", PlanType.values());
            model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
            return "admin/plans/form";
        }
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public String deactivate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        planService.deactivatePlan(id);
        redirectAttrs.addFlashAttribute("success", "Plan deactivated.");
        return "redirect:/admin/plans";
    }

    @RequestMapping(value = "/{id}/rules/save", method = RequestMethod.POST)
    public String saveCoverageRule(
            @PathVariable("id") int planId,
            @RequestParam("serviceType") String serviceType,
            @RequestParam("coveragePct") BigDecimal coveragePct,
            @RequestParam(value = "requiresReferral", defaultValue = "false") boolean requiresReferral,
            @RequestParam(value = "requiresPriorAuth", defaultValue = "false") boolean requiresPriorAuth,
            RedirectAttributes redirectAttrs) {
        try {
            planService.saveCoverageRule(planId, serviceType, coveragePct, requiresReferral, requiresPriorAuth);
            redirectAttrs.addFlashAttribute("success", "Coverage rule saved.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/plans/" + planId;
    }

    @RequestMapping(value = "/{id}/rules/{ruleId}/delete", method = RequestMethod.POST)
    public String deleteCoverageRule(
            @PathVariable("id") int planId,
            @PathVariable("ruleId") int ruleId,
            RedirectAttributes redirectAttrs) {
        planService.deleteCoverageRule(ruleId);
        redirectAttrs.addFlashAttribute("success", "Coverage rule removed.");
        return "redirect:/admin/plans/" + planId;
    }
}
