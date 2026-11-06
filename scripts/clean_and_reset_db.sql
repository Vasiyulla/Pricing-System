-- Price Bridge: Complete Database Clean & Reset Script
-- Drops old observation data while preserving or reseeding clean master tables.

BEGIN;

-- 1. Wipe volatile operational tables
DELETE FROM price_records;
DELETE FROM yellow_stickers;
DELETE FROM bounties;
DELETE FROM outbox_events;
DELETE FROM stores;

-- 2. Verify master categories
INSERT INTO categories (id, name) VALUES
    ('all', 'All'),
    ('Dairy & Chilled', 'Dairy & Chilled'),
    ('Bakery', 'Bakery'),
    ('Pantry & Staples', 'Pantry & Staples'),
    ('Fresh Produce', 'Fresh Produce'),
    ('Beverages', 'Beverages'),
    ('Meat & Poultry', 'Meat & Poultry'),
    ('Frozen Foods', 'Frozen Foods')
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- Database cleaned and ready for live testing anywhere in the world.

