package middleware

import (
	"context"
	"crypto/subtle"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5/middleware"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/telemetry"
	"pricebridge/backend/internal/transport/http/respond"
)

type ctxKey string

const UserContextKey ctxKey = "authenticated_user"

// SessionData holds authenticated user metadata attached to request contexts.
type SessionData struct {
	UserID      string    `json:"user_id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"display_name"`
	Role        string    `json:"role"`
	ExpiresAt   time.Time `json:"expires_at"`
}

// SessionStore is an injectable durable session cache backed by PostgreSQL.
type SessionStore struct {
	mu       sync.RWMutex
	sessions map[string]SessionData
	repo     repository.SessionRepository
}

func NewSessionStore(repo repository.SessionRepository) *SessionStore {
	return &SessionStore{
		sessions: make(map[string]SessionData),
		repo:     repo,
	}
}

func (s *SessionStore) Save(ctx context.Context, token string, data SessionData) {
	s.mu.Lock()
	s.sessions[token] = data
	s.mu.Unlock()

	if s.repo != nil {
		dbCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
		defer cancel()
		_ = s.repo.SaveSession(dbCtx, domain.AuthSessionRecord{
			Token:       token,
			UserID:      data.UserID,
			Email:       data.Email,
			DisplayName: data.DisplayName,
			Role:        data.Role,
			ExpiresAt:   data.ExpiresAt,
		})
	}
}

func (s *SessionStore) Get(ctx context.Context, token string) (SessionData, bool) {
	s.mu.RLock()
	data, exists := s.sessions[token]
	s.mu.RUnlock()

	if exists && time.Now().Before(data.ExpiresAt) {
		return data, true
	}

	if s.repo != nil {
		dbCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
		defer cancel()
		if rec, err := s.repo.GetSession(dbCtx, token); err == nil && rec != nil {
			sd := SessionData{
				UserID:      rec.UserID,
				Email:       rec.Email,
				DisplayName: rec.DisplayName,
				Role:        rec.Role,
				ExpiresAt:   rec.ExpiresAt,
			}
			s.mu.Lock()
			s.sessions[token] = sd
			s.mu.Unlock()
			return sd, true
		}
	}
	return SessionData{}, false
}

func (s *SessionStore) Revoke(ctx context.Context, token string) {
	s.mu.Lock()
	delete(s.sessions, token)
	s.mu.Unlock()

	if s.repo != nil {
		dbCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
		defer cancel()
		_ = s.repo.DeleteSession(dbCtx, token)
	}
}

// DistributedRateLimiter uses Redis sliding fixed-window counters across replicas,
// falling back to a TTL-bounded in-memory map (preventing OOM leaks).
type DistributedRateLimiter struct {
	rdb     *redis.Client
	metrics *telemetry.Metrics

	mu        sync.Mutex
	buckets   map[string]*memBucket
	lastSweep time.Time
}

type memBucket struct {
	tokens     int
	lastRefill time.Time
}

func NewDistributedRateLimiter(rdb *redis.Client, metrics *telemetry.Metrics) *DistributedRateLimiter {
	return &DistributedRateLimiter{
		rdb:       rdb,
		metrics:   metrics,
		buckets:   make(map[string]*memBucket),
		lastSweep: time.Now(),
	}
}

func (l *DistributedRateLimiter) Allow(ctx context.Context, key string, limit int, window time.Duration) bool {
	if l.rdb != nil {
		redisKey := fmt.Sprintf("rl:%s:%d", key, time.Now().Unix()/int64(window.Seconds()))
		pipe := l.rdb.TxPipeline()
		incr := pipe.Incr(ctx, redisKey)
		pipe.Expire(ctx, redisKey, window+time.Second)
		if _, err := pipe.Exec(ctx); err == nil {
			return incr.Val() <= int64(limit)
		}
	}
	return l.allowMemory(key, limit, window)
}

func (l *DistributedRateLimiter) allowMemory(key string, maxTokens int, window time.Duration) bool {
	l.mu.Lock()
	defer l.mu.Unlock()

	now := time.Now()
	// Periodic TTL eviction & hard cap prevents unbounded OOM map growth
	if now.Sub(l.lastSweep) > 2*time.Minute || len(l.buckets) > 10000 {
		for k, b := range l.buckets {
			if now.Sub(b.lastRefill) > 10*time.Minute {
				delete(l.buckets, k)
			}
		}
		l.lastSweep = now
	}

	b, ok := l.buckets[key]
	if !ok {
		l.buckets[key] = &memBucket{tokens: maxTokens - 1, lastRefill: now}
		return true
	}

	refillRate := float64(maxTokens) / window.Seconds()
	added := int(now.Sub(b.lastRefill).Seconds() * refillRate)
	if added > 0 {
		b.tokens = min(maxTokens, b.tokens+added)
		b.lastRefill = now
	}
	if b.tokens > 0 {
		b.tokens--
		return true
	}
	return false
}

func (l *DistributedRateLimiter) Middleware(prefix string, limit int, window time.Duration) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ip := r.RemoteAddr
			if fwd := r.Header.Get("X-Forwarded-For"); fwd != "" {
				ip = strings.TrimSpace(strings.Split(fwd, ",")[0])
			} else if idx := strings.LastIndex(ip, ":"); idx != -1 {
				ip = ip[:idx]
			}

			if !l.Allow(r.Context(), prefix+":"+ip, limit, window) {
				if l.metrics != nil {
					l.metrics.RateLimitRejections.Add(1)
				}
				w.Header().Set("Retry-After", fmt.Sprintf("%d", int(window.Seconds())))
				respond.Error(w, r, http.StatusTooManyRequests, "Rate limit exceeded. Please slow down.")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RequestID injects/propagates X-Request-Id across context and response headers.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		reqID := strings.TrimSpace(r.Header.Get("X-Request-Id"))
		if reqID == "" {
			reqID = uuid.New().String()
		}
		w.Header().Set("X-Request-Id", reqID)
		ctx := telemetry.WithRequestID(r.Context(), reqID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// RequestTelemetry emits structured JSON request logs via slog and records Prometheus metrics.
func RequestTelemetry(logger *slog.Logger, metrics *telemetry.Metrics) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
			next.ServeHTTP(ww, r)

			status := ww.Status()
			if status == 0 {
				status = http.StatusOK
			}
			latencyMs := time.Since(start).Milliseconds()
			if metrics != nil {
				metrics.RecordRequest(r.Method, r.URL.Path, status, latencyMs)
			}
			if logger != nil && r.URL.Path != "/metrics" {
				logger.Info("http_request",
					"request_id", telemetry.RequestIDFromContext(r.Context()),
					"method", r.Method,
					"path", r.URL.Path,
					"status", status,
					"bytes", ww.BytesWritten(),
					"latency_ms", latencyMs,
					"remote_ip", r.RemoteAddr,
				)
			}
		})
	}
}

// MaxBodySize limits incoming payload bytes to guard against memory exhaustion.
func MaxBodySize(maxBytes int64) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
			next.ServeHTTP(w, r)
		})
	}
}

// RequireAuth validates Bearer session tokens using the injected SessionStore.
func RequireAuth(store *SessionStore) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token, err := ExtractBearerToken(r)
			if err != nil {
				respond.Error(w, r, http.StatusUnauthorized, err.Error())
				return
			}
			session, valid := store.Get(r.Context(), token)
			if !valid {
				respond.Error(w, r, http.StatusUnauthorized, "Invalid or expired session token")
				return
			}
			ctx := context.WithValue(r.Context(), UserContextKey, session)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// RequireAdmin checks constant-time ADMIN_SECRET verification.
func RequireAdmin(adminSecret string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if adminSecret == "" {
				respond.Error(w, r, http.StatusForbidden, "Admin operations disabled: ADMIN_SECRET not configured")
				return
			}
			token := strings.TrimSpace(r.Header.Get("X-Admin-Secret"))
			if token == "" {
				token, _ = ExtractBearerToken(r)
			}
			if token == "" || subtle.ConstantTimeCompare([]byte(token), []byte(adminSecret)) != 1 {
				respond.Error(w, r, http.StatusUnauthorized, "Unauthorized: invalid admin credentials")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func ExtractBearerToken(r *http.Request) (string, error) {
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	if authHeader == "" {
		return "", fmt.Errorf("Authorization header required (Bearer <token>)")
	}
	parts := strings.SplitN(authHeader, " ", 2)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return "", fmt.Errorf("Malformed authorization header: expected 'Bearer <token>'")
	}
	tok := strings.TrimSpace(parts[1])
	if tok == "" {
		return "", fmt.Errorf("Empty bearer token provided")
	}
	return tok, nil
}
