package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.SlaBreachDAO;
import com.meridian.claims.job.SlaEscalationJob;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.util.Page;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

public class SlaEscalationJobTest {

    private SlaEscalationJob job;
    private ClaimDAO claimDAO;
    private SlaBreachDAO slaBreachDAO;
    private ClaimAuditDAO claimAuditDAO;
    private SlaService slaService;
    private MailService mailService;
    private ScheduledJobLogService jobLogService;
    private JobExecutionContext ctx;

    @Before
    public void setUp() {
        job = new SlaEscalationJob();
        claimDAO    = Mockito.mock(ClaimDAO.class);
        slaBreachDAO = Mockito.mock(SlaBreachDAO.class);
        claimAuditDAO = Mockito.mock(ClaimAuditDAO.class);
        slaService  = Mockito.mock(SlaService.class);
        mailService = Mockito.mock(MailService.class);
        jobLogService = Mockito.mock(ScheduledJobLogService.class);
        ctx         = Mockito.mock(JobExecutionContext.class);

        ReflectionTestUtils.setField(job, "claimDAO", claimDAO);
        ReflectionTestUtils.setField(job, "slaBreachDAO", slaBreachDAO);
        ReflectionTestUtils.setField(job, "claimAuditDAO", claimAuditDAO);
        ReflectionTestUtils.setField(job, "slaService", slaService);
        ReflectionTestUtils.setField(job, "mailService", mailService);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
        ReflectionTestUtils.setField(job, "supervisorEmail", "admin@test.local");
    }

    @Test
    public void execute_breachedClaim_noPriorRow_recordsBreachAndEmails() {
        Claim claim = new Claim();
        claim.setId(1);
        claim.setClaimNumber("CLM-20260629-000001");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setStatusEnteredAt(hoursAgo(30));

        Page<Claim> page = new Page<Claim>(Arrays.asList(claim), 1, 100, 1);
        Page<Claim> empty = new Page<Claim>(Collections.<Claim>emptyList(), 1, 100, 0);

        Mockito.when(claimDAO.search(null, "SUBMITTED", 1, 100)).thenReturn(page);
        Mockito.when(claimDAO.search(null, "IN_REVIEW", 1, 100)).thenReturn(empty);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(empty);
        Mockito.when(slaService.isBreached(claim)).thenReturn(true);
        Mockito.when(slaService.computeExpectedBy(Mockito.anyString(), Mockito.any())).thenReturn(new Date());
        Mockito.when(slaBreachDAO.existsForClaimStatus(1, "SUBMITTED")).thenReturn(false);

        job.execute(ctx);

        Mockito.verify(slaBreachDAO).insert(Mockito.any());
        Mockito.verify(claimAuditDAO).insert(Mockito.any());
        Mockito.verify(mailService).send(
            Mockito.eq("admin@test.local"),
            Mockito.contains("CLM-20260629-000001"),
            Mockito.anyString());
    }

    @Test
    public void execute_existingBreachRow_doesNotDuplicate() {
        Claim claim = new Claim();
        claim.setId(2);
        claim.setClaimNumber("CLM-20260629-000002");
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setStatusEnteredAt(hoursAgo(30));

        Page<Claim> page = new Page<Claim>(Arrays.asList(claim), 1, 100, 1);
        Page<Claim> empty = new Page<Claim>(Collections.<Claim>emptyList(), 1, 100, 0);

        Mockito.when(claimDAO.search(null, "SUBMITTED", 1, 100)).thenReturn(page);
        Mockito.when(claimDAO.search(null, "IN_REVIEW", 1, 100)).thenReturn(empty);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(empty);
        Mockito.when(slaService.isBreached(claim)).thenReturn(true);
        Mockito.when(slaBreachDAO.existsForClaimStatus(2, "SUBMITTED")).thenReturn(true); // already recorded

        job.execute(ctx);

        Mockito.verify(slaBreachDAO, Mockito.never()).insert(Mockito.any());
        Mockito.verify(mailService, Mockito.never()).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void execute_notBreached_doesNothing() {
        Claim claim = new Claim();
        claim.setId(3);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setStatusEnteredAt(hoursAgo(2));

        Page<Claim> page = new Page<Claim>(Arrays.asList(claim), 1, 100, 1);
        Page<Claim> empty = new Page<Claim>(Collections.<Claim>emptyList(), 1, 100, 0);

        Mockito.when(claimDAO.search(null, "SUBMITTED", 1, 100)).thenReturn(page);
        Mockito.when(claimDAO.search(null, "IN_REVIEW", 1, 100)).thenReturn(empty);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(empty);
        Mockito.when(slaService.isBreached(claim)).thenReturn(false);

        job.execute(ctx);

        Mockito.verify(slaBreachDAO, Mockito.never()).insert(Mockito.any());
        Mockito.verify(mailService, Mockito.never()).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    private Date hoursAgo(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        return cal.getTime();
    }
}
