package com.meridian.claims.controller;

import com.meridian.claims.model.Appeal;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AppealService;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Appeal management for REVIEWER/ADMIN.
 * Routes under /appeals.
 */
@Controller
@RequestMapping("/appeals")
public class AppealController {

    @Autowired private AppealService appealService;
    @Autowired private AuthenticationService authService;

    @RequestMapping(method = RequestMethod.GET)
    public String list(Model model) {
        List<Appeal> open = appealService.findOpen();
        model.addAttribute("appeals", open);
        model.addAttribute("today", new java.util.Date());
        return "appeals/list";
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String detail(@PathVariable("id") int id, Model model) {
        Appeal appeal = appealService.findById(id);
        if (appeal == null) {
            return "redirect:/appeals";
        }
        model.addAttribute("appeal", appeal);
        return "appeals/detail";
    }

    /** STAFF or above can submit an appeal on behalf of a member. */
    @RequestMapping(method = RequestMethod.POST)
    public String submit(@RequestParam("claimId") int claimId,
                         @RequestParam("appealType") String appealType,
                         HttpServletRequest req,
                         RedirectAttributes flash) {
        User user = authService.getCurrentUser(req);
        if (user == null) {
            flash.addFlashAttribute("error", "Not authenticated");
            return "redirect:/claims/" + claimId;
        }
        try {
            Appeal appeal = appealService.submitAppeal(claimId, appealType, user.getId());
            flash.addFlashAttribute("success", "Appeal submitted (deadline: " + appeal.getDeadlineDate() + ")");
            return "redirect:/appeals/" + appeal.getId();
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/claims/" + claimId;
        }
    }

    @RequestMapping(value = "/{id}/approve", method = RequestMethod.POST)
    public String approve(@PathVariable("id") int id,
                          @RequestParam("outcomeNotes") String outcomeNotes,
                          HttpServletRequest req,
                          RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(req, flash, id);
        if (user == null) return "redirect:/appeals/" + id;
        try {
            appealService.approveAppeal(id, outcomeNotes, user.getId());
            flash.addFlashAttribute("success", "Appeal approved — claim re-adjudicated");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    @RequestMapping(value = "/{id}/deny", method = RequestMethod.POST)
    public String deny(@PathVariable("id") int id,
                       @RequestParam("outcomeNotes") String outcomeNotes,
                       HttpServletRequest req,
                       RedirectAttributes flash) {
        User user = requireReviewerOrAdmin(req, flash, id);
        if (user == null) return "redirect:/appeals/" + id;
        try {
            appealService.denyAppeal(id, outcomeNotes, user.getId());
            flash.addFlashAttribute("success", "Appeal denied");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    @RequestMapping(value = "/{id}/withdraw", method = RequestMethod.POST)
    public String withdraw(@PathVariable("id") int id,
                           HttpServletRequest req,
                           RedirectAttributes flash) {
        User user = authService.getCurrentUser(req);
        if (user == null) {
            flash.addFlashAttribute("error", "Not authenticated");
            return "redirect:/appeals/" + id;
        }
        try {
            appealService.withdrawAppeal(id, user.getId());
            flash.addFlashAttribute("success", "Appeal withdrawn");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/appeals/" + id;
    }

    private User requireReviewerOrAdmin(HttpServletRequest req, RedirectAttributes flash, int id) {
        User user = authService.getCurrentUser(req);
        if (user == null || (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.REVIEWER)) {
            flash.addFlashAttribute("error", "Only ADMIN or REVIEWER may perform this action");
            return null;
        }
        return user;
    }
}
