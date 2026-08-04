-- =====================================================================
-- Meridian Claims — V15 Phase 12: EDI Acknowledgments (TA1 / 999 / 277CA)
--
-- Adds:
--   * edi_transactions — the integration transaction log: the reconciliation
--     backbone for the interoperability program (PHASES.md "Legacy Stack
--     Fidelity (Integration Program)"). One row per inbound or outbound EDI
--     interchange/transaction. Acknowledgment rows (999, 277CA, TA1) link
--     back to the inbound transaction they respond to via
--     related_transaction_id, so the full 837 -> 999/277CA chain is
--     reconstructable for any file.
-- =====================================================================

CREATE TABLE edi_transactions (
    id                      SERIAL          PRIMARY KEY,
    direction               VARCHAR(10)     NOT NULL,           -- INBOUND / OUTBOUND
    transaction_type        VARCHAR(10)     NOT NULL,           -- 837, 999, 277CA, TA1
    isa_control_number      VARCHAR(20),
    gs_control_number       VARCHAR(20),
    st_control_number       VARCHAR(20),
    status                  VARCHAR(20)     NOT NULL,           -- ACCEPTED / REJECTED / PARTIAL
    related_transaction_id  INTEGER         REFERENCES edi_transactions(id),
    file_reference          VARCHAR(255),
    detail                  TEXT,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_edi_txn_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_edi_txn_status CHECK (status IN ('ACCEPTED', 'REJECTED', 'PARTIAL'))
);

CREATE INDEX idx_edi_transactions_isa ON edi_transactions (isa_control_number);
CREATE INDEX idx_edi_transactions_related ON edi_transactions (related_transaction_id);
CREATE INDEX idx_edi_transactions_created_at ON edi_transactions (created_at);

CREATE TRIGGER edi_transactions_updated_at
    BEFORE UPDATE ON edi_transactions
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();
