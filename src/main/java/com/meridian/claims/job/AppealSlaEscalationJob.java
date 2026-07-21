package com.meridian.claims.job;

import com.meridian.claims.dao.AppealDAO;
import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.SlaBreachDAO;
import com.meridian.claims.model.Appeal;
import com.meridian.claims.model.ClaimAuditEntry;
import com.meridian.claims.model.SlaBreach;
import com.meridian.claims.service.MailService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.List;

/**
 * Runs hourly. Scans OPEN appeals whose deadline_date has passed and which have no
 * breach row yet. On first breach: records an sla_breaches row (status="APPEAL"),
 * emails the supervisor, and logs to claim_audit. Idempotent — never double-emails.
 * Mirrors SlaEscalationJob but keyed on appeals.deadline_date.
 */
public class AppealSlaEscalationJob implements Job {

    private static final Logger LOG = Logger.getLogger(AppealSlaEscalationJob.class);

    private static final String BREACH_STATUS = "APPEAL";

    @Autowired private AppealDAO appealDAO;
    @Autowired private SlaBreachDAO slaBreachDAO;
    @Autowired private ClaimAuditDAO claimAuditDAO;
    @Autowired private MailService mailService;
    @Autowired private ScheduledJobLogService jobLogService;

    @Value("${claims.escalation.supervisor.email:admin@meridian.local}")
    private String supervisorEmail;

    @Override
    public void execute(JobExecutionContext context) {
        int logId = jobLogService.start("AppealSlaEscalationJob");
        int breachCount = 0;
        try {
            LOG.info("AppealSlaEscalationJob: starting scan");
            Date now = new Date();
            // Inserting a breach row does not change appeal status, so the OPEN set is stable.
            List<Appeal> openAppeals = appealDAO.findByStatus("OPEN");
            for (Appeal appeal : openAppeals) {
                if (appeal.getDeadlineDate() != null && now.after(appeal.getDeadlineDate())
                        && !slaBreachDAO.existsForClaimStatus(appeal.getClaimId(), BREACH_STATUS)) {
                    recordBreach(appeal);
                    breachCount++;
                }
            }
            jobLogService.complete(logId, breachCount);
            LOG.info("AppealSlaEscalationJob: done — " + breachCount + " appeal breach(es) recorded");
        } catch (Exception e) {
            LOG.error("AppealSlaEscalationJob failed", e);
            jobLogService.fail(logId, e.getMessage());
        }
    }

    private void recordBreach(Appeal appeal) {
        SlaBreach breach = new SlaBreach();
        breach.setClaimId(appeal.getClaimId());
        breach.setStatus(BREACH_STATUS);
        breach.setExpectedBy(appeal.getDeadlineDate());
        slaBreachDAO.insert(breach);

        ClaimAuditEntry audit = new ClaimAuditEntry();
        audit.setClaimId(appeal.getClaimId());
        audit.setEventType("APPEAL_SLA_BREACHED");
        audit.setNotes("Appeal id=" + appeal.getId() + " past deadline " + appeal.getDeadlineDate());
        claimAuditDAO.insert(audit);

        String body = "Appeal SLA Breach\n\n" +
            "Appeal:  " + appeal.getId() + " (claim " + appeal.getClaimId() + ")\n" +
            "Type:    " + appeal.getAppealType() + "\n" +
            "Deadline: " + appeal.getDeadlineDate() + "\n" +
            "Detected at: " + new Date() + "\n\n" +
            "This appeal is past its deadline and still OPEN. Please action it.";
        mailService.send(supervisorEmail, "Appeal SLA Breach: appeal " + appeal.getId(), body);
    }
}
