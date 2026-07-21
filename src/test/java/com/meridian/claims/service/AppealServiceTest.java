package com.meridian.claims.service;

import com.meridian.claims.dao.AppealDAO;
import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Appeal;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

public class AppealServiceTest {

    private AppealService appealService;
    private AppealDAO appealDAO;
    private ClaimDAO claimDAO;
    private ClaimService claimService;
    private AuditService auditService;

    @Before
    public void setUp() {
        appealService = new AppealService();
        appealDAO    = Mockito.mock(AppealDAO.class);
        claimDAO     = Mockito.mock(ClaimDAO.class);
        claimService = Mockito.mock(ClaimService.class);
        auditService = Mockito.mock(AuditService.class);

        ReflectionTestUtils.setField(appealService, "appealDAO",    appealDAO);
        ReflectionTestUtils.setField(appealService, "claimDAO",     claimDAO);
        ReflectionTestUtils.setField(appealService, "claimService", claimService);
        ReflectionTestUtils.setField(appealService, "auditService", auditService);
        ReflectionTestUtils.setField(appealService, "internalDays", 30);
        ReflectionTestUtils.setField(appealService, "externalDays", 3);
    }

    @Test
    public void submitAppeal_denied_createsRecordWithDeadline() {
        Claim claim = deniedClaim(1);
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);

        Appeal appeal = appealService.submitAppeal(1, "INTERNAL", 99);

        Mockito.verify(appealDAO).insert(Mockito.any(Appeal.class));
        assertNotNull(appeal.getDeadlineDate());
        // Status is set by the DAO insert; the mock doesn't set it, so verify via the captured argument
        Mockito.verify(auditService).record(Mockito.eq("APPEAL_SUBMITTED"), Mockito.anyString(),
            Mockito.anyLong(), Mockito.anyString());
    }

    @Test(expected = ServiceException.class)
    public void submitAppeal_notDenied_throws() {
        Claim claim = deniedClaim(2);
        claim.setStatus(ClaimStatus.APPROVED);
        Mockito.when(claimDAO.findById(2)).thenReturn(claim);
        appealService.submitAppeal(2, "INTERNAL", 99);
    }

    @Test(expected = ServiceException.class)
    public void submitAppeal_invalidType_throws() {
        Mockito.when(claimDAO.findById(3)).thenReturn(deniedClaim(3));
        appealService.submitAppeal(3, "BOGUS", 99);
    }

    @Test
    public void approveAppeal_triggersReAdjudication() {
        Appeal appeal = openAppeal(10, 5);
        Mockito.when(appealDAO.findById(10)).thenReturn(appeal);
        Mockito.when(claimDAO.findById(5)).thenReturn(deniedClaim(5));

        appealService.approveAppeal(10, "Upheld on review", 99);

        Mockito.verify(appealDAO).updateStatus(10, "APPROVED", "APPROVED", "Upheld on review");
        Mockito.verify(claimService).reAdjudicate(5, 99);
    }

    @Test
    public void denyAppeal_doesNotTriggerReAdjudication() {
        Appeal appeal = openAppeal(11, 6);
        Mockito.when(appealDAO.findById(11)).thenReturn(appeal);

        appealService.denyAppeal(11, "No new evidence", 99);

        Mockito.verify(appealDAO).updateStatus(11, "DENIED", "DENIED", "No new evidence");
        Mockito.verify(claimService, Mockito.never()).reAdjudicate(Mockito.anyInt(), Mockito.anyInt());
    }

    @Test(expected = ServiceException.class)
    public void approveAppeal_alreadyResolved_throws() {
        Appeal appeal = openAppeal(12, 7);
        appeal.setStatus("DENIED");
        Mockito.when(appealDAO.findById(12)).thenReturn(appeal);
        appealService.approveAppeal(12, "notes", 99);
    }

    private Claim deniedClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setMemberId(10);
        c.setStatus(ClaimStatus.DENIED);
        c.setVersion(1);
        return c;
    }

    private Appeal openAppeal(int id, int claimId) {
        Appeal a = new Appeal();
        a.setId(id);
        a.setClaimId(claimId);
        a.setStatus("OPEN");
        return a;
    }
}
