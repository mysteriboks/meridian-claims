package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimAccumulatorContributionDAO;
import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.model.ClaimAccumulatorContribution;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.util.Money;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class VoidReversalServiceTest {

    private VoidReversalService voidReversalService;
    private ClaimAccumulatorContributionDAO contributionDAO;
    private DeductibleAccumulatorDAO accumulatorDAO;

    private static final int CLAIM_ID  = 1;
    private static final int MEMBER_ID = 10;
    private static final int PLAN_ID   = 1;
    private static final Date BENEFIT_YEAR_START = new Date(0L);

    @Before
    public void setUp() {
        voidReversalService = new VoidReversalService();
        contributionDAO = Mockito.mock(ClaimAccumulatorContributionDAO.class);
        accumulatorDAO  = Mockito.mock(DeductibleAccumulatorDAO.class);

        ReflectionTestUtils.setField(voidReversalService, "contributionDAO", contributionDAO);
        ReflectionTestUtils.setField(voidReversalService, "accumulatorDAO",  accumulatorDAO);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void happyPath_reversesDeductibleAndOop() {
        ClaimAccumulatorContribution contribution = new ClaimAccumulatorContribution();
        contribution.setClaimId(CLAIM_ID);
        contribution.setDeductibleContributed(Money.of("50.00"));
        contribution.setOopContributed(Money.of("50.00"));
        contribution.setReversed(false);
        Mockito.when(contributionDAO.findByClaimId(CLAIM_ID)).thenReturn(contribution);

        DeductibleAccumulator acc = new DeductibleAccumulator();
        acc.setDeductibleAccumulated(Money.of("200.00"));
        acc.setOopAccumulated(Money.of("200.00"));
        Mockito.when(accumulatorDAO.findOrCreateForUpdate(MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START))
            .thenReturn(acc);

        voidReversalService.reverse(CLAIM_ID, MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START);

        ArgumentCaptor<DeductibleAccumulator> captor = ArgumentCaptor.forClass(DeductibleAccumulator.class);
        Mockito.verify(accumulatorDAO).update(captor.capture());
        DeductibleAccumulator updated = captor.getValue();

        assertEquals("deductible should decrease by 50", Money.of("150.00"), updated.getDeductibleAccumulated());
        assertEquals("oop should decrease by 50", Money.of("150.00"), updated.getOopAccumulated());
        Mockito.verify(contributionDAO).markReversed(CLAIM_ID);
    }

    // -------------------------------------------------------------------------
    // Underflow floors at zero — no exception thrown
    // -------------------------------------------------------------------------

    @Test
    public void underflow_floorsAtZero() {
        ClaimAccumulatorContribution contribution = new ClaimAccumulatorContribution();
        contribution.setClaimId(CLAIM_ID);
        contribution.setDeductibleContributed(Money.of("100.00"));
        contribution.setOopContributed(Money.of("10.00"));
        contribution.setReversed(false);
        Mockito.when(contributionDAO.findByClaimId(CLAIM_ID)).thenReturn(contribution);

        DeductibleAccumulator acc = new DeductibleAccumulator();
        // accumulated (30) < contributed (100) → underflow → floor at 0
        acc.setDeductibleAccumulated(Money.of("30.00"));
        acc.setOopAccumulated(Money.of("10.00"));
        Mockito.when(accumulatorDAO.findOrCreateForUpdate(MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START))
            .thenReturn(acc);

        // Must NOT throw — underflow produces a LOG.warn and floors at zero
        voidReversalService.reverse(CLAIM_ID, MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START);

        ArgumentCaptor<DeductibleAccumulator> captor = ArgumentCaptor.forClass(DeductibleAccumulator.class);
        Mockito.verify(accumulatorDAO).update(captor.capture());
        assertEquals("deductible underflow must floor at zero", Money.ZERO, captor.getValue().getDeductibleAccumulated());
    }

    // -------------------------------------------------------------------------
    // Already reversed — throws ServiceException
    // -------------------------------------------------------------------------

    @Test(expected = ServiceException.class)
    public void alreadyReversed_throwsServiceException() {
        ClaimAccumulatorContribution contribution = new ClaimAccumulatorContribution();
        contribution.setClaimId(CLAIM_ID);
        contribution.setDeductibleContributed(Money.of("50.00"));
        contribution.setOopContributed(Money.of("50.00"));
        contribution.setReversed(true);
        Mockito.when(contributionDAO.findByClaimId(CLAIM_ID)).thenReturn(contribution);

        voidReversalService.reverse(CLAIM_ID, MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START);
    }

    // -------------------------------------------------------------------------
    // Missing contribution record — throws ServiceException
    // -------------------------------------------------------------------------

    @Test(expected = ServiceException.class)
    public void missingContribution_throwsServiceException() {
        Mockito.when(contributionDAO.findByClaimId(CLAIM_ID)).thenReturn(null);

        voidReversalService.reverse(CLAIM_ID, MEMBER_ID, PLAN_ID, BENEFIT_YEAR_START);
    }
}
