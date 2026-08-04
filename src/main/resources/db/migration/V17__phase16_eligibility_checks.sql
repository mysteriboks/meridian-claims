-- =====================================================================
-- Meridian Claims — V17 Phase 16: 270/271 Real-Time Eligibility
--
-- Adds:
--   * eligibility_checks — a record of every real-time eligibility inquiry
--     Meridian sent (X12 270) and the response it received (X12 271),
--     triggered by the "Check Eligibility" action on the member screen.
--     Not tied to a claim — a standalone verification record, same spirit
--     as phi_access_log (an event log, not a domain entity with a lifecycle).
-- =====================================================================

CREATE TABLE eligibility_checks (
    id                  SERIAL          PRIMARY KEY,
    member_id           INTEGER         NOT NULL REFERENCES members(id),
    provider_id         INTEGER         REFERENCES providers(id),
    service_type        VARCHAR(50),
    inquiry_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_at         TIMESTAMP,
    result_status       VARCHAR(20)     NOT NULL, -- ACTIVE / INACTIVE / ERROR
    coverage_snapshot   TEXT,                      -- human-readable summary of the 271 response
    checked_by_user_id  INTEGER         REFERENCES users(id),
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_eligibility_result_status CHECK (result_status IN ('ACTIVE', 'INACTIVE', 'ERROR'))
);

CREATE INDEX idx_eligibility_checks_member ON eligibility_checks (member_id);
CREATE INDEX idx_eligibility_checks_inquiry_at ON eligibility_checks (inquiry_at);

CREATE TRIGGER eligibility_checks_updated_at
    BEFORE UPDATE ON eligibility_checks
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
