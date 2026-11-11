package repository

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io/fs"
	"log"
	"sort"
	"strings"
	"time"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/migrations"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// PostgresStore coordinates repository implementations backed by PostgreSQL 16 + PostGIS
type PostgresStore struct {
	pool              *pgxpool.Pool
	storeRepo         StoreRepository
	productRepo       ProductRepository
	priceRepo         PriceRepository
	bountyRepo        BountyRepository
	yellowStickerRepo YellowStickerRepository
	categoryRepo      CategoryRepository
	sessionRepo       SessionRepository
	evidenceRepo      EvidenceRepository
	discrepancyRepo   DiscrepancyRepository
	outboxRepo        OutboxRepository
}

func NewPostgresStore(ctx context.Context, databaseURL string) (*PostgresStore, error) {
	config, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("invalid DATABASE_URL: %w", err)
	}

	config.MaxConns = 25
	config.MinConns = 2
	config.MaxConnLifetime = 1 * time.Hour
	config.MaxConnIdleTime = 15 * time.Minute

	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, fmt.Errorf("failed to create connection pool: %w", err)
	}

	// Verify connectivity
	pingCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("failed to ping PostgreSQL: %w", err)
	}

	log.Printf("[Postgres] Successfully connected to PostgreSQL at %s", config.ConnConfig.Host)

	// Execute Versioned Schema Migrations
	if err := runMigrations(ctx, pool); err != nil {
		log.Printf("[Postgres] Warning: migration check returned: %v", err)
	}

	store := &PostgresStore{
		pool: pool,
	}

	store.storeRepo = &pgStoreRepo{pool: pool}
	store.productRepo = &pgProductRepo{pool: pool}
	store.priceRepo = &pgPriceRepo{pool: pool}
	store.bountyRepo = &pgBountyRepo{pool: pool}
	store.yellowStickerRepo = &pgYellowStickerRepo{pool: pool}
	store.categoryRepo = &pgCategoryRepo{pool: pool}
	store.sessionRepo = &pgSessionRepo{pool: pool}
	store.evidenceRepo = &pgEvidenceRepo{pool: pool}
	store.discrepancyRepo = &pgDiscrepancyRepo{pool: pool}
	store.outboxRepo = &pgOutboxRepo{pool: pool}

	// Auto-seed if stores table is empty
	if err := store.seedIfEmpty(ctx); err != nil {
		log.Printf("[Postgres] Warning during auto-seed: %v", err)
	}

	return store, nil
}

func (s *PostgresStore) StoreRepo() StoreRepository                 { return s.storeRepo }
func (s *PostgresStore) ProductRepo() ProductRepository             { return s.productRepo }
func (s *PostgresStore) PriceRepo() PriceRepository                 { return s.priceRepo }
func (s *PostgresStore) BountyRepo() BountyRepository               { return s.bountyRepo }
func (s *PostgresStore) YellowStickerRepo() YellowStickerRepository { return s.yellowStickerRepo }
func (s *PostgresStore) CategoryRepo() CategoryRepository           { return s.categoryRepo }
func (s *PostgresStore) SessionRepo() SessionRepository             { return s.sessionRepo }
func (s *PostgresStore) EvidenceRepo() EvidenceRepository           { return s.evidenceRepo }
func (s *PostgresStore) DiscrepancyRepo() DiscrepancyRepository     { return s.discrepancyRepo }
func (s *PostgresStore) OutboxRepo() OutboxRepository               { return s.outboxRepo }
func (s *PostgresStore) Close()                                     { s.pool.Close() }
func (s *PostgresStore) Ping(ctx context.Context) error             { return s.pool.Ping(ctx) }
func (s *PostgresStore) Pool() *pgxpool.Pool                        { return s.pool }

func (s *PostgresStore) ExecuteRawSQL(ctx context.Context, sql string) error {
	_, err := s.pool.Exec(ctx, sql)
	return err
}

func (r *pgStoreRepo) ExecuteRawSQL(ctx context.Context, sql string) error {
	_, err := r.pool.Exec(ctx, sql)
	return err
}

func runMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	_, err := pool.Exec(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version VARCHAR(128) PRIMARY KEY,
			checksum VARCHAR(64) NOT NULL,
			applied_at TIMESTAMPTZ DEFAULT NOW()
		);
	`)
	if err != nil {
		return fmt.Errorf("creating schema_migrations failed: %w", err)
	}

	entries, err := fs.ReadDir(migrations.FS, ".")
	if err != nil {
		return fmt.Errorf("reading embedded migrations failed: %w", err)
	}

	var files []string
	for _, entry := range entries {
		if !entry.IsDir() && strings.HasSuffix(entry.Name(), ".up.sql") {
			files = append(files, entry.Name())
		}
	}
	sort.Strings(files)

	for _, name := range files {
		sqlBytes, readErr := fs.ReadFile(migrations.FS, name)
		if readErr != nil {
			return readErr
		}
		sum := sha256.Sum256(sqlBytes)
		checksum := hex.EncodeToString(sum[:])

		var existingChecksum string
		err := pool.QueryRow(ctx, "SELECT checksum FROM schema_migrations WHERE version = $1", name).Scan(&existingChecksum)
		if err == nil && existingChecksum == checksum {
			continue // Already applied identically
		}

		log.Printf("[Postgres] Applying versioned migration %s...", name)
		if _, execErr := pool.Exec(ctx, string(sqlBytes)); execErr != nil {
			return fmt.Errorf("executing migration %s failed: %w", name, execErr)
		}

		_, _ = pool.Exec(ctx, `
			INSERT INTO schema_migrations (version, checksum)
			VALUES ($1, $2)
			ON CONFLICT (version) DO UPDATE SET checksum = EXCLUDED.checksum, applied_at = NOW();
		`, name, checksum)
	}

	log.Printf("[Postgres] Versioned schema migrations up to date (%d files).", len(files))
	return nil
}

func (s *PostgresStore) seedIfEmpty(ctx context.Context) error {
	var count int
	err := s.pool.QueryRow(ctx, "SELECT COUNT(*) FROM stores").Scan(&count)
	if err != nil {
		return err
	}
	if count > 0 {
		log.Printf("[Postgres] Database already populated (%d stores present). Skipping seed.", count)
		return nil
	}

	log.Printf("[Postgres] Empty database detected. Seeding London Supermarkets & staple products...")
	mem := NewMemoryStore()

	// Seed Stores
	stores, _ := mem.StoreRepo().GetAll(ctx)
	for _, st := range stores {
		_ = s.storeRepo.Create(ctx, st)
	}

	// Seed Products
	products, _ := mem.ProductRepo().GetAll(ctx)
	for _, pr := range products {
		_ = s.productRepo.Create(ctx, pr)
	}

	// Seed Prices
	prices, _ := mem.PriceRepo().GetAllPrices(ctx)
	for _, pc := range prices {
		_ = s.priceRepo.SubmitPrice(ctx, pc)
	}

	log.Printf("[Postgres] Seeding complete (%d stores, %d products seeded).", len(stores), len(products))
	return nil
}

// ─────────────────────────────────────────────────────────────
// STORE REPOSITORY IMPLEMENTATION (PostGIS Spatial Queries)
// ─────────────────────────────────────────────────────────────

type pgStoreRepo struct {
	pool *pgxpool.Pool
}

func (r *pgStoreRepo) GetByID(ctx context.Context, id string) (*domain.Store, error) {
	row := r.pool.QueryRow(ctx, `
		SELECT id, chain, format, name, address, postcode,
		       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon,
		       COALESCE(operating_hours, '7:00 AM - 11:00 PM'),
		       COALESCE(phone, ''),
		       COALESCE(reliability_score, 95),
		       COALESCE(is_confirmed_partner, FALSE)
		FROM stores WHERE id = $1
	`, id)

	var st domain.Store
	var lat, lon float64

	err := row.Scan(&st.ID, &st.Chain, &st.Format, &st.Name, &st.Address, &st.Postcode,
		&lat, &lon, &st.OperatingHours, &st.Phone, &st.ReliabilityScore, &st.IsConfirmedPartner)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, fmt.Errorf("store not found: %s", id)
		}
		return nil, err
	}
	st.Latitude = lat
	st.Longitude = lon
	return &st, nil
}

func (r *pgStoreRepo) GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Store, error) {
	radiusMeters := radiusKm * 1000.0

	// PostGIS ST_DWithin and ST_Distance on GEOGRAPHY
	query := `
		SELECT id, chain, format, name, address, postcode,
		       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon,
		       ROUND((ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) / 1000.0)::numeric, 2) AS distance_km,
		       COALESCE(operating_hours, '7:00 AM - 11:00 PM'),
		       COALESCE(phone, ''),
		       COALESCE(reliability_score, 95),
		       COALESCE(is_confirmed_partner, FALSE)
		FROM stores
		WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
		ORDER BY geom <-> ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography
		LIMIT 20;
	`

	rows, err := r.pool.Query(ctx, query, lat, lon, radiusMeters)
	if err != nil {
		// Fallback without PostGIS if extension not enabled
		return r.GetAll(ctx)
	}
	defer rows.Close()

	var stores []domain.Store
	for rows.Next() {
		var s domain.Store
		if err := rows.Scan(&s.ID, &s.Chain, &s.Format, &s.Name, &s.Address, &s.Postcode,
			&s.Latitude, &s.Longitude, &s.DistanceKm,
			&s.OperatingHours, &s.Phone, &s.ReliabilityScore, &s.IsConfirmedPartner); err == nil {
			stores = append(stores, s)
		}
	}
	return stores, nil
}

func (r *pgStoreRepo) GetAll(ctx context.Context) ([]domain.Store, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, chain, format, name, address, postcode,
		       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon,
		       COALESCE(operating_hours, '7:00 AM - 11:00 PM'),
		       COALESCE(phone, ''),
		       COALESCE(reliability_score, 95),
		       COALESCE(is_confirmed_partner, FALSE)
		FROM stores ORDER BY name
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var stores []domain.Store
	for rows.Next() {
		var s domain.Store
		if err := rows.Scan(&s.ID, &s.Chain, &s.Format, &s.Name, &s.Address, &s.Postcode,
			&s.Latitude, &s.Longitude,
			&s.OperatingHours, &s.Phone, &s.ReliabilityScore, &s.IsConfirmedPartner); err == nil {
			stores = append(stores, s)
		}
	}
	return stores, nil
}

func (r *pgStoreRepo) Create(ctx context.Context, store domain.Store) error {
	hours := store.OperatingHours
	if hours == "" {
		hours = "7:00 AM - 11:00 PM"
	}
	relScore := store.ReliabilityScore
	if relScore == 0 {
		relScore = 95
	}
	_, err := r.pool.Exec(ctx, `
		INSERT INTO stores (id, chain, format, name, address, postcode, geom, operating_hours, phone, reliability_score, is_confirmed_partner)
		VALUES ($1, $2, $3, $4, $5, $6, ST_SetSRID(ST_MakePoint($7, $8), 4326)::geography, $9, $10, $11, $12)
		ON CONFLICT (id) DO UPDATE SET
			chain = EXCLUDED.chain,
			format = EXCLUDED.format,
			name = EXCLUDED.name,
			address = EXCLUDED.address,
			postcode = EXCLUDED.postcode,
			geom = EXCLUDED.geom,
			operating_hours = EXCLUDED.operating_hours,
			phone = EXCLUDED.phone,
			reliability_score = EXCLUDED.reliability_score,
			is_confirmed_partner = EXCLUDED.is_confirmed_partner;
	`, store.ID, store.Chain, store.Format, store.Name, store.Address, store.Postcode,
		store.Longitude, store.Latitude, hours, store.Phone, relScore, store.IsConfirmedPartner)
	return err
}

// ─────────────────────────────────────────────────────────────
// PRODUCT REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgProductRepo struct {
	pool *pgxpool.Pool
}

func (r *pgProductRepo) GetByID(ctx context.Context, id string) (*domain.Product, error) {
	var p domain.Product
	var createdAt time.Time
	err := r.pool.QueryRow(ctx, `
		SELECT id, name, brand, COALESCE(barcode, ''), category, package_size, unit_metric,
		       target_mrp_gbp::float8, COALESCE(created_by_user_id, ''), created_at
		FROM products WHERE id = $1
	`, id).Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize, &p.UnitMetric,
		&p.TargetMRP, &p.CreatedByUserID, &createdAt)
	if err != nil {
		return nil, err
	}
	p.CreatedAt = &createdAt
	return &p, nil
}

func (r *pgProductRepo) GetByBarcode(ctx context.Context, ean string) (*domain.Product, error) {
	var p domain.Product
	var createdAt time.Time
	err := r.pool.QueryRow(ctx, `
		SELECT id, name, brand, COALESCE(barcode, ''), category, package_size, unit_metric,
		       target_mrp_gbp::float8, COALESCE(created_by_user_id, ''), created_at
		FROM products WHERE barcode = $1
	`, ean).Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize, &p.UnitMetric,
		&p.TargetMRP, &p.CreatedByUserID, &createdAt)
	if err != nil {
		return nil, err
	}
	p.CreatedAt = &createdAt
	return &p, nil
}

func (r *pgProductRepo) Search(ctx context.Context, query string, category string) ([]domain.Product, error) {
	var sb strings.Builder
	sb.WriteString(`SELECT id, name, brand, COALESCE(barcode, ''), category, package_size, unit_metric, target_mrp_gbp::float8, COALESCE(created_by_user_id, ''), created_at FROM products WHERE 1=1`)
	var args []interface{}
	idx := 1

	if query != "" {
		sb.WriteString(fmt.Sprintf(" AND (LOWER(name) LIKE $%d OR LOWER(brand) LIKE $%d)", idx, idx))
		args = append(args, "%"+strings.ToLower(query)+"%")
		idx++
	}

	if category != "" && category != "all" {
		sb.WriteString(fmt.Sprintf(" AND category = $%d", idx))
		args = append(args, category)
		idx++
	}

	sb.WriteString(" ORDER BY name LIMIT 50")

	rows, err := r.pool.Query(ctx, sb.String(), args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	products := make([]domain.Product, 0)
	for rows.Next() {
		var p domain.Product
		var createdAt time.Time
		if err := rows.Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize,
			&p.UnitMetric, &p.TargetMRP, &p.CreatedByUserID, &createdAt); err == nil {
			p.CreatedAt = &createdAt
			products = append(products, p)
		}
	}
	return products, nil
}

func (r *pgProductRepo) GetAll(ctx context.Context) ([]domain.Product, error) {
	return r.Search(ctx, "", "")
}

func (r *pgProductRepo) GetByCreator(ctx context.Context, userID string, limit, offset int) ([]domain.Product, error) {
	if userID == "" {
		return []domain.Product{}, nil
	}
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	rows, err := r.pool.Query(ctx, `
		SELECT p.id, p.name, p.brand, COALESCE(p.barcode, ''), p.category, p.package_size, p.unit_metric,
		       p.target_mrp_gbp::float8, COALESCE(p.created_by_user_id, ''), p.created_at
		FROM products p
		WHERE p.created_by_user_id = $1
		   OR EXISTS (
		       SELECT 1 FROM price_records pr
		       WHERE pr.product_id = p.id AND pr.submitted_by_user_id = $1
		   )
		ORDER BY p.created_at DESC
		LIMIT $2 OFFSET $3;
	`, userID, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	products := make([]domain.Product, 0)
	for rows.Next() {
		var p domain.Product
		var createdAt time.Time
		if err := rows.Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize,
			&p.UnitMetric, &p.TargetMRP, &p.CreatedByUserID, &createdAt); err == nil {
			p.CreatedAt = &createdAt
			products = append(products, p)
		}
	}
	return products, nil
}

func (r *pgProductRepo) Create(ctx context.Context, p domain.Product) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if p.IdempotencyKey != "" {
		var existingID string
		if err := tx.QueryRow(ctx, `SELECT id FROM products WHERE idempotency_key = $1 LIMIT 1`, p.IdempotencyKey).Scan(&existingID); err == nil && existingID != "" {
			return tx.Commit(ctx)
		}
	}

	_, err = tx.Exec(ctx, `
		INSERT INTO products (
			id, name, brand, barcode, category, package_size, unit_metric,
			target_mrp_gbp, created_by_user_id, idempotency_key
		)
		VALUES ($1, $2, $3, NULLIF($4, ''), $5, $6, $7, $8, NULLIF($9, ''), NULLIF($10, ''))
		ON CONFLICT (id) DO UPDATE SET
			name = EXCLUDED.name,
			brand = EXCLUDED.brand,
			barcode = COALESCE(EXCLUDED.barcode, products.barcode),
			category = EXCLUDED.category,
			package_size = EXCLUDED.package_size,
			unit_metric = EXCLUDED.unit_metric,
			target_mrp_gbp = EXCLUDED.target_mrp_gbp,
			created_by_user_id = COALESCE(NULLIF(products.created_by_user_id, ''), EXCLUDED.created_by_user_id),
			idempotency_key = COALESCE(NULLIF(products.idempotency_key, ''), EXCLUDED.idempotency_key);
	`, p.ID, p.Name, p.Brand, p.Barcode, p.Category, p.PackageSize, p.UnitMetric,
		p.TargetMRP, p.CreatedByUserID, p.IdempotencyKey)
	if err != nil {
		return err
	}

	if payloadBytes, marshalErr := json.Marshal(map[string]any{
		"product_id":         p.ID,
		"name":               p.Name,
		"created_by_user_id": p.CreatedByUserID,
	}); marshalErr == nil {
		_, _ = tx.Exec(ctx, `
			INSERT INTO outbox_events (event_type, aggregate_type, aggregate_id, payload)
			VALUES ('product.created', 'Product', $1, $2::jsonb)
		`, p.ID, string(payloadBytes))
	}

	return tx.Commit(ctx)
}

// ─────────────────────────────────────────────────────────────
// PRICE REPOSITORY IMPLEMENTATION (Append-Only Ledger)
// ─────────────────────────────────────────────────────────────

type pgPriceRepo struct {
	pool *pgxpool.Pool
}

func (r *pgPriceRepo) GetStorePrices(ctx context.Context, productID string) ([]domain.PriceRecord, error) {
	query := `
		SELECT DISTINCT ON (pr.store_id)
		       pr.id::text, pr.product_id, pr.store_id, s.name,
		       pr.regular_price_pence, pr.loyalty_price_pence, pr.loyalty_scheme,
		       pr.freshness_status, pr.source_type, pr.observed_at
		FROM price_records pr
		JOIN stores s ON pr.store_id = s.id
		WHERE pr.product_id = $1 AND pr.superseded_by IS NULL
		ORDER BY pr.store_id, pr.observed_at DESC;
	`

	rows, err := r.pool.Query(ctx, query, productID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var records []domain.PriceRecord
	for rows.Next() {
		var pr domain.PriceRecord
		var loyaltyScheme string
		var freshness string

		if err := rows.Scan(&pr.ID, &pr.ProductID, &pr.StoreID, &pr.StoreName,
			&pr.RegularPricePence, &pr.LoyaltyPricePence, &loyaltyScheme,
			&freshness, &pr.SourceType, &pr.ObservedAt); err == nil {
			pr.LoyaltyScheme = domain.LoyaltyScheme(loyaltyScheme)
			pr.FreshnessStatus = domain.FreshnessStatus(freshness)
			records = append(records, pr)
		}
	}
	return records, nil
}

func (r *pgPriceRepo) GetLatestPrice(ctx context.Context, productID, storeID string) (*domain.PriceRecord, error) {
	query := `
		SELECT pr.id::text, pr.product_id, pr.store_id, s.name,
		       pr.regular_price_pence, pr.loyalty_price_pence, pr.loyalty_scheme,
		       pr.freshness_status, pr.source_type, pr.observed_at
		FROM price_records pr
		JOIN stores s ON pr.store_id = s.id
		WHERE pr.product_id = $1 AND pr.store_id = $2 AND pr.superseded_by IS NULL
		ORDER BY pr.observed_at DESC LIMIT 1;
	`

	var pr domain.PriceRecord
	var loyaltyScheme, freshness string

	err := r.pool.QueryRow(ctx, query, productID, storeID).Scan(
		&pr.ID, &pr.ProductID, &pr.StoreID, &pr.StoreName,
		&pr.RegularPricePence, &pr.LoyaltyPricePence, &loyaltyScheme,
		&freshness, &pr.SourceType, &pr.ObservedAt,
	)
	if err != nil {
		return nil, err
	}
	pr.LoyaltyScheme = domain.LoyaltyScheme(loyaltyScheme)
	pr.FreshnessStatus = domain.FreshnessStatus(freshness)
	return &pr, nil
}

func (r *pgPriceRepo) SubmitPrice(ctx context.Context, record domain.PriceRecord) error {
	effectivePrice := record.RegularPricePence
	if record.LoyaltyPricePence != nil && *record.LoyaltyPricePence < effectivePrice {
		effectivePrice = *record.LoyaltyPricePence
	}

	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	var idempotencyKey *string
	if record.IdempotencyKey != "" {
		idempotencyKey = &record.IdempotencyKey
	}

	var submittedBy *string
	if record.SubmittedByUserID != "" {
		submittedBy = &record.SubmittedByUserID
	}

	// Insert into append-only price ledger with ON CONFLICT (idempotency_key) DO NOTHING
	var newID string
	err = tx.QueryRow(ctx, `
		INSERT INTO price_records (
			product_id, store_id, regular_price_pence, loyalty_price_pence,
			loyalty_scheme, effective_price_pence, source_type, freshness_status,
			idempotency_key, submitted_by_user_id, evidence_id, observed_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11::uuid, NOW())
		ON CONFLICT (idempotency_key) DO NOTHING
		RETURNING id::text;
	`, record.ProductID, record.StoreID, record.RegularPricePence, record.LoyaltyPricePence,
		string(record.LoyaltyScheme), effectivePrice, record.SourceType, string(record.FreshnessStatus),
		idempotencyKey, submittedBy, record.EvidenceID).Scan(&newID)
	if err != nil {
		if err == pgx.ErrNoRows {
			// Duplicate submission with matching idempotency key; commit transaction idempotently
			return tx.Commit(ctx)
		}
		return err
	}

	// Update previous active price record within transaction to update superseded_by pointer
	_, err = tx.Exec(ctx, `
		UPDATE price_records
		SET superseded_by = $1
		WHERE product_id = $2 AND store_id = $3 AND superseded_by IS NULL AND id != $1
	`, newID, record.ProductID, record.StoreID)
	if err != nil {
		return err
	}

	// Write Outbox event for Kafka/CDC
	outboxPayload := fmt.Sprintf(`{"price_id":"%s","product_id":"%s","store_id":"%s","price_pence":%d}`,
		newID, record.ProductID, record.StoreID, effectivePrice)
	_, _ = tx.Exec(ctx, `
		INSERT INTO outbox_events (event_type, aggregate_type, aggregate_id, payload)
		VALUES ('price.submitted', 'PriceRecord', $1, $2::jsonb)
	`, newID, outboxPayload)

	return tx.Commit(ctx)
}

func (r *pgPriceRepo) ConfirmFreshness(ctx context.Context, productID, storeID, userID string) (int, error) {
	if userID == "" {
		userID = "anon"
	}

	// Record user corroboration cleanly (idempotent per user)
	_, err := r.pool.Exec(ctx, `
		INSERT INTO freshness_confirmations (product_id, store_id, user_id)
		VALUES ($1, $2, $3)
		ON CONFLICT (product_id, store_id, user_id) DO UPDATE SET confirmed_at = NOW();
	`, productID, storeID, userID)
	if err != nil {
		return 0, err
	}

	var count int
	err = r.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT user_id) FROM freshness_confirmations
		WHERE product_id = $1 AND store_id = $2 AND confirmed_at >= NOW() - INTERVAL '14 days';
	`, productID, storeID).Scan(&count)
	if err != nil {
		count = 1
	}

	// Promote to COMMUNITY_CORROBORATED only when 2+ independent users confirm; never claim STORE_CONFIRMED
	newStatus := string(domain.FreshnessRecentlyObserved)
	if count >= 2 {
		newStatus = string(domain.FreshnessCommunityCorroborated)
	}

	_, err = r.pool.Exec(ctx, `
		UPDATE price_records
		SET freshness_status = CASE
				WHEN freshness_status = 'STORE_CONFIRMED' THEN 'STORE_CONFIRMED'
				ELSE $3
			END,
			observed_at = NOW()
		WHERE product_id = $1 AND store_id = $2 AND superseded_by IS NULL
	`, productID, storeID, newStatus)
	return count, err
}

func (r *pgPriceRepo) GetAllPrices(ctx context.Context) ([]domain.PriceRecord, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT pr.id::text, pr.product_id, pr.store_id, s.name,
		       pr.regular_price_pence, pr.loyalty_price_pence, pr.loyalty_scheme,
		       pr.freshness_status, pr.source_type, pr.observed_at
		FROM price_records pr
		JOIN stores s ON pr.store_id = s.id
		WHERE pr.superseded_by IS NULL;
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var records []domain.PriceRecord
	for rows.Next() {
		var pr domain.PriceRecord
		var scheme, freshness string
		if err := rows.Scan(&pr.ID, &pr.ProductID, &pr.StoreID, &pr.StoreName,
			&pr.RegularPricePence, &pr.LoyaltyPricePence, &scheme,
			&freshness, &pr.SourceType, &pr.ObservedAt); err == nil {
			pr.LoyaltyScheme = domain.LoyaltyScheme(scheme)
			pr.FreshnessStatus = domain.FreshnessStatus(freshness)
			records = append(records, pr)
		}
	}
	return records, nil
}

func formatRelativeAge(t *time.Time) string {
	if t == nil {
		return "Recently"
	}
	diff := time.Since(*t)
	switch {
	case diff < time.Minute:
		return "Just now"
	case diff < time.Hour:
		return fmt.Sprintf("%d mins ago", int(diff.Minutes()))
	case diff < 24*time.Hour:
		h := int(diff.Hours())
		if h == 1 {
			return "1 hour ago"
		}
		return fmt.Sprintf("%d hours ago", h)
	default:
		d := int(diff.Hours() / 24)
		if d == 1 {
			return "1 day ago"
		}
		return fmt.Sprintf("%d days ago", d)
	}
}

func (r *pgPriceRepo) GetSubmissionsByUser(ctx context.Context, userID string) ([]domain.PriceRecord, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT pr.id::text, pr.product_id, COALESCE(p.name, pr.product_id), pr.store_id, COALESCE(s.name, pr.store_id),
		       pr.regular_price_pence, pr.loyalty_price_pence, pr.loyalty_scheme,
		       pr.freshness_status, pr.source_type, pr.observed_at
		FROM price_records pr
		LEFT JOIN products p ON pr.product_id = p.id
		LEFT JOIN stores s ON pr.store_id = s.id
		WHERE pr.submitted_by_user_id = $1
		ORDER BY pr.observed_at DESC
		LIMIT 50;
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var records []domain.PriceRecord
	for rows.Next() {
		var pr domain.PriceRecord
		var scheme, freshness string
		if err := rows.Scan(&pr.ID, &pr.ProductID, &pr.ProductName, &pr.StoreID, &pr.StoreName,
			&pr.RegularPricePence, &pr.LoyaltyPricePence, &scheme,
			&freshness, &pr.SourceType, &pr.ObservedAt); err == nil {
			pr.LoyaltyScheme = domain.LoyaltyScheme(scheme)
			pr.FreshnessStatus = domain.FreshnessStatus(freshness)
			records = append(records, pr)
		}
	}
	return records, nil
}

// GetBestDealsSingleQuery resolves products and their cheapest nearby store prices in one SQL round-trip,
// completely eliminating N+1 queries on the deals endpoint.
func (r *pgPriceRepo) GetBestDealsSingleQuery(ctx context.Context, lat, lon, radiusKm float64, category string) ([]domain.ProductDealSummary, error) {
	radiusMeters := radiusKm * 1000.0
	query := `
		WITH nearby_stores AS (
			SELECT id, chain, format, name, address, postcode,
			       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon,
			       (ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) / 1000.0) AS distance_km,
			       reliability_score, is_confirmed_partner
			FROM stores
			WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
		),
		latest_prices AS (
			SELECT DISTINCT ON (pr.product_id, pr.store_id)
			       pr.product_id, pr.store_id, pr.regular_price_pence, pr.loyalty_price_pence,
			       pr.loyalty_scheme, pr.effective_price_pence, pr.freshness_status, pr.observed_at
			FROM price_records pr
			JOIN nearby_stores s ON pr.store_id = s.id
			WHERE pr.superseded_by IS NULL
			ORDER BY pr.product_id, pr.store_id, pr.observed_at DESC
		)
		SELECT p.id, p.name, p.brand, COALESCE(p.barcode, ''), p.category, p.package_size, p.unit_metric, p.target_mrp_gbp::float8,
		       s.id, s.chain, s.format, s.name, s.address, s.postcode, s.lat, s.lon, ROUND(s.distance_km::numeric, 2)::float8,
		       lp.regular_price_pence, lp.loyalty_price_pence, lp.loyalty_scheme, lp.effective_price_pence, lp.freshness_status, lp.observed_at
		FROM products p
		INNER JOIN latest_prices lp ON p.id = lp.product_id
		INNER JOIN nearby_stores s ON lp.store_id = s.id
		WHERE ($4 = '' OR p.category = $4)
		ORDER BY p.name ASC, lp.effective_price_pence ASC;
	`

	rows, err := r.pool.Query(ctx, query, lat, lon, radiusMeters, category)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	grouped := make(map[string]*domain.ProductDealSummary)
	var order []string

	for rows.Next() {
		var prod domain.Product
		var storeID, chain, format, storeName, address, postcode *string
		var sLat, sLon, sDist *float64
		var regPence, effPence *int
		var loyPence *int
		var loyScheme, freshness *string
		var obsAt *time.Time

		if err := rows.Scan(
			&prod.ID, &prod.Name, &prod.Brand, &prod.Barcode, &prod.Category, &prod.PackageSize, &prod.UnitMetric, &prod.TargetMRP,
			&storeID, &chain, &format, &storeName, &address, &postcode, &sLat, &sLon, &sDist,
			&regPence, &loyPence, &loyScheme, &effPence, &freshness, &obsAt,
		); err != nil {
			continue
		}

		if storeID != nil && effPence != nil {
			summary, exists := grouped[prod.ID]
			if !exists {
				summary = &domain.ProductDealSummary{
					Product: prod,
				}
				grouped[prod.ID] = summary
				order = append(order, prod.ID)
			}

			comp := domain.StorePriceComparison{
				Store: domain.Store{
					ID:         *storeID,
					Chain:      domain.StoreChain(*chain),
					Format:     domain.StoreFormat(*format),
					Name:       *storeName,
					Address:    *address,
					Postcode:   *postcode,
					Latitude:   *sLat,
					Longitude:  *sLon,
					DistanceKm: *sDist,
				},
				RegularPricePence:   *regPence,
				RegularPriceGBP:     fmt.Sprintf("£%.2f", float64(*regPence)/100.0),
				LoyaltyPricePence:   loyPence,
				LoyaltyScheme:       domain.LoyaltyScheme(*loyScheme),
				EffectivePricePence: *effPence,
				UnitPricePence:      domain.ComputeUnitPricePence(*effPence, prod.PackageSize),
				UnitPriceFormatted:  fmt.Sprintf("£%.2f/unit", float64(domain.ComputeUnitPricePence(*effPence, prod.PackageSize))/100.0),
				FreshnessStatus:     domain.FreshnessStatus(*freshness),
				ObservedAgo:         formatRelativeAge(obsAt),
			}
			if loyPence != nil {
				s := fmt.Sprintf("£%.2f", float64(*loyPence)/100.0)
				comp.LoyaltyPriceGBP = &s
			}
			if len(summary.Alternatives) == 0 {
				comp.IsBestDeal = true
				summary.BestPricePence = *effPence
				summary.BestPriceGBP = fmt.Sprintf("£%.2f", float64(*effPence)/100.0)
				summary.BestStoreName = *storeName
				summary.BestStoreID = *storeID
				summary.BestStoreDistanceKm = *sDist
				summary.FreshnessStatus = comp.FreshnessStatus
			}
			summary.Alternatives = append(summary.Alternatives, comp)
			summary.ComparedStoreCount = len(summary.Alternatives)
		}
	}

	out := make([]domain.ProductDealSummary, 0, len(order))
	for _, id := range order {
		s := grouped[id]
		if s.ComparedStoreCount > 0 {
			out = append(out, *s)
		}
	}
	return out, nil
}

// ─────────────────────────────────────────────────────────────
// BOUNTY REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgBountyRepo struct {
	pool *pgxpool.Pool
}

func (r *pgBountyRepo) GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Bounty, error) {
	radiusMeters := radiusKm * 1000.0

	// PostGIS spatial query filtering by store location
	query := `
		SELECT b.id, b.store_id, s.name, COALESCE(b.product_id, ''), b.task_description, b.reward_points,
		       ROUND((ST_Distance(s.geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) / 1000.0)::numeric, 2) AS distance_km
		FROM bounties b
		JOIN stores s ON b.store_id = s.id
		WHERE b.is_claimed = FALSE
		  AND ST_DWithin(s.geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography, $3)
		ORDER BY s.geom <-> ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography
		LIMIT 10;
	`

	rows, err := r.pool.Query(ctx, query, lat, lon, radiusMeters)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var bounties []domain.Bounty
	for rows.Next() {
		var b domain.Bounty
		if err := rows.Scan(&b.ID, &b.StoreID, &b.StoreName, &b.ProductID, &b.ProductName, &b.RewardPoints, &b.DistanceKm); err == nil {
			b.Status = "OPEN"
			bounties = append(bounties, b)
		}
	}
	return bounties, nil
}

func (r *pgBountyRepo) getNearbyFallback(ctx context.Context) ([]domain.Bounty, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT b.id, b.store_id, s.name, COALESCE(b.product_id, ''), b.task_description, b.reward_points
		FROM bounties b
		JOIN stores s ON b.store_id = s.id
		WHERE b.is_claimed = FALSE
		LIMIT 10;
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var bounties []domain.Bounty
	for rows.Next() {
		var b domain.Bounty
		if err := rows.Scan(&b.ID, &b.StoreID, &b.StoreName, &b.ProductID, &b.ProductName, &b.RewardPoints); err == nil {
			b.Status = "OPEN"
			bounties = append(bounties, b)
		}
	}
	return bounties, nil
}

func (r *pgBountyRepo) Claim(ctx context.Context, bountyID string) error {
	_, err := r.pool.Exec(ctx, "UPDATE bounties SET is_claimed = TRUE WHERE id = $1", bountyID)
	return err
}

func (r *pgBountyRepo) Create(ctx context.Context, b domain.Bounty) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO bounties (id, store_id, product_id, task_description, reward_points)
		VALUES ($1, $2, $3, $4, $5)
	`, b.ID, b.StoreID, b.ProductID, b.ProductName, b.RewardPoints)
	return err
}

func (r *pgBountyRepo) GetActivePrompt(ctx context.Context) (*domain.BountyPrompt, error) {
	row := r.pool.QueryRow(ctx, `
		SELECT b.id, COALESCE(p.name, b.task_description), COALESCE(s.name, 'Nearby Store'),
		       COALESCE(pr.regular_price_pence, 145), b.reward_points
		FROM bounties b
		LEFT JOIN products p ON b.product_id = p.id
		LEFT JOIN stores s ON b.store_id = s.id
		LEFT JOIN LATERAL (
			SELECT regular_price_pence FROM price_records
			WHERE product_id = b.product_id AND store_id = b.store_id
			ORDER BY observed_at DESC LIMIT 1
		) pr ON true
		WHERE b.is_claimed = FALSE
		ORDER BY b.reward_points DESC
		LIMIT 1;
	`)
	var id, prodName, storeName string
	var pricePence, rewardPoints int
	err := row.Scan(&id, &prodName, &storeName, &pricePence, &rewardPoints)
	if err != nil {
		if err == pgx.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	return &domain.BountyPrompt{
		BountyID:          id,
		ProductName:       prodName,
		StoreName:         storeName,
		LastObservedPrice: fmt.Sprintf("£%.2f", float64(pricePence)/100.0),
		RewardPoints:      rewardPoints,
	}, nil
}

// ─────────────────────────────────────────────────────────────
// YELLOW STICKER REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgYellowStickerRepo struct {
	pool *pgxpool.Pool
}

func (r *pgYellowStickerRepo) GetActive(ctx context.Context, storeID string) ([]domain.YellowStickerDeal, error) {
	query := `
		SELECT y.id, y.store_id, s.name, y.product_name, y.original_price_pence,
		       y.reduced_price_pence, y.discount_percent, y.use_by_date
		FROM yellow_stickers y
		JOIN stores s ON y.store_id = s.id
	`
	var rows pgx.Rows
	var err error
	if storeID != "" {
		rows, err = r.pool.Query(ctx, query+" WHERE y.store_id = $1 ORDER BY y.created_at DESC", storeID)
	} else {
		rows, err = r.pool.Query(ctx, query+" ORDER BY y.created_at DESC LIMIT 20")
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var deals []domain.YellowStickerDeal
	for rows.Next() {
		var d domain.YellowStickerDeal
		if err := rows.Scan(&d.ID, &d.StoreID, &d.StoreName, &d.ProductName,
			&d.WasPricePence, &d.NowPricePence, &d.DiscountPercent, &d.UseByDate); err == nil {
			deals = append(deals, d)
		}
	}
	return deals, nil
}

func (r *pgYellowStickerRepo) Create(ctx context.Context, d domain.YellowStickerDeal) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO yellow_stickers (
			id, store_id, product_name, original_price_pence,
			reduced_price_pence, discount_percent, use_by_date
		) VALUES ($1, $2, $3, $4, $5, $6, $7)
	`, d.ID, d.StoreID, d.ProductName, d.WasPricePence, d.NowPricePence, d.DiscountPercent, d.UseByDate)
	return err
}

// ─────────────────────────────────────────────────────────────
// CATEGORY REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgCategoryRepo struct {
	pool *pgxpool.Pool
}

func (r *pgCategoryRepo) GetAll(ctx context.Context) ([]domain.Category, error) {
	rows, err := r.pool.Query(ctx, "SELECT id, name FROM categories ORDER BY (id = 'all') DESC, name ASC")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var categories []domain.Category
	for rows.Next() {
		var c domain.Category
		if err := rows.Scan(&c.ID, &c.Name); err == nil {
			categories = append(categories, c)
		}
	}
	return categories, nil
}

// ─────────────────────────────────────────────────────────────
// SESSION & USER REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgSessionRepo struct {
	pool *pgxpool.Pool
}

func (r *pgSessionRepo) UpsertUser(ctx context.Context, user domain.UserProfileRecord) (*domain.UserProfileRecord, error) {
	if user.KarmaPoints == 0 {
		user.KarmaPoints = 50
	}
	if user.Role == "" {
		user.Role = "ActiveContributor"
	}
	row := r.pool.QueryRow(ctx, `
		INSERT INTO users (id, email, display_name, avatar_url, role, karma_points, auth_mode)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		ON CONFLICT (id) DO UPDATE SET
			email = EXCLUDED.email,
			display_name = CASE WHEN EXCLUDED.display_name != '' THEN EXCLUDED.display_name ELSE users.display_name END,
			avatar_url = CASE WHEN EXCLUDED.avatar_url != '' THEN EXCLUDED.avatar_url ELSE users.avatar_url END,
			updated_at = NOW()
		RETURNING id, email, display_name, avatar_url, role, karma_points, prices_reported, prices_verified, auth_mode;
	`, user.ID, user.Email, user.DisplayName, user.AvatarURL, user.Role, user.KarmaPoints, user.AuthMode)

	var out domain.UserProfileRecord
	if err := row.Scan(&out.ID, &out.Email, &out.DisplayName, &out.AvatarURL, &out.Role,
		&out.KarmaPoints, &out.PricesReported, &out.PricesVerified, &out.AuthMode); err != nil {
		return nil, err
	}
	return &out, nil
}

func (r *pgSessionRepo) GetUserProfile(ctx context.Context, userID string) (*domain.UserProfileRecord, error) {
	row := r.pool.QueryRow(ctx, `
		SELECT id, email, display_name, avatar_url, role, karma_points, prices_reported, prices_verified, auth_mode
		FROM users WHERE id = $1;
	`, userID)
	var out domain.UserProfileRecord
	if err := row.Scan(&out.ID, &out.Email, &out.DisplayName, &out.AvatarURL, &out.Role,
		&out.KarmaPoints, &out.PricesReported, &out.PricesVerified, &out.AuthMode); err != nil {
		return nil, err
	}
	_ = r.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT p.id)
		FROM products p
		WHERE p.created_by_user_id = $1
		   OR EXISTS (SELECT 1 FROM price_records pr WHERE pr.product_id = p.id AND pr.submitted_by_user_id = $1);
	`, userID).Scan(&out.ProductsAdded)
	return &out, nil
}

func (r *pgSessionRepo) IncrementUserStats(ctx context.Context, userID string, karmaDelta, reportedDelta, verifiedDelta int) error {
	if userID == "" {
		return nil
	}
	_, err := r.pool.Exec(ctx, `
		UPDATE users
		SET karma_points = karma_points + $2,
		    prices_reported = prices_reported + $3,
		    prices_verified = prices_verified + $4,
		    updated_at = NOW()
		WHERE id = $1;
	`, userID, karmaDelta, reportedDelta, verifiedDelta)
	return err
}

func (r *pgSessionRepo) SaveSession(ctx context.Context, sess domain.AuthSessionRecord) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO sessions (token, user_id, email, display_name, role, expires_at)
		VALUES ($1, $2, $3, $4, $5, $6)
		ON CONFLICT (token) DO UPDATE SET
			expires_at = EXCLUDED.expires_at,
			display_name = EXCLUDED.display_name;
	`, sess.Token, sess.UserID, sess.Email, sess.DisplayName, sess.Role, sess.ExpiresAt)
	return err
}

func (r *pgSessionRepo) GetSession(ctx context.Context, token string) (*domain.AuthSessionRecord, error) {
	row := r.pool.QueryRow(ctx, `
		SELECT token, user_id, email, display_name, role, expires_at
		FROM sessions
		WHERE token = $1 AND expires_at > NOW();
	`, token)
	var sess domain.AuthSessionRecord
	if err := row.Scan(&sess.Token, &sess.UserID, &sess.Email, &sess.DisplayName, &sess.Role, &sess.ExpiresAt); err != nil {
		return nil, err
	}
	return &sess, nil
}

func (r *pgSessionRepo) DeleteSession(ctx context.Context, token string) error {
	_, err := r.pool.Exec(ctx, "DELETE FROM sessions WHERE token = $1", token)
	return err
}

func (r *pgSessionRepo) PurgeExpiredSessions(ctx context.Context) (int64, error) {
	tag, err := r.pool.Exec(ctx, "DELETE FROM sessions WHERE expires_at <= NOW()")
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

// ─────────────────────────────────────────────────────────────
// EVIDENCE REPOSITORY IMPLEMENTATION (Object Storage Metadata)
// ─────────────────────────────────────────────────────────────

type pgEvidenceRepo struct {
	pool *pgxpool.Pool
}

func (r *pgEvidenceRepo) SaveEvidence(ctx context.Context, ev domain.EvidenceRecord) (string, error) {
	var prodID *string
	if ev.ProductID != "" {
		prodID = &ev.ProductID
	}
	var storeID *string
	if ev.StoreID != "" {
		storeID = &ev.StoreID
	}
	var id string
	err := r.pool.QueryRow(ctx, `
		INSERT INTO evidence (user_id, product_id, store_id, source_type, object_key, evidence_url, image_hash, exif_stripped, ocr_raw_text)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING id::text;
	`, ev.UserID, prodID, storeID, ev.SourceType, ev.ObjectKey, ev.EvidenceURL, ev.ImageHash, ev.ExifStripped, ev.OcrRawText).Scan(&id)
	return id, err
}

// ─────────────────────────────────────────────────────────────
// DISCREPANCY REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgDiscrepancyRepo struct {
	pool *pgxpool.Pool
}

func (r *pgDiscrepancyRepo) RecordDiscrepancy(ctx context.Context, report *domain.StoreDiscrepancyReport) (int, error) {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO store_discrepancies (store_id, reporter_id, reason, details, user_latitude, user_longitude, reported_at)
		VALUES ($1, $2, $3, $4, $5, $6, NOW())
		ON CONFLICT (store_id, reporter_id, reason) DO UPDATE SET
			details = EXCLUDED.details,
			reported_at = NOW();
	`, report.StoreID, report.ReporterID, report.Reason, report.Details, report.UserLatitude, report.UserLongitude)
	if err != nil {
		return 0, err
	}

	var independentCount int
	err = r.pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT reporter_id) FROM store_discrepancies
		WHERE store_id = $1 AND reported_at >= NOW() - INTERVAL '30 days';
	`, report.StoreID).Scan(&independentCount)
	return independentCount, err
}

func (r *pgDiscrepancyRepo) UpdateStoreReliability(ctx context.Context, storeID string, score int) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE stores
		SET reliability_score = $2,
		    is_confirmed_partner = CASE WHEN $2 < 60 THEN FALSE ELSE is_confirmed_partner END
		WHERE id = $1;
	`, storeID, score)
	return err
}

// ─────────────────────────────────────────────────────────────
// TRANSACTIONAL OUTBOX REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgOutboxRepo struct {
	pool *pgxpool.Pool
}

func (r *pgOutboxRepo) FetchPending(ctx context.Context, limit int) ([]domain.OutboxEvent, error) {
	if limit <= 0 {
		limit = 25
	}
	rows, err := r.pool.Query(ctx, `
		SELECT id::text, event_type, aggregate_type, aggregate_id, payload::text, created_at
		FROM outbox_events
		WHERE processed_at IS NULL
		ORDER BY created_at ASC
		LIMIT $1;
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var events []domain.OutboxEvent
	for rows.Next() {
		var ev domain.OutboxEvent
		if err := rows.Scan(&ev.ID, &ev.EventType, &ev.AggregateType, &ev.AggregateID, &ev.PayloadJSON, &ev.CreatedAt); err == nil {
			events = append(events, ev)
		}
	}
	return events, nil
}

func (r *pgOutboxRepo) MarkProcessed(ctx context.Context, id string) error {
	_, err := r.pool.Exec(ctx, "UPDATE outbox_events SET processed_at = NOW() WHERE id = $1::uuid", id)
	return err
}
