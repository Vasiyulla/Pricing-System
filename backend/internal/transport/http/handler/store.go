package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"pricebridge/backend/internal/cache"
	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
	"pricebridge/backend/internal/transport/http/middleware"
)

type StoreHandler struct {
	storeRepo   repository.StoreRepository
	productRepo repository.ProductRepository
	priceRepo   repository.PriceRepository
	bountyRepo  repository.BountyRepository
	stickerRepo repository.YellowStickerRepository
	verifierSvc service.StoreVerifier
	geoCache    *cache.GeoSpatialCache
	routeSvc    *service.RouteService
	bgWG        *sync.WaitGroup
}

func NewStoreHandler(
	storeRepo repository.StoreRepository,
	productRepo repository.ProductRepository,
	priceRepo repository.PriceRepository,
	bountyRepo repository.BountyRepository,
	stickerRepo repository.YellowStickerRepository,
	verifierSvc service.StoreVerifier,
	geoCache *cache.GeoSpatialCache,
	routeSvc *service.RouteService,
	bgWG *sync.WaitGroup,
) *StoreHandler {
	return &StoreHandler{
		storeRepo:   storeRepo,
		productRepo: productRepo,
		priceRepo:   priceRepo,
		bountyRepo:  bountyRepo,
		stickerRepo: stickerRepo,
		verifierSvc: verifierSvc,
		geoCache:    geoCache,
		routeSvc:    routeSvc,
		bgWG:        bgWG,
	}
}

func (h *StoreHandler) GetNearby(w http.ResponseWriter, r *http.Request) {
	lat, latErr := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, lonErr := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if latErr != nil || lonErr != nil || (lat == 0 && lon == 0) {
		respond.JSON(w, http.StatusOK, []domain.Store{})
		return
	}
	radiusKm, err := strconv.ParseFloat(r.URL.Query().Get("radius_km"), 64)
	if err != nil || radiusKm <= 0 {
		radiusKm = 10.0
	}
	mode := service.NormalizeRouteMode(r.URL.Query().Get("mode"))

	if h.geoCache != nil && lat != 0 && lon != 0 {
		if cachedStores, err := h.geoCache.SearchNearby(r.Context(), lat, lon, radiusKm); err == nil && len(cachedStores) > 0 {
			if h.routeSvc != nil {
				cachedStores = h.routeSvc.EnrichStoreRoutes(r.Context(), lat, lon, mode, cachedStores)
			}
			respond.JSON(w, http.StatusOK, cachedStores)
			return
		}
	}

	stores, err := h.storeRepo.GetNearby(r.Context(), lat, lon, radiusKm)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if stores == nil {
		stores = []domain.Store{}
	}

	// Tracked background Redis geo indexing instead of an unguarded fire-and-forget goroutine
	if h.geoCache != nil && len(stores) > 0 && h.bgWG != nil {
		storesCopy := make([]domain.Store, len(stores))
		copy(storesCopy, stores)
		h.bgWG.Add(1)
		go func() {
			defer h.bgWG.Done()
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			_ = h.geoCache.IndexStores(ctx, storesCopy)
		}()
	}

	if h.routeSvc != nil && len(stores) > 0 {
		stores = h.routeSvc.EnrichStoreRoutes(r.Context(), lat, lon, mode, stores)
	}

	respond.JSON(w, http.StatusOK, stores)
}

func (h *StoreHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	store, err := h.storeRepo.GetByID(r.Context(), id)
	if err != nil {
		respond.Error(w, r, http.StatusNotFound, "store not found")
		return
	}
	respond.JSON(w, http.StatusOK, store)
}

func sanitizeStoreSlug(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	var b strings.Builder
	for _, r := range s {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		} else if r == ' ' || r == '-' || r == '_' {
			b.WriteRune('_')
		}
	}
	res := strings.Trim(b.String(), "_")
	if len(res) > 20 {
		res = res[:20]
	}
	if res == "" {
		res = "store"
	}
	return res
}

// Create registers a community-added store with proximity deduplication.
func (h *StoreHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateStoreRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	name := strings.TrimSpace(req.Name)
	if name == "" {
		respond.Error(w, r, http.StatusBadRequest, "store name is required")
		return
	}
	if req.Latitude == 0 && req.Longitude == 0 {
		respond.Error(w, r, http.StatusBadRequest, "valid coordinates (latitude, longitude) are required")
		return
	}

	// Proximity deduplication: check if a store with the same name already exists within 50m
	if existing, err := h.storeRepo.GetNearby(r.Context(), req.Latitude, req.Longitude, 0.05); err == nil {
		for _, s := range existing {
			if strings.EqualFold(strings.TrimSpace(s.Name), name) {
				respond.JSON(w, http.StatusOK, s)
				return
			}
		}
	}

	id := strings.TrimSpace(req.ID)
	if id == "" {
		id = fmt.Sprintf("store_%s_%s", sanitizeStoreSlug(name), uuid.New().String()[:8])
	}

	chain := domain.StoreChain(req.Chain)
	if chain == "" {
		chain = domain.StoreChain(name)
	}

	format := domain.StoreFormat(req.Format)
	if format == "" {
		format = domain.FormatLocal
	}

	address := strings.TrimSpace(req.Address)
	if address == "" {
		address = fmt.Sprintf("Near %.4f, %.4f", req.Latitude, req.Longitude)
	}

	store := domain.Store{
		ID:                 id,
		Chain:              chain,
		Format:             format,
		Name:               name,
		Latitude:           req.Latitude,
		Longitude:          req.Longitude,
		Address:            address,
		Postcode:           req.Postcode,
		VerificationStatus: domain.StoreStatusCommunityPending,
		ReliabilityScore:   85,
		IsConfirmedPartner: false,
		OperatingHours:     "8:00 AM - 10:00 PM",
	}

	if err := h.storeRepo.Create(r.Context(), store); err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}

	// Index in geoCache in background
	if h.geoCache != nil && h.bgWG != nil {
		h.bgWG.Add(1)
		go func() {
			defer h.bgWG.Done()
			ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			_ = h.geoCache.IndexStores(ctx, []domain.Store{store})
		}()
	}

	respond.JSON(w, http.StatusCreated, store)
}

func (h *StoreHandler) Verify(w http.ResponseWriter, r *http.Request) {
	var name string
	var lat, lon float64

	if r.Method == http.MethodPost {
		var req dto.VerifyStoreRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err == nil {
			name = req.Name
			lat = req.Latitude
			lon = req.Longitude
		}
	}

	if name == "" {
		name = r.URL.Query().Get("name")
	}
	if lat == 0 {
		lat, _ = strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	}
	if lon == 0 {
		lon, _ = strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	}
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	res, err := h.verifierSvc.VerifyStoreExistence(r.Context(), name, lat, lon)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, res)
}

func (h *StoreHandler) ReportDiscrepancy(w http.ResponseWriter, r *http.Request) {
	storeID := chi.URLParam(r, "id")
	if storeID == "" {
		respond.Error(w, r, http.StatusBadRequest, "store id required")
		return
	}

	var req dto.ReportDiscrepancyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid request body")
		return
	}

	if sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData); ok && sess.UserID != "" {
		req.ReporterID = sess.UserID
	}
	if req.ReporterID == "" {
		req.ReporterID = "anonymous_user"
	}
	if req.Reason == "" {
		req.Reason = "STORE_CLOSED"
	}

	report := &domain.StoreDiscrepancyReport{
		StoreID:       storeID,
		ReporterID:    req.ReporterID,
		Reason:        req.Reason,
		Details:       req.Details,
		UserLatitude:  req.UserLatitude,
		UserLongitude: req.UserLongitude,
		ReportedAt:    time.Now().UTC(),
	}

	res, err := h.verifierSvc.ReportDiscrepancy(r.Context(), report)
	if err != nil {
		respond.Error(w, r, http.StatusNotFound, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, res)
}

// SeedAroundCoordinates seeds baseline stores and prices around cold-start GPS coordinates.
func (h *StoreHandler) SeedAroundCoordinates(ctx context.Context, lat, lon float64, areaName string) ([]domain.Store, error) {
	templates := []struct {
		name    string
		chain   domain.StoreChain
		format  domain.StoreFormat
		dLat    float64
		dLon    float64
		address string
		partner bool
	}{
		{"City Express Supermarket", domain.ChainTesco, domain.FormatExpress, 0.0032, 0.0028, "High Street", true},
		{"Cornerstone Fresh Grocers", domain.ChainSainsburys, domain.FormatLocal, -0.0028, 0.0045, "Station Road", false},
		{"Metro Value Foods", domain.ChainAldi, domain.FormatSuperstore, 0.0058, -0.0040, "Commercial Crescent", true},
		{"Community Quality Market", domain.ChainLidl, domain.FormatSuperstore, -0.0065, -0.0055, "Green Lane", true},
	}

	var created []domain.Store
	prods, _ := h.productRepo.GetAll(ctx)

	for i, t := range templates {
		stID := fmt.Sprintf("store_live_%d_%d", int(math.Abs(lat*100)), i+1)
		store := domain.Store{
			ID:                 stID,
			Chain:              t.chain,
			Format:             t.format,
			Name:               fmt.Sprintf("%s (%s)", t.name, areaName),
			Address:            fmt.Sprintf("%d %s, %s", (i+1)*12, t.address, areaName),
			Postcode:           "LOCAL",
			Latitude:           lat + t.dLat,
			Longitude:          lon + t.dLon,
			DistanceKm:         0.3 + float64(i)*0.35,
			ReliabilityScore:   95 - i*2,
			IsConfirmedPartner: t.partner,
		}

		_ = h.storeRepo.Create(ctx, store)
		created = append(created, store)

		for _, p := range prods {
			variance := 0.85 + (float64((i*7+len(p.ID))%25) / 100.0)
			regularPence := max(50, int(p.TargetMRP*variance*100.0))

			var loyaltyPence *int
			loyaltyScheme := domain.LoyaltyNone
			if t.partner && i%2 == 0 {
				loy := int(float64(regularPence) * 0.90)
				loyaltyPence = &loy
				loyaltyScheme = domain.LoyaltyClubcard
			}

			_ = h.priceRepo.SubmitPrice(ctx, domain.PriceRecord{
				ID:                uuid.New().String(),
				ProductID:         p.ID,
				StoreID:           stID,
				RegularPricePence: regularPence,
				LoyaltyPricePence: loyaltyPence,
				LoyaltyScheme:     loyaltyScheme,
				UnitPricePence:    domain.ComputeUnitPricePence(regularPence, p.PackageSize),
				SourceType:        "verified_shelf_tag",
				ConfidenceScore:   0.85,
				FreshnessStatus:   domain.FreshnessRecentlyObserved,
				ObservedAt:        time.Now().Add(-time.Duration(i*45) * time.Minute),
			})
		}

		if len(prods) > i {
			_ = h.bountyRepo.Create(ctx, domain.Bounty{
				ID:           fmt.Sprintf("bounty_live_%d", i+1),
				StoreID:      stID,
				ProductID:    prods[i].ID,
				ProductName:  fmt.Sprintf("Verify shelf tag for %s", prods[i].Name),
				RewardPoints: 25 + i*5,
			})
			_ = h.stickerRepo.Create(ctx, domain.YellowStickerDeal{
				ID:              fmt.Sprintf("sticker_live_%d", i+1),
				StoreID:         stID,
				StoreName:       store.Name,
				ProductName:     prods[len(prods)-1-i].Name,
				WasPricePence:   int(prods[len(prods)-1-i].TargetMRP * 100),
				NowPricePence:   int(prods[len(prods)-1-i].TargetMRP * 50),
				DiscountPercent: 50,
				UseByDate:       "Midnight Tonight",
			})
		}
	}
	return created, nil
}
