-- H2-compatible stub for V3 (no PL/pgSQL trigger on user_sessions)
CREATE TABLE user_sessions (
    id           SERIAL PRIMARY KEY,
    user_id      INTEGER      NOT NULL REFERENCES users (id),
    session_id   VARCHAR(200) NOT NULL,
    ip_address   VARCHAR(45)  NULL,
    login_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_at    TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id  ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_login_at ON user_sessions (login_at);
