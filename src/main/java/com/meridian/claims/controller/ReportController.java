package com.meridian.claims.controller;

import com.meridian.claims.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * All standard reports. Routes under /reports/*.
 * CSV export available on every report via ?export=csv.
 * Role access: ANALYST, REVIEWER, FINANCE, and ADMIN may read reports. STAFF is
 * blocked from /reports and /dashboard by RoleFilter — aggregate report data
 * exceeds STAFF minimum-necessary access (HIPAA).
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

    @Autowired private ReportService reportService;
    @Autowired private com.meridian.claims.util.PaginationConfig paginationConfig;

    // -------------------------------------------------------------------------
    // Claims Summary
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/claims-summary", method = RequestMethod.GET)
    public String claimsSummary(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "planId", required = false) Integer planId,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(3));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.claimsSummary(from, to, planId);
        if ("csv".equals(export)) return writeCsv(response, "claims-summary", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/claims-summary";
    }

    // -------------------------------------------------------------------------
    // Claims Detail
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/claims-detail", method = RequestMethod.GET)
    public String claimsDetail(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "status", defaultValue = "") String status,
            @RequestParam(value = "planId", required = false) Integer planId,
            @RequestParam(value = "providerId", required = false) Integer providerId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(1));
        Date to   = parseDate(toStr, new Date());
        String statusFilter = status.isEmpty() ? null : status;
        if ("csv".equals(export)) {
            // Export all pages
            List<Map<String, Object>> all = reportService.claimsDetail(from, to, statusFilter, planId, providerId, 1, 10000).getItems();
            return writeCsv(response, "claims-detail", all);
        }
        com.meridian.claims.util.Page<Map<String, Object>> pageResult =
            reportService.claimsDetail(from, to, statusFilter, planId, providerId, page, paginationConfig.getReportPageSize());
        model.addAttribute("page", pageResult);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        model.addAttribute("statusFilter", status); model.addAttribute("planId", planId);
        model.addAttribute("providerId", providerId);
        return "reports/claims-detail";
    }

    // -------------------------------------------------------------------------
    // Denial Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/denials", method = RequestMethod.GET)
    public String denialReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(3));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.denialReport(from, to);
        if ("csv".equals(export)) return writeCsv(response, "denial-report", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/denials";
    }

    // -------------------------------------------------------------------------
    // Payment Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/payments", method = RequestMethod.GET)
    public String paymentReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "planId", required = false) Integer planId,
            @RequestParam(value = "providerId", required = false) Integer providerId,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(1));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.paymentReport(from, to, planId, providerId);
        if ("csv".equals(export)) return writeCsv(response, "payment-report", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/payments";
    }

    // -------------------------------------------------------------------------
    // Member Activity
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/member-activity", method = RequestMethod.GET)
    public String memberActivity(
            @RequestParam(value = "memberId", required = false) Integer memberId,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = memberId != null
            ? reportService.memberActivityReport(memberId)
            : java.util.Collections.<Map<String,Object>>emptyList();
        if ("csv".equals(export) && memberId != null) return writeCsv(response, "member-activity", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("memberId", memberId);
        return "reports/member-activity";
    }

    // -------------------------------------------------------------------------
    // Provider Activity
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/provider-activity", method = RequestMethod.GET)
    public String providerActivity(
            @RequestParam(value = "providerId", required = false) Integer providerId,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = providerId != null
            ? reportService.providerActivityReport(providerId)
            : java.util.Collections.<Map<String,Object>>emptyList();
        if ("csv".equals(export) && providerId != null) return writeCsv(response, "provider-activity", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("providerId", providerId);
        return "reports/provider-activity";
    }

    // -------------------------------------------------------------------------
    // SLA Performance
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/sla-performance", method = RequestMethod.GET)
    public String slaPerformance(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(1));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.slaPerformanceReport(from, to);
        if ("csv".equals(export)) return writeCsv(response, "sla-performance", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/sla-performance";
    }

    // -------------------------------------------------------------------------
    // Adjudication Rule Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/adjudication-rules", method = RequestMethod.GET)
    public String adjudicationRules(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(3));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.adjudicationRuleReport(from, to);
        if ("csv".equals(export)) return writeCsv(response, "adjudication-rules", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/adjudication-rules";
    }

    // -------------------------------------------------------------------------
    // Appeals Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/appeals", method = RequestMethod.GET)
    public String appealsReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(3));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.appealsReport(from, to);
        if ("csv".equals(export)) return writeCsv(response, "appeals-report", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/appeals";
    }

    // -------------------------------------------------------------------------
    // COB Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/cob", method = RequestMethod.GET)
    public String cobReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        Date from = parseDate(fromStr, monthsAgo(3));
        Date to   = parseDate(toStr, new Date());
        List<Map<String, Object>> rows = reportService.cobReport(from, to);
        if ("csv".equals(export)) return writeCsv(response, "cob-report", rows);
        model.addAttribute("rows", rows);
        model.addAttribute("from", fromStr); model.addAttribute("to", toStr);
        return "reports/cob";
    }

    // -------------------------------------------------------------------------
    // Subrogation Report
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/subrogation", method = RequestMethod.GET)
    public String subrogationReport(
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.subrogationReport();
        if ("csv".equals(export)) return writeCsv(response, "subrogation-report", rows);
        model.addAttribute("rows", rows);
        return "reports/subrogation";
    }

    // -------------------------------------------------------------------------
    // Fee Schedule Coverage
    // -------------------------------------------------------------------------
    @RequestMapping(value = "/fee-schedule-coverage", method = RequestMethod.GET)
    public String feeScheduleCoverage(
            @RequestParam(value = "export", required = false) String export,
            Model model, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.feeScheduleCoverageReport();
        if ("csv".equals(export)) return writeCsv(response, "fee-schedule-coverage", rows);
        model.addAttribute("rows", rows);
        return "reports/fee-schedule-coverage";
    }

    // -------------------------------------------------------------------------
    // Report index
    // -------------------------------------------------------------------------
    @RequestMapping(method = RequestMethod.GET)
    public String index() {
        return "reports/index";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String writeCsv(HttpServletResponse response, String filename,
                             List<Map<String, Object>> rows) throws IOException {
        String csv = reportService.toCsv(rows);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename + ".csv");
        response.getWriter().write(csv);
        return null;
    }

    private Date parseDate(String s, Date fallback) {
        if (s == null || s.trim().isEmpty()) return fallback;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s.trim());
        } catch (ParseException e) {
            return fallback;
        }
    }

    private Date monthsAgo(int months) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -months);
        return cal.getTime();
    }
}
