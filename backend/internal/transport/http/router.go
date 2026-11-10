package http

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"

	"pricebridge/backend/internal/config"
	"pricebridge/backend/internal/telemetry"
	"pricebridge/backend/internal/transport/http/handler"
	"pricebridge/backend/internal/transport/http/middleware"
)

type Handlers struct {
	Health   *handler.HealthHandler
	Auth     *handler.AuthHandler
	Store    *handler.StoreHandler
	Product  *handler.ProductHandler
	Price    *handler.PriceHandler
	Basket   *handler.BasketHandler
	Bounty   *handler.BountyHandler
	User     *handler.UserHandler
	Evidence *handler.EvidenceHandler
	AI       *handler.AIHandler
	Admin    *handler.AdminHandler
}

func NewRouter(
	cfg *config.Config,
	logger *slog.Logger,
	metrics *telemetry.Metrics,
	sessionStore *middleware.SessionStore,
	rateLimiter *middleware.DistributedRateLimiter,
	h Handlers,
) *chi.Mux {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(chimw.RealIP)
	r.Use(chimw.Recoverer)
	r.Use(middleware.RequestTelemetry(logger, metrics))
	r.Use(middleware.MaxBodySize(10 * 1024 * 1024))
	r.Use(rateLimiter.Middleware("global", 120, time.Minute))

	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   cfg.CORSAllowedOrigins,
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token", "Idempotency-Key", "X-Admin-Secret", "X-Request-Id"},
		ExposedHeaders:   []string{"Link", "X-Request-Id"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// Health & Prometheus Metrics
	r.Get("/health", h.Health.Health)
	r.Get("/metrics", h.Health.Metrics)

	// Serve locally stored EXIF-stripped evidence images when using disk fallback
	if cfg.S3Endpoint == "" {
		fileServer := http.StripPrefix("/media/evidence/", http.FileServer(http.Dir(cfg.LocalUploadDir)))
		r.Get("/media/evidence/*", func(w http.ResponseWriter, req *http.Request) {
			fileServer.ServeHTTP(w, req)
		})
	}

	r.Route("/api/v1", func(r chi.Router) {
		// Stores
		r.Route("/stores", func(r chi.Router) {
			r.Get("/nearby", h.Store.GetNearby)
			r.With(rateLimiter.Middleware("verify", 15, time.Minute)).Get("/verify", h.Store.Verify)
			r.With(rateLimiter.Middleware("verify", 15, time.Minute)).Post("/verify", h.Store.Verify)
			r.Get("/{id}", h.Store.GetByID)
			r.With(middleware.RequireAuth(sessionStore)).Post("/{id}/report-discrepancy", h.Store.ReportDiscrepancy)
		})

		// Products & Barcodes
		r.Route("/products", func(r chi.Router) {
			r.With(middleware.RequireAuth(sessionStore)).Post("/", h.Product.Create)
			r.Get("/deals", h.Product.GetBestDealsBatch)
			r.Get("/search", h.Product.Search)
			r.Get("/barcode/{ean}", h.Product.GetByBarcode)
			r.Get("/{id}", h.Product.GetByID)
			r.Get("/{id}/compare", h.Product.Compare)
			r.Get("/{id}/trolley-whisper", h.Product.TrolleyWhisper)
		})

		// Authentication
		r.Route("/auth", func(r chi.Router) {
			r.With(rateLimiter.Middleware("auth", 20, time.Minute)).Post("/sign-in", h.Auth.SignIn)
			r.With(rateLimiter.Middleware("auth", 15, time.Minute)).Post("/sign-up", h.Auth.SignUp)
			r.With(rateLimiter.Middleware("auth", 20, time.Minute)).Post("/google", h.Auth.GoogleAuth)
			r.Post("/logout", h.Auth.Logout)
		})

		// Authenticated User Profile & Submission History
		r.Route("/users/me", func(r chi.Router) {
			r.Use(middleware.RequireAuth(sessionStore))
			r.Get("/profile", h.User.GetProfile)
			r.Get("/submissions", h.User.GetSubmissions)
		})

		// Price Submissions & Freshness
		r.Route("/prices", func(r chi.Router) {
			r.Use(middleware.RequireAuth(sessionStore))
			r.Post("/submit", h.Price.Submit)
			r.Post("/batch-sync", h.Price.BatchSync)
			r.Post("/confirm-freshness", h.Price.ConfirmFreshness)
		})

		// EXIF-Stripped Evidence Storage
		r.Route("/evidence", func(r chi.Router) {
			r.Use(middleware.MaxBodySize(20 * 1024 * 1024))
			r.With(middleware.RequireAuth(sessionStore)).Post("/upload", h.Evidence.Upload)
		})

		// 2-Stop Pareto Basket Optimizer
		r.Post("/basket/optimize", h.Basket.Optimize)

		// Bounties, Categories & Yellow Stickers
		r.Route("/bounties", func(r chi.Router) {
			r.Get("/nearby", h.Bounty.GetNearby)
			r.Get("/prompt", h.Bounty.GetActivePrompt)
			r.With(middleware.RequireAuth(sessionStore)).Post("/{id}/claim", h.Bounty.Claim)
		})
		r.Get("/categories", h.Bounty.GetCategories)
		r.Get("/yellow-stickers/active", h.Bounty.GetActiveYellowStickers)

		// AI Multimodal Scanning (Strict Quota Shield: max 10 req/min per IP + 20MB cap)
		r.Route("/ai", func(r chi.Router) {
			r.Use(middleware.MaxBodySize(20 * 1024 * 1024))
			r.Use(rateLimiter.Middleware("ai", 10, time.Minute))
			r.Post("/receipt-scan", h.AI.ScanReceipt)
			r.Post("/yellow-sticker-scan", h.AI.ScanYellowSticker)
		})

		// Admin Controls
		r.Route("/admin", func(r chi.Router) {
			r.Use(middleware.RequireAdmin(cfg.AdminSecret))
			r.Post("/db/reset", h.Admin.ResetDatabase)
		})
	})

	return r
}

