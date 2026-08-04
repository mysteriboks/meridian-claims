package com.meridian.claims.controller;

import com.meridian.claims.model.ReferenceDataImportBatch;
import com.meridian.claims.model.User;
import com.meridian.claims.service.AuthenticationService;
import com.meridian.claims.service.ReferenceDataImportService;
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

@Controller
@RequestMapping("/admin/reference-data-imports")
public class ReferenceDataImportController {

    @Autowired private ReferenceDataImportService referenceDataImportService;
    @Autowired private AuthenticationService authService;

    @RequestMapping(method = RequestMethod.GET)
    public String list(@RequestParam(value = "limit", defaultValue = "50") int limit, Model model) {
        if (limit < 1 || limit > 200) limit = 50;
        model.addAttribute("batches", referenceDataImportService.findRecentBatches(limit));
        model.addAttribute("limit", limit);
        return "admin/reference-data-imports/list";
    }

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public String detail(@PathVariable("id") int id, Model model) {
        ReferenceDataImportBatch batch = referenceDataImportService.findBatchById(id);
        if (batch == null) {
            return "redirect:/admin/reference-data-imports";
        }
        model.addAttribute("batch", batch);
        java.util.List<?> rows = referenceDataImportService.findRowsByBatchId(id);
        model.addAttribute("rows", rows);
        model.addAttribute("rowCount", rows.size());
        return "admin/reference-data-imports/detail";
    }

    @RequestMapping(value = "/{id}/apply", method = RequestMethod.POST)
    public String apply(@PathVariable("id") int id, HttpServletRequest req, RedirectAttributes flash) {
        User user = authService.getCurrentUser(req);
        try {
            referenceDataImportService.applyImport(id, user != null ? user.getId() : 0);
            flash.addFlashAttribute("success", "Import batch applied.");
        } catch (ServiceException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/reference-data-imports/" + id;
    }
}
