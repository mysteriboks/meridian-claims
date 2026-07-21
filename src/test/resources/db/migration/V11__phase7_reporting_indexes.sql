-- H2 test stub for V11. H2 does not support partial indexes (WHERE clause);
-- create plain indexes instead so Flyway migrates cleanly.
CREATE INDEX IF NOT EXISTS idx_claims_submission_date ON claims (submission_date);
CREATE INDEX IF NOT EXISTS idx_claims_denial_code ON claims (denial_reason_code, submission_date);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments (payment_date);
CREATE INDEX IF NOT EXISTS idx_adj_results_rule ON adjudication_results (rule_name, passed);
CREATE INDEX IF NOT EXISTS idx_claims_status_entered ON claims (status, status_entered_at);
CREATE INDEX IF NOT EXISTS idx_line_items_no_rate ON claim_line_items (rate_source);
