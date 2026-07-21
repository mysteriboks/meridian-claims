-- Phase 5 Slice 1: Claim Review, Assignment, SLA & PHI Logging
-- Tables: info_requests, claim_notes, sla_breaches, phi_access_log

CREATE TABLE info_requests (
    id                      SERIAL PRIMARY KEY,
    claim_id                INTEGER         NOT NULL REFERENCES claims(id),
    requested_from          VARCHAR(10)     NOT NULL,   -- MEMBER / PROVIDER / BOTH
    requested_by_user_id    INTEGER         NOT NULL REFERENCES users(id),
    requested_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    due_date                DATE            NOT NULL,
    request_notes           TEXT            NOT NULL,
    response_received_at    TIMESTAMP,
    response_notes          TEXT,
    status                  VARCHAR(10)     NOT NULL DEFAULT 'OPEN',  -- OPEN / RESPONDED / WAIVED
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_info_requests_claim ON info_requests (claim_id);
CREATE INDEX idx_info_requests_status ON info_requests (status);

CREATE TRIGGER info_requests_updated_at
    BEFORE UPDATE ON info_requests
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE claim_notes (
    id                  SERIAL PRIMARY KEY,
    claim_id            INTEGER         NOT NULL REFERENCES claims(id),
    author_user_id      INTEGER         NOT NULL REFERENCES users(id),
    note                TEXT            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claim_notes_claim ON claim_notes (claim_id);

CREATE TABLE sla_breaches (
    id          SERIAL PRIMARY KEY,
    claim_id    INTEGER         NOT NULL REFERENCES claims(id),
    status      VARCHAR(20)     NOT NULL,
    expected_by TIMESTAMP       NOT NULL,
    breached_at TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sla_breaches_claim ON sla_breaches (claim_id, status);

CREATE TABLE phi_access_log (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER         REFERENCES users(id),
    member_id   INTEGER         NOT NULL REFERENCES members(id),
    claim_id    INTEGER         NOT NULL REFERENCES claims(id),
    action      VARCHAR(30)     NOT NULL,   -- VIEW / APPROVED / DENIED / INFO_REQUESTED / CLAIM_ASSIGNED
    accessed_at TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_phi_access_claim  ON phi_access_log (claim_id);
CREATE INDEX idx_phi_access_member ON phi_access_log (member_id);
