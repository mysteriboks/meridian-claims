-- H2-compatible stub: PL/pgSQL triggers are skipped (H2 does not support that dialect).
-- The updated_at guarantee is enforced by DAO code in test; production uses DB triggers.
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
