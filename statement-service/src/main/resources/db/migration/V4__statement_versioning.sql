
ALTER TABLE statements DROP CONSTRAINT uq_statement_account_period;

ALTER TABLE statements ADD COLUMN version_number INTEGER NOT NULL DEFAULT 1;
ALTER TABLE statements ADD COLUMN version_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT';
ALTER TABLE statements ADD COLUMN superseded_by UUID;
ALTER TABLE statements ADD COLUMN regeneration_reason VARCHAR(500);
ALTER TABLE statements ADD COLUMN eligibility_fingerprint CHAR(64);

UPDATE statements SET version_status = 'FAILED' WHERE status = 'FAILED';

ALTER TABLE statements ADD CONSTRAINT chk_statement_version_status
    CHECK (version_status IN ('CURRENT', 'SUPERSEDED', 'FAILED'));
ALTER TABLE statements ADD CONSTRAINT fk_statement_superseded_by
    FOREIGN KEY (superseded_by) REFERENCES statements(id);
ALTER TABLE statements ADD CONSTRAINT uq_statement_version
    UNIQUE (account_id, statement_period_start, statement_period_end, version_number);

CREATE UNIQUE INDEX uq_statement_current_version
    ON statements(account_id, statement_period_start, statement_period_end)
    WHERE version_status = 'CURRENT';

CREATE TABLE statement_transaction_snapshots (
    statement_id UUID NOT NULL REFERENCES statements(id),
    sequence_no INTEGER NOT NULL,
    transaction_reference VARCHAR(120) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    description VARCHAR(500),
    direction VARCHAR(10) NOT NULL,
    debit NUMERIC(19, 2),
    credit NUMERIC(19, 2),
    running_balance NUMERIC(19, 2) NOT NULL,

    PRIMARY KEY (statement_id, sequence_no),
    CONSTRAINT chk_snapshot_direction CHECK (direction IN ('CREDIT', 'DEBIT'))
);
