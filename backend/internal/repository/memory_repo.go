package repository

import (
	"context"
	"fmt"
	"math"
	"strings"
	"sync"
	"time"

	"pricebridge/backend/internal/domain"
)

// HaversineDistanceKm calculates great-circle distance between two GPS coordinates
func HaversineDistanceKm(lat1, lon1, lat2, lon2 float64) float64 {
	const earthRadiusKm = 6371.0

	dLat := (lat2 - lat1) * (math.Pi / 180.0)
	dLon := (lon2 - lon1) * (math.Pi / 180.0)

	rLat1 := lat1 * (math.Pi / 180.0)
	rLat2 := lat2 * (math.Pi / 180.0)

	a := math.Sin(dLat/2)*math.Sin(dLat/2) +
		math.Sin(dLon/2)*math.Sin(dLon/2)*math.Cos(rLat1)*math.Cos(rLat2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))

	return earthRadiusKm * c
}

type MemoryStore struct {
	mu             sync.RWMutex
	stores         map[string]domain.Store
	products       map[string]domain.Product
	barcodeIndex   map[string]string               // barcode -> productID
	prices         map[string][]domain.PriceRecord // productID -> records
	bounties       map[string]domain.Bounty
	yellowStickers map[string]domain.YellowStickerDeal
	users          map[string]domain.UserProfileRecord
	sessions       map[string]domain.AuthSessionRecord
	evidences      map[string]domain.EvidenceRecord
	discrepancies  map[string]map[string]bool // storeID -> set of reporterID
	freshnessVotes map[string]map[string]bool // "prod|store" -> set of userID
}

func NewMemoryStore() *MemoryStore {
	ms := &MemoryStore{
		stores:         make(map[string]domain.Store),
		products:       make(map[string]domain.Product),
		barcodeIndex:   make(map[string]string),
		prices:         make(map[string][]domain.PriceRecord),
		bounties:       make(map[string]domain.Bounty),
		yellowStickers: make(map[string]domain.YellowStickerDeal),
		users:          make(map[string]domain.UserProfileRecord),
		sessions:       make(map[string]domain.AuthSessionRecord),
		evidences:      make(map[string]domain.EvidenceRecord),
		discrepancies:  make(map[string]map[string]bool),
		freshnessVotes: make(map[string]map[string]bool),
	}
	ms.seedLondonData()
	return ms
}

// ── Store Repository Adapter ──

type memoryStoreRepo struct{ ms *MemoryStore }

func (r *memoryStoreRepo) GetByID(ctx context.Context, id string) (*domain.Store, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	store, exists := r.ms.stores[id]
	if !exists {
		return nil, fmt.Errorf("store %s not found", id)
	}
	return &store, nil
}

func (r *memoryStoreRepo) GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Store, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.Store
	for _, store := range r.ms.stores {
		dist := HaversineDistanceKm(lat, lon, store.Latitude, store.Longitude)
		if dist <= radiusKm {
			storeCopy := store
			storeCopy.DistanceKm = math.Round(dist*100) / 100
			result = append(result, storeCopy)
		}
	}
	return result, nil
}

func (r *memoryStoreRepo) GetAll(ctx context.Context) ([]domain.Store, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	var result []domain.Store
	for _, s := range r.ms.stores {
		result = append(result, s)
	}
	return result, nil
}

func (r *memoryStoreRepo) Create(ctx context.Context, store domain.Store) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	r.ms.stores[store.ID] = store
	return nil
}

// ── Product Repository Adapter ──

type memoryProductRepo struct{ ms *MemoryStore }

func (r *memoryProductRepo) GetByID(ctx context.Context, id string) (*domain.Product, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	prod, exists := r.ms.products[id]
	if !exists {
		return nil, fmt.Errorf("product %s not found", id)
	}
	return &prod, nil
}

func (r *memoryProductRepo) GetByBarcode(ctx context.Context, ean string) (*domain.Product, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	prodID, exists := r.ms.barcodeIndex[ean]
	if !exists {
		return nil, fmt.Errorf("barcode %s not found", ean)
	}
	prod := r.ms.products[prodID]
	return &prod, nil
}

func (r *memoryProductRepo) Search(ctx context.Context, query string, category string) ([]domain.Product, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.Product
	q := strings.ToLower(query)

	for _, p := range r.ms.products {
		if category != "" && !strings.EqualFold(p.Category, category) {
			continue
		}
		if q == "" || strings.Contains(strings.ToLower(p.Name), q) || strings.Contains(strings.ToLower(p.Brand), q) {
			result = append(result, p)
		}
	}
	return result, nil
}

func (r *memoryProductRepo) GetAll(ctx context.Context) ([]domain.Product, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	result := make([]domain.Product, 0, len(r.ms.products))
	for _, p := range r.ms.products {
		result = append(result, p)
	}
	return result, nil
}

func (r *memoryProductRepo) GetByCreator(ctx context.Context, userID string, limit, offset int) ([]domain.Product, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	if userID == "" {
		return []domain.Product{}, nil
	}
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	result := make([]domain.Product, 0)
	for _, p := range r.ms.products {
		matched := p.CreatedByUserID == userID
		if !matched {
			for _, rec := range r.ms.prices[p.ID] {
				if rec.SubmittedByUserID == userID {
					matched = true
					break
				}
			}
		}
		if matched {
			result = append(result, p)
		}
	}

	if offset >= len(result) {
		return []domain.Product{}, nil
	}
	end := offset + limit
	if end > len(result) {
		end = len(result)
	}
	return result[offset:end], nil
}

func (r *memoryProductRepo) Create(ctx context.Context, product domain.Product) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()

	if product.IdempotencyKey != "" {
		for _, existing := range r.ms.products {
			if existing.IdempotencyKey == product.IdempotencyKey {
				return nil
			}
		}
	}

	r.ms.products[product.ID] = product
	if product.Barcode != "" {
		r.ms.barcodeIndex[product.Barcode] = product.ID
	}
	return nil
}

// ── Price Repository Adapter ──

type memoryPriceRepo struct{ ms *MemoryStore }

func (r *memoryPriceRepo) GetStorePrices(ctx context.Context, productID string) ([]domain.PriceRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	records, exists := r.ms.prices[productID]
	if !exists {
		return []domain.PriceRecord{}, nil
	}

	latestPerStore := make(map[string]domain.PriceRecord)
	for _, rec := range records {
		if rec.SupersededBy == nil {
			latestPerStore[rec.StoreID] = rec
		}
	}

	var result []domain.PriceRecord
	for _, rec := range latestPerStore {
		result = append(result, rec)
	}
	return result, nil
}

func (r *memoryPriceRepo) GetLatestPrice(ctx context.Context, productID, storeID string) (*domain.PriceRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	records, exists := r.ms.prices[productID]
	if !exists {
		return nil, fmt.Errorf("no prices for product %s", productID)
	}

	for _, rec := range records {
		if rec.StoreID == storeID && rec.SupersededBy == nil {
			return &rec, nil
		}
	}
	return nil, fmt.Errorf("no active price for product %s at store %s", productID, storeID)
}

func (r *memoryPriceRepo) GetLatestPricesForProductsAndStores(ctx context.Context, productIDs, storeIDs []string) ([]domain.PriceRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	if len(productIDs) == 0 || len(storeIDs) == 0 {
		return []domain.PriceRecord{}, nil
	}
	storeSet := make(map[string]bool, len(storeIDs))
	for _, s := range storeIDs {
		storeSet[s] = true
	}
	result := make([]domain.PriceRecord, 0)
	for _, pid := range productIDs {
		for _, rec := range r.ms.prices[pid] {
			if rec.SupersededBy == nil && storeSet[rec.StoreID] {
				result = append(result, rec)
			}
		}
	}
	return result, nil
}

func (r *memoryPriceRepo) SubmitPrice(ctx context.Context, record domain.PriceRecord) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()

	if record.IdempotencyKey != "" {
		for _, recs := range r.ms.prices {
			for _, rec := range recs {
				if rec.IdempotencyKey == record.IdempotencyKey {
					return nil
				}
			}
		}
	}

	existing := r.ms.prices[record.ProductID]
	for i := range existing {
		if existing[i].StoreID == record.StoreID && existing[i].SupersededBy == nil {
			id := record.ID
			existing[i].SupersededBy = &id
		}
	}

	r.ms.prices[record.ProductID] = append(existing, record)
	return nil
}

func (r *memoryPriceRepo) ConfirmFreshness(ctx context.Context, productID, storeID, userID string) (int, error) {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()

	key := productID + "|" + storeID
	if r.ms.freshnessVotes[key] == nil {
		r.ms.freshnessVotes[key] = make(map[string]bool)
	}
	if userID == "" {
		userID = "anon"
	}
	r.ms.freshnessVotes[key][userID] = true
	count := len(r.ms.freshnessVotes[key])

	records := r.ms.prices[productID]
	for i := range records {
		if records[i].StoreID == storeID && records[i].SupersededBy == nil {
			records[i].ObservedAt = time.Now()
			if count >= 2 {
				records[i].FreshnessStatus = domain.FreshnessCommunityCorroborated
			} else {
				records[i].FreshnessStatus = domain.FreshnessRecentlyObserved
			}
			return count, nil
		}
	}
	return count, fmt.Errorf("no active price to refresh for product %s at store %s", productID, storeID)
}

func (r *memoryPriceRepo) GetAllPrices(ctx context.Context) ([]domain.PriceRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.PriceRecord
	for _, recs := range r.ms.prices {
		for _, rec := range recs {
			if rec.SupersededBy == nil {
				result = append(result, rec)
			}
		}
	}
	return result, nil
}

func (r *memoryPriceRepo) GetSubmissionsByUser(ctx context.Context, userID string) ([]domain.PriceRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.PriceRecord
	for _, recs := range r.ms.prices {
		for _, rec := range recs {
			if rec.SubmittedByUserID == userID {
				copyRec := rec
				if p, ok := r.ms.products[rec.ProductID]; ok {
					copyRec.ProductName = p.Name
				}
				if st, ok := r.ms.stores[rec.StoreID]; ok {
					copyRec.StoreName = st.Name
				}
				result = append(result, copyRec)
			}
		}
	}
	return result, nil
}

func (r *memoryPriceRepo) GetBestDealsSingleQuery(ctx context.Context, lat, lon, radiusKm float64, category string) ([]domain.ProductDealSummary, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var summaries []domain.ProductDealSummary
	for _, prod := range r.ms.products {
		if category != "" && prod.Category != category {
			continue
		}
		summary := domain.ProductDealSummary{
			Product:            prod,
			ComparedStoreCount: 0,
			FreshnessStatus:    domain.FreshnessRecentlyObserved,
		}
		bestEff := math.MaxInt32
		for _, rec := range r.ms.prices[prod.ID] {
			if rec.SupersededBy != nil {
				continue
			}
			st, ok := r.ms.stores[rec.StoreID]
			if !ok {
				continue
			}
			dist := HaversineDistanceKm(lat, lon, st.Latitude, st.Longitude)
			if dist > radiusKm {
				continue
			}
			eff := rec.RegularPricePence
			if rec.LoyaltyPricePence != nil && *rec.LoyaltyPricePence < eff {
				eff = *rec.LoyaltyPricePence
			}
			if eff < bestEff {
				bestEff = eff
				summary.BestPricePence = eff
				summary.BestPriceGBP = fmt.Sprintf("£%.2f", float64(eff)/100.0)
				summary.BestStoreName = st.Name
				summary.BestStoreID = st.ID
				summary.BestStoreDistanceKm = math.Round(dist*100) / 100
				summary.FreshnessStatus = rec.FreshnessStatus
			}
			summary.ComparedStoreCount++
		}
		if summary.ComparedStoreCount > 0 {
			summaries = append(summaries, summary)
		}
	}
	return summaries, nil
}

// ── Bounty Repository Adapter ──

type memoryBountyRepo struct{ ms *MemoryStore }

func (r *memoryBountyRepo) GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Bounty, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.Bounty
	for _, b := range r.ms.bounties {
		if b.Status != "OPEN" {
			continue
		}
		store, exists := r.ms.stores[b.StoreID]
		if !exists {
			continue
		}
		dist := HaversineDistanceKm(lat, lon, store.Latitude, store.Longitude)
		if dist <= radiusKm {
			bCopy := b
			bCopy.DistanceKm = math.Round(dist*100) / 100
			result = append(result, bCopy)
		}
	}
	return result, nil
}

func (r *memoryBountyRepo) Claim(ctx context.Context, bountyID string) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()

	b, exists := r.ms.bounties[bountyID]
	if !exists {
		return fmt.Errorf("bounty %s not found", bountyID)
	}
	b.Status = "CLAIMED"
	r.ms.bounties[bountyID] = b
	return nil
}

func (r *memoryBountyRepo) Create(ctx context.Context, bounty domain.Bounty) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	r.ms.bounties[bounty.ID] = bounty
	return nil
}

func (r *memoryBountyRepo) GetActivePrompt(ctx context.Context) (*domain.BountyPrompt, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	for _, b := range r.ms.bounties {
		if b.Status == "OPEN" {
			return &domain.BountyPrompt{
				BountyID:          b.ID,
				ProductName:       b.ProductName,
				StoreName:         b.StoreName,
				LastObservedPrice: "£1.45",
				RewardPoints:      b.RewardPoints,
			}, nil
		}
	}
	return nil, nil
}

// ── Category Repository Adapter ──

type memoryCategoryRepo struct{ ms *MemoryStore }

func (r *memoryCategoryRepo) GetAll(ctx context.Context) ([]domain.Category, error) {
	return []domain.Category{
		{ID: "all", Name: "All"},
		{ID: "Dairy & Chilled", Name: "Dairy & Chilled"},
		{ID: "Bakery", Name: "Bakery"},
		{ID: "Pantry & Staples", Name: "Pantry & Staples"},
		{ID: "Fresh Produce", Name: "Fresh Produce"},
		{ID: "Beverages", Name: "Beverages"},
		{ID: "Meat & Poultry", Name: "Meat & Poultry"},
		{ID: "Frozen Foods", Name: "Frozen Foods"},
	}, nil
}

// ── Yellow Sticker Repository Adapter ──

type memoryYellowStickerRepo struct{ ms *MemoryStore }

func (r *memoryYellowStickerRepo) GetActive(ctx context.Context, storeID string) ([]domain.YellowStickerDeal, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()

	var result []domain.YellowStickerDeal
	now := time.Now()
	for _, d := range r.ms.yellowStickers {
		if d.ExpiresAt.After(now) {
			if storeID == "" || d.StoreID == storeID {
				result = append(result, d)
			}
		}
	}
	return result, nil
}

func (r *memoryYellowStickerRepo) Create(ctx context.Context, deal domain.YellowStickerDeal) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	r.ms.yellowStickers[deal.ID] = deal
	return nil
}

// ── Session, Evidence & Discrepancy Adapters ──

type memorySessionRepo struct{ ms *MemoryStore }

func (r *memorySessionRepo) UpsertUser(ctx context.Context, user domain.UserProfileRecord) (*domain.UserProfileRecord, error) {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	if existing, ok := r.ms.users[user.ID]; ok {
		existing.Email = user.Email
		existing.DisplayName = user.DisplayName
		if user.AvatarURL != "" {
			existing.AvatarURL = user.AvatarURL
		}
		r.ms.users[user.ID] = existing
		return &existing, nil
	}
	if user.KarmaPoints == 0 {
		user.KarmaPoints = 50
	}
	r.ms.users[user.ID] = user
	return &user, nil
}

func (r *memorySessionRepo) GetUserProfile(ctx context.Context, userID string) (*domain.UserProfileRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	if u, ok := r.ms.users[userID]; ok {
		return &u, nil
	}
	return nil, fmt.Errorf("user not found")
}

func (r *memorySessionRepo) IncrementUserStats(ctx context.Context, userID string, karmaDelta, reportedDelta, verifiedDelta int) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	u, ok := r.ms.users[userID]
	if !ok {
		return nil
	}
	u.KarmaPoints += karmaDelta
	u.PricesReported += reportedDelta
	u.PricesVerified += verifiedDelta
	r.ms.users[userID] = u
	return nil
}

func (r *memorySessionRepo) SaveSession(ctx context.Context, sess domain.AuthSessionRecord) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	r.ms.sessions[sess.Token] = sess
	return nil
}

func (r *memorySessionRepo) GetSession(ctx context.Context, token string) (*domain.AuthSessionRecord, error) {
	r.ms.mu.RLock()
	defer r.ms.mu.RUnlock()
	s, ok := r.ms.sessions[token]
	if !ok || time.Now().After(s.ExpiresAt) {
		return nil, fmt.Errorf("session not found or expired")
	}
	return &s, nil
}

func (r *memorySessionRepo) DeleteSession(ctx context.Context, token string) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	delete(r.ms.sessions, token)
	return nil
}

func (r *memorySessionRepo) PurgeExpiredSessions(ctx context.Context) (int64, error) {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	var count int64
	now := time.Now()
	for tok, s := range r.ms.sessions {
		if now.After(s.ExpiresAt) {
			delete(r.ms.sessions, tok)
			count++
		}
	}
	return count, nil
}

type memoryOutboxRepo struct{}

func (r *memoryOutboxRepo) FetchPending(ctx context.Context, limit int) ([]domain.OutboxEvent, error) {
	return nil, nil
}

func (r *memoryOutboxRepo) MarkProcessed(ctx context.Context, id string) error {
	return nil
}

type memoryEvidenceRepo struct{ ms *MemoryStore }

func (r *memoryEvidenceRepo) SaveEvidence(ctx context.Context, ev domain.EvidenceRecord) (string, error) {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	if ev.ID == "" {
		ev.ID = fmt.Sprintf("ev_%d", time.Now().UnixNano())
	}
	r.ms.evidences[ev.ID] = ev
	return ev.ID, nil
}

type memoryDiscrepancyRepo struct{ ms *MemoryStore }

func (r *memoryDiscrepancyRepo) RecordDiscrepancy(ctx context.Context, report *domain.StoreDiscrepancyReport) (int, error) {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	if r.ms.discrepancies[report.StoreID] == nil {
		r.ms.discrepancies[report.StoreID] = make(map[string]bool)
	}
	r.ms.discrepancies[report.StoreID][report.ReporterID] = true
	return len(r.ms.discrepancies[report.StoreID]), nil
}

func (r *memoryDiscrepancyRepo) UpdateStoreReliability(ctx context.Context, storeID string, score int) error {
	r.ms.mu.Lock()
	defer r.ms.mu.Unlock()
	if st, ok := r.ms.stores[storeID]; ok {
		st.ReliabilityScore = score
		if score < 60 {
			st.IsConfirmedPartner = false
			st.VerificationStatus = domain.StoreStatusFlaggedInactive
		}
		r.ms.stores[storeID] = st
	}
	return nil
}

// ── Factory Getters ──

func (ms *MemoryStore) StoreRepo() StoreRepository     { return &memoryStoreRepo{ms: ms} }
func (ms *MemoryStore) ProductRepo() ProductRepository { return &memoryProductRepo{ms: ms} }
func (ms *MemoryStore) PriceRepo() PriceRepository     { return &memoryPriceRepo{ms: ms} }
func (ms *MemoryStore) BountyRepo() BountyRepository   { return &memoryBountyRepo{ms: ms} }
func (ms *MemoryStore) YellowStickerRepo() YellowStickerRepository {
	return &memoryYellowStickerRepo{ms: ms}
}
func (ms *MemoryStore) CategoryRepo() CategoryRepository       { return &memoryCategoryRepo{ms: ms} }
func (ms *MemoryStore) SessionRepo() SessionRepository         { return &memorySessionRepo{ms: ms} }
func (ms *MemoryStore) EvidenceRepo() EvidenceRepository       { return &memoryEvidenceRepo{ms: ms} }
func (ms *MemoryStore) DiscrepancyRepo() DiscrepancyRepository { return &memoryDiscrepancyRepo{ms: ms} }
func (ms *MemoryStore) OutboxRepo() OutboxRepository           { return &memoryOutboxRepo{} }

// ── London Seed Data ──

func (ms *MemoryStore) seedLondonData() {
	// 10 London Supermarkets
	storesList := []domain.Store{
		{
			ID: "store_tesco_camden", Chain: domain.ChainTesco, Format: domain.FormatExpress,
			Name: "Tesco Express Camden", Latitude: 51.5390, Longitude: -0.1426,
			Address: "124-126 Camden High St", Postcode: "NW1 0LU",
		},
		{
			ID: "store_sainsburys_camden", Chain: domain.ChainSainsburys, Format: domain.FormatSuperstore,
			Name: "Sainsbury's Camden", Latitude: 51.5385, Longitude: -0.1415,
			Address: "17-21 Camden Rd", Postcode: "NW1 9LL",
		},
		{
			ID: "store_aldi_camden", Chain: domain.ChainAldi, Format: domain.FormatSuperstore,
			Name: "Aldi Camden High St", Latitude: 51.5410, Longitude: -0.1435,
			Address: "235 Camden High St", Postcode: "NW1 7BU",
		},
		{
			ID: "store_lidl_tottenham", Chain: domain.ChainLidl, Format: domain.FormatSuperstore,
			Name: "Lidl Tottenham Court Rd", Latitude: 51.5218, Longitude: -0.1332,
			Address: "180 Tottenham Court Rd", Postcode: "W1T 7PD",
		},
		{
			ID: "store_ms_covent_garden", Chain: domain.ChainMarksAndSpencer, Format: domain.FormatSuperstore,
			Name: "M&S Foodhall Covent Garden", Latitude: 51.5120, Longitude: -0.1230,
			Address: "107 Long Acre", Postcode: "WC2E 9NT",
		},
		{
			ID: "store_asda_oldkent", Chain: domain.ChainAsda, Format: domain.FormatSuperstore,
			Name: "Asda Old Kent Road Superstore", Latitude: 51.4870, Longitude: -0.0680,
			Address: "464-504 Old Kent Rd", Postcode: "SE1 5AG",
		},
		{
			ID: "store_waitrose_bloomsbury", Chain: domain.ChainWaitrose, Format: domain.FormatSuperstore,
			Name: "Waitrose Bloomsbury", Latitude: 51.5240, Longitude: -0.1245,
			Address: "The Brunswick Centre", Postcode: "WC1N 1AF",
		},
		{
			ID: "store_morrisons_chalk_farm", Chain: domain.ChainMorrisons, Format: domain.FormatSuperstore,
			Name: "Morrisons Chalk Farm", Latitude: 51.5450, Longitude: -0.1500,
			Address: "Chalk Farm Rd", Postcode: "NW1 8EH",
		},
		{
			ID: "store_tesco_surrey_quays", Chain: domain.ChainTesco, Format: domain.FormatSuperstore,
			Name: "Tesco Extra Surrey Quays", Latitude: 51.4930, Longitude: -0.0480,
			Address: "Surrey Quays Shopping Centre", Postcode: "SE16 7LL",
		},
		{
			ID: "store_coop_kingscross", Chain: domain.ChainCoop, Format: domain.FormatLocal,
			Name: "Co-op King's Cross", Latitude: 51.5310, Longitude: -0.1220,
			Address: "1 Pancras Sq", Postcode: "N1C 4AG",
		},
	}
	for _, s := range storesList {
		ms.stores[s.ID] = s
	}

	// 25 UK Grocery Staple Products
	productsList := []domain.Product{
		{ID: "prod_lurpak", Name: "Lurpak Slightly Salted Spreadable Butter", Brand: "Lurpak", Barcode: "5740900400812", Category: "Dairy & Chilled", PackageSize: "500g", UnitMetric: "100g", TargetMRP: 4.50},
		{ID: "prod_yorkshire_tea", Name: "Yorkshire Tea Bags", Brand: "Yorkshire Tea", Barcode: "5010357112048", Category: "Hot Beverages", PackageSize: "160 Bags (500g)", UnitMetric: "100g", TargetMRP: 5.50},
		{ID: "prod_whole_milk", Name: "British Whole Milk 4 Pints", Brand: "Store Brand", Barcode: "5000119001010", Category: "Dairy & Chilled", PackageSize: "2.27L (4 Pints)", UnitMetric: "L", TargetMRP: 1.65},
		{ID: "prod_cheddar", Name: "Cathedral City Mature Cheddar", Brand: "Cathedral City", Barcode: "5010078028114", Category: "Dairy & Chilled", PackageSize: "350g", UnitMetric: "100g", TargetMRP: 3.80},
		{ID: "prod_beans", Name: "Heinz Baked Beans in Tomato Sauce", Brand: "Heinz", Barcode: "5000157024671", Category: "Food Cupboard", PackageSize: "415g", UnitMetric: "100g", TargetMRP: 1.40},
		{ID: "prod_bread", Name: "Warburtons Toastie Thick Sliced White Bread", Brand: "Warburtons", Barcode: "5010044000427", Category: "Bakery", PackageSize: "800g", UnitMetric: "100g", TargetMRP: 1.45},
		{ID: "prod_eggs", Name: "Free Range Large British Eggs 12 Pack", Brand: "Store Brand", Barcode: "5000119001027", Category: "Dairy & Chilled", PackageSize: "12 Pack", UnitMetric: "item", TargetMRP: 3.20},
		{ID: "prod_pasta", Name: "De Cecco Penne Rigate No. 41", Brand: "De Cecco", Barcode: "8001250120410", Category: "Food Cupboard", PackageSize: "500g", UnitMetric: "100g", TargetMRP: 2.20},
		{ID: "prod_chicken", Name: "British Fresh Chicken Breast Fillets", Brand: "Store Brand", Barcode: "5000119001034", Category: "Fresh Meat", PackageSize: "650g", UnitMetric: "kg", TargetMRP: 5.25},
		{ID: "prod_bananas", Name: "Fairtrade Bananas Pack", Brand: "Store Brand", Barcode: "5000119001041", Category: "Fresh Produce", PackageSize: "5 Pack", UnitMetric: "item", TargetMRP: 1.00},
		{ID: "prod_olive_oil", Name: "Filippo Berio Extra Virgin Olive Oil", Brand: "Filippo Berio", Barcode: "8002210111118", Category: "Food Cupboard", PackageSize: "500ml", UnitMetric: "100ml", TargetMRP: 6.50},
		{ID: "prod_fairy_pods", Name: "Fairy Platinum All in One Dishwasher Tablets", Brand: "Fairy", Barcode: "8001090123456", Category: "Household", PackageSize: "60 Pods", UnitMetric: "wash", TargetMRP: 12.00},
	}
	for _, p := range productsList {
		ms.products[p.ID] = p
		ms.barcodeIndex[p.Barcode] = p.ID
	}

	// Seed Prices with UK Regular vs Clubcard/Nectar Discounts
	now := time.Now()
	p250 := 250
	p300 := 300
	p375 := 375
	p100 := 100
	p900 := 900

	seedPrices := []domain.PriceRecord{
		// Lurpak
		{ID: "pr_1", ProductID: "prod_lurpak", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 450, LoyaltyPricePence: &p300, LoyaltyScheme: domain.LoyaltyClubcard, UnitPricePence: 60, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-2 * time.Hour)},
		{ID: "pr_2", ProductID: "prod_lurpak", StoreID: "store_aldi_camden", StoreName: "Aldi Camden High St", RegularPricePence: 299, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 60, FreshnessStatus: domain.FreshnessCommunityCorroborated, SourceType: "checkout_receipt", ObservedAt: now.Add(-5 * time.Hour)},
		{ID: "pr_3", ProductID: "prod_lurpak", StoreID: "store_sainsburys_camden", StoreName: "Sainsbury's Camden", RegularPricePence: 440, LoyaltyPricePence: &p300, LoyaltyScheme: domain.LoyaltyNectar, UnitPricePence: 60, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-1 * time.Hour)},
		{ID: "pr_4", ProductID: "prod_lurpak", StoreID: "store_ms_covent_garden", StoreName: "M&S Foodhall Covent Garden", RegularPricePence: 475, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 95, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-6 * time.Hour)},

		// Yorkshire Tea
		{ID: "pr_5", ProductID: "prod_yorkshire_tea", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 550, LoyaltyPricePence: &p375, LoyaltyScheme: domain.LoyaltyClubcard, UnitPricePence: 75, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-3 * time.Hour)},
		{ID: "pr_6", ProductID: "prod_yorkshire_tea", StoreID: "store_aldi_camden", StoreName: "Aldi Camden High St", RegularPricePence: 415, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 83, FreshnessStatus: domain.FreshnessCommunityCorroborated, SourceType: "checkout_receipt", ObservedAt: now.Add(-4 * time.Hour)},
		{ID: "pr_7", ProductID: "prod_yorkshire_tea", StoreID: "store_asda_oldkent", StoreName: "Asda Old Kent Road Superstore", RegularPricePence: 450, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 90, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-8 * time.Hour)},

		// Whole Milk 4 Pints
		{ID: "pr_8", ProductID: "prod_whole_milk", StoreID: "store_aldi_camden", StoreName: "Aldi Camden High St", RegularPricePence: 145, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 64, FreshnessStatus: domain.FreshnessCommunityCorroborated, SourceType: "shelf_label", ObservedAt: now.Add(-1 * time.Hour)},
		{ID: "pr_9", ProductID: "prod_whole_milk", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 165, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 72, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-2 * time.Hour)},
		{ID: "pr_10", ProductID: "prod_whole_milk", StoreID: "store_sainsburys_camden", StoreName: "Sainsbury's Camden", RegularPricePence: 155, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 68, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-3 * time.Hour)},

		// Cathedral City Cheddar
		{ID: "pr_11", ProductID: "prod_cheddar", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 380, LoyaltyPricePence: &p250, LoyaltyScheme: domain.LoyaltyClubcard, UnitPricePence: 71, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-2 * time.Hour)},
		{ID: "pr_12", ProductID: "prod_cheddar", StoreID: "store_sainsburys_camden", StoreName: "Sainsbury's Camden", RegularPricePence: 375, LoyaltyPricePence: &p250, LoyaltyScheme: domain.LoyaltyNectar, UnitPricePence: 71, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-4 * time.Hour)},
		{ID: "pr_13", ProductID: "prod_cheddar", StoreID: "store_aldi_camden", StoreName: "Aldi Camden High St", RegularPricePence: 269, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 76, FreshnessStatus: domain.FreshnessCommunityCorroborated, SourceType: "shelf_label", ObservedAt: now.Add(-6 * time.Hour)},

		// Heinz Baked Beans
		{ID: "pr_14", ProductID: "prod_beans", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 140, LoyaltyPricePence: &p100, LoyaltyScheme: domain.LoyaltyClubcard, UnitPricePence: 24, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-2 * time.Hour)},
		{ID: "pr_15", ProductID: "prod_beans", StoreID: "store_aldi_camden", StoreName: "Aldi Camden High St", RegularPricePence: 125, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 30, FreshnessStatus: domain.FreshnessCommunityCorroborated, SourceType: "checkout_receipt", ObservedAt: now.Add(-5 * time.Hour)},

		// Fairy Dishwasher Pods
		{ID: "pr_16", ProductID: "prod_fairy_pods", StoreID: "store_tesco_camden", StoreName: "Tesco Express Camden", RegularPricePence: 1400, LoyaltyPricePence: &p900, LoyaltyScheme: domain.LoyaltyClubcard, UnitPricePence: 15, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-1 * time.Hour)},
		{ID: "pr_17", ProductID: "prod_fairy_pods", StoreID: "store_morrisons_chalk_farm", StoreName: "Morrisons Chalk Farm", RegularPricePence: 999, LoyaltyScheme: domain.LoyaltyNone, UnitPricePence: 16, FreshnessStatus: domain.FreshnessRecentlyObserved, SourceType: "shelf_label", ObservedAt: now.Add(-3 * time.Hour)},
	}

	for _, p := range seedPrices {
		ms.prices[p.ProductID] = append(ms.prices[p.ProductID], p)
	}

	// Active AI Bounties (Missing Price Quests)
	bountiesList := []domain.Bounty{
		{
			ID: "bounty_1", StoreID: "store_morrisons_chalk_farm", StoreName: "Morrisons Chalk Farm",
			ProductID: "prod_chicken", ProductName: "British Fresh Chicken Breast Fillets",
			RewardPoints: 50, ExpiresAt: now.Add(24 * time.Hour), Status: "OPEN",
		},
		{
			ID: "bounty_2", StoreID: "store_ms_covent_garden", StoreName: "M&S Foodhall Covent Garden",
			ProductID: "prod_bread", ProductName: "Warburtons Toastie Thick Sliced White Bread",
			RewardPoints: 75, ExpiresAt: now.Add(12 * time.Hour), Status: "OPEN",
		},
		{
			ID: "bounty_3", StoreID: "store_asda_oldkent", StoreName: "Asda Old Kent Road Superstore",
			ProductID: "prod_olive_oil", ProductName: "Filippo Berio Extra Virgin Olive Oil",
			RewardPoints: 100, ExpiresAt: now.Add(36 * time.Hour), Status: "OPEN",
		},
	}
	for _, b := range bountiesList {
		ms.bounties[b.ID] = b
	}

	// Active Yellow Sticker Clearance Deals (Reduced to Clear)
	stickersList := []domain.YellowStickerDeal{
		{
			ID: "ys_1", StoreID: "store_ms_covent_garden", StoreName: "M&S Foodhall Covent Garden",
			ProductName: "M&S British Fresh Chicken Breast (Clearance)", WasPricePence: 650, NowPricePence: 195,
			DiscountPercent: 70, UseByDate: "Today (Reduce to clear)", ExpiresAt: now.Add(3 * time.Hour),
		},
		{
			ID: "ys_2", StoreID: "store_sainsburys_camden", StoreName: "Sainsbury's Camden",
			ProductName: "Taste the Difference Sourdough Loaf", WasPricePence: 250, NowPricePence: 60,
			DiscountPercent: 76, UseByDate: "Today (Bakery clearance)", ExpiresAt: now.Add(2 * time.Hour),
		},
	}
	for _, s := range stickersList {
		ms.yellowStickers[s.ID] = s
	}
}
