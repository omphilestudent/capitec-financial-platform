CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          customer_id UUID NOT NULL,
                          account_number VARCHAR(20) NOT NULL UNIQUE,
                          account_type VARCHAR(30) NOT NULL,
                          balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
                          currency VARCHAR(3) NOT NULL DEFAULT 'ZAR',
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT chk_account_balance CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);