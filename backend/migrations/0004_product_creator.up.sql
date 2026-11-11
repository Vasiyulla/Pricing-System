-- Version 0004: Track product creators, idempotency keys, and per-user contribution indexes
ALTER TABLE products ADD COLUMN IF NOT EXISTS created_by_user_id VARCHAR(128);
ALTER TABLE products ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_products_idempotency_key
    ON products(idempotency_key)
    WHERE idempotency_key IS NOT NULL AND idempotency_key != '';

CREATE INDEX IF NOT EXISTS idx_products_creator_created
    ON products(created_by_user_id, created_at DESC)
    WHERE created_by_user_id IS NOT NULL AND created_by_user_id != '';

CREATE INDEX IF NOT EXISTS idx_price_records_submitter_observed
    ON price_records(submitted_by_user_id, observed_at DESC)
    WHERE submitted_by_user_id IS NOT NULL AND submitted_by_user_id != '';

