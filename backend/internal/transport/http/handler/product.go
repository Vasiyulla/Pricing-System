package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
)

type ProductHandler struct {
	productRepo  repository.ProductRepository
	priceRepo    repository.PriceRepository
	storeRepo    repository.StoreRepository
	compareSvc   *service.CompareService
	trolleySvc   *service.TrolleyService
	storeHandler *StoreHandler
}

func NewProductHandler(
	productRepo repository.ProductRepository,
	priceRepo repository.PriceRepository,
	storeRepo repository.StoreRepository,
	compareSvc *service.CompareService,
	trolleySvc *service.TrolleyService,
	storeHandler *StoreHandler,
) *ProductHandler {
	return &ProductHandler{
		productRepo:  productRepo,
		priceRepo:    priceRepo,
		storeRepo:    storeRepo,
		compareSvc:   compareSvc,
		trolleySvc:   trolleySvc,
		storeHandler: storeHandler,
	}
}

func (h *ProductHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateProductRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.Name) == "" {
		respond.Error(w, r, http.StatusBadRequest, "Product name is required")
		return
	}

	if bc := strings.TrimSpace(req.Barcode); bc != "" {
		if existing, err := h.productRepo.GetByBarcode(r.Context(), bc); err == nil && existing != nil {
			respond.JSON(w, http.StatusOK, existing)
			return
		}
	}

	prodID := strings.TrimSpace(req.ID)
	if prodID == "" {
		prodID = "prod_" + uuid.New().String()[:12]
	}
	category := strings.TrimSpace(req.Category)
	if category == "" {
		category = "Pantry & Staples"
	}
	pkgSize := strings.TrimSpace(req.PackageSize)
	if pkgSize == "" {
		pkgSize = "1 unit"
	}
	brand := strings.TrimSpace(req.Brand)
	if brand == "" {
		brand = strings.Split(strings.TrimSpace(req.Name), " ")[0]
	}
	unit := strings.TrimSpace(req.UnitMetric)
	if unit == "" {
		unit = "unit"
	}

	product := domain.Product{
		ID:          prodID,
		Name:        strings.TrimSpace(req.Name),
		Brand:       brand,
		Barcode:     strings.TrimSpace(req.Barcode),
		Category:    category,
		PackageSize: pkgSize,
		UnitMetric:  unit,
		TargetMRP:   req.TargetMRP,
	}

	if err := h.productRepo.Create(r.Context(), product); err != nil {
		respond.Error(w, r, http.StatusInternalServerError, "Failed to create product: "+err.Error())
		return
	}
	respond.JSON(w, http.StatusCreated, product)
}

func (h *ProductHandler) Search(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	category := r.URL.Query().Get("category")

	prods, err := h.productRepo.Search(r.Context(), q, category)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, prods)
}

func (h *ProductHandler) GetByBarcode(w http.ResponseWriter, r *http.Request) {
	ean := chi.URLParam(r, "ean")
	prod, err := h.productRepo.GetByBarcode(r.Context(), ean)
	if err != nil {
		respond.Error(w, r, http.StatusNotFound, "product with barcode not found")
		return
	}
	respond.JSON(w, http.StatusOK, prod)
}

func (h *ProductHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	prod, err := h.productRepo.GetByID(r.Context(), id)
	if err != nil {
		respond.Error(w, r, http.StatusNotFound, "product not found")
		return
	}
	respond.JSON(w, http.StatusOK, prod)
}

// GetBestDealsBatch eliminates the previous N+1 query loop using a single lateral/DISTINCT ON SQL query.
func (h *ProductHandler) GetBestDealsBatch(w http.ResponseWriter, r *http.Request) {
	category := r.URL.Query().Get("category")
	lat, _ := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, _ := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	if nearby, _ := h.storeRepo.GetNearby(r.Context(), lat, lon, 10.0); len(nearby) == 0 && h.storeHandler != nil {
		areaName := fmt.Sprintf("Local (%.2f, %.2f)", lat, lon)
		_, _ = h.storeHandler.SeedAroundCoordinates(r.Context(), lat, lon, areaName)
	}

	summaries, err := h.priceRepo.GetBestDealsSingleQuery(r.Context(), lat, lon, 10.0, category)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, summaries)
}

func (h *ProductHandler) Compare(w http.ResponseWriter, r *http.Request) {
	productID := chi.URLParam(r, "id")
	lat, _ := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, _ := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if lat == 0 && lon == 0 {
		lat = 51.5390
		lon = -0.1426
	}

	userLoyalty := map[domain.LoyaltyScheme]bool{
		domain.LoyaltyClubcard: true,
		domain.LoyaltyNectar:   true,
		domain.LoyaltyLidlPlus: true,
	}

	comparisons, err := h.compareSvc.CompareProductAcrossStores(r.Context(), productID, lat, lon, 10.0, userLoyalty)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, comparisons)
}

func (h *ProductHandler) TrolleyWhisper(w http.ResponseWriter, r *http.Request) {
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
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, whisper)
}
