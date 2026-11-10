package service

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/telemetry"
)

// OutboxWorker polls outbox_events and runs periodic expired session cleanup.
// Uses context cancellation and sync.WaitGroup for zero-loss graceful shutdown.
type OutboxWorker struct {
	outboxRepo  repository.OutboxRepository
	sessionRepo repository.SessionRepository
	compareSvc  *CompareService
	logger      *slog.Logger
	metrics     *telemetry.Metrics
	wg          sync.WaitGroup
}

func NewOutboxWorker(
	outboxRepo repository.OutboxRepository,
	sessionRepo repository.SessionRepository,
	compareSvc *CompareService,
	logger *slog.Logger,
	metrics *telemetry.Metrics,
) *OutboxWorker {
	return &OutboxWorker{
		outboxRepo:  outboxRepo,
		sessionRepo: sessionRepo,
		compareSvc:  compareSvc,
		logger:      logger,
		metrics:     metrics,
	}
}

// Start launches the outbox polling and session cleanup loops until ctx is cancelled.
func (w *OutboxWorker) Start(ctx context.Context) {
	w.wg.Add(1)
	go func() {
		defer w.wg.Done()
		ticker := time.NewTicker(5 * time.Second)
		cleanupTicker := time.NewTicker(15 * time.Minute)
		defer ticker.Stop()
		defer cleanupTicker.Stop()

		w.logger.Info("outbox worker started")
		for {
			select {
			case <-ctx.Done():
				w.logger.Info("outbox worker draining and stopping")
				return
			case <-ticker.C:
				w.processBatch(ctx)
			case <-cleanupTicker.C:
				if w.sessionRepo != nil {
					if purged, err := w.sessionRepo.PurgeExpiredSessions(ctx); err == nil && purged > 0 {
						w.logger.Info("purged expired sessions", "count", purged)
					}
				}
			}
		}
	}()
}

// Wait blocks until the background worker loop exits cleanly.
func (w *OutboxWorker) Wait() {
	w.wg.Wait()
}

func (w *OutboxWorker) processBatch(ctx context.Context) {
	if w.outboxRepo == nil {
		return
	}
	events, err := w.outboxRepo.FetchPending(ctx, 25)
	if err != nil || len(events) == 0 {
		return
	}

	for _, ev := range events {
		if ctx.Err() != nil {
			return
		}
		if ev.EventType == "price.submitted" && w.compareSvc != nil {
			var payload struct {
				ProductID string `json:"product_id"`
				StoreID   string `json:"store_id"`
			}
			if json.Unmarshal([]byte(ev.PayloadJSON), &payload) == nil && payload.ProductID != "" {
				_ = w.compareSvc.InvalidateProductCache(ctx, payload.ProductID, payload.StoreID)
			}
		}

		if err := w.outboxRepo.MarkProcessed(ctx, ev.ID); err != nil {
			if w.metrics != nil {
				w.metrics.OutboxFailed.Add(1)
			}
			w.logger.Warn("failed marking outbox event processed", "event_id", ev.ID, "error", err)
			continue
		}
		if w.metrics != nil {
			w.metrics.OutboxProcessed.Add(1)
		}
	}
}

