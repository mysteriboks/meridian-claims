-- =====================================================================
-- Meridian Claims — V12 Phase 9: Straight-Through Batch Claim Intake
--
-- Adds:
--   * claim_intake_batches — file-level idempotency ledger for the
--     inbound claim file poller job. Keyed on SHA-256 file hash so
--     re-submitting the same file is a no-op.
--   * Seeded 'system' user — the batch actor for claims submitted by
--     the intake job. active=false blocks login; role STAFF keeps it
--     within the existing CHECK constraint.
-- =====================================================================

-- ---------------------------------------------------------------------
-- claim_intake_batches : one row per processed inbound file
-- ---------------------------------------------------------------------
CREATE TABLE claim_intake_batches (
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
    CONSTRAINT uq_intake_file_hash UNIQUE (file_hash),
    CONSTRAINT ck_intake_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_intake_batches_status ON claim_intake_batches (status);

CREATE TRIGGER claim_intake_batches_updated_at
    BEFORE UPDATE ON claim_intake_batches
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ---------------------------------------------------------------------
-- Seed the system user (batch actor). active=false prevents login.
-- The password_hash value is a bcrypt placeholder that can never match
-- any real password (cost-12 hash of an unpredictable internal value).
-- ---------------------------------------------------------------------
INSERT INTO users (
    username, password_hash, full_name, role,
    active, failed_login_count, force_reset,
    password_changed_at, created_at, updated_at
) VALUES (
    'system',
    '$2a$12$SYSTEM_PLACEHOLDER_HASH_CANNOT_MATCH_ANY_REAL_PASSWORD_xx',
    'Batch Intake System',
    'STAFF',
    FALSE, 0, FALSE,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
