-- Phase 3: Core Domain — Members, Providers, Plans, Coverage, Fee Schedules,
--           Prior Authorizations, Referrals, and Lookup tables.

-- ============================================================
-- MEMBERS
-- ============================================================
CREATE TABLE members (
    id                  SERIAL PRIMARY KEY,
    member_number       VARCHAR(20)  NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    dob                 DATE         NOT NULL,
    address             VARCHAR(255),
    phone               VARCHAR(20),
    email               VARCHAR(150),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_members_last_first ON members (last_name, first_name);
CREATE INDEX idx_members_member_number ON members (member_number);
CREATE INDEX idx_members_dob ON members (dob);

CREATE TRIGGER members_updated_at
    BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- PROVIDERS
-- ============================================================
CREATE TABLE providers (
    id                  SERIAL PRIMARY KEY,
    npi                 VARCHAR(10)  NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    provider_type       VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    specialty           VARCHAR(100),
    network_status      VARCHAR(20)  NOT NULL DEFAULT 'IN_NETWORK',
    phone               VARCHAR(20),
    address             VARCHAR(255),
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_providers_npi ON providers (npi);
CREATE INDEX idx_providers_name ON providers (name);
CREATE INDEX idx_providers_specialty ON providers (specialty);

CREATE TRIGGER providers_updated_at
    BEFORE UPDATE ON providers
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- PLANS
-- ============================================================
CREATE TABLE plans (
    id                          SERIAL PRIMARY KEY,
    plan_name                   VARCHAR(200)    NOT NULL,
    plan_type                   VARCHAR(10)     NOT NULL,
    deductible_amount           NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    oop_max                     NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    copay_amount                NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    coverage_pct_in_network     NUMERIC(5,2)    NOT NULL DEFAULT 80.00,
    coverage_pct_out_network    NUMERIC(5,2)    NOT NULL DEFAULT 60.00,
    benefit_year_start          DATE            NOT NULL DEFAULT '2026-01-01',
    timely_filing_days          INTEGER         NOT NULL DEFAULT 180,
    deleted_at                  TIMESTAMP,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE TRIGGER plans_updated_at
    BEFORE UPDATE ON plans
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- PLAN COVERAGE RULES
-- ============================================================
CREATE TABLE plan_coverage_rules (
    id                  SERIAL PRIMARY KEY,
    plan_id             INTEGER     NOT NULL REFERENCES plans(id),
    service_type        VARCHAR(50) NOT NULL,
    coverage_pct        NUMERIC(5,2) NOT NULL DEFAULT 80.00,
    requires_referral   BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_prior_auth BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (plan_id, service_type)
);

CREATE INDEX idx_pcr_plan_id ON plan_coverage_rules (plan_id);

-- ============================================================
-- MEMBER COVERAGE
-- ============================================================
CREATE TABLE member_coverage (
    id                  SERIAL PRIMARY KEY,
    member_id           INTEGER     NOT NULL REFERENCES members(id),
    plan_id             INTEGER     NOT NULL REFERENCES plans(id),
    coverage_order      VARCHAR(10) NOT NULL,
    effective_date      DATE        NOT NULL,
    termination_date    DATE,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_member_coverage_member ON member_coverage (member_id);
CREATE INDEX idx_member_coverage_plan   ON member_coverage (plan_id);

CREATE TRIGGER member_coverage_updated_at
    BEFORE UPDATE ON member_coverage
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- FEE SCHEDULE RATES
-- ============================================================
CREATE TABLE fee_schedule_rates (
    id                  SERIAL PRIMARY KEY,
    plan_id             INTEGER         NOT NULL REFERENCES plans(id),
    provider_id         INTEGER         REFERENCES providers(id),
    procedure_code      VARCHAR(10)     NOT NULL,
    allowed_amount      NUMERIC(12,2)   NOT NULL,
    effective_date      DATE            NOT NULL,
    termination_date    DATE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fsr_plan_proc_date ON fee_schedule_rates (plan_id, procedure_code, effective_date);
CREATE INDEX idx_fsr_provider        ON fee_schedule_rates (provider_id);

CREATE TRIGGER fee_schedule_rates_updated_at
    BEFORE UPDATE ON fee_schedule_rates
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- PRIOR AUTHORIZATIONS
-- ============================================================
CREATE TABLE prior_authorizations (
    id                  SERIAL PRIMARY KEY,
    member_id           INTEGER     NOT NULL REFERENCES members(id),
    provider_id         INTEGER     NOT NULL REFERENCES providers(id),
    procedure_code      VARCHAR(10) NOT NULL,
    service_type        VARCHAR(50),
    authorized_from     DATE        NOT NULL,
    authorized_to       DATE        NOT NULL,
    auth_number         VARCHAR(50) NOT NULL UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approved_units      INTEGER     NOT NULL DEFAULT 1,
    notes               TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pa_member ON prior_authorizations (member_id);
CREATE INDEX idx_pa_auth_number ON prior_authorizations (auth_number);

CREATE TRIGGER prior_authorizations_updated_at
    BEFORE UPDATE ON prior_authorizations
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- REFERRALS
-- ============================================================
CREATE TABLE referrals (
    id                      SERIAL PRIMARY KEY,
    member_id               INTEGER     NOT NULL REFERENCES members(id),
    referring_provider_id   INTEGER     NOT NULL REFERENCES providers(id),
    referred_to_provider_id INTEGER     NOT NULL REFERENCES providers(id),
    service_type            VARCHAR(50) NOT NULL,
    valid_from              DATE        NOT NULL,
    valid_to                DATE        NOT NULL,
    referral_number         VARCHAR(50) NOT NULL UNIQUE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes                   TEXT,
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_referrals_member ON referrals (member_id);
CREATE INDEX idx_referrals_number ON referrals (referral_number);

CREATE TRIGGER referrals_updated_at
    BEFORE UPDATE ON referrals
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- ============================================================
-- LOOKUP TABLES
-- ============================================================
CREATE TABLE denial_reason_codes (
    code        VARCHAR(50)  PRIMARY KEY,
    carc_code   VARCHAR(10),
    description VARCHAR(500) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE procedure_codes (
    code        VARCHAR(50)  PRIMARY KEY,
    description VARCHAR(500) NOT NULL,
    service_type VARCHAR(50),
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE diagnosis_codes (
    code        VARCHAR(50)  PRIMARY KEY,
    description VARCHAR(500) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE service_type_categories (
    code        VARCHAR(50)  PRIMARY KEY,
    description VARCHAR(200) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE
);

-- ============================================================
-- SEED: Service type categories (internal taxonomy)
-- ============================================================
INSERT INTO service_type_categories (code, description) VALUES
    ('OFFICE_VISIT',      'Office / Outpatient Visit'),
    ('PREVENTIVE',        'Preventive Care'),
    ('SPECIALIST',        'Specialist Consultation'),
    ('EMERGENCY',         'Emergency Services'),
    ('URGENT_CARE',       'Urgent Care'),
    ('INPATIENT',         'Inpatient Hospitalization'),
    ('OUTPATIENT',        'Outpatient Surgery / Procedure'),
    ('MENTAL_HEALTH',     'Mental Health / Behavioral Health'),
    ('SUBSTANCE_ABUSE',   'Substance Abuse Treatment'),
    ('PHYSICAL_THERAPY',  'Physical / Occupational Therapy'),
    ('LABORATORY',        'Laboratory / Pathology'),
    ('IMAGING',           'Radiology / Imaging'),
    ('PHARMACY',          'Prescription Drug'),
    ('DURABLE_EQUIPMENT', 'Durable Medical Equipment'),
    ('AMBULANCE',         'Ambulance / Transport'),
    ('HOME_HEALTH',       'Home Health Services'),
    ('SKILLED_NURSING',   'Skilled Nursing Facility'),
    ('HOSPICE',           'Hospice / Palliative Care'),
    ('MATERNITY',         'Maternity / OB'),
    ('OTHER',             'Other / Miscellaneous')
ON CONFLICT (code) DO NOTHING;

-- ============================================================
-- SEED: Common denial reason codes (CARC / RARC, public domain)
-- Seeded on first install only; admin may add/edit afterward.
-- ============================================================
INSERT INTO denial_reason_codes (code, carc_code, description) VALUES
    ('TIMELY_FILING',   '29',  'The time limit for filing has expired'),
    ('NOT_ELIGIBLE',    '26',  'Expenses incurred prior to coverage'),
    ('DUPLICATE',       '18',  'Exact duplicate claim or service'),
    ('NOT_COVERED',     '96',  'Non-covered charge(s)'),
    ('NO_PRIOR_AUTH',   '197', 'Precertification/authorization/notification absent'),
    ('NO_REFERRAL',     '37',  'Claim must be filed by the provider of service'),
    ('UNTIMELY',        '29',  'Time limit for filing has expired'),
    ('COB_EXCEEDED',    '23',  'Payment adjusted due to involvement of another payer'),
    ('COORDINATION',    '22',  'Coordination of benefits'),
    ('DEDUCTIBLE',      '1',   'Deductible amount'),
    ('COPAY',           '2',   'Coinsurance amount'),
    ('OOP_MET',         '3',   'Co-payment amount'),
    ('NOT_MEDICALLY_NECESSARY', '50', 'Non-covered services — not medically necessary'),
    ('INFO_REQUESTED',  '16',  'Claim lacks information or has submission/billing error'),
    ('INVALID_CODE',    '6',   'The procedure code is inconsistent with the modifier')
ON CONFLICT (code) DO NOTHING;

-- procedure_codes table intentionally left empty (CPT is AMA-licensed;
-- admin enters only the codes the organisation is licensed to use).
