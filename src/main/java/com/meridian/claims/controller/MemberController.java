package com.meridian.claims.controller;

import com.meridian.claims.model.Member;
import com.meridian.claims.model.MemberStatus;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.PlanService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.util.Page;
import com.meridian.claims.util.ValidationUtil;
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
@RequestMapping("/members")
public class MemberController {

    private final MemberService memberService;
    private final PlanService planService;
    private final com.meridian.claims.util.PaginationConfig paginationConfig;

    @Autowired
    public MemberController(MemberService memberService, PlanService planService,
                            com.meridian.claims.util.PaginationConfig paginationConfig) {
        this.memberService = memberService;
        this.planService = planService;
        this.paginationConfig = paginationConfig;
    }

    @RequestMapping(method = RequestMethod.GET)
    public String list(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "1") int pageNumber,
            Model model) {
        Page<Member> page = memberService.search(query, pageNumber, paginationConfig.getListPageSize());
        model.addAttribute("page", page);
        model.addAttribute("query", query);
        return "members/list";
    }

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String showCreate(Model model) {
        model.addAttribute("statuses", MemberStatus.values());
        return "members/form";
    }

    @RequestMapping(value = "/new", method = RequestMethod.POST)
    public String create(
            @RequestParam("memberNumber") String memberNumber,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "dob", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dob,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            Model model,
            RedirectAttributes redirectAttrs) {
        String err = validateMemberFields(memberNumber, firstName, lastName, address, phone, email);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("statuses", MemberStatus.values());
            return "members/form";
        }
        try {
            Member m = memberService.createMember(memberNumber, firstName, lastName, dob, address, phone, email);
            redirectAttrs.addFlashAttribute("success", "Member " + m.getMemberNumber() + " created.");
            return "redirect:/members/" + m.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("statuses", MemberStatus.values());
            return "members/form";
        }
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, Model model) {
        Member m = memberService.findById(id);
        model.addAttribute("member", m);
        model.addAttribute("coverageRecords", memberService.getCoverageRecords(id));
        model.addAttribute("plans", planService.listAllActive());
        model.addAttribute("coverageOrders", new String[]{"PRIMARY", "SECONDARY"});
        return "members/view";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.GET)
    public String showEdit(@PathVariable("id") int id, Model model) {
        model.addAttribute("member", memberService.findById(id));
        model.addAttribute("statuses", MemberStatus.values());
        return "members/form";
    }

    @RequestMapping(value = "/{id}/edit", method = RequestMethod.POST)
    public String update(
            @PathVariable("id") int id,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "dob", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dob,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "status", required = false) String status,
            Model model,
            RedirectAttributes redirectAttrs) {
        String err = validateMemberFields(null, firstName, lastName, address, phone, email);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("member", memberService.findById(id));
            model.addAttribute("statuses", MemberStatus.values());
            return "members/form";
        }
        try {
            memberService.updateMember(id, firstName, lastName, dob, address, phone, email, status);
            redirectAttrs.addFlashAttribute("success", "Member updated.");
            return "redirect:/members/" + id;
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("member", memberService.findById(id));
            model.addAttribute("statuses", MemberStatus.values());
            return "members/form";
        }
    }

    private String validateMemberFields(String memberNumber, String firstName, String lastName,
                                        String address, String phone, String email) {
        if (memberNumber != null) {
            String mnErr = ValidationUtil.validateRequiredText(memberNumber, "Member number", 50);
            if (mnErr != null) return mnErr;
        }
        String fnErr = ValidationUtil.validateRequiredText(firstName, "First name", 100);
        if (fnErr != null) return fnErr;
        String lnErr = ValidationUtil.validateRequiredText(lastName, "Last name", 100);
        if (lnErr != null) return lnErr;
        String addrErr = ValidationUtil.validateOptionalText(address, "Address", 500);
        if (addrErr != null) return addrErr;
        String phoneErr = ValidationUtil.validateOptionalText(phone, "Phone", 30);
        if (phoneErr != null) return phoneErr;
        String emailErr = ValidationUtil.validateOptionalText(email, "Email", 255);
        if (emailErr != null) return emailErr;
        return null;
    }

    @RequestMapping(value = "/{id}/deactivate", method = RequestMethod.POST)
    public String deactivate(@PathVariable("id") int id, RedirectAttributes redirectAttrs) {
        memberService.deactivateMember(id);
        redirectAttrs.addFlashAttribute("success", "Member deactivated.");
        return "redirect:/members";
    }

    // --- Coverage sub-resource ---

    @RequestMapping(value = "/{id}/coverage/add", method = RequestMethod.POST)
    public String addCoverage(
            @PathVariable("id") int memberId,
            @RequestParam("planId") int planId,
            @RequestParam("coverageOrder") String coverageOrder,
            @RequestParam("effectiveDate") @DateTimeFormat(pattern = "yyyy-MM-dd") Date effectiveDate,
            @RequestParam(value = "terminationDate", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date terminationDate,
            RedirectAttributes redirectAttrs) {
        try {
            memberService.addCoverage(memberId, planId, coverageOrder, effectiveDate, terminationDate);
            redirectAttrs.addFlashAttribute("success", "Coverage record added.");
        } catch (ServiceException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/members/" + memberId;
    }

    @RequestMapping(value = "/{id}/coverage/{coverageId}/remove", method = RequestMethod.POST)
    public String removeCoverage(
            @PathVariable("id") int memberId,
            @PathVariable("coverageId") int coverageId,
            RedirectAttributes redirectAttrs) {
        memberService.removeCoverage(coverageId);
        redirectAttrs.addFlashAttribute("success", "Coverage record removed.");
        return "redirect:/members/" + memberId;
    }
}
