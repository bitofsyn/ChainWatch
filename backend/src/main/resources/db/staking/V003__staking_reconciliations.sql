CREATE TABLE IF NOT EXISTS staking_reconciliations (
    id BIGSERIAL PRIMARY KEY,
    network VARCHAR(80) NOT NULL,
    genesis_root VARCHAR(66) NOT NULL,
    accounting_epoch BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','MATCHED','MISMATCH','INCOMPLETE')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    report_json TEXT,
    evidence_json TEXT
);
CREATE INDEX IF NOT EXISTS idx_reconciliation_network ON staking_reconciliations(network,genesis_root,started_at);
-- Attempts are append-only. Repeating an epoch creates evidence, never a monetary posting.
