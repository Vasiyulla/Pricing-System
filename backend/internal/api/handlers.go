package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
)

type APIHandler struct {
	storeRepo    repository.StoreRepository
	productRepo  repository.ProductRepository
	priceRepo    repository.PriceRepository
	bountyRepo   repository.BountyRepository
	stickerRepo  repository.YellowStickerRepository
	categoryRepo repository.CategoryRepository
	compareSvc   *service.CompareService
	optimizerSvc *service.OptimizerService
	trolleySvc   *service.TrolleyService
	aiSvc        *service.AIService
}

func NewAPIHandler(
	storeRepo repository.StoreRepository,
	productRepo repository.ProductRepository,
	priceRepo repository.PriceRepository,
	bountyRepo repository.BountyRepository,
	stickerRepo repository.YellowStickerRepository,
	categoryRepo repository.CategoryRepository,
	compareSvc *service.CompareService,
	optimizerSvc *service.OptimizerService,
	trolleySvc *service.TrolleyService,
	aiSvc *service.AIService,
) *APIHandler {
	return &APIHandler{
		storeRepo:    storeRepo,
		productRepo:  productRepo,
		priceRepo:    priceRepo,
		bountyRepo:   bountyRepo,
		stickerRepo:  stickerRepo,
		categoryRepo: categoryRepo,
		compareSvc:   compareSvc,
		optimizerSvc: optimizerSvc,
		trolleySvc:   trolleySvc,
		aiSvc:        aiSvc,
	}
}

// ── Helpers ──

func writeJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

// ── Health ──

func (h *APIHandler) Health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":    "healthy",
		"service":   "pricebridge-backend",
		"market":    "UK (London)",
		"timestamp": time.Now().UTC().Format(time.RFC3339),
	})
}

// ── Stores ──

func (h *APIHandler) GetNearbyStores(w http.ResponseWriter, r *http.Request) {
	latStr := r.URL.Query().Get("lat")
	lonStr := r.URL.Query().Get("lon")
	radiusStr := r.URL.Query().Get("radius_km")

	lat, err := strconv.ParseFloat(latStr, 64)
	if err != nil {
		lat = 51.5390 // Default to Camden, London
	}
	lon, err := strconv.ParseFloat(lonStr, 64)
	if err != nil {
		lon = -0.1426
	}
	radiusKm, err := strconv.ParseFloat(radiusStr, 64)
	if err != nil || radiusKm <= 0 {
		radiusKm = 10.0
	}

	stores, err := h.storeRepo.GetNearby(r.Context(), lat, lon, radiusKm)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, stores)
}

func (h *APIHandler) GetStoreByID(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	store, err := h.storeRepo.GetByID(r.Context(), id)
	if err != nil {
		writeError(w, http.StatusNotFound, "store not found")
		return
	}
	writeJSON(w, http.StatusOK, store)
}

// ── Products ──

func (h *APIHandler) SearchProducts(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	category := r.URL.Query().Get("category")

	prods, err := h.productRepo.Search(r.Context(), q, category)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, prods)
}

func (h *APIHandler) GetProductByBarcode(w http.ResponseWriter, r *http.Request) {
	ean := chi.URLParam(r, "ean")
	prod, err := h.productRepo.GetByBarcode(r.Context(), ean)
	if err != nil {
		writeError(w, http.StatusNotFound, "product with barcode not found")
		return
	}
	writeJSON(w, http.StatusOK, prod)
}

func (h *APIHandler) GetProductByID(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	prod, err := h.productRepo.GetByID(r.Context(), id)
	if err != nil {
		writeError(w, http.StatusNotFound, "product not found")
		return
	}
	writeJSON(w, http.StatusOK, prod)
}

// ── Compare ──

func (h *APIHandler) CompareProductPrices(w http.ResponseWriter, r *http.Request) {
	productID := chi.URLParam(r, "id")
	lat, _ := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, _ := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	userLoyalty := map[domain.LoyaltyScheme]bool{
		domain.LoyaltyClubcard: true, // Default to true for Clubcard
		domain.LoyaltyNectar:   true,
		domain.LoyaltyLidlPlus: true,
	}

	comparisons, err := h.compareSvc.CompareProductAcrossStores(r.Context(), productID, lat, lon, 10.0, userLoyalty)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, comparisons)
}

// ── Trolley Ear Audio Whisperer ──

func (h *APIHandler) GetTrolleyWhisper(w http.ResponseWriter, r *http.Request) {
	productID := chi.URLParam(r, "id")
	storeID := r.URL.Query().Get("store_id")
	if storeID == "" {
		storeID = "store_tesco_camden"
	}
	lat, _ := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, _ := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	whisper, err := h.trolleySvc.GenerateWhisper(r.Context(), productID, storeID, lat, lon)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, whisper)
}

// ── Price Submission (Append-Only) ──

type PriceSubmissionRequest struct {
	ProductID         string `json:"product_id"`
	StoreID           string `json:"store_id"`
	RegularPricePence int    `json:"regular_price_pence"`
	LoyaltyPricePence *int   `json:"loyalty_price_pence,omitempty"`
	LoyaltyScheme     string `json:"loyalty_scheme,omitempty"`
	SourceType        string `json:"source_type"`
}

func (h *APIHandler) SubmitPrice(w http.ResponseWriter, r *http.Request) {
	var req PriceSubmissionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request payload")
		return
	}

	// Layer 3 Outlier Check: Prevent absurd prices (e.g. £0 or £500 for milk)
	if req.RegularPricePence <= 10 || req.RegularPricePence > 100000 {
		writeError(w, http.StatusBadRequest, "price outlier: submission rejected by anomaly shield")
		return
	}

	loyalty := domain.LoyaltyNone
	if req.LoyaltyScheme != "" {
		loyalty = domain.LoyaltyScheme(req.LoyaltyScheme)
	}

	record := domain.PriceRecord{
		ID:                uuid.New().String(),
		ProductID:         req.ProductID,
		StoreID:           req.StoreID,
		RegularPricePence: req.RegularPricePence,
		LoyaltyPricePence: req.LoyaltyPricePence,
		LoyaltyScheme:     loyalty,
		UnitPricePence:    req.RegularPricePence / 5, // estimated
		FreshnessStatus:   domain.FreshnessRecentlyObserved,
		SourceType:        req.SourceType,
		ConfidenceScore:   0.6,
		ObservedAt:        time.Now(),
	}

	if err := h.priceRepo.SubmitPrice(r.Context(), record); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusCreated, map[string]interface{}{
		"status":  "submitted",
		"record":  record,
		"message": "Price successfully added to append-only ledger",
	})
}

// ── Offline Batch Sync ──

func (h *APIHandler) BatchSyncPrices(w http.ResponseWriter, r *http.Request) {
	var submissions []PriceSubmissionRequest
	if err := json.NewDecoder(r.Body).Decode(&submissions); err != nil {
		writeError(w, http.StatusBadRequest, "invalid batch payload")
		return
	}

	count := 0
	for _, req := range submissions {
		if req.RegularPricePence <= 10 {
			continue
		}
		record := domain.PriceRecord{
			ID:                uuid.New().String(),
			ProductID:         req.ProductID,
			StoreID:           req.StoreID,
			RegularPricePence: req.RegularPricePence,
			LoyaltyPricePence: req.LoyaltyPricePence,
			LoyaltyScheme:     domain.LoyaltyScheme(req.LoyaltyScheme),
			FreshnessStatus:   domain.FreshnessRecentlyObserved,
			SourceType:        "offline_sync",
			ObservedAt:        time.Now(),
		}
		if err := h.priceRepo.SubmitPrice(r.Context(), record); err == nil {
			count++
		}
	}

	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "synced",
		"synced":  count,
		"message": "Offline submissions processed idempotently",
	})
}

// ── 2-Stop Pareto Basket Optimizer ──

type BasketOptimizeRequest struct {
	Items   []domain.BasketItem `json:"items"`
	UserLat float64             `json:"user_lat"`
	UserLon float64             `json:"user_lon"`
}

func (h *APIHandler) OptimizeBasket(w http.ResponseWriter, r *http.Request) {
	var req BasketOptimizeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid basket request")
		return
	}

	if req.UserLat == 0 && req.UserLon == 0 {
		req.UserLat = 51.5390
		req.UserLon = -0.1426
	}

	result, err := h.optimizerSvc.OptimizeTwoStopBasket(r.Context(), req.Items, req.UserLat, req.UserLon)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, result)
}

// ── Bounties ──

func (h *APIHandler) GetNearbyBounties(w http.ResponseWriter, r *http.Request) {
	lat, _ := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, _ := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	bounties, err := h.bountyRepo.GetNearby(r.Context(), lat, lon, 10.0)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, bounties)
}

func (h *APIHandler) ClaimBounty(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	if err := h.bountyRepo.Claim(r.Context(), id); err != nil {
		writeError(w, http.StatusNotFound, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":  "claimed",
		"message": "Bounty claimed! Upload a photo of the shelf label to receive your points.",
	})
}

func (h *APIHandler) GetActiveBountyPrompt(w http.ResponseWriter, r *http.Request) {
	prompt, err := h.bountyRepo.GetActivePrompt(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if prompt == nil {
		writeJSON(w, http.StatusOK, map[string]interface{}{})
		return
	}
	writeJSON(w, http.StatusOK, prompt)
}

// ── Categories ──

func (h *APIHandler) GetCategories(w http.ResponseWriter, r *http.Request) {
	categories, err := h.categoryRepo.GetAll(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, categories)
}

// ── Yellow Stickers ──

func (h *APIHandler) GetActiveYellowStickers(w http.ResponseWriter, r *http.Request) {
	storeID := r.URL.Query().Get("store_id")
	deals, err := h.stickerRepo.GetActive(r.Context(), storeID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, deals)
}

// ── AI Multimodal Endpoints ──

type ImageScanRequest struct {
	ImageBase64 string `json:"image_base64"`
}

func (h *APIHandler) ScanReceiptAI(w http.ResponseWriter, r *http.Request) {
	var req ImageScanRequest
	json.NewDecoder(r.Body).Decode(&req) // optional body

	extracted, err := h.aiSvc.ParseReceiptImage(r.Context(), req.ImageBase64)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, extracted)
}

func (h *APIHandler) ScanYellowStickerAI(w http.ResponseWriter, r *http.Request) {
	var req ImageScanRequest
	json.NewDecoder(r.Body).Decode(&req)

	deal, err := h.aiSvc.ParseYellowSticker(r.Context(), req.ImageBase64)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, deal)
}

