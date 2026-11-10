package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
)

type AdminHandler struct {
	storeRepo    repository.StoreRepository
	compareSvc   *service.CompareService
	storeHandler *StoreHandler
	logger       *slog.Logger
}

func NewAdminHandler(
	storeRepo repository.StoreRepository,
	compareSvc *service.CompareService,
	storeHandler *StoreHandler,
	logger *slog.Logger,
) *AdminHandler {
	return &AdminHandler{
		storeRepo:    storeRepo,
		compareSvc:   compareSvc,
		storeHandler: storeHandler,
		logger:       logger,
	}
}

func (h *AdminHandler) ResetDatabase(w http.ResponseWriter, r *http.Request) {
	var req dto.AdminResetRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	targetLat := req.Lat
	targetLon := req.Lon
	areaName := req.AreaName
	if targetLat == 0 && targetLon == 0 {
		targetLat = 51.5390
		targetLon = -0.1426
		if areaName == "" {
			areaName = "Camden Town, London"
		}
	} else if areaName == "" {
		areaName = fmt.Sprintf("Live Area (%.3f, %.3f)", targetLat, targetLon)
	}

	if pgStore, ok := h.storeRepo.(interface {
		ExecuteRawSQL(ctx context.Context, sql string) error
	}); ok {
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM price_records;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM yellow_stickers;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM bounties;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM outbox_events;")
		if req.WipeStores {
			_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM stores;")
		}
	}

	seededStores, err := h.storeHandler.SeedAroundCoordinates(ctx, targetLat, targetLon, areaName)
	if err != nil && h.logger != nil {
		h.logger.Warn("admin reset seeding warning", "error", err)
	}

	if h.compareSvc != nil {
		h.compareSvc.ClearCache(ctx)
	}

	respond.JSON(w, http.StatusOK, dto.AdminResetResponse{
		Status:       "success",
		StoresCount:  len(seededStores),
		Message:      fmt.Sprintf("Database cleaned and re-seeded with %d active stores around %s", len(seededStores), areaName),
		TargetArea:   areaName,
		ResetTimeUTC: time.Now().UTC().Format(time.RFC3339),
	})
}
