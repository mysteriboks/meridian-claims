-- =====================================================================
-- Meridian Claims — V20 Phase 19: Reference-Data & Registry Feeds
--
-- Adds:
--   * reference_data_import_batches / reference_data_import_rows — a
--     stage-then-apply ledger for bulk code-set/registry imports (ICD-10,
--     CPT/HCPCS, X12 CARC/RARC, NPPES NPI validation). Staging never
--     touches the live lookup tables or providers; an explicit Admin
--     "Apply" action does, honoring the CPT AMA-license rule (admin
--     review before any procedure-code data is written) — extended to
--     every feed type for consistency, not just CPT.
--   * carc_rarc_codes — the official X12 external CARC/RARC code list,
--     distinct from the app's own curated denial_reason_codes.
--   * providers.npi_validation_status / npi_validated_at — the result of
--     the most recent NPPES check for that provider.
-- =====================================================================

CREATE TABLE reference_data_import_batches (
    id                  SERIAL          PRIMARY KEY,
    feed_type           VARCHAR(30)     NOT NULL,
    file_name           VARCHAR(255)    NOT NULL,
    file_hash           VARCHAR(64)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'STAGED',
    total_records       INTEGER         NOT NULL DEFAULT 0,
    added_count         INTEGER         NOT NULL DEFAULT 0,
    changed_count       INTEGER         NOT NULL DEFAULT 0,
    flagged_count       INTEGER         NOT NULL DEFAULT 0,
    error_message       TEXT,
    applied_by_user_id  INTEGER         REFERENCES users(id),
    applied_at          TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refdata_file_hash UNIQUE (file_hash),
    CONSTRAINT ck_refdata_feed_type CHECK (feed_type IN ('DIAGNOSIS_CODES', 'PROCEDURE_CODES', 'CARC_RARC', 'NPPES')),
    CONSTRAINT ck_refdata_status CHECK (status IN ('STAGED', 'APPLIED', 'FAILED'))
);

CREATE INDEX idx_refdata_batches_status ON reference_data_import_batches (status);

CREATE TRIGGER refdata_batches_updated_at
    BEFORE UPDATE ON reference_data_import_batches
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE reference_data_import_rows (
    id           SERIAL          PRIMARY KEY,
    batch_id     INTEGER         NOT NULL REFERENCES reference_data_import_batches(id),
    row_type     VARCHAR(10)     NOT NULL,
    code         VARCHAR(50)     NOT NULL,
    description  VARCHAR(500),
    extra        VARCHAR(500),
    applied      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_refdata_row_type CHECK (row_type IN ('ADD', 'CHANGE', 'FLAG'))
);

CREATE INDEX idx_refdata_rows_batch ON reference_data_import_rows (batch_id);

CREATE TABLE carc_rarc_codes (
    code        VARCHAR(10)     PRIMARY KEY,
    code_type   VARCHAR(4)      NOT NULL,
    description VARCHAR(500)    NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_carc_rarc_code_type CHECK (code_type IN ('CARC', 'RARC'))
);

CREATE TRIGGER carc_rarc_codes_updated_at
    BEFORE UPDATE ON carc_rarc_codes
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

ALTER TABLE providers ADD COLUMN npi_validation_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED';
ALTER TABLE providers ADD COLUMN npi_validated_at TIMESTAMP;
ALTER TABLE providers ADD CONSTRAINT ck_provider_npi_validation_status
    CHECK (npi_validation_status IN ('UNVERIFIED', 'VALID', 'INVALID'));
