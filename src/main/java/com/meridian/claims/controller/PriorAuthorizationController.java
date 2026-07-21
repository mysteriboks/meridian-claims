package com.meridian.claims.controller;

import com.meridian.claims.model.PriorAuthStatus;
import com.meridian.claims.service.LookupService;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.PriorAuthorizationService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.util.Page;
import com.meridian.claims.model.PriorAuthorization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Date;

@Controller
@RequestMapping("/prior-auth")
public class PriorAuthorizationController {

    private final PriorAuthorizationService priorAuthService;
    private final MemberService memberService;
    private final ProviderService providerService;
    private final LookupService lookupService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public PriorAuthorizationController(PriorAuthorizationService priorAuthService,
                                         MemberService memberService,
                                         ProviderService providerService,
                                         LookupService lookupService,
                                         com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.priorAuthService = priorAuthService;
        this.memberService = memberService;
        this.providerService = providerService;
        this.lookupService = lookupService;
        this.paginationConfig = paginationConfig;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(
            @RequestParam(value = "memberId", required = false) Integer memberId,
            @RequestParam(value = "page", defaultValue = "1") int pageNumber,
            Model model) {
        Page<PriorAuthorization> page;
        if (memberId != null) {
            page = priorAuthService.listByMember(memberId, pageNumber, paginationConfig.getListPageSize());
            model.addAttribute("member", memberService.findById(memberId));
        } else {
            page = priorAuthService.listAll(pageNumber, paginationConfig.getListPageSize());
        }
        model.addAttribute("page", page);
        return "prior-auth/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(
            @RequestParam(value = "memberId", required = false) Integer memberId,
            Model model) {
        model.addAttribute("members", memberService.listAllActive());
        model.addAttribute("providers", providerService.listAllActive());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        if (memberId != null) {
            model.addAttribute("preselectedMemberId", memberId);
        }
        return "prior-auth/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("memberId") int memberId,
            @RequestParam("providerId") int providerId,
            @RequestParam("procedureCode") String procedureCode,
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam("authorizedFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") Date authorizedFrom,
            @RequestParam("authorizedTo") @DateTimeFormat(pattern = "yyyy-MM-dd") Date authorizedTo,
            @RequestParam(value = "approvedUnits", defaultValue = "1") int approvedUnits,
            @RequestParam(value = "notes", required = false) String notes,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            PriorAuthorization a = priorAuthService.createAuthorization(
                memberId, providerId, procedureCode, serviceType,
                authorizedFrom, authorizedTo, approvedUnits, notes);
            redirectAttrs.addFlashAttribute("success", "Prior authorization " + a.getAuthNumber() + " created.");
            return "redirect:/prior-auth/" + a.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("members", memberService.listAllActive());
            model.addAttribute("providers", providerService.listAllActive());
            model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
            return "prior-auth/form";
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, Model model) {
        model.addAttribute("auth", priorAuthService.findById(id));
        model.addAttribute("statuses", PriorAuthStatus.values());
        return "prior-auth/view";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("auth", priorAuthService.findById(id));
        model.addAttribute("providers", providerService.listAllActive());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        model.addAttribute("statuses", PriorAuthStatus.values());
        return "prior-auth/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("procedureCode") String procedureCode,
            @RequestParam(value = "serviceType", required = false) String serviceType,
            @RequestParam("authorizedFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") Date authorizedFrom,
            @RequestParam("authorizedTo") @DateTimeFormat(pattern = "yyyy-MM-dd") Date authorizedTo,
            @RequestParam("status") String status,
            @RequestParam(value = "approvedUnits", defaultValue = "1") int approvedUnits,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttrs) {
        try {
            priorAuthService.updateAuthorization(id, procedureCode, serviceType,
                authorizedFrom, authorizedTo, status, approvedUnits, notes);
            redirectAttrs.addFlashAttribute("success", "Prior authorization updated.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/prior-auth/" + id;
    }

    @RequestMapping(value = "/{id}/expire", method = RequestMethod.POST)
    public String expire(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        priorAuthService.expireAuthorization(id);
        redirectAttrs.addFlashAttribute("success", "Authorization expired.");
        return "redirect:/prior-auth/" + id;
    }
}
