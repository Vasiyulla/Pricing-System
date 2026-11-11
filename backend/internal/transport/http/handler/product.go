package handler

import (
	"encoding/json"
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
	routeSvc     *service.RouteService
}

func NewProductHandler(
	productRepo repository.ProductRepository,
	priceRepo repository.PriceRepository,
	storeRepo repository.StoreRepository,
	compareSvc *service.CompareService,
	trolleySvc *service.TrolleyService,
	storeHandler *StoreHandler,
	routeSvc ...*service.RouteService,
) *ProductHandler {
	var rs *service.RouteService
	if len(routeSvc) > 0 {
		rs = routeSvc[0]
	}
	return &ProductHandler{
		productRepo:  productRepo,
		priceRepo:    priceRepo,
		storeRepo:    storeRepo,
		compareSvc:   compareSvc,
		trolleySvc:   trolleySvc,
		storeHandler: storeHandler,
		routeSvc:     rs,
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
	lat, latErr := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, lonErr := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if latErr != nil || lonErr != nil || (lat == 0 && lon == 0) {
		respond.JSON(w, http.StatusOK, []domain.ProductDealSummary{})
		return
	}

	mode := service.NormalizeRouteMode(r.URL.Query().Get("mode"))
	summaries, err := h.priceRepo.GetBestDealsSingleQuery(r.Context(), lat, lon, 10.0, category)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if summaries == nil {
		summaries = []domain.ProductDealSummary{}
	}

	if h.routeSvc != nil && len(summaries) > 0 {
		// Enrich unique referenced stores in a single matrix batch call
		uniqueMap := make(map[string]domain.Store)
		for _, s := range summaries {
			for _, alt := range s.Alternatives {
				if _, seen := uniqueMap[alt.Store.ID]; !seen {
					uniqueMap[alt.Store.ID] = alt.Store
				}
			}
		}
		if len(uniqueMap) > 0 {
			uniqueStores := make([]domain.Store, 0, len(uniqueMap))
			for _, st := range uniqueMap {
				uniqueStores = append(uniqueStores, st)
			}
			enriched := h.routeSvc.EnrichStoreRoutes(r.Context(), lat, lon, mode, uniqueStores)
			enrichedMap := make(map[string]domain.Store, len(enriched))
			for _, st := range enriched {
				enrichedMap[st.ID] = st
			}
			for i := range summaries {
				for j := range summaries[i].Alternatives {
					if st, ok := enrichedMap[summaries[i].Alternatives[j].Store.ID]; ok {
						summaries[i].Alternatives[j].Store = st
					}
				}
				if bestSt, ok := enrichedMap[summaries[i].BestStoreID]; ok {
					summaries[i].BestStoreDistanceKm = bestSt.DistanceKm
					summaries[i].BestStoreDurationMins = bestSt.DurationMinutes
					summaries[i].BestStoreRouteMode = bestSt.RouteMode
					summaries[i].IsExactRoute = bestSt.IsExactRoute
				}
			}
		}
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
	mode := service.NormalizeRouteMode(r.URL.Query().Get("mode"))

	userLoyalty := map[domain.LoyaltyScheme]bool{
		domain.LoyaltyClubcard: true,
		domain.LoyaltyNectar:   true,
		domain.LoyaltyLidlPlus: true,
	}

	comparisons, err := h.compareSvc.CompareProductAcrossStores(r.Context(), productID, lat, lon, 10.0, userLoyalty, mode)
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
