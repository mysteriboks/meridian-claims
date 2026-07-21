-- Phase 6: Workflow & Operations
-- Tables: appeals, subrogation_cases, payment_batches, scheduled_job_log, claims_archive
-- Plus: payments.batch_id FK backfill

-- ============================================================
-- APPEALS
-- ============================================================
CREATE TABLE appeals (
    id                  SERIAL PRIMARY KEY,
    claim_id            INTEGER         NOT NULL REFERENCES claims(id),
    member_id           INTEGER         NOT NULL REFERENCES members(id),
    appeal_type         VARCHAR(10)     NOT NULL,   -- INTERNAL / EXTERNAL
    submitted_date      DATE            NOT NULL,
    deadline_date       DATE            NOT NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'OPEN',  -- OPEN / APPROVED / DENIED / WITHDRAWN
    assigned_to         INTEGER         REFERENCES users(id),
    resolved_date       DATE,
    outcome             VARCHAR(10),    -- APPROVED / DENIED / WITHDRAWN
    outcome_notes       TEXT,
    submitted_by_user_id INTEGER        REFERENCES users(id),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appeals_claim    ON appeals (claim_id);
CREATE INDEX idx_appeals_status   ON appeals (status);
CREATE INDEX idx_appeals_deadline ON appeals (deadline_date);

CREATE TRIGGER appeals_updated_at
    BEFORE UPDATE ON appeals
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- SUBROGATION CASES
-- ============================================================
CREATE TABLE subrogation_cases (
    id                  SERIAL PRIMARY KEY,
    claim_id            INTEGER         NOT NULL REFERENCES claims(id),
    opened_date         DATE            NOT NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'OPEN',  -- OPEN / RECOVERED / CLOSED
    liable_party        VARCHAR(200),
    recovery_amount     NUMERIC(12,2),
    notes               TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_subrogation_claim ON subrogation_cases (claim_id);
CREATE INDEX idx_subrogation_status ON subrogation_cases (status);

CREATE TRIGGER subrogation_cases_updated_at
    BEFORE UPDATE ON subrogation_cases
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- PAYMENT BATCHES
-- ============================================================
CREATE TABLE payment_batches (
    id              SERIAL PRIMARY KEY,
    batch_date      DATE            NOT NULL,
    total_amount    NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    status          VARCHAR(15)     NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / EXPORTED
    exported_at     TIMESTAMP,
    file_reference  VARCHAR(200),
    created_by      INTEGER         REFERENCES users(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TRIGGER payment_batches_updated_at
    BEFORE UPDATE ON payment_batches
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- Add batch_id FK to payments (nullable — existing payments have no batch)
ALTER TABLE payments ADD COLUMN batch_id INTEGER REFERENCES payment_batches(id);
CREATE INDEX idx_payments_batch ON payments (batch_id);

-- ============================================================
-- SCHEDULED JOB LOG
-- ============================================================
CREATE TABLE scheduled_job_log (
    id                  SERIAL PRIMARY KEY,
    job_name            VARCHAR(100)    NOT NULL,
    started_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP,
    status              VARCHAR(10)     NOT NULL DEFAULT 'RUNNING',  -- RUNNING / SUCCESS / FAILED
    records_processed   INTEGER         NOT NULL DEFAULT 0,
    error_message       TEXT
);

CREATE INDEX idx_job_log_name ON scheduled_job_log (job_name, started_at DESC);

-- ============================================================
-- CLAIMS ARCHIVE (mirrors claims schema; immutable once moved)
-- ============================================================
CREATE TABLE claims_archive (
    id                      INTEGER         NOT NULL,
    claim_number            VARCHAR(30)     NOT NULL,
    member_id               INTEGER         NOT NULL,
    provider_id             INTEGER         NOT NULL,
    claim_type              VARCHAR(15)     NOT NULL,
    original_claim_id       INTEGER,
    date_of_service         DATE            NOT NULL,
    submission_date         DATE            NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    plan_id                 INTEGER         NOT NULL,
    coverage_order          VARCHAR(10)     NOT NULL,
    denial_reason_code      VARCHAR(50),
    notes                   TEXT,
    archived_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_claims_archive_member ON claims_archive (member_id);
CREATE INDEX idx_claims_archive_number ON claims_archive (claim_number);
