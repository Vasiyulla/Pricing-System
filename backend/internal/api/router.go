package api 

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

// AdminAuthMiddleware ensures only authorized callers with valid admin credentials can access admin routes.
func AdminAuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if ok, status, msg := CheckAdminAuthorization(r); !ok {
			writeError(w, status, msg)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func NewRouter(h *APIHandler) *chi.Mux {
	r := chi.NewRouter()

	// Standard middleware
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

	// Cybersecurity Hardening: Enforce 10MB global body limit and IP rate limiting (60 req burst, 10/sec refill)
	r.Use(MaxBodySizeMiddleware(10 * 1024 * 1024))
	r.Use(RateLimitMiddleware(60, 10))

	// Cross-Origin Resource Sharing (CORS)
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"*"},
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token", "Idempotency-Key"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	// Health Check
	r.Get("/health", h.Health)

	// API V1
	r.Route("/api/v1", func(r chi.Router) {
		// Stores
		r.Route("/stores", func(r chi.Router) {
			r.Get("/nearby", h.GetNearbyStores)
			r.Get("/verify", h.VerifyStore)
			r.Post("/verify", h.VerifyStore)
			r.Get("/{id}", h.GetStoreByID)
			r.With(RequireAuth).Post("/{id}/report-discrepancy", h.ReportStoreDiscrepancy)
		})

		// Products & Barcodes
		r.Route("/products", func(r chi.Router) {
			r.Get("/deals", h.GetBestDealsBatch)
			r.Get("/search", h.SearchProducts)
			r.Get("/barcode/{ean}", h.GetProductByBarcode)
			r.Get("/{id}", h.GetProductByID)
			r.Get("/{id}/compare", h.CompareProductPrices)
			r.Get("/{id}/trolley-whisper", h.GetTrolleyWhisper)
		})

		// Authentication (Neon PostgreSQL Auth & Google)
		r.Route("/auth", func(r chi.Router) {
			r.Post("/sign-in", h.NeonEmailSignIn)
			r.Post("/sign-up", h.NeonEmailSignUp)
			r.Post("/google", h.GoogleAuth)
		})

		// Price Submissions & Outbox (Protected by Session Auth)
		r.Route("/prices", func(r chi.Router) {
			r.With(RequireAuth).Post("/submit", h.SubmitPrice)
			r.With(RequireAuth).Post("/batch-sync", h.BatchSyncPrices)
		})

		// 2-Stop Pareto Basket Optimizer
		r.Post("/basket/optimize", h.OptimizeBasket)

		// AI Bounty Hunter (Claims require authentication)
		r.Route("/bounties", func(r chi.Router) {
			r.Get("/nearby", h.GetNearbyBounties)
			r.Get("/prompt", h.GetActiveBountyPrompt)
			r.With(RequireAuth).Post("/{id}/claim", h.ClaimBounty)
		})

		// Categories
		r.Get("/categories", h.GetCategories)

		// Yellow Sticker Deals
		r.Get("/yellow-stickers/active", h.GetActiveYellowStickers)

		// AI Multimodal Ingestion (Receipts & Stickers) with 20MB limit for high-res images
		r.Route("/ai", func(r chi.Router) {
			r.Use(MaxBodySizeMiddleware(20 * 1024 * 1024))
			r.Post("/receipt-scan", h.ScanReceiptAI)
			r.Post("/yellow-sticker-scan", h.ScanYellowStickerAI)
		})

		// Admin & Testing Controls (Secured with Admin Authentication Guard)
		r.Route("/admin", func(r chi.Router) {
			r.Use(AdminAuthMiddleware)
			r.Post("/db/reset", h.ResetDatabase)
		})
	})

	return r
}

