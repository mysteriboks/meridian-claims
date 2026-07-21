package com.meridian.claims.controller;

import com.meridian.claims.service.FeeScheduleService;
import com.meridian.claims.service.PlanService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.util.Page;
import com.meridian.claims.model.FeeScheduleRate;
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
@RequestMapping("/admin/fee-schedule")
public class FeeScheduleController {

    private final FeeScheduleService feeScheduleService;
    private final PlanService planService;
    private final ProviderService providerService;

    @Autowired
    public FeeScheduleController(FeeScheduleService feeScheduleService,
                                  PlanService planService,
                                  ProviderService providerService) {
        this.feeScheduleService = feeScheduleService;
        this.planService = planService;
        this.providerService = providerService;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String listByPlan(
            @RequestParam("planId") int planId,
            @RequestParam(value = "page", defaultValue = "1") int pageNumber,
            Model model) {
        Page<FeeScheduleRate> page = feeScheduleService.findByPlan(planId, pageNumber, 25);
        model.addAttribute("page", page);
        model.addAttribute("plan", planService.findById(planId));
        model.addAttribute("today", new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()));
        return "admin/fee-schedule/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(
            @RequestParam("planId") int planId,
            Model model) {
        model.addAttribute("plan", planService.findById(planId));
        model.addAttribute("providers", providerService.listAllActive());
        return "admin/fee-schedule/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("planId") int planId,
            @RequestParam(value = "providerId", required = false) Integer providerId,
            @RequestParam("procedureCode") String procedureCode,
            @RequestParam("allowedAmount") BigDecimal allowedAmount,
            @RequestParam("effectiveDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date effectiveDate,
            @RequestParam(value = "terminationDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date terminationDate,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            feeScheduleService.createRate(planId, providerId, procedureCode, allowedAmount, effectiveDate, terminationDate);
            redirectAttrs.addFlashAttribute("success", "Fee schedule rate added.");
            return "redirect:/admin/fee-schedule?planId=" + planId;
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("plan", planService.findById(planId));
            model.addAttribute("providers", providerService.listAllActive());
            return "admin/fee-schedule/form";
        }
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        FeeScheduleRate rate = feeScheduleService.findById(id);
        model.addAttribute("rate", rate);
        model.addAttribute("plan", planService.findById(rate.getPlanId()));
        model.addAttribute("providers", providerService.listAllActive());
        return "admin/fee-schedule/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("allowedAmount") BigDecimal allowedAmount,
            @RequestParam("effectiveDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date effectiveDate,
            @RequestParam(value = "terminationDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date terminationDate,
            RedirectAttributes redirectAttrs) {
        FeeScheduleRate rate = feeScheduleService.findById(id);
        feeScheduleService.updateRate(id, allowedAmount, effectiveDate, terminationDate);
        redirectAttrs.addFlashAttribute("success", "Rate updated.");
        return "redirect:/admin/fee-schedule?planId=" + rate.getPlanId();
    }

    @RequestMapping(value = "/{id}/expire", method = RequestMethod.POST)
    public String expire(
            @PathVariable("id") int id,
            @RequestParam("terminationDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date terminationDate,
            RedirectAttributes redirectAttrs) {
        FeeScheduleRate rate = feeScheduleService.findById(id);
        feeScheduleService.expireRate(id, terminationDate);
        redirectAttrs.addFlashAttribute("success", "Rate expired.");
        return "redirect:/admin/fee-schedule?planId=" + rate.getPlanId();
    }
}
