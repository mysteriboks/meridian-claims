-- =====================================================================
-- Meridian Claims — V2 foundation hardening
--
-- Append-only follow-up to V1 (Phase 1 review). Two foundation fixes that
-- get progressively more expensive to retrofit as tables multiply:
--
--   1. set_updated_at() trigger function + triggers so updated_at actually
--      tracks modifications. V1 only defaulted updated_at on INSERT; nothing
--      maintained it on UPDATE. Every table created from here on should
--      attach this trigger (see the pattern at the bottom).
--   2. Composite index on audit_log (entity_type, entity_id) — the audit
--      trail is read almost exclusively as "everything that happened to
--      entity X", which V1 had no supporting index for.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Shared updated_at maintenance trigger
-- ---------------------------------------------------------------------
-- One reusable function; each table gets a BEFORE UPDATE trigger that calls
-- it. Keeps the "updated_at always reflects the last modification" guarantee
-- in the database rather than relying on every DAO to remember to set it.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE PROCEDURE set_updated_at();

-- audit_log rows are conceptually immutable (we append, never edit), so it
-- intentionally does NOT get an updated_at trigger.

-- ---------------------------------------------------------------------
-- 2. Audit lookup-by-entity index
-- ---------------------------------------------------------------------
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
