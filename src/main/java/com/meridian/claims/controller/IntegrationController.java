package com.meridian.claims.controller;

import com.meridian.claims.service.IntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only Admin monitor over the integration transaction log
 * ({@code edi_transactions}) — the reconciliation backbone for the
 * interoperability program (Phase 12+). Mirrors IntakeBatchController.
 */
@Controller
@RequestMapping("/admin/integrations")
public class IntegrationController {

    @Autowired private IntakeService intakeService;

    @RequestMapping(method = RequestMethod.GET)
    public String list(@RequestParam(value = "limit", defaultValue = "50") int limit,
                       Model model) {
        if (limit < 1 || limit > 200) limit = 50;
        model.addAttribute("transactions", intakeService.findRecentEdiTransactions(limit));
        model.addAttribute("limit", limit);
        return "admin/integrations";
    }
}
