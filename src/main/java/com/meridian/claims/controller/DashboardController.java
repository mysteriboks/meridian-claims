package com.meridian.claims.controller;

import com.meridian.claims.model.User;
import com.meridian.claims.model.UserRole;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;

@Controller
public class DashboardController {

    @Autowired private ReportService reportService;
    @Autowired private AuthenticationService authService;

    @RequestMapping(value = "/dashboard", method = RequestMethod.GET)
    public String dashboard(HttpServletRequest req, Model model) {
        User user = authService.getCurrentUser(req);

        model.addAttribute("claimsByStatus", reportService.claimCountsByStatus());
        model.addAttribute("todayCount", reportService.claimsSubmittedToday());
        model.addAttribute("weekCount", reportService.claimsSubmittedThisWeek());
        model.addAttribute("recentActivity", reportService.recentActivity());
        model.addAttribute("topDenialReasons", reportService.topDenialReasons());
        model.addAttribute("paymentTotals", reportService.paymentTotals());

        if (user != null && (user.getRole() == UserRole.REVIEWER || user.getRole() == UserRole.ADMIN)) {
            model.addAttribute("myQueueSize", reportService.myQueueSize(user.getId()));
            model.addAttribute("slaBreachCount", reportService.slaBreachCount());
        }
        return "dashboard";
    }
}
