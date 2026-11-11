-- Version 0003: Purge synthetic auto-seeded store_live_* rows while preserving seeded UK pilot stores
DELETE FROM bounties WHERE id LIKE 'bounty_live_%' OR store_id LIKE 'store_live_%';
DELETE FROM yellow_stickers WHERE id LIKE 'sticker_live_%' OR store_id LIKE 'store_live_%';
DELETE FROM price_records WHERE store_id LIKE 'store_live_%';
DELETE FROM stores WHERE id LIKE 'store_live_%';

