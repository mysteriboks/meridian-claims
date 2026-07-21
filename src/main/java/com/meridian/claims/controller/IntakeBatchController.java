package com.meridian.claims.controller;

import com.meridian.claims.service.IntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/intake-batches")
public class IntakeBatchController {

    @Autowired private IntakeService intakeService;

    @RequestMapping(method = RequestMethod.GET)
    public String list(@RequestParam(value = "limit", defaultValue = "50") int limit,
                       Model model) {
        if (limit < 1 || limit > 200) limit = 50;
        model.addAttribute("batches", intakeService.findRecentBatches(limit));
        model.addAttribute("limit", limit);
        return "admin/intake-batches";
    }
}
