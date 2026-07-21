package com.meridian.claims.job;

import com.meridian.claims.dao.ClaimAuditDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.InfoRequestDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.InfoRequest;
import com.meridian.claims.service.ScheduledJobLogService;
import com.meridian.claims.util.Page;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class StaleClaimJobTest {

    private StaleClaimJob job;
    private ClaimDAO claimDAO;
    private InfoRequestDAO infoRequestDAO;
    private ClaimAuditDAO claimAuditDAO;
    private ScheduledJobLogService jobLogService;

    @Before
    public void setUp() {
        job = new StaleClaimJob();
        claimDAO = Mockito.mock(ClaimDAO.class);
        infoRequestDAO = Mockito.mock(InfoRequestDAO.class);
        claimAuditDAO = Mockito.mock(ClaimAuditDAO.class);
        jobLogService = Mockito.mock(ScheduledJobLogService.class);
        ReflectionTestUtils.setField(job, "claimDAO", claimDAO);
        ReflectionTestUtils.setField(job, "infoRequestDAO", infoRequestDAO);
        ReflectionTestUtils.setField(job, "claimAuditDAO", claimAuditDAO);
        ReflectionTestUtils.setField(job, "jobLogService", jobLogService);
    }

    @Test
    public void execute_abandonsClaimsPastDueDate() {
        Claim c1 = pendingInfo(1);
        Claim c2 = pendingInfo(2);
        // Single page of 2 claims
        Page<Claim> page = new Page<Claim>(java.util.Arrays.asList(c1, c2), 1, 100, 2);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(page);

        // c1 past due, c2 not
        InfoRequest pastDue = new InfoRequest();
        pastDue.setDueDate(daysAgo(5));
        InfoRequest future = new InfoRequest();
        future.setDueDate(daysFromNow(5));
        Mockito.when(infoRequestDAO.findOpenByClaimId(1)).thenReturn(pastDue);
        Mockito.when(infoRequestDAO.findOpenByClaimId(2)).thenReturn(future);

        job.execute(Mockito.mock(JobExecutionContext.class));

        // Only c1 abandoned
        Mockito.verify(claimDAO).updateStatus(1, "ABANDONED", c1.getVersion());
        Mockito.verify(claimDAO, Mockito.never()).updateStatus(Mockito.eq(2), Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    public void execute_collectsAcrossPagesBeforeMutating() {
        // Regression for paging-while-mutating: two full pages, ALL due. Every claim must be
        // collected (read-only) before any status update, so none are skipped.
        List<Claim> pageOne = new ArrayList<Claim>();
        for (int i = 1; i <= 100; i++) pageOne.add(pendingInfo(i));
        List<Claim> pageTwo = new ArrayList<Claim>();
        for (int i = 101; i <= 150; i++) pageTwo.add(pendingInfo(i));

        Page<Claim> p1 = new Page<Claim>(pageOne, 1, 100, 150);  // hasNext true
        Page<Claim> p2 = new Page<Claim>(pageTwo, 2, 100, 150);  // hasNext false
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(p1);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 2, 100)).thenReturn(p2);

        InfoRequest pastDue = new InfoRequest();
        pastDue.setDueDate(daysAgo(1));
        Mockito.when(infoRequestDAO.findOpenByClaimId(Mockito.anyInt())).thenReturn(pastDue);

        job.execute(Mockito.mock(JobExecutionContext.class));

        // All 150 claims must be abandoned — none skipped by page shifting
        Mockito.verify(claimDAO, Mockito.times(150))
            .updateStatus(Mockito.anyInt(), Mockito.eq("ABANDONED"), Mockito.anyInt());
        Mockito.verify(jobLogService).complete(Mockito.anyInt(), Mockito.eq(150));
    }

    @Test
    public void execute_noClaims_completesWithZero() {
        Page<Claim> empty = new Page<Claim>(Collections.<Claim>emptyList(), 1, 100, 0);
        Mockito.when(claimDAO.search(null, "PENDING_INFO", 1, 100)).thenReturn(empty);

        job.execute(Mockito.mock(JobExecutionContext.class));

        Mockito.verify(jobLogService).complete(Mockito.anyInt(), Mockito.eq(0));
    }

    private Claim pendingInfo(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setStatus(ClaimStatus.PENDING_INFO);
        c.setVersion(1);
        return c;
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
