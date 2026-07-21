package com.meridian.claims.job;

import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.InfoRequestDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimAuditEntry;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.InfoRequest;
import com.meridian.claims.service.ScheduledJobLogService;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Flags PENDING_INFO claims whose info_request.due_date has passed as ABANDONED.
 * Keys on info_requests.due_date per claim — not a fixed 30-day timer.
 */
public class StaleClaimJob implements Job {

    private static final Logger LOG = Logger.getLogger(StaleClaimJob.class);

    @Autowired private ClaimDAO claimDAO;
    @Autowired private InfoRequestDAO infoRequestDAO;
    @Autowired private ClaimAuditDAO claimAuditDAO;
    @Autowired private ScheduledJobLogService jobLogService;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("StaleClaimJob");
        int count = 0;
        try {
            LOG.info("StaleClaimJob: starting scan");
            Date now = new Date();

            // Pass 1 (read-only): collect all due claims. We must NOT mutate while paging the
            // PENDING_INFO filter — abandoning a claim removes it from the result set and an
            // OFFSET-based next page would skip rows. Collect first, then update.
            List<Claim> due = new ArrayList<Claim>();
            int page = 1;
            while (true) {
                Page<Claim> batch = claimDAO.search(null, "PENDING_INFO", page, 100);
                for (Claim claim : batch.getItems()) {
                    InfoRequest open = infoRequestDAO.findOpenByClaimId(claim.getId());
                    if (open != null && open.getDueDate() != null && now.after(open.getDueDate())) {
                        due.add(claim);
                    }
                }
                if (!batch.hasNext()) break;
                page++;
            }

            // Pass 2: update the collected claims.
            for (Claim claim : due) {
                claimDAO.updateStatus(claim.getId(), ClaimStatus.ABANDONED.name(), claim.getVersion());
                ClaimAuditEntry audit = new ClaimAuditEntry();
                audit.setClaimId(claim.getId());
                audit.setEventType("ABANDONED");
                audit.setOldStatus("PENDING_INFO");
                audit.setNewStatus("ABANDONED");
                audit.setNotes("Stale: PENDING_INFO past info_request due_date");
                claimAuditDAO.insert(audit);
                count++;
            }
            jobLogService.complete(logId, count);
            LOG.info("StaleClaimJob: done — " + count + " claim(s) abandoned");
        } catch (Exception e) {
            LOG.error("StaleClaimJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }
}
