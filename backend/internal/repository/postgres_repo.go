package repository

import (
	"context"
	_ "embed"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"pricebridge/backend/internal/domain"
)

//go:embed schema.sql
var schemaSQL string

// PostgresStore coordinates repository implementations backed by PostgreSQL 16 + PostGIS
type PostgresStore struct {
	pool               *pgxpool.Pool
	storeRepo          StoreRepository
	productRepo        ProductRepository
	priceRepo          PriceRepository
	bountyRepo         BountyRepository
	yellowStickerRepo  YellowStickerRepository
	categoryRepo       CategoryRepository
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

	// Execute Schema Migrations
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
func (s *PostgresStore) Close()                                     { s.pool.Close() }

func (s *PostgresStore) ExecuteRawSQL(ctx context.Context, sql string) error {
	_, err := s.pool.Exec(ctx, sql)
	return err
}

func (r *pgStoreRepo) ExecuteRawSQL(ctx context.Context, sql string) error {
	_, err := r.pool.Exec(ctx, sql)
	return err
}

func runMigrations(ctx context.Context, pool *pgxpool.Pool) error {
	log.Printf("[Postgres] Applying schema migrations...")
	_, err := pool.Exec(ctx, schemaSQL)
	if err != nil {
		return fmt.Errorf("executing schema.sql failed: %w", err)
	}
	log.Printf("[Postgres] Schema migrations applied successfully.")
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
		       operating_hours, phone, reliability_score, is_confirmed_partner
		FROM stores WHERE id = $1
	`, id)

	var st domain.Store
	var lat, lon float64
	var hours, phone *string
	var relScore int
	var partner bool

	err := row.Scan(&st.ID, &st.Chain, &st.Format, &st.Name, &st.Address, &st.Postcode,
		&lat, &lon, &hours, &phone, &relScore, &partner)
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
		       ROUND((ST_Distance(geom, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography) / 1000.0)::numeric, 2) AS distance_km
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
			&s.Latitude, &s.Longitude, &s.DistanceKm); err == nil {
			stores = append(stores, s)
		}
	}
	return stores, nil
}

func (r *pgStoreRepo) GetAll(ctx context.Context) ([]domain.Store, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, chain, format, name, address, postcode,
		       ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lon
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
			&s.Latitude, &s.Longitude); err == nil {
			stores = append(stores, s)
		}
	}
	return stores, nil
}

func (r *pgStoreRepo) Create(ctx context.Context, store domain.Store) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO stores (id, chain, format, name, address, postcode, geom)
		VALUES ($1, $2, $3, $4, $5, $6, ST_SetSRID(ST_MakePoint($7, $8), 4326)::geography)
		ON CONFLICT (id) DO UPDATE SET
			chain = EXCLUDED.chain,
			format = EXCLUDED.format,
			name = EXCLUDED.name,
			address = EXCLUDED.address,
			postcode = EXCLUDED.postcode,
			geom = EXCLUDED.geom;
	`, store.ID, store.Chain, store.Format, store.Name, store.Address, store.Postcode,
		store.Longitude, store.Latitude)
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
	err := r.pool.QueryRow(ctx, `
		SELECT id, name, brand, barcode, category, package_size, unit_metric, target_mrp_gbp
		FROM products WHERE id = $1
	`, id).Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize, &p.UnitMetric, &p.TargetMRP)
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (r *pgProductRepo) GetByBarcode(ctx context.Context, ean string) (*domain.Product, error) {
	var p domain.Product
	err := r.pool.QueryRow(ctx, `
		SELECT id, name, brand, barcode, category, package_size, unit_metric, target_mrp_gbp
		FROM products WHERE barcode = $1
	`, ean).Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize, &p.UnitMetric, &p.TargetMRP)
	if err != nil {
		return nil, err
	}
	return &p, nil
}

func (r *pgProductRepo) Search(ctx context.Context, query string, category string) ([]domain.Product, error) {
	var sb strings.Builder
	sb.WriteString("SELECT id, name, brand, barcode, category, package_size, unit_metric, target_mrp_gbp FROM products WHERE 1=1")
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

	var products []domain.Product
	for rows.Next() {
		var p domain.Product
		if err := rows.Scan(&p.ID, &p.Name, &p.Brand, &p.Barcode, &p.Category, &p.PackageSize, &p.UnitMetric, &p.TargetMRP); err == nil {
			products = append(products, p)
		}
	}
	return products, nil
}

func (r *pgProductRepo) GetAll(ctx context.Context) ([]domain.Product, error) {
	return r.Search(ctx, "", "")
}

func (r *pgProductRepo) Create(ctx context.Context, p domain.Product) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO products (id, name, brand, barcode, category, package_size, unit_metric, target_mrp_gbp)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (id) DO UPDATE SET
			name = EXCLUDED.name,
			brand = EXCLUDED.brand,
			barcode = EXCLUDED.barcode,
			category = EXCLUDED.category,
			package_size = EXCLUDED.package_size,
			unit_metric = EXCLUDED.unit_metric,
			target_mrp_gbp = EXCLUDED.target_mrp_gbp;
	`, p.ID, p.Name, p.Brand, p.Barcode, p.Category, p.PackageSize, p.UnitMetric, p.TargetMRP)
	return err
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

	// Insert into append-only price ledger
	var newID string
	err = tx.QueryRow(ctx, `
		INSERT INTO price_records (
			product_id, store_id, regular_price_pence, loyalty_price_pence,
			loyalty_scheme, effective_price_pence, source_type, freshness_status, observed_at
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
		RETURNING id::text;
	`, record.ProductID, record.StoreID, record.RegularPricePence, record.LoyaltyPricePence,
		string(record.LoyaltyScheme), effectivePrice, record.SourceType, string(record.FreshnessStatus)).Scan(&newID)
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

func (r *pgPriceRepo) ConfirmFreshness(ctx context.Context, productID, storeID string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE price_records
		SET freshness_status = 'STORE_CONFIRMED', observed_at = NOW()
		WHERE product_id = $1 AND store_id = $2 AND superseded_by IS NULL
	`, productID, storeID)
	return err
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

// ─────────────────────────────────────────────────────────────
// BOUNTY REPOSITORY IMPLEMENTATION
// ─────────────────────────────────────────────────────────────

type pgBountyRepo struct {
	pool *pgxpool.Pool
}

func (r *pgBountyRepo) GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Bounty, error) {
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

