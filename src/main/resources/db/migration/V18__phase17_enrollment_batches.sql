-- =====================================================================
-- Meridian Claims — V18 Phase 17: 834 Enrollment Ingestion
--
-- Adds:
--   * enrollment_batches — file-level idempotency ledger for the inbound
--     X12 834 enrollment poller, mirroring claim_intake_batches (V12).
--     Keyed on SHA-256 file hash so re-submitting the same file is a no-op.
-- =====================================================================

CREATE TABLE enrollment_batches (
    id               SERIAL          PRIMARY KEY,
    file_name        VARCHAR(255)    NOT NULL,
    file_hash        VARCHAR(64)     NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'PROCESSING',
    total_records    INTEGER         NOT NULL DEFAULT 0,
    succeeded        INTEGER         NOT NULL DEFAULT 0,
    quarantined      INTEGER         NOT NULL DEFAULT 0,
    error_message    TEXT,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_enrollment_file_hash UNIQUE (file_hash),
    CONSTRAINT ck_enrollment_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_enrollment_batches_status ON enrollment_batches (status);

CREATE TRIGGER enrollment_batches_updated_at
    BEFORE UPDATE ON enrollment_batches
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
