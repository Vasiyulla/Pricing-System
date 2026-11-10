package telemetry

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"sync"
	"sync/atomic"
)

type ctxKey string

const requestIDKey ctxKey = "request_id"

// NewLogger creates a structured log/slog logger (JSON in production, text in local dev).
func NewLogger(env string) *slog.Logger {
	opts := &slog.HandlerOptions{Level: slog.LevelInfo}
	var handler slog.Handler
	if env == "development" || env == "local" {
		handler = slog.NewTextHandler(os.Stdout, opts)
	} else {
		handler = slog.NewJSONHandler(os.Stdout, opts)
	}
	return slog.New(handler)
}

func WithRequestID(ctx context.Context, reqID string) context.Context {
	return context.WithValue(ctx, requestIDKey, reqID)
}

func RequestIDFromContext(ctx context.Context) string {
	if v, ok := ctx.Value(requestIDKey).(string); ok {
		return v
	}
	return ""
}

// Metrics tracks lightweight lock-free Prometheus-compatible telemetry counters.
type Metrics struct {
	HTTPRequestsTotal   atomic.Int64
	HTTP4xxTotal        atomic.Int64
	HTTP5xxTotal        atomic.Int64
	HTTPLatencyMsSum    atomic.Int64
	OutboxProcessed     atomic.Int64
	OutboxFailed        atomic.Int64
	CacheHits           atomic.Int64
	CacheMisses         atomic.Int64
	RateLimitRejections atomic.Int64

	routeMu     sync.RWMutex
	routeCounts map[string]*atomic.Int64
}

func NewMetrics() *Metrics {
	return &Metrics{
		routeCounts: make(map[string]*atomic.Int64),
	}
}

func (m *Metrics) RecordRequest(method, path string, status int, latencyMs int64) {
	m.HTTPRequestsTotal.Add(1)
	m.HTTPLatencyMsSum.Add(latencyMs)
	if status >= 500 {
		m.HTTP5xxTotal.Add(1)
	} else if status >= 400 {
		m.HTTP4xxTotal.Add(1)
	}

	key := method + " " + path
	m.routeMu.RLock()
	cnt, ok := m.routeCounts[key]
	m.routeMu.RUnlock()
	if !ok {
		m.routeMu.Lock()
		if cnt, ok = m.routeCounts[key]; !ok {
			cnt = &atomic.Int64{}
			m.routeCounts[key] = cnt
		}
		m.routeMu.Unlock()
	}
	cnt.Add(1)
}

// RenderPrometheus renders standard Prometheus text exposition format (text/plain; version=0.0.4).
func (m *Metrics) RenderPrometheus() string {
	out := fmt.Sprintf(
		"# TYPE http_requests_total counter\nhttp_requests_total %d\n"+
			"# TYPE http_requests_4xx_total counter\nhttp_requests_4xx_total %d\n"+
			"# TYPE http_requests_5xx_total counter\nhttp_requests_5xx_total %d\n"+
			"# TYPE http_request_latency_ms_sum counter\nhttp_request_latency_ms_sum %d\n"+
			"# TYPE outbox_events_processed_total counter\noutbox_events_processed_total %d\n"+
			"# TYPE outbox_events_failed_total counter\noutbox_events_failed_total %d\n"+
			"# TYPE rate_limit_rejections_total counter\nrate_limit_rejections_total %d\n",
		m.HTTPRequestsTotal.Load(),
		m.HTTP4xxTotal.Load(),
		m.HTTP5xxTotal.Load(),
		m.HTTPLatencyMsSum.Load(),
		m.OutboxProcessed.Load(),
		m.OutboxFailed.Load(),
		m.RateLimitRejections.Load(),
	)
	return out
}

