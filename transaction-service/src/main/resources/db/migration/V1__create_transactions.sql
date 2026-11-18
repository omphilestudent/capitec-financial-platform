CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    transaction_reference VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_transaction_status CHECK (
        status IN ('PENDING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

CREATE INDEX idx_transactions_created_at
    ON transactions(created_at);

CREATE INDEX idx_transactions_status
    ON transactions(status);
