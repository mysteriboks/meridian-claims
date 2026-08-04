package com.meridian.claims.controller;

import com.meridian.claims.service.EnrollmentIntakeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/enrollment-batches")
public class EnrollmentBatchController {

    @Autowired private EnrollmentIntakeService enrollmentIntakeService;

    @RequestMapping(method = RequestMethod.GET)
    public String list(@RequestParam(value = "limit", defaultValue = "50") int limit,
                       Model model) {
        if (limit < 1 || limit > 200) limit = 50;
        model.addAttribute("batches", enrollmentIntakeService.findRecentBatches(limit));
        model.addAttribute("limit", limit);
        return "admin/enrollment-batches";
    }
}
