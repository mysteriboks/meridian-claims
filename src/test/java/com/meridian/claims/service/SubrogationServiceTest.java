package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.dao.SubrogationCaseDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.model.SubrogationCase;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.*;

public class SubrogationServiceTest {

    private SubrogationService subrogationService;
    private SubrogationCaseDAO subrogationCaseDAO;
    private ClaimDAO claimDAO;
    private AuditService auditService;

    @Before
    public void setUp() {
        subrogationService  = new SubrogationService();
        subrogationCaseDAO  = Mockito.mock(SubrogationCaseDAO.class);
        claimDAO            = Mockito.mock(ClaimDAO.class);
        auditService        = Mockito.mock(AuditService.class);

        ReflectionTestUtils.setField(subrogationService, "subrogationCaseDAO", subrogationCaseDAO);
        ReflectionTestUtils.setField(subrogationService, "claimDAO",            claimDAO);
        ReflectionTestUtils.setField(subrogationService, "auditService",        auditService);
    }

    @Test
    public void openIfAccident_accidentClaim_createsCase() {
        Claim claim = paidAccidentClaim(1);
        Mockito.when(claimDAO.findById(1)).thenReturn(claim);
        Mockito.when(subrogationCaseDAO.findByClaimId(1)).thenReturn(null);

        SubrogationCase sc = subrogationService.openIfAccident(1);

        Mockito.verify(subrogationCaseDAO).insert(Mockito.any(SubrogationCase.class));
        assertNotNull(sc);
    }

    @Test
    public void openIfAccident_notAccident_returnsNull() {
        Claim claim = paidAccidentClaim(2);
        claim.setAccidentIndicator(false);
        Mockito.when(claimDAO.findById(2)).thenReturn(claim);

        SubrogationCase sc = subrogationService.openIfAccident(2);

        assertNull(sc);
        Mockito.verify(subrogationCaseDAO, Mockito.never()).insert(Mockito.any());
    }

    @Test
    public void openIfAccident_alreadyExists_returnsExisting() {
        Claim claim = paidAccidentClaim(3);
        Mockito.when(claimDAO.findById(3)).thenReturn(claim);
        SubrogationCase existing = new SubrogationCase();
        existing.setId(77);
        Mockito.when(subrogationCaseDAO.findByClaimId(3)).thenReturn(existing);

        SubrogationCase sc = subrogationService.openIfAccident(3);

        assertEquals(77, sc.getId());
        Mockito.verify(subrogationCaseDAO, Mockito.never()).insert(Mockito.any());
    }

    @Test
    public void recordRecovery_openCase_recordsAndAudits() {
        SubrogationCase sc = openCase(10, 5);
        Mockito.when(subrogationCaseDAO.findById(10)).thenReturn(sc);

        subrogationService.recordRecovery(10, "Insurer ABC", Money.of("5000.00"), "settled", 99);

        Mockito.verify(subrogationCaseDAO).recordRecovery(10, "Insurer ABC", Money.of("5000.00"), "settled");
    }

    @Test(expected = ServiceException.class)
    public void recordRecovery_zeroAmount_throws() {
        Mockito.when(subrogationCaseDAO.findById(10)).thenReturn(openCase(10, 5));
        subrogationService.recordRecovery(10, "party", Money.ZERO, "", 99);
    }

    private Claim paidAccidentClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setMemberId(10);
        c.setStatus(ClaimStatus.PAID);
        c.setAccidentIndicator(true);
        return c;
    }

    private SubrogationCase openCase(int id, int claimId) {
        SubrogationCase sc = new SubrogationCase();
        sc.setId(id);
        sc.setClaimId(claimId);
        sc.setStatus("OPEN");
        return sc;
    }
}
