package com.meridian.claims.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * All reporting queries. Returns List&lt;Map&lt;String,Object&gt;&gt; so results bind directly
 * to JSTL without extra result POJOs. Each method corresponds to one report or dashboard widget.
 */
public interface ReportDAO {

    // ---- Dashboard widgets ----
    Map<String, Long> claimCountsByStatus();
    long claimsSubmittedToday();
    long claimsSubmittedThisWeek();
    long myQueueSize(int reviewerUserId);
    long slaBreachCount();
    List<Map<String, Object>> recentActivity(int limit);
    List<Map<String, Object>> topDenialReasons(int days, int topN);
    Map<String, Object> paymentTotals();

    // ---- Standard reports ----
    List<Map<String, Object>> claimsSummary(Date from, Date to, Integer planId);
    List<Map<String, Object>> claimsDetail(Date from, Date to, String status, Integer planId,
                                            Integer providerId, int page, int size);
    long claimsDetailCount(Date from, Date to, String status, Integer planId, Integer providerId);
    List<Map<String, Object>> denialReport(Date from, Date to);
    List<Map<String, Object>> paymentReport(Date from, Date to, Integer planId, Integer providerId);
    List<Map<String, Object>> memberActivityReport(int memberId);
    List<Map<String, Object>> providerActivityReport(int providerId);
    List<Map<String, Object>> slaPerformanceReport(Date from, Date to);
    List<Map<String, Object>> adjudicationRuleReport(Date from, Date to);
    List<Map<String, Object>> appealsReport(Date from, Date to);
    List<Map<String, Object>> cobReport(Date from, Date to);
    List<Map<String, Object>> subrogationReport();
    List<Map<String, Object>> feeScheduleCoverageReport();
}
