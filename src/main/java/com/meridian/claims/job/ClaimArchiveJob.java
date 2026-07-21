package com.meridian.claims.job;

import com.meridian.claims.dao.ClaimArchiveDAO;
import com.meridian.claims.service.ScheduledJobLogService;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Calendar;
import java.util.Date;

/**
 * Nightly: moves terminal-state claims older than the retention threshold (default 7 years)
 * from `claims` into `claims_archive`. Archived claims remain viewable via Admin search but
 * are excluded from active worklists (they no longer live in the `claims` table).
 */
public class ClaimArchiveJob implements Job {

    private static final Logger LOG = Logger.getLogger(ClaimArchiveJob.class);

    @Autowired private ClaimArchiveDAO claimArchiveDAO;
    @Autowired private ScheduledJobLogService jobLogService;

    @Value("${claims.archive.retention.years:7}")
    private int retentionYears;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("ClaimArchiveJob");
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, -retentionYears);
            Date cutoff = cal.getTime();

            int archived = claimArchiveDAO.archiveClaimsOlderThan(cutoff);
            jobLogService.complete(logId, archived);
            LOG.info("ClaimArchiveJob: done — " + archived + " claim(s) archived (cutoff=" + cutoff + ")");
        } catch (Exception e) {
            LOG.error("ClaimArchiveJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }
}
