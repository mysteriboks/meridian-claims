package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimAccumulatorContributionDAO;
import com.meridian.claims.dao.DeductibleAccumulatorDAO;
import com.meridian.claims.model.ClaimAccumulatorContribution;
import com.meridian.claims.model.DeductibleAccumulator;
import com.meridian.claims.util.Money;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Reverses the deductible/OOP accumulator contribution made by a paid claim.
 * Must be called inside an active @Transactional context.
 */
@Service
public class VoidReversalService {

    private static final Logger LOG = Logger.getLogger(VoidReversalService.class);

    @Autowired private ClaimAccumulatorContributionDAO contributionDAO;
    @Autowired private DeductibleAccumulatorDAO accumulatorDAO;

    /**
     * Reverses the accumulator contribution for the given claim.
     * Throws ServiceException if no contribution exists or it was already reversed.
     */
    public void reverse(int claimId, int memberId, int planId, java.util.Date benefitYearStart) {
        ClaimAccumulatorContribution contribution = contributionDAO.findByClaimId(claimId);
        if (contribution == null) {
            throw new ServiceException("No accumulator contribution found for claim id=" + claimId + "; cannot reverse");
        }
        if (contribution.isReversed()) {
            throw new ServiceException("Accumulator contribution for claim id=" + claimId + " is already reversed");
        }

        DeductibleAccumulator acc = accumulatorDAO.findOrCreateForUpdate(memberId, planId, benefitYearStart);

        // Floor at zero, but surface any anomaly: reversing more than was accumulated means the
        // accumulator and the contribution row disagree (e.g. concurrent reversal). Don't lose it silently.
        BigDecimal deductibleRemainder =
            Money.subtract(acc.getDeductibleAccumulated(), contribution.getDeductibleContributed());
        if (deductibleRemainder.compareTo(Money.ZERO) < 0) {
            LOG.warn("Deductible reversal underflow for claim id=" + claimId +
                ": accumulated=" + acc.getDeductibleAccumulated() +
                " contributed=" + contribution.getDeductibleContributed() + "; flooring at zero");
        }
        BigDecimal oopRemainder =
            Money.subtract(acc.getOopAccumulated(), contribution.getOopContributed());
        if (oopRemainder.compareTo(Money.ZERO) < 0) {
            LOG.warn("OOP reversal underflow for claim id=" + claimId +
                ": accumulated=" + acc.getOopAccumulated() +
                " contributed=" + contribution.getOopContributed() + "; flooring at zero");
        }

        acc.setDeductibleAccumulated(Money.max(Money.ZERO, deductibleRemainder));
        acc.setOopAccumulated(Money.max(Money.ZERO, oopRemainder));

        accumulatorDAO.update(acc);
        contributionDAO.markReversed(claimId);

        LOG.info("Reversed accumulator contribution for claim id=" + claimId +
            " deductible=" + contribution.getDeductibleContributed() +
            " oop=" + contribution.getOopContributed());
    }
}
