package com.meridian.claims.controller;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.meridian.claims.service.HealthService;
import com.meridian.claims.service.HealthStatus;

/**
 * Health check endpoint (PHASES.md Phase 1).
 *
 * GET /health renders a small status page showing database reachability and
 * DBCP2 pool statistics. Returns HTTP 503 when the database is down so external
 * monitors / load balancers can detect an unhealthy instance.
 */
@Controller
public class HealthController {

    private final HealthService healthService;

    @Autowired
    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @RequestMapping(value = "/health", method = RequestMethod.GET)
    public String health(Model model, HttpServletResponse response) {
        HealthStatus status = healthService.check();
        model.addAttribute("health", status);
        if (!status.isDatabaseUp()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return "health";
    }
}
