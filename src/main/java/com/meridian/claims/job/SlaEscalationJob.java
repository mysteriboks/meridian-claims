package com.meridian.claims.job;

import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.SlaBreachDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimAuditEntry;
import com.meridian.claims.model.SlaBreach;
import com.meridian.claims.service.MailService;
import com.meridian.claims.service.ScheduledJobLogService;
import com.meridian.claims.service.SlaService;
import com.meridian.claims.util.Page;
import com.meridian.claims.dao.ClaimDAO;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

/**
 * Runs hourly. Scans for claims whose current status has exceeded the SLA threshold
 * and for which no breach row already exists. On first breach: inserts an sla_breaches
 * row, emails the supervisor (ADMIN), and logs to claim_audit. Idempotent.
 */
public class SlaEscalationJob implements Job {

    private static final Logger LOG = Logger.getLogger(SlaEscalationJob.class);

    @Autowired private ClaimDAO claimDAO;
    @Autowired private SlaBreachDAO slaBreachDAO;
    @Autowired private ClaimAuditDAO claimAuditDAO;
    @Autowired private SlaService slaService;
    @Autowired private MailService mailService;
    @Autowired private ScheduledJobLogService jobLogService;

    @Value("${claims.escalation.supervisor.email:admin@meridian.local}")
    private String supervisorEmail;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("SlaEscalationJob");
        LOG.info("SlaEscalationJob: starting scan");
        try {
            // Scan statuses that have SLA thresholds. Inserting breach rows does not change claim
            // status, so claims stay in the filtered set — OFFSET paging is safe here.
            String[] watchedStatuses = {"SUBMITTED", "IN_REVIEW", "PENDING_INFO"};
            int breachCount = 0;
            for (String status : watchedStatuses) {
                int page = 1;
                while (true) {
                    Page<Claim> batch = claimDAO.search(null, status, page, 100);
                    for (Claim claim : batch.getItems()) {
                        if (slaService.isBreached(claim) &&
                                !slaBreachDAO.existsForClaimStatus(claim.getId(), status)) {
                            recordBreach(claim, status);
                            breachCount++;
                        }
                    }
                    if (!batch.hasNext()) break;
                    page++;
                }
            }
            jobLogService.complete(logId, breachCount);
            LOG.info("SlaEscalationJob: done — " + breachCount + " new breach(es) recorded");
        } catch (Exception e) {
            LOG.error("SlaEscalationJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    private void recordBreach(Claim claim, String status) {
        Date expectedBy = slaService.computeExpectedBy(status, claim.getStatusEnteredAt());

        SlaBreach breach = new SlaBreach();
        breach.setClaimId(claim.getId());
        breach.setStatus(status);
        breach.setExpectedBy(expectedBy != null ? expectedBy : new Date());
        slaBreachDAO.insert(breach);

        ClaimAuditEntry audit = new ClaimAuditEntry();
        audit.setClaimId(claim.getId());
        audit.setEventType("SLA_ESCALATED");
        audit.setOldStatus(status);
        audit.setNewStatus(status);
        audit.setNotes("SLA breached in status " + status);
        claimAuditDAO.insert(audit);

        String body = loadTemplate(claim, status, expectedBy);
        mailService.send(supervisorEmail, "SLA Breach: Claim " + claim.getClaimNumber(), body);
    }

    private String loadTemplate(Claim claim, String status, Date expectedBy) {
        return "SLA Breach Alert\n\n" +
            "Claim:  " + claim.getClaimNumber() + "\n" +
            "Status: " + status + "\n" +
            "Expected by: " + expectedBy + "\n" +
            "Detected at: " + new Date() + "\n\n" +
            "Please review and take action.";
    }
}
