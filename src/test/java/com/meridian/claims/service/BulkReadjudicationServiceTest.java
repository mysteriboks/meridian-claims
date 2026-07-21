package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.model.ClaimStatus;
import com.meridian.claims.util.Page;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class BulkReadjudicationServiceTest {

    private BulkReadjudicationService bulkReadjudicationService;
    private ClaimDAO claimDAO;
    private ClaimService claimService;

    @Before
    public void setUp() {
        bulkReadjudicationService = new BulkReadjudicationService();
        claimDAO    = Mockito.mock(ClaimDAO.class);
        claimService = Mockito.mock(ClaimService.class);

        ReflectionTestUtils.setField(bulkReadjudicationService, "claimDAO",    claimDAO);
        ReflectionTestUtils.setField(bulkReadjudicationService, "claimService", claimService);
    }

    // -------------------------------------------------------------------------
    // Unsupported status → ServiceException
    // -------------------------------------------------------------------------

    @Test(expected = ServiceException.class)
    public void invalidStatus_throws() {
        bulkReadjudicationService.reprocessByStatus("PAID", 1);
    }

    // -------------------------------------------------------------------------
    // One claim errors, second succeeds → count = 1
    // -------------------------------------------------------------------------

    @Test
    public void twoClaimsOneErrors_countsContinues() {
        Claim c1 = deniedClaim(1);
        Claim c2 = deniedClaim(2);

        Page<Claim> page1 = singlePage(Arrays.asList(c1, c2));
        Mockito.when(claimDAO.search(null, "DENIED", 1, 50)).thenReturn(page1);

        // c1 throws, c2 succeeds
        Mockito.doThrow(new ServiceException("adjudication failed"))
            .when(claimService).reAdjudicate(1, 99);
        Mockito.when(claimService.reAdjudicate(2, 99)).thenReturn(c2);

        int count = bulkReadjudicationService.reprocessByStatus("DENIED", 99);

        assertEquals("only the successful claim should be counted", 1, count);
    }

    // -------------------------------------------------------------------------
    // Two DENIED claims both succeed → count = 2
    // -------------------------------------------------------------------------

    @Test
    public void validStatus_processesAllClaims() {
        Claim c1 = deniedClaim(10);
        Claim c2 = deniedClaim(11);

        Page<Claim> page1 = singlePage(Arrays.asList(c1, c2));
        Mockito.when(claimDAO.search(null, "DENIED", 1, 50)).thenReturn(page1);

        Mockito.when(claimService.reAdjudicate(10, 5)).thenReturn(c1);
        Mockito.when(claimService.reAdjudicate(11, 5)).thenReturn(c2);

        int count = bulkReadjudicationService.reprocessByStatus("DENIED", 5);

        assertEquals("both claims should be counted", 2, count);
        Mockito.verify(claimService).reAdjudicate(10, 5);
        Mockito.verify(claimService).reAdjudicate(11, 5);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Claim deniedClaim(int id) {
        Claim c = new Claim();
        c.setId(id);
        c.setStatus(ClaimStatus.DENIED);
        return c;
    }

    /**
     * Builds a single-page Page that has no next page, so the while loop exits after one iteration.
     */
    private Page<Claim> singlePage(java.util.List<Claim> items) {
        // totalItems == items.size() and pageSize == 50, so getTotalPages() == 1, hasNext() == false
        return new Page<Claim>(items, 1, 50, items.size());
    }
}
