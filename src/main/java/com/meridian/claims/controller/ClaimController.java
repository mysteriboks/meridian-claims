package com.meridian.claims.controller;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.ClaimType;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.ClaimService;
import com.meridian.claims.service.LookupService;
import com.meridian.claims.service.MemberService;
import com.meridian.claims.service.PhiAccessLogService;
import com.meridian.claims.service.ProviderService;
import com.meridian.claims.service.ServiceException;
import com.meridian.claims.service.SlaService;
import com.meridian.claims.util.Page;
import com.meridian.claims.util.ValidationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@RequestMapping("/claims")
public class ClaimController {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setLenient(false);
        binder.registerCustomEditor(Date.class, new java.beans.PropertyEditorSupport() {
            @Override
            public void setAsText(String text) throws IllegalArgumentException {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                    return;
                }
                try {
                    setValue(dateFormat.parse(text.trim()));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid date: " + text + " (expected yyyy-MM-dd)");
                }
            }
        });
    }

    @Autowired private ClaimService claimService;
    @Autowired private MemberService memberService;
    @Autowired private ProviderService providerService;
    @Autowired private com.meridian.claims.service.UserService userService;
    @Autowired private LookupService lookupService;
    @Autowired private PhiAccessLogService phiAccessLogService;
    @Autowired private SlaService slaService;
    @Autowired private AuthenticationService authService;
    @Autowired private com.meridian.claims.util.PaginationConfig paginationConfig;

    // -------------------------------------------------------------------------
    // Worklist
    // -------------------------------------------------------------------------

    @RequestMapping(method = RequestMethod.GET)
    public String list(@RequestParam(value = "q",           defaultValue = "")    String query,
                       @RequestParam(value = "status",      defaultValue = "")    String status,
                       @RequestParam(value = "assignee",    required = false)     Integer assigneeId,
                       @RequestParam(value = "unassigned",  defaultValue = "false") boolean unassignedOnly,
                       @RequestParam(value = "slaBreached", defaultValue = "false") boolean slaBreachedOnly,
                       @RequestParam(value = "page",        defaultValue = "1")   int page,
                       HttpServletRequest httpReq,
                       Model model) {
        User me = resolveUser(httpReq);
        // HIPAA minimum-necessary: REVIEWER and ADMIN see all claims; other roles
        // see only claims they submitted or are assigned to.
        Integer scopeToUserId = null;
        if (me != null && me.getRole() != UserRole.REVIEWER && me.getRole() != UserRole.ADMIN) {
            scopeToUserId = me.getId();
        }
        Page<Claim> claimPage = claimService.searchWorklist(
            query, status.isEmpty() ? null : status,
            assigneeId, unassignedOnly, slaBreachedOnly, scopeToUserId, page, paginationConfig.getListPageSize());
        model.addAttribute("page", claimPage);
        model.addAttribute("query", query);
        model.addAttribute("statusFilter", status);
        model.addAttribute("assigneeId", assigneeId);
        model.addAttribute("unassignedOnly", unassignedOnly);
        model.addAttribute("slaBreachedOnly", slaBreachedOnly);
        model.addAttribute("statuses", ClaimStatus.values());
        model.addAttribute("currentUser", me);
        return "claims/list";
    }

    // -------------------------------------------------------------------------
    // Submission
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/new", method = RequestMethod.GET)
    public String newForm(Model model) {
        model.addAttribute("claimRequest", new SubmitClaimRequest());
        model.addAttribute("members", memberService.listAllActive());
        model.addAttribute("providers", providerService.listAllActive());
        model.addAttribute("procedureCodes", lookupService.listProcedureCodes());
        model.addAttribute("diagnosisCodes", lookupService.listDiagnosisCodes());
        model.addAttribute("claimTypes", ClaimType.values());
        return "claims/form";
    }

    @RequestMapping(method = RequestMethod.POST)
    public String submit(@ModelAttribute("claimRequest") SubmitClaimRequest req,
                         HttpServletRequest httpReq,
                         RedirectAttributes flash,
                         Model model) {
        String validationError = validateSubmitClaimRequest(req);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            model.addAttribute("claimRequest", req);
            model.addAttribute("members", memberService.listAllActive());
            model.addAttribute("providers", providerService.listAllActive());
            model.addAttribute("procedureCodes", lookupService.listProcedureCodes());
            model.addAttribute("diagnosisCodes", lookupService.listDiagnosisCodes());
            model.addAttribute("claimTypes", ClaimType.values());
            return "claims/form";
        }
        int userId = resolveUserId(httpReq);
        try {
            Claim claim = claimService.submit(req, userId);
            flash.addFlashAttribute("success", "Claim " + claim.getClaimNumber() +
                " submitted successfully. Status: " + claim.getStatus());
            return "redirect:/claims/" + claim.getId();
        } catch (ServiceException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("claimRequest", req);
            model.addAttribute("members", memberService.listAllActive());
            model.addAttribute("providers", providerService.listAllActive());
            model.addAttribute("procedureCodes", lookupService.listProcedureCodes());
            model.addAttribute("diagnosisCodes", lookupService.listDiagnosisCodes());
            model.addAttribute("claimTypes", ClaimType.values());
            return "claims/form";
        }
    }

    private String validateSubmitClaimRequest(SubmitClaimRequest req) {
        if (!ValidationUtil.positiveId(req.getMemberId())) return "Member is required.";
        if (!ValidationUtil.positiveId(req.getProviderId())) return "Provider is required.";
        if (req.getDateOfService() == null) return "Date of service is required.";
        if (req.getLineItems() == null || req.getLineItems().isEmpty()) {
            return "At least one line item is required.";
        }
        for (int i = 0; i < req.getLineItems().size(); i++) {
            SubmitClaimRequest.LineItemRow row = req.getLineItems().get(i);
            String cptError = ValidationUtil.validateProcedureCode(row.getProcedureCode());
            if (cptError != null) return "Line item " + (i + 1) + ": " + cptError;
            if (row.getBilledAmount() == null) {
                return "Line item " + (i + 1) + ": billed amount is required.";
            }
        }
        if (req.getDiagnoses() == null || req.getDiagnoses().isEmpty()) {
            return "At least one diagnosis code is required.";
        }
        for (int i = 0; i < req.getDiagnoses().size(); i++) {
            String icd10Error = ValidationUtil.validateDiagnosisCode(req.getDiagnoses().get(i).getDiagnosisCode());
            if (icd10Error != null) return "Diagnosis " + (i + 1) + ": " + icd10Error;
        }
        String notesError = ValidationUtil.validateOptionalText(req.getNotes(), "Notes", 2000);
        if (notesError != null) return notesError;
        return null;
    }

    // -------------------------------------------------------------------------
    // Detail view
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String view(@PathVariable("id") int id, HttpServletRequest httpReq,
                       RedirectAttributes flash, Model model) {
        Claim claim = claimService.findById(id);
        if (claim == null) {
            return "redirect:/claims";
        }
        User me = resolveUser(httpReq);
        if (!claimService.canViewClaim(claim, me)) {
            flash.addFlashAttribute("error", "You do not have permission to view this claim.");
            return "redirect:/claims";
        }
        // PHI access log on every authorised view
        phiAccessLogService.record(claim.getMemberId(), id, "VIEW");

        model.addAttribute("claim", claim);
        model.addAttribute("lineItems", claimService.findLineItems(id));
        model.addAttribute("diagnoses", claimService.findDiagnoses(id));
        model.addAttribute("adjudicationResults", claimService.findAdjudicationResults(id));
        model.addAttribute("auditTrail", claimService.findAuditTrail(id));
        model.addAttribute("infoRequests", claimService.findInfoRequests(id));
        model.addAttribute("notes", claimService.findNotes(id));
        model.addAttribute("slaBreached", slaService.isBreached(claim));
        model.addAttribute("currentUser", me);
        // Reviewers eligible for claim assignment (REVIEWER/ADMIN). Drives the
        // assignment dropdown so a user is picked by name, not a raw id.
        model.addAttribute("reviewers", userService.listAssignableReviewers());
        return "claims/view";
    }

    // -------------------------------------------------------------------------
    // Reviewer actions (REVIEWER or ADMIN only)
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/{id}/approve", method = RequestMethod.POST)
    public String approve(@PathVariable("id") int id,
                          @RequestParam("notes") String notes,
                          HttpServletRequest httpReq,
                          RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.approve(id, user.getId(), notes);
            flash.addFlashAttribute("success", "Claim approved");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/deny", method = RequestMethod.POST)
    public String deny(@PathVariable("id") int id,
                       @RequestParam("denialReasonCode") String denialReasonCode,
                       @RequestParam("notes") String notes,
                       HttpServletRequest httpReq,
                       RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.deny(id, user.getId(), denialReasonCode, notes);
            flash.addFlashAttribute("success", "Claim denied");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/request-info", method = RequestMethod.POST)
    public String requestInfo(@PathVariable("id") int id,
                              @RequestParam("requestedFrom") String requestedFrom,
                              @RequestParam("dueDate") Date dueDate,
                              @RequestParam("requestNotes") String requestNotes,
                              HttpServletRequest httpReq,
                              RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.requestInfo(id, user.getId(), requestedFrom, dueDate, requestNotes);
            flash.addFlashAttribute("success", "Info request created");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/resubmit", method = RequestMethod.POST)
    public String resubmit(@PathVariable("id") int id,
                           HttpServletRequest httpReq,
                           RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.resubmit(id, user.getId());
            flash.addFlashAttribute("success", "Claim moved back to IN_REVIEW");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/assign", method = RequestMethod.POST)
    public String assign(@PathVariable("id") int id,
                         @RequestParam(value = "reviewerId", required = false) Integer reviewerId,
                         HttpServletRequest httpReq,
                         RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.assignClaim(id, reviewerId, user.getId());
            flash.addFlashAttribute("success", reviewerId == null ? "Claim unassigned" : "Claim assigned");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/notes", method = RequestMethod.POST)
    public String addNote(@PathVariable("id") int id,
                          @RequestParam("note") String note,
                          HttpServletRequest httpReq,
                          RedirectAttributes flash) {
        User user = resolveUser(httpReq);
        if (user == null) {
            flash.addFlashAttribute("error", "You must be logged in");
            return "redirect:/claims/" + id;
        }
        try {
            claimService.addNote(id, user.getId(), note);
            flash.addFlashAttribute("success", "Note added");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    @RequestMapping(value = "/{id}/readjudicate", method = RequestMethod.POST)
    public String reAdjudicate(@PathVariable("id") int id,
                                HttpServletRequest httpReq,
                                RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(httpReq, flash, id);
        if (user == null) return "redirect:/claims/" + id;
        try {
            claimService.reAdjudicate(id, user.getId());
            flash.addFlashAttribute("success", "Claim re-adjudicated successfully");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + id;
    }

    // -------------------------------------------------------------------------
    // Info request respond (STAFF, REVIEWER, ADMIN)
    // -------------------------------------------------------------------------

    @RequestMapping(value = "/info-requests/{irId}/respond", method = RequestMethod.POST)
    public String respondInfo(@PathVariable("irId") int irId,
                              @RequestParam("claimId") int claimId,
                              @RequestParam("responseNotes") String responseNotes,
                              HttpServletRequest httpReq,
                              RedirectAttributes flash) {
        User user = resolveUser(httpReq);
        if (user == null) {
            flash.addFlashAttribute("error", "You must be logged in");
            return "redirect:/claims/" + claimId;
        }
        try {
            claimService.respondInfo(irId, user.getId(), responseNotes);
            flash.addFlashAttribute("success", "Info response recorded");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/claims/" + claimId;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User requireReviewerOrAdmin(HttpServletRequest req, RedirectAttributes flash, int claimId) {
        User user = resolveUser(req);
        if (user == null || (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.REVIEWER)) {
            flash.addFlashAttribute("error", "Only ADMIN or REVIEWER may perform this action");
            return null;
        }
        return user;
    }

    private int resolveUserId(HttpServletRequest req) {
        User user = resolveUser(req);
        return user != null ? user.getId() : 0;
    }

    private User resolveUser(HttpServletRequest req) {
        return authService.getCurrentUser(req);
    }
}
