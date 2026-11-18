ALTER TABLE transactions ADD COLUMN entry_kind VARCHAR(30) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE transactions ADD COLUMN posted_at TIMESTAMP;

UPDATE transactions SET posted_at = created_at WHERE status = 'COMPLETED' AND posted_at IS NULL;

ALTER TABLE transactions ADD CONSTRAINT chk_transaction_entry_kind
    CHECK (entry_kind IN ('STANDARD', 'OPENING_BALANCE', 'FUNDING'));
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_posted_status
    CHECK (status <> 'COMPLETED' OR posted_at IS NOT NULL);

CREATE UNIQUE INDEX uq_transactions_idempotency_key
    ON transactions(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uq_transactions_one_opening_balance_per_account
    ON transactions(account_id) WHERE entry_kind = 'OPENING_BALANCE';
