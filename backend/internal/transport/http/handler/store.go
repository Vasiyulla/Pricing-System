package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strconv"
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
		bgWG:        bgWG,
	}
}

func (h *StoreHandler) GetNearby(w http.ResponseWriter, r *http.Request) {
	lat, err := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	if err != nil {
		lat = 51.5390
	}
	lon, err := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if err != nil {
		lon = -0.1426
	}
	radiusKm, err := strconv.ParseFloat(r.URL.Query().Get("radius_km"), 64)
	if err != nil || radiusKm <= 0 {
		radiusKm = 10.0
	}

	if h.geoCache != nil && lat != 0 && lon != 0 {
		if cachedStores, err := h.geoCache.SearchNearby(r.Context(), lat, lon, radiusKm); err == nil && len(cachedStores) > 0 {
			respond.JSON(w, http.StatusOK, cachedStores)
			return
		}
	}

	stores, err := h.storeRepo.GetNearby(r.Context(), lat, lon, radiusKm)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}

	if len(stores) == 0 && lat != 0 && lon != 0 {
		areaName := fmt.Sprintf("Local (%.2f, %.2f)", lat, lon)
		seeded, seedErr := h.SeedAroundCoordinates(r.Context(), lat, lon, areaName)
		if seedErr == nil && len(seeded) > 0 {
			stores = seeded
		}
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
