-- =====================================================================
-- Meridian Claims — V1 initial schema (Phase 1: Project Foundation)
--
-- Scope per PHASES.md Phase 1 "Database":
--   * users      — application accounts (auth details fleshed out in Phase 2)
--   * audit_log  — generic system audit trail
--
-- Conventions (CLAUDE.md / HIGH_LEVEL_DESIGN.md):
--   * snake_case table and column names
--   * every table has id / created_at / updated_at
--   * all monetary columns are NUMERIC(12,2) — none in this migration yet
-- =====================================================================

-- ---------------------------------------------------------------------
-- users : application login accounts
-- Columns mirror HIGH_LEVEL_DESIGN.md §4 USERS. Phase 2 adds the auth
-- behaviour (lockout, expiry) on top of these columns.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                   SERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL,
    password_hash        VARCHAR(200) NOT NULL,
    full_name            VARCHAR(200) NOT NULL,
    role                 VARCHAR(20)  NOT NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count   INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMP    NULL,
    last_login_at        TIMESTAMP    NULL,
    password_changed_at  TIMESTAMP    NULL,
    force_reset          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'STAFF', 'REVIEWER', 'FINANCE', 'ANALYST'))
);

-- ---------------------------------------------------------------------
-- audit_log : generic system-wide audit trail
-- Claim-specific status auditing lives in claim_audit (Phase 4); this is
-- the catch-all for system events (config changes, logins, job runs).
-- ---------------------------------------------------------------------
CREATE TABLE audit_log (
    id           SERIAL PRIMARY KEY,
    event_type   VARCHAR(80)  NOT NULL,
    entity_type  VARCHAR(80)  NULL,
    entity_id    BIGINT       NULL,
    user_id      INTEGER      NULL REFERENCES users (id),
    description  VARCHAR(1000) NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_event_type ON audit_log (event_type);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
