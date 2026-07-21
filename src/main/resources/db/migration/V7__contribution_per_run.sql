-- Phase 4 follow-up: allow multiple accumulator contribution rows per claim.
--
-- Re-adjudication retains history: each run reverses the prior contribution (reversed = TRUE)
-- and inserts a NEW contribution row for the same claim. The original UNIQUE(claim_id) constraint
-- permitted only one row per claim, so re-adjudicating a claim that already contributed (an
-- IN_REVIEW claim) would violate it. Drop the constraint; the "active" contribution is the most
-- recent row (see JdbcClaimAccumulatorContributionDAO.findByClaimId — ORDER BY id DESC).
-- An index keeps per-claim lookups fast.

ALTER TABLE claim_accumulator_contributions
    DROP CONSTRAINT IF EXISTS claim_accumulator_contributions_claim_id_key;

CREATE INDEX IF NOT EXISTS idx_contrib_claim ON claim_accumulator_contributions (claim_id);
