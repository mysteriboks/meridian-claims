-- Phase 7: Reporting & Dashboard — additional indexes for report query performance.
-- No new tables; all reporting runs over the existing schema.

-- Dashboard: claims-by-status counts, keyed on submission_date
CREATE INDEX IF NOT EXISTS idx_claims_submission_date ON claims (submission_date);

-- Denial report: denials by reason code + date range
CREATE INDEX IF NOT EXISTS idx_claims_denial_code ON claims (denial_reason_code, submission_date)
    WHERE denial_reason_code IS NOT NULL;

-- Payment report: payments by date
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments (payment_date)
    WHERE payment_date IS NOT NULL;

-- Adjudication rule report: rule results grouped by rule name
CREATE INDEX IF NOT EXISTS idx_adj_results_rule ON adjudication_results (rule_name, passed);

-- SLA performance: status_entered_at for time-in-status calculations
CREATE INDEX IF NOT EXISTS idx_claims_status_entered ON claims (status, status_entered_at)
    WHERE status_entered_at IS NOT NULL;

-- Fee-schedule coverage: unrated lines (rate_source = 'NO_RATE')
CREATE INDEX IF NOT EXISTS idx_line_items_no_rate ON claim_line_items (rate_source)
    WHERE rate_source = 'NO_RATE';
