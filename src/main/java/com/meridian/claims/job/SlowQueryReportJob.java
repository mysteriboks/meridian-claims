package com.meridian.claims.job;

import com.meridian.claims.service.MailService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Nightly: queries PostgreSQL's pg_stat_statements for queries whose mean execution time
 * exceeds the configured threshold and emails a summary to the DBA. If pg_stat_statements
 * is unavailable (e.g. H2 in tests, or the extension not installed), the job logs a notice
 * and completes cleanly — it never fails the scheduler.
 */
public class SlowQueryReportJob implements Job {

    private static final Logger LOG = Logger.getLogger(SlowQueryReportJob.class);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MailService mailService;
    @Autowired private ScheduledJobLogService jobLogService;

    @Value("${claims.slowquery.threshold.ms:500}")
    private long thresholdMs;

    @Value("${claims.dba.email:dba@meridian.local}")
    private String dbaEmail;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("SlowQueryReportJob");
        try {
            List<Map<String, Object>> rows;
            try {
                // pg_stat_statements.mean_exec_time is in milliseconds (PG 13+).
                rows = jdbcTemplate.queryForList(
                    "SELECT query, calls, round(mean_exec_time::numeric, 2) AS mean_ms " +
                    "FROM pg_stat_statements WHERE mean_exec_time > ? " +
                    "ORDER BY mean_exec_time DESC LIMIT 20", thresholdMs);
            } catch (Exception statsUnavailable) {
                LOG.info("SlowQueryReportJob: pg_stat_statements not available — skipping (" +
                    statsUnavailable.getMessage() + ")");
                jobLogService.complete(logId, 0);
                return;
            }

            if (rows.isEmpty()) {
                LOG.info("SlowQueryReportJob: no queries over " + thresholdMs + "ms");
                jobLogService.complete(logId, 0);
                return;
            }

            StringBuilder body = new StringBuilder();
            body.append("Slow Query Report — queries exceeding ").append(thresholdMs).append("ms\n\n");
            for (Map<String, Object> row : rows) {
                body.append("mean_ms=").append(row.get("mean_ms"))
                    .append(" calls=").append(row.get("calls"))
                    .append("\n  ").append(String.valueOf(row.get("query"))).append("\n\n");
            }
            mailService.send(dbaEmail, "Meridian Claims — Nightly Slow Query Report", body.toString());

            jobLogService.complete(logId, rows.size());
            LOG.info("SlowQueryReportJob: done — " + rows.size() + " slow queries reported");
        } catch (Exception e) {
            LOG.error("SlowQueryReportJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }
}
