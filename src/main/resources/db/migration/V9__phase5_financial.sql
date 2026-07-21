-- Phase 5 Slice 2: EOB Generation, Payment Processing & Provider Remittance

CREATE TABLE payments (
    id                      SERIAL PRIMARY KEY,
    claim_id                INTEGER         NOT NULL REFERENCES claims(id),
    billed_total            NUMERIC(12,2)   NOT NULL,
    allowed_total           NUMERIC(12,2)   NOT NULL,
    plan_paid_total         NUMERIC(12,2)   NOT NULL,
    member_responsibility   NUMERIC(12,2)   NOT NULL,
    partial_payment_flag    BOOLEAN         NOT NULL DEFAULT FALSE,
    amount_paid             NUMERIC(12,2),          -- actual amount paid (may be < plan_paid_total on partial)
    remaining_balance       NUMERIC(12,2),
    reference_number        VARCHAR(100),
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING', -- PENDING / PAID / VOIDED
    payment_date            DATE,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_claim  ON payments (claim_id);
CREATE INDEX idx_payments_status ON payments (status);

CREATE TRIGGER payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

CREATE TABLE eob_documents (
    id                  SERIAL PRIMARY KEY,
    claim_id            INTEGER         NOT NULL REFERENCES claims(id),
    member_id           INTEGER         NOT NULL REFERENCES members(id),
    content             TEXT            NOT NULL,
    delivery_method     VARCHAR(10)     NOT NULL DEFAULT 'PENDING', -- PENDING / MAILED / EMAILED
    delivered_at        TIMESTAMP,
    mailed_by_user_id   INTEGER         REFERENCES users(id),
    generated_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_eob_claim  ON eob_documents (claim_id);
CREATE INDEX idx_eob_member ON eob_documents (member_id);

CREATE TABLE remittance_batches (
    id              SERIAL PRIMARY KEY,
    payment_date    DATE            NOT NULL,
    total_paid      NUMERIC(12,2)   NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'GENERATED', -- GENERATED / SENT
    generated_at    TIMESTAMP       NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TABLE remittance_batch_items (
    id                      SERIAL PRIMARY KEY,
    batch_id                INTEGER         NOT NULL REFERENCES remittance_batches(id),
    claim_id                INTEGER         NOT NULL REFERENCES claims(id),
    provider_id             INTEGER         NOT NULL REFERENCES providers(id),
    billed                  NUMERIC(12,2)   NOT NULL,
    allowed                 NUMERIC(12,2)   NOT NULL,
    plan_paid               NUMERIC(12,2)   NOT NULL,
    adjustment_reason_code  VARCHAR(50),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_remit_batch    ON remittance_batch_items (batch_id);
CREATE INDEX idx_remit_provider ON remittance_batch_items (provider_id);
