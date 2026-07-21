package com.meridian.claims.controller;

import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ScheduledJobLog;
import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.ArchiveService;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.BulkReadjudicationService;
import com.meridian.claims.service.ScheduledJobLogService;
import com.meridian.claims.service.ServiceException;
import org.apache.log4j.Logger;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

/**
 * Admin operational tools: bulk re-adjudication, scheduled job history + manual trigger,
 * and archived-claim search. All endpoints require ADMIN role (RoleFilter gates /admin/**).
 */
@Controller
@RequestMapping("/admin/operations")
public class AdminOperationsController {

    private static final Logger LOG = Logger.getLogger(AdminOperationsController.class);

    // Quartz JobDetail bean names that an admin may trigger on demand.
    private static final List<String> TRIGGERABLE_JOBS = Arrays.asList(
        "slaEscalationJobDetail", "staleClaimJobDetail", "benefitYearRolloverJobDetail",
        "appealSlaEscalationJobDetail", "slowQueryReportJobDetail", "claimArchiveJobDetail",
        "inboundClaimFilePollerJobDetail");

    @Autowired private BulkReadjudicationService bulkService;
    @Autowired private ScheduledJobLogService jobLogService;
    @Autowired private ArchiveService archiveService;
    @Autowired private Scheduler scheduler;
    @Autowired private AuthenticationService authService;

    @RequestMapping(method = RequestMethod.GET)
    public String index(Model model) {
        model.addAttribute("recentJobs", jobLogService.findRecent(20));
        model.addAttribute("triggerableJobs", TRIGGERABLE_JOBS);
        return "admin/operations";
    }

    @RequestMapping(value = "/bulk-readjudicate", method = RequestMethod.POST)
    public String bulkReadjudicate(@RequestParam("status") String status,
                                    HttpServletRequest req,
                                    RedirectAttributes flash) {
        User user = requireAdmin(req, flash);
        if (user == null) return "redirect:/admin/operations";
        try {
            int count = bulkService.reprocessByStatus(status, user.getId());
            flash.addFlashAttribute("success", "Bulk re-adjudication complete: " + count + " claims reprocessed");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/operations";
    }

    @RequestMapping(value = "/trigger-job", method = RequestMethod.POST)
    public String triggerJob(@RequestParam("jobName") String jobName,
                             HttpServletRequest req,
                             RedirectAttributes flash) {
        User user = requireAdmin(req, flash);
        if (user == null) return "redirect:/admin/operations";
        if (!TRIGGERABLE_JOBS.contains(jobName)) {
            flash.addFlashAttribute("error", "Unknown job: " + jobName);
            return "redirect:/admin/operations";
        }
        try {
            scheduler.triggerJob(JobKey.jobKey(jobName));
            flash.addFlashAttribute("success", "Triggered job: " + jobName);
        } catch (Exception e) {
            LOG.error("Manual job trigger failed jobName=" + jobName, e);
            flash.addFlashAttribute("error", "Could not trigger job: " + e.getMessage());
        }
        return "redirect:/admin/operations";
    }

    @RequestMapping(value = "/archive", method = RequestMethod.GET)
    public String archiveSearch(@RequestParam(value = "q", defaultValue = "") String query,
                                Model model) {
        List<Claim> results = archiveService.search(query);
        model.addAttribute("results", results);
        model.addAttribute("query", query);
        return "admin/archive-search";
    }

    private User requireAdmin(HttpServletRequest req, RedirectAttributes flash) {
        User user = authService.getCurrentUser(req);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            flash.addFlashAttribute("error", "Only ADMIN may perform this action");
            return null;
        }
        return user;
    }
}
