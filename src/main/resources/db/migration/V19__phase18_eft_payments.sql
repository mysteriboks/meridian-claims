-- =====================================================================
-- Meridian Claims — V19 Phase 18: EFT/ACH Payment Issuance + TRN Reassociation
--
-- Adds:
--   * providers.ach_routing_number / ach_account_number / ach_account_type —
--     the payee banking details needed to issue a real NACHA ACH credit
--     entry. Nullable: a provider with no banking info configured is simply
--     skipped when a batch's ACH file is generated (reported, not fatal).
--   * eft_payments — one row per payment batch's ACH file generation,
--     carrying the TRN reassociation number shared with the paired 835
--     (Edi835Generator), the batch amount, and a manually-tracked
--     settlement status (no live bank feed exists to detect it automatically).
-- =====================================================================

ALTER TABLE providers ADD COLUMN ach_routing_number VARCHAR(9);
ALTER TABLE providers ADD COLUMN ach_account_number VARCHAR(17);
ALTER TABLE providers ADD COLUMN ach_account_type VARCHAR(10);

ALTER TABLE providers ADD CONSTRAINT ck_provider_ach_account_type
    CHECK (ach_account_type IS NULL OR ach_account_type IN ('CHECKING', 'SAVINGS'));

CREATE TABLE eft_payments (
    id                       SERIAL          PRIMARY KEY,
    payment_batch_id         INTEGER         NOT NULL REFERENCES payment_batches(id),
    remittance_batch_id      INTEGER         NOT NULL REFERENCES remittance_batches(id),
    trn_reassociation_number VARCHAR(30)     NOT NULL,
    amount                   NUMERIC(12,2)   NOT NULL,
    settlement_status        VARCHAR(20)     NOT NULL DEFAULT 'GENERATED',
    ach_file_reference       VARCHAR(255),
    entry_count              INTEGER         NOT NULL DEFAULT 0,
    skipped_provider_count   INTEGER         NOT NULL DEFAULT 0,
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_eft_payment_batch UNIQUE (payment_batch_id),
    CONSTRAINT ck_eft_settlement_status CHECK (settlement_status IN ('GENERATED', 'SETTLED', 'FAILED'))
);

CREATE INDEX idx_eft_payments_batch ON eft_payments (payment_batch_id);

CREATE TRIGGER eft_payments_updated_at
    BEFORE UPDATE ON eft_payments
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
