-- Phase 4: Claim Submission & Adjudication
-- Tables: claims, claim_diagnoses, claim_line_items, deductible_accumulators,
--         claim_accumulator_contributions, adjudication_results, claim_audit
-- Adds denial reason codes needed by the adjudication engine (append-only).

-- ============================================================
-- CLAIMS
-- ============================================================
CREATE TABLE claims (
    id                      SERIAL PRIMARY KEY,
    claim_number            VARCHAR(30)     NOT NULL UNIQUE,
    member_id               INTEGER         NOT NULL REFERENCES members(id),
    provider_id             INTEGER         NOT NULL REFERENCES providers(id),
    claim_type              VARCHAR(15)     NOT NULL,           -- ORIGINAL / CORRECTED / VOID
    original_claim_id       INTEGER         REFERENCES claims(id),
    date_of_service         DATE            NOT NULL,
    submission_date         DATE            NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'SUBMITTED',
    plan_id                 INTEGER         NOT NULL REFERENCES plans(id),
    coverage_order          VARCHAR(10)     NOT NULL,           -- PRIMARY / SECONDARY
    prior_auth_number       VARCHAR(50),
    referral_number         VARCHAR(50),
    cob_primary_paid        NUMERIC(12,2),
    accident_indicator      BOOLEAN         NOT NULL DEFAULT FALSE,
    accident_type           VARCHAR(20),
    accident_date           DATE,
    denial_reason_code      VARCHAR(50)     REFERENCES denial_reason_codes(code),
    notes                   TEXT,
    assigned_to_user_id     INTEGER         REFERENCES users(id),
    created_by_user_id      INTEGER         REFERENCES users(id),
    status_entered_at       TIMESTAMP,
    version                 INTEGER         NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claims_member_dos   ON claims (member_id, date_of_service);
CREATE INDEX idx_claims_status       ON claims (status);
CREATE INDEX idx_claims_claim_number ON claims (claim_number);
CREATE INDEX idx_claims_provider     ON claims (provider_id);

CREATE TRIGGER claims_updated_at
    BEFORE UPDATE ON claims
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- CLAIM DIAGNOSES
-- ============================================================
CREATE TABLE claim_diagnoses (
    id                  SERIAL PRIMARY KEY,
    claim_id            INTEGER         NOT NULL REFERENCES claims(id),
    diagnosis_code      VARCHAR(50)     NOT NULL,
    sequence_number     INTEGER         NOT NULL,
    diagnosis_type      VARCHAR(10)     NOT NULL,           -- PRIMARY / SECONDARY
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claim_diagnoses_claim ON claim_diagnoses (claim_id);

-- ============================================================
-- CLAIM LINE ITEMS
-- ============================================================
CREATE TABLE claim_line_items (
    id                      SERIAL PRIMARY KEY,
    claim_id                INTEGER         NOT NULL REFERENCES claims(id),
    procedure_code          VARCHAR(50)     NOT NULL,
    description             VARCHAR(500),
    billed_amount           NUMERIC(12,2)   NOT NULL,
    allowed_amount          NUMERIC(12,2),
    plan_paid_amount        NUMERIC(12,2),
    member_responsibility   NUMERIC(12,2),
    deductible_applied      NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    copay_applied           NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    rate_source             VARCHAR(30),    -- PROVIDER_SPECIFIC / PLAN_WIDE / NO_RATE
    adjustment_reason_code  VARCHAR(50),
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claim_line_items_claim ON claim_line_items (claim_id);

-- ============================================================
-- DEDUCTIBLE ACCUMULATORS
-- ============================================================
CREATE TABLE deductible_accumulators (
    id                          SERIAL PRIMARY KEY,
    member_id                   INTEGER         NOT NULL REFERENCES members(id),
    plan_id                     INTEGER         NOT NULL REFERENCES plans(id),
    benefit_year_start          DATE            NOT NULL,
    deductible_accumulated      NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    oop_accumulated             NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, plan_id, benefit_year_start)
);

CREATE INDEX idx_deductible_accum ON deductible_accumulators (member_id, plan_id, benefit_year_start);

CREATE TRIGGER deductible_accumulators_updated_at
    BEFORE UPDATE ON deductible_accumulators
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- CLAIM ACCUMULATOR CONTRIBUTIONS
-- ============================================================
CREATE TABLE claim_accumulator_contributions (
    id                          SERIAL PRIMARY KEY,
    claim_id                    INTEGER         NOT NULL UNIQUE REFERENCES claims(id),
    benefit_year_start          DATE            NOT NULL,
    deductible_contributed      NUMERIC(12,2)   NOT NULL,
    oop_contributed             NUMERIC(12,2)   NOT NULL,
    reversed                    BOOLEAN         NOT NULL DEFAULT FALSE,
    applied_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ADJUDICATION RESULTS
-- ============================================================
CREATE TABLE adjudication_results (
    id              SERIAL PRIMARY KEY,
    claim_id        INTEGER         NOT NULL REFERENCES claims(id),
    run_id          INTEGER         NOT NULL DEFAULT 1,
    step_number     INTEGER         NOT NULL,
    rule_name       VARCHAR(50)     NOT NULL,
    rule_type       VARCHAR(10)     NOT NULL,   -- HARD / SOFT
    passed          BOOLEAN         NOT NULL,
    reason          VARCHAR(500),
    evaluated_at    TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_adj_results_claim_run ON adjudication_results (claim_id, run_id);

-- ============================================================
-- CLAIM AUDIT
-- ============================================================
CREATE TABLE claim_audit (
    id                      SERIAL PRIMARY KEY,
    claim_id                INTEGER         NOT NULL REFERENCES claims(id),
    event_type              VARCHAR(50)     NOT NULL,
    old_status              VARCHAR(20),
    new_status              VARCHAR(20),
    changed_by_user_id      INTEGER         REFERENCES users(id),
    change_reason           VARCHAR(500),
    notes                   TEXT,
    changed_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_claim_audit_claim ON claim_audit (claim_id);

-- ============================================================
-- SEED: Additional denial reason codes needed by the adjudication engine
-- The codes TIMELY_FILING, NOT_ELIGIBLE, DUPLICATE, NOT_COVERED, NO_PRIOR_AUTH
-- were seeded in V4. Add NO_AUTH and NO_REFERRAL aliases used by the engine.
-- ============================================================
INSERT INTO denial_reason_codes (code, carc_code, description)
    SELECT 'NO_AUTH', '197', 'Prior authorization required but absent'
    WHERE NOT EXISTS (SELECT 1 FROM denial_reason_codes WHERE code = 'NO_AUTH');

INSERT INTO denial_reason_codes (code, carc_code, description)
    SELECT 'NO_REFERRAL', '37', 'Referral required but absent'
    WHERE NOT EXISTS (SELECT 1 FROM denial_reason_codes WHERE code = 'NO_REFERRAL');
