-- Version 0002: Move evidence binary payloads to Object Store keys/URLs and optimize indexes
ALTER TABLE evidence ADD COLUMN IF NOT EXISTS object_key VARCHAR(256) DEFAULT '';
ALTER TABLE evidence ADD COLUMN IF NOT EXISTS evidence_url TEXT DEFAULT '';
ALTER TABLE evidence ALTER COLUMN image_base64 DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_price_records_active_prod ON price_records(product_id, store_id, observed_at DESC)
    WHERE superseded_by IS NULL;

