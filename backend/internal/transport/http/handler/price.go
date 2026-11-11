package handler

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
	"pricebridge/backend/internal/transport/http/middleware"
)

type PriceHandler struct {
	priceRepo   repository.PriceRepository
	productRepo repository.ProductRepository
	sessionRepo repository.SessionRepository
	compareSvc  *service.CompareService
	productSvc  *service.ProductService
}

func NewPriceHandler(
	priceRepo repository.PriceRepository,
	productRepo repository.ProductRepository,
	sessionRepo repository.SessionRepository,
	compareSvc *service.CompareService,
	productSvc ...*service.ProductService,
) *PriceHandler {
	var ps *service.ProductService
	if len(productSvc) > 0 {
		ps = productSvc[0]
	}
	return &PriceHandler{
		priceRepo:   priceRepo,
		productRepo: productRepo,
		sessionRepo: sessionRepo,
		compareSvc:  compareSvc,
		productSvc:  ps,
	}
}

func (h *PriceHandler) Submit(w http.ResponseWriter, r *http.Request) {
	var req dto.PriceSubmissionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid request payload")
		return
	}

	if req.RegularPricePence <= 10 || req.RegularPricePence > 100000 {
		respond.Error(w, r, http.StatusBadRequest, "price outlier: submission rejected by anomaly shield")
		return
	}

	loyalty := domain.LoyaltyNone
	if req.LoyaltyScheme != "" {
		loyalty = domain.LoyaltyScheme(req.LoyaltyScheme)
	}

	idempotencyKey := strings.TrimSpace(req.IdempotencyKey)
	if idempotencyKey == "" {
		idempotencyKey = strings.TrimSpace(r.Header.Get("Idempotency-Key"))
	}

	var submittedBy string
	if sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData); ok {
		submittedBy = sess.UserID
	}

	pkgSize := "1 unit"
	if prod, err := h.productRepo.GetByID(r.Context(), req.ProductID); err == nil && prod != nil {
		pkgSize = prod.PackageSize
	}

	record := domain.PriceRecord{
		ID:                uuid.New().String(),
		ProductID:         req.ProductID,
		StoreID:           req.StoreID,
		RegularPricePence: req.RegularPricePence,
		LoyaltyPricePence: req.LoyaltyPricePence,
		LoyaltyScheme:     loyalty,
		UnitPricePence:    domain.ComputeUnitPricePence(req.RegularPricePence, pkgSize),
		FreshnessStatus:   domain.FreshnessRecentlyObserved,
		SourceType:        req.SourceType,
		ConfidenceScore:   domain.ComputeConfidenceScore(req.SourceType),
		ObservedAt:        time.Now(),
		IdempotencyKey:    idempotencyKey,
		SubmittedByUserID: submittedBy,
		EvidenceID:        req.EvidenceID,
	}

	if err := h.priceRepo.SubmitPrice(r.Context(), record); err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}

	if h.sessionRepo != nil && submittedBy != "" {
		karmaBonus := 15
		if req.EvidenceID != nil && *req.EvidenceID != "" {
			karmaBonus = 25
		}
		_ = h.sessionRepo.IncrementUserStats(r.Context(), submittedBy, karmaBonus, 1, 0)
	}

	if h.compareSvc != nil {
		_ = h.compareSvc.InvalidateProductCache(r.Context(), record.ProductID, record.StoreID)
	}
	if h.productSvc != nil && submittedBy != "" {
		h.productSvc.InvalidateUserProductsCache(r.Context(), submittedBy)
	}

	respond.JSON(w, http.StatusCreated, map[string]any{
		"status":  "submitted",
		"record":  record,
		"message": "Price successfully added to append-only ledger",
	})
}

func (h *PriceHandler) BatchSync(w http.ResponseWriter, r *http.Request) {
	var submissions []dto.PriceSubmissionRequest
	if err := json.NewDecoder(r.Body).Decode(&submissions); err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid batch payload")
		return
	}

	var submittedBy string
	if sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData); ok {
		submittedBy = sess.UserID
	}

	synced := 0
	failed := 0
	for _, req := range submissions {
		if r.Context().Err() != nil {
			break
		}
		if req.RegularPricePence <= 10 || req.RegularPricePence > 100000 {
			failed++
			continue
		}

		pkgSize := "1 unit"
		if prod, err := h.productRepo.GetByID(r.Context(), req.ProductID); err == nil && prod != nil {
			pkgSize = prod.PackageSize
		}

		record := domain.PriceRecord{
			ID:                uuid.New().String(),
			ProductID:         req.ProductID,
			StoreID:           req.StoreID,
			RegularPricePence: req.RegularPricePence,
			LoyaltyPricePence: req.LoyaltyPricePence,
			LoyaltyScheme:     domain.LoyaltyScheme(req.LoyaltyScheme),
			UnitPricePence:    domain.ComputeUnitPricePence(req.RegularPricePence, pkgSize),
			FreshnessStatus:   domain.FreshnessRecentlyObserved,
			SourceType:        "offline_sync",
			ConfidenceScore:   domain.ComputeConfidenceScore("offline_sync"),
			ObservedAt:        time.Now(),
			IdempotencyKey:    req.IdempotencyKey,
			SubmittedByUserID: submittedBy,
		}
		if err := h.priceRepo.SubmitPrice(r.Context(), record); err == nil {
			synced++
			if h.compareSvc != nil {
				_ = h.compareSvc.InvalidateProductCache(r.Context(), record.ProductID, record.StoreID)
			}
		} else {
			failed++
		}
	}

	if h.sessionRepo != nil && submittedBy != "" && synced > 0 {
		_ = h.sessionRepo.IncrementUserStats(r.Context(), submittedBy, synced*15, synced, 0)
	}
	if h.productSvc != nil && submittedBy != "" && synced > 0 {
		h.productSvc.InvalidateUserProductsCache(r.Context(), submittedBy)
	}

	respond.JSON(w, http.StatusOK, map[string]any{
		"status":  "synced",
		"synced":  synced,
		"failed":  failed,
		"message": "Offline submissions processed idempotently",
	})
}

func (h *PriceHandler) ConfirmFreshness(w http.ResponseWriter, r *http.Request) {
	var req dto.ConfirmFreshnessRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.ProductID == "" || req.StoreID == "" {
		respond.Error(w, r, http.StatusBadRequest, "product_id and store_id are required")
		return
	}

	var userID string
	if sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData); ok {
		userID = sess.UserID
	}

	corroborations, err := h.priceRepo.ConfirmFreshness(r.Context(), req.ProductID, req.StoreID, userID)
	if err != nil {
		respond.Error(w, r, http.StatusNotFound, err.Error())
		return
	}

	if h.sessionRepo != nil && userID != "" {
		_ = h.sessionRepo.IncrementUserStats(r.Context(), userID, 5, 0, 1)
	}
	if h.compareSvc != nil {
		_ = h.compareSvc.InvalidateProductCache(r.Context(), req.ProductID, req.StoreID)
	}

	respond.JSON(w, http.StatusOK, map[string]any{
		"status":         "confirmed",
		"corroborations": corroborations,
		"karma_awarded":  5,
	})
}
