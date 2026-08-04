-- =====================================================================
-- Meridian Claims — V16 Phase 13: Trading-Partner & Transport Layer
--
-- Adds:
--   * trading_partners — the trading-partner master. Each row describes one
--     partner's X12 identity (ISA/GS qualifiers), which transactions they
--     exchange, and how files move to/from them (local directory or SFTP).
--     Transport credentials are NEVER stored here in plaintext — only a
--     credential_ref key that TradingPartnerService resolves from the
--     external prod property overlay (see docs/meridian-claims-prod.properties.sample),
--     the same pattern the DB datasource itself uses (CLAUDE.md "Config").
-- =====================================================================

CREATE TABLE trading_partners (
    id                      SERIAL          PRIMARY KEY,
    partner_name            VARCHAR(100)    NOT NULL,
    isa_qualifier           VARCHAR(2)      NOT NULL,
    isa_id                  VARCHAR(15)     NOT NULL,
    gs_id                   VARCHAR(15)     NOT NULL,
    enabled_transactions    VARCHAR(200),                       -- comma-separated, e.g. "837,999,277CA,835"
    transport_type          VARCHAR(10)     NOT NULL DEFAULT 'LOCAL', -- LOCAL / SFTP
    transport_host          VARCHAR(255),
    transport_port          INTEGER,
    transport_username      VARCHAR(100),
    transport_credential_ref VARCHAR(200),                      -- key resolved from the external prod overlay; never a secret itself
    inbound_path            VARCHAR(500),                       -- local dir (LOCAL) or remote path (SFTP)
    outbound_path           VARCHAR(500),
    active                  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_trading_partner_transport CHECK (transport_type IN ('LOCAL', 'SFTP'))
);

CREATE INDEX idx_trading_partners_active ON trading_partners (active);

CREATE TRIGGER trading_partners_updated_at
    BEFORE UPDATE ON trading_partners
    FOR EACH ROW EXECUTE PROCEDURE set_updated_at();

-- edi_transactions rows can now optionally be attributed to the trading partner
-- that sent/received them, so the Integrations monitor and reconciliation can
-- filter/group by partner.
ALTER TABLE edi_transactions ADD COLUMN trading_partner_id INTEGER REFERENCES trading_partners(id);
CREATE INDEX idx_edi_transactions_trading_partner ON edi_transactions (trading_partner_id);
