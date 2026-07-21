package com.meridian.claims.job;

import com.meridian.claims.dao.AppealDAO;
import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.SlaBreachDAO;
import com.meridian.claims.model.Appeal;
import com.meridian.claims.service.MailService;
import com.meridian.claims.service.ScheduledJobLogService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

public class AppealSlaEscalationJobTest {

    private AppealSlaEscalationJob job;
    private AppealDAO appealDAO;
    private SlaBreachDAO slaBreachDAO;
    private ClaimAuditDAO claimAuditDAO;
    private MailService mailService;
    private ScheduledJobLogService jobLogService;

    @Before
    public void setUp() {
        job = new AppealSlaEscalationJob();
        appealDAO = Mockito.mock(AppealDAO.class);
        slaBreachDAO = Mockito.mock(SlaBreachDAO.class);
        claimAuditDAO = Mockito.mock(ClaimAuditDAO.class);
        mailService = Mockito.mock(MailService.class);
        jobLogService = Mockito.mock(ScheduledJobLogService.class);
        ReflectionTestUtils.setField(job, "appealDAO", appealDAO);
        ReflectionTestUtils.setField(job, "slaBreachDAO", slaBreachDAO);
        ReflectionTestUtils.setField(job, "claimAuditDAO", claimAuditDAO);
        ReflectionTestUtils.setField(job, "mailService", mailService);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
        ReflectionTestUtils.setField(job, "supervisorEmail", "admin@test.local");
    }

    @Test
    public void execute_breachedAppeal_recordsAndEmails() {
        Appeal a = openAppeal(1, 100, daysAgo(2));
        Mockito.when(appealDAO.findByStatus("OPEN")).thenReturn(Arrays.asList(a));
        Mockito.when(slaBreachDAO.existsForClaimStatus(100, "APPEAL")).thenReturn(false);

        job.execute(Mockito.mock(JobExecutionContext.class));

        Mockito.verify(slaBreachDAO).insert(Mockito.any());
        Mockito.verify(mailService).send(Mockito.eq("admin@test.local"),
            Mockito.contains("Appeal SLA Breach"), Mockito.anyString());
        Mockito.verify(jobLogService).complete(Mockito.anyInt(), Mockito.eq(1));
    }

    @Test
    public void execute_alreadyBreached_noDuplicateEmail() {
        Appeal a = openAppeal(2, 200, daysAgo(2));
        Mockito.when(appealDAO.findByStatus("OPEN")).thenReturn(Arrays.asList(a));
        Mockito.when(slaBreachDAO.existsForClaimStatus(200, "APPEAL")).thenReturn(true);

        job.execute(Mockito.mock(JobExecutionContext.class));

        Mockito.verify(slaBreachDAO, Mockito.never()).insert(Mockito.any());
        Mockito.verify(mailService, Mockito.never()).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void execute_notYetDue_noBreach() {
        Appeal a = openAppeal(3, 300, daysFromNow(5));
        Mockito.when(appealDAO.findByStatus("OPEN")).thenReturn(Arrays.asList(a));

        job.execute(Mockito.mock(JobExecutionContext.class));

        Mockito.verify(slaBreachDAO, Mockito.never()).insert(Mockito.any());
    }

    private Appeal openAppeal(int id, int claimId, Date deadline) {
        Appeal a = new Appeal();
        a.setId(id);
        a.setClaimId(claimId);
        a.setAppealType("INTERNAL");
        a.setStatus("OPEN");
        a.setDeadlineDate(deadline);
        return a;
    }

    private Date daysAgo(int d) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -d);
        return cal.getTime();
    }

    private Date daysFromNow(int d) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, d);
        return cal.getTime();
    }
}
