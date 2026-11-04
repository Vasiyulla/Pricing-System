-- Price Bridge Production PostgreSQL 16 + PostGIS Schema
-- Enables spatial geospatial queries, append-only price ledger, and transactional outbox.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- 1. Stores Table with PostGIS Geography Point
CREATE TABLE IF NOT EXISTS stores (
    id VARCHAR(64) PRIMARY KEY,
    chain VARCHAR(64) NOT NULL,
    format VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address TEXT NOT NULL,
    postcode VARCHAR(16) NOT NULL,
    geom GEOGRAPHY(Point, 4326) NOT NULL,
    operating_hours VARCHAR(64) DEFAULT '7:00 AM - 11:00 PM',
    phone VARCHAR(32),
    reliability_score INT DEFAULT 95,
    is_confirmed_partner BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_stores_geom ON stores USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_stores_chain ON stores(chain);

-- 2. Master Product Catalog
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    brand VARCHAR(128) NOT NULL,
    barcode VARCHAR(32) UNIQUE,
    category VARCHAR(64) NOT NULL,
    package_size VARCHAR(64) NOT NULL,
    unit_metric VARCHAR(16) NOT NULL,
    target_mrp_gbp NUMERIC(8,2) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_name ON products USING gin(to_tsvector('english', name));

-- 3. Immutable Append-Only Price Observation Ledger
CREATE TABLE IF NOT EXISTS price_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id VARCHAR(64) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    store_id VARCHAR(64) NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    regular_price_pence INT NOT NULL,
    loyalty_price_pence INT,
    loyalty_scheme VARCHAR(32) DEFAULT 'NONE',
    effective_price_pence INT NOT NULL,
    source_type VARCHAR(32) DEFAULT 'mobile_app',
    freshness_status VARCHAR(32) DEFAULT 'RECENTLY_OBSERVED',
    observed_at TIMESTAMPTZ DEFAULT NOW(),
    superseded_by UUID REFERENCES price_records(id),
    idempotency_key VARCHAR(64) UNIQUE
);
CREATE INDEX IF NOT EXISTS idx_price_records_lookup ON price_records(product_id, store_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_records_effective ON price_records(product_id, effective_price_pence ASC);

-- 4. AI Yellow Sticker Markdown Deals
CREATE TABLE IF NOT EXISTS yellow_stickers (
    id VARCHAR(64) PRIMARY KEY,
    store_id VARCHAR(64) NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    product_name VARCHAR(256) NOT NULL,
    original_price_pence INT NOT NULL,
    reduced_price_pence INT NOT NULL,
    discount_percent INT NOT NULL,
    use_by_date VARCHAR(64) NOT NULL,
    spotted_relative VARCHAR(64) DEFAULT 'Just now',
    image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_yellow_stickers_store ON yellow_stickers(store_id);

-- 5. AI Bounty Tasks
CREATE TABLE IF NOT EXISTS bounties (
    id VARCHAR(64) PRIMARY KEY,
    store_id VARCHAR(64) NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    product_id VARCHAR(64) REFERENCES products(id) ON DELETE SET NULL,
    task_description TEXT NOT NULL,
    reward_points INT DEFAULT 20,
    is_claimed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bounties_store ON bounties(store_id);

-- 6. Transactional Outbox for Async Event Streaming (Kafka / CDC Ready)
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox_events(created_at) WHERE processed_at IS NULL;

