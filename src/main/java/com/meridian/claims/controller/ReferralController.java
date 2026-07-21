package com.meridian.claims.controller;

import com.meridian.claims.model.Referral;
import com.meridian.claims.model.ReferralStatus;
import com.meridian.claims.service.LookupService;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.service.ReferralService;
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

import java.util.Date;

@Controller
@RequestMapping("/referrals")
public class ReferralController {

    private final ReferralService referralService;
    private final MemberService memberService;
    private final ProviderService providerService;
    private final LookupService lookupService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public ReferralController(ReferralService referralService,
                               MemberService memberService,
                               ProviderService providerService,
                               LookupService lookupService,
                               com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.referralService = referralService;
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
        Page<Referral> page;
        if (memberId != null) {
            page = referralService.listByMember(memberId, pageNumber, paginationConfig.getListPageSize());
            model.addAttribute("member", memberService.findById(memberId));
        } else {
            page = referralService.listAll(pageNumber, paginationConfig.getListPageSize());
        }
        model.addAttribute("page", page);
        return "referrals/list";
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
        return "referrals/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("memberId") int memberId,
            @RequestParam("referringProviderId") int referringProviderId,
            @RequestParam("referredToProviderId") int referredToProviderId,
            @RequestParam("serviceType") String serviceType,
            @RequestParam("validFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") Date validFrom,
            @RequestParam("validTo") @DateTimeFormat(pattern = "yyyy-MM-dd") Date validTo,
            @RequestParam(value = "notes", required = false) String notes,
            Model model,
            RedirectAttributes redirectAttrs) {
        try {
            Referral ref = referralService.createReferral(
                memberId, referringProviderId, referredToProviderId,
                serviceType, validFrom, validTo, notes);
            redirectAttrs.addFlashAttribute("success", "Referral " + ref.getReferralNumber() + " created.");
            return "redirect:/referrals/" + ref.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("members", memberService.listAllActive());
            model.addAttribute("providers", providerService.listAllActive());
            model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
            return "referrals/form";
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, Model model) {
        model.addAttribute("referral", referralService.findById(id));
        model.addAttribute("statuses", ReferralStatus.values());
        return "referrals/view";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("referral", referralService.findById(id));
        model.addAttribute("providers", providerService.listAllActive());
        model.addAttribute("serviceTypes", lookupService.listServiceTypeCategories());
        model.addAttribute("statuses", ReferralStatus.values());
        return "referrals/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("serviceType") String serviceType,
            @RequestParam("validFrom") @DateTimeFormat(pattern = "yyyy-MM-dd") Date validFrom,
            @RequestParam("validTo") @DateTimeFormat(pattern = "yyyy-MM-dd") Date validTo,
            @RequestParam("status") String status,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttrs) {
        try {
            referralService.updateReferral(id, serviceType, validFrom, validTo, status, notes);
            redirectAttrs.addFlashAttribute("success", "Referral updated.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/referrals/" + id;
    }

    @RequestMapping(value = "/{id}/expire", method = RequestMethod.POST)
    public String expire(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        referralService.expireReferral(id);
        redirectAttrs.addFlashAttribute("success", "Referral expired.");
        return "redirect:/referrals/" + id;
    }
}
