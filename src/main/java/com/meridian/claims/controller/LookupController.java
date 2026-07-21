package com.meridian.claims.controller;

import com.meridian.claims.model.DenialReasonCode;
import com.meridian.claims.model.DiagnosisCode;
import com.meridian.claims.model.ProcedureCode;
import com.meridian.claims.model.ServiceTypeCategory;
import com.meridian.claims.service.LookupService;
import com.meridian.claims.service.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/lookups")
public class LookupController {

    private final LookupService lookupService;

    @Autowired
    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    // --- Cache refresh ---

    @RequestMapping(value = "/refresh", method = RequestMethod.POST)
    public String refresh(
            @RequestParam(value = "returnTo", defaultValue = "/admin/lookups/service-types") String returnTo,
            RedirectAttributes redirectAttrs) {
        lookupService.refreshAll();
        redirectAttrs.addFlashAttribute("success", "Lookup caches reloaded from the database.");
        // Validate returnTo to prevent open redirect — must be a relative path within the app.
        if (!returnTo.startsWith("/") || returnTo.startsWith("//")) {
            returnTo = "/admin/lookups/service-types";
        }
        return "redirect:" + returnTo;
    }

    // --- Denial Reason Codes ---

    @RequestMapping(value = "/denial-reasons", method = RequestMethod.GET)
    public String listDenialReasons(Model model) {
        model.addAttribute("codes", lookupService.listDenialReasonCodes());
        return "admin/lookups/denial-reasons";
    }

    @RequestMapping(value = "/denial-reasons/save", method = RequestMethod.POST)
    public String saveDenialReason(
            @RequestParam("code") String code,
            @RequestParam(value = "carcCode", required = false) String carcCode,
            @RequestParam("description") String description,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            RedirectAttributes redirectAttrs) {
        try {
            DenialReasonCode c = new DenialReasonCode();
            c.setCode(code.trim().toUpperCase());
            c.setCarcCode(carcCode);
            c.setDescription(description);
            c.setActive(active);
            lookupService.saveDenialReasonCode(c);
            redirectAttrs.addFlashAttribute("success", "Denial reason code saved.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lookups/denial-reasons";
    }

    // --- Procedure Codes (CPT) ---

    @RequestMapping(value = "/procedure-codes", method = RequestMethod.GET)
    public String listProcedureCodes(Model model) {
        model.addAttribute("codes", lookupService.listProcedureCodes());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        return "admin/lookups/procedure-codes";
    }

    @RequestMapping(value = "/procedure-codes/save", method = RequestMethod.POST)
    public String saveProcedureCode(
            @RequestParam("code") String code,
            @RequestParam("description") String description,
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            RedirectAttributes redirectAttrs) {
        try {
            ProcedureCode c = new ProcedureCode();
            c.setCode(code.trim().toUpperCase());
            c.setDescription(description);
            c.setServiceType(serviceType);
            c.setActive(active);
            lookupService.saveProcedureCode(c);
            redirectAttrs.addFlashAttribute("success", "Procedure code saved.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lookups/procedure-codes";
    }

    // --- Diagnosis Codes (ICD-10) ---

    @RequestMapping(value = "/diagnosis-codes", method = RequestMethod.GET)
    public String listDiagnosisCodes(Model model) {
        model.addAttribute("codes", lookupService.listDiagnosisCodes());
        return "admin/lookups/diagnosis-codes";
    }

    @RequestMapping(value = "/diagnosis-codes/save", method = RequestMethod.POST)
    public String saveDiagnosisCode(
            @RequestParam("code") String code,
            @RequestParam("description") String description,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            RedirectAttributes redirectAttrs) {
        try {
            DiagnosisCode c = new DiagnosisCode();
            c.setCode(code.trim().toUpperCase());
            c.setDescription(description);
            c.setActive(active);
            lookupService.saveDiagnosisCode(c);
            redirectAttrs.addFlashAttribute("success", "Diagnosis code saved.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lookups/diagnosis-codes";
    }

    // --- Service Type Categories ---

    @RequestMapping(value = "/service-types", method = RequestMethod.GET)
    public String listServiceTypes(Model model) {
        model.addAttribute("categories", lookupService.listServiceTypeCategories());
        return "admin/lookups/service-types";
    }

    @RequestMapping(value = "/service-types/save", method = RequestMethod.POST)
    public String saveServiceType(
            @RequestParam("code") String code,
            @RequestParam("description") String description,
            @RequestParam(value = "active", defaultValue = "true") boolean active,
            RedirectAttributes redirectAttrs) {
        try {
            ServiceTypeCategory c = new ServiceTypeCategory();
            c.setCode(code.trim().toUpperCase());
            c.setDescription(description);
            c.setActive(active);
            lookupService.saveServiceTypeCategory(c);
            redirectAttrs.addFlashAttribute("success", "Service type saved.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lookups/service-types";
    }
}
