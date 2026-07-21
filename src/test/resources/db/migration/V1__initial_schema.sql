-- H2-compatible copy of V1 (identical — H2 handles these DDL statements fine)
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
