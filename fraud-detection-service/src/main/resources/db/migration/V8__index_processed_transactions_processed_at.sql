-- The evaluation backfill pages through processed_transactions in processed_at order. Without this
-- index every page re-sorts the whole table. Additive only; no data is changed.
CREATE INDEX IF NOT EXISTS idx_processed_tx_processed_at ON processed_transactions (processed_at);
