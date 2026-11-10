package handler

import (
	"context"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"

	"pricebridge/backend/internal/telemetry"
	"pricebridge/backend/internal/transport/http/respond"
)

type HealthHandler struct {
	pgPing  func(ctx context.Context) error
	rdb     *redis.Client
	metrics *telemetry.Metrics
}

func NewHealthHandler(pgPing func(ctx context.Context) error, rdb *redis.Client, metrics *telemetry.Metrics) *HealthHandler {
	return &HealthHandler{
		pgPing:  pgPing,
		rdb:     rdb,
		metrics: metrics,
	}
}

func (h *HealthHandler) Health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	status := "healthy"
	deps := make(map[string]any)

	if h.pgPing != nil {
		if err := h.pgPing(ctx); err != nil {
			status = "degraded"
			deps["postgres"] = map[string]any{"status": "unhealthy", "error": err.Error()}
		} else {
			deps["postgres"] = map[string]any{"status": "healthy"}
		}
	} else {
		deps["postgres"] = map[string]any{"status": "in_memory"}
	}

	if h.rdb != nil {
		if err := h.rdb.Ping(ctx).Err(); err != nil {
			status = "degraded"
			deps["redis"] = map[string]any{"status": "unhealthy", "error": err.Error()}
		} else {
			deps["redis"] = map[string]any{"status": "healthy"}
		}
	} else {
		deps["redis"] = map[string]any{"status": "disabled"}
	}

	respond.JSON(w, http.StatusOK, map[string]any{
		"status":       status,
		"service":      "pricebridge-backend",
		"market":       "UK (London)",
		"timestamp":    time.Now().UTC().Format(time.RFC3339),
		"dependencies": deps,
	})
}

func (h *HealthHandler) Metrics(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	if h.metrics != nil {
		_, _ = w.Write([]byte(h.metrics.RenderPrometheus()))
	}
}
