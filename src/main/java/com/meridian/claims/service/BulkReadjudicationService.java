package com.meridian.claims.service;

import com.meridian.claims.dao.ClaimDAO;
import com.meridian.claims.model.Claim;
import com.meridian.claims.util.Page;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Bulk re-adjudication for Admin — useful after a plan rule change affecting historical claims.
 * Processes claims in pages to avoid timeout on large batches.
 * Each claim is re-adjudicated individually and logged to claim_audit.
 */
@Service
public class BulkReadjudicationService {

    private static final Logger LOG = Logger.getLogger(BulkReadjudicationService.class);

    @Autowired private ClaimDAO claimDAO;
    @Autowired private ClaimService claimService;

    /**
     * Re-adjudicates all DENIED or IN_REVIEW claims matching the given status filter.
     * @return count of claims reprocessed
     */
    public int reprocessByStatus(String status, int userId) {
        if (!"DENIED".equals(status) && !"IN_REVIEW".equals(status)) {
            throw new ServiceException("Bulk re-adjudication only supports DENIED or IN_REVIEW; got: " + status);
        }
        int count = 0;
        int errors = 0;
        int page = 1;
        while (true) {
            Page<Claim> batch = claimDAO.search(null, status, page, 50);
            for (Claim claim : batch.getItems()) {
                try {
                    claimService.reAdjudicate(claim.getId(), userId);
                    count++;
                } catch (Exception e) {
                    LOG.warn("Bulk re-adjudication failed for claimId=" + claim.getId() + ": " + e.getMessage());
                    errors++;
                }
            }
            if (!batch.hasNext()) break;
            page++;
        }
        LOG.info("Bulk re-adjudication complete: " + count + " succeeded, " + errors + " errors; status=" + status);
        return count;
    }
}
