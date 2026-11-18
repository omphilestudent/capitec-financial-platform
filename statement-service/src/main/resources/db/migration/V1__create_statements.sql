CREATE TABLE statements (
    id UUID PRIMARY KEY,
    statement_reference VARCHAR(40) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    statement_period_start DATE NOT NULL,
    statement_period_end DATE NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    closing_balance NUMERIC(19, 2) NOT NULL,
    total_credits NUMERIC(19, 2) NOT NULL,
    total_debits NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    storage_key VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_statement_period CHECK (statement_period_start <= statement_period_end),
    CONSTRAINT chk_statement_status CHECK (status IN ('GENERATED', 'FAILED')),
    CONSTRAINT uq_statement_account_period UNIQUE (account_id, statement_period_start, statement_period_end)
);

CREATE INDEX idx_statements_customer ON statements(customer_id, generated_at);
CREATE INDEX idx_statements_account ON statements(account_id, generated_at);

CREATE TABLE statement_download_tokens (
    id UUID PRIMARY KEY,
    statement_id UUID NOT NULL REFERENCES statements(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_download_tokens_statement ON statement_download_tokens(statement_id);

CREATE TABLE statement_access_logs (
    id UUID PRIMARY KEY,
    statement_id UUID REFERENCES statements(id),
    customer_id UUID,
    actor_subject VARCHAR(100),
    access_type VARCHAR(30) NOT NULL,
    accessed_at TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),

    CONSTRAINT chk_access_type CHECK (access_type IN (
        'GENERATED', 'LIST', 'VIEW', 'LINK_CREATED', 'LINK_REVOKED', 'DOWNLOAD_ATTEMPT', 'DOWNLOADED'))
);

CREATE INDEX idx_access_logs_statement ON statement_access_logs(statement_id, accessed_at);
CREATE INDEX idx_access_logs_customer ON statement_access_logs(customer_id, accessed_at);
