package com.meridian.claims.service;

import com.meridian.claims.dao.ReportDAO;
import com.meridian.claims.util.CsvWriter;
import com.meridian.claims.util.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Reporting service. Delegates all queries to ReportDAO; adds CSV export via CsvWriter.
 * All methods are read-only and do not require @Transactional.
 */
@Service
public class ReportService {

    @Autowired private ReportDAO reportDAO;

    // -------------------------------------------------------------------------
    // Dashboard
    // -------------------------------------------------------------------------

    public Map<String, Long> claimCountsByStatus() { return reportDAO.claimCountsByStatus(); }
    public long claimsSubmittedToday()             { return reportDAO.claimsSubmittedToday(); }
    public long claimsSubmittedThisWeek()          { return reportDAO.claimsSubmittedThisWeek(); }
    public long myQueueSize(int userId)            { return reportDAO.myQueueSize(userId); }
    public long slaBreachCount()                   { return reportDAO.slaBreachCount(); }
    public List<Map<String, Object>> recentActivity() { return reportDAO.recentActivity(20); }
    public List<Map<String, Object>> topDenialReasons() { return reportDAO.topDenialReasons(30, 5); }
    public Map<String, Object> paymentTotals()     { return reportDAO.paymentTotals(); }

    // -------------------------------------------------------------------------
    // Reports
    // -------------------------------------------------------------------------

    public List<Map<String, Object>> claimsSummary(Date from, Date to, Integer planId) {
        return reportDAO.claimsSummary(from, to, planId);
    }

    public Page<Map<String, Object>> claimsDetail(Date from, Date to, String status,
                                                   Integer planId, Integer providerId,
                                                   int page, int size) {
        List<Map<String, Object>> items = reportDAO.claimsDetail(from, to, status, planId, providerId, page, size);
        long total = reportDAO.claimsDetailCount(from, to, status, planId, providerId);
        return new Page<Map<String, Object>>(items, page, size, (int) total);
    }

    public List<Map<String, Object>> denialReport(Date from, Date to) {
        return reportDAO.denialReport(from, to);
    }

    public List<Map<String, Object>> paymentReport(Date from, Date to,
                                                    Integer planId, Integer providerId) {
        return reportDAO.paymentReport(from, to, planId, providerId);
    }

    public List<Map<String, Object>> memberActivityReport(int memberId) {
        return reportDAO.memberActivityReport(memberId);
    }

    public List<Map<String, Object>> providerActivityReport(int providerId) {
        return reportDAO.providerActivityReport(providerId);
    }

    public List<Map<String, Object>> slaPerformanceReport(Date from, Date to) {
        return reportDAO.slaPerformanceReport(from, to);
    }

    public List<Map<String, Object>> adjudicationRuleReport(Date from, Date to) {
        return reportDAO.adjudicationRuleReport(from, to);
    }

    public List<Map<String, Object>> appealsReport(Date from, Date to) {
        return reportDAO.appealsReport(from, to);
    }

    public List<Map<String, Object>> cobReport(Date from, Date to) {
        return reportDAO.cobReport(from, to);
    }

    public List<Map<String, Object>> subrogationReport() {
        return reportDAO.subrogationReport();
    }

    public List<Map<String, Object>> feeScheduleCoverageReport() {
        return reportDAO.feeScheduleCoverageReport();
    }

    // -------------------------------------------------------------------------
    // CSV export — reuses the shared CsvWriter util
    // -------------------------------------------------------------------------

    /**
     * Converts any List&lt;Map&lt;String,Object&gt;&gt; report result to a CSV string.
     * Column headers are the map keys from the first row.
     */
    public String toCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        CsvWriter csv = new CsvWriter();
        // Header from first row's key set (LinkedHashMap preserves SQL column order)
        String[] headers = rows.get(0).keySet().toArray(new String[0]);
        csv.header(headers);
        for (Map<String, Object> row : rows) {
            String[] cells = new String[headers.length];
            for (int i = 0; i < headers.length; i++) {
                Object val = row.get(headers[i]);
                cells[i] = val != null ? val.toString() : "";
            }
            csv.row(cells);
        }
        return csv.build();
    }
}
