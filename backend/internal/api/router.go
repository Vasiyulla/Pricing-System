package api

import (
	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

func NewRouter(h *APIHandler) *chi.Mux {
	r := chi.NewRouter()

	// Standard middleware
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Logger)
	r.Use(middleware.Recoverer)

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
			r.Get("/{id}", h.GetStoreByID)
		})

		// Products & Barcodes
		r.Route("/products", func(r chi.Router) {
			r.Get("/search", h.SearchProducts)
			r.Get("/barcode/{ean}", h.GetProductByBarcode)
			r.Get("/{id}", h.GetProductByID)
			r.Get("/{id}/compare", h.CompareProductPrices)
			r.Get("/{id}/trolley-whisper", h.GetTrolleyWhisper)
		})

		// Price Submissions & Outbox
		r.Route("/prices", func(r chi.Router) {
			r.Post("/submit", h.SubmitPrice)
			r.Post("/batch-sync", h.BatchSyncPrices)
		})

		// 2-Stop Pareto Basket Optimizer
		r.Post("/basket/optimize", h.OptimizeBasket)

		// AI Bounty Hunter
		r.Route("/bounties", func(r chi.Router) {
			r.Get("/nearby", h.GetNearbyBounties)
			r.Get("/prompt", h.GetActiveBountyPrompt)
			r.Post("/{id}/claim", h.ClaimBounty)
		})

		// Categories
		r.Get("/categories", h.GetCategories)

		// Yellow Sticker Deals
		r.Get("/yellow-stickers/active", h.GetActiveYellowStickers)

		// AI Multimodal Ingestion (Receipts & Stickers)
		r.Route("/ai", func(r chi.Router) {
			r.Post("/receipt-scan", h.ScanReceiptAI)
			r.Post("/yellow-sticker-scan", h.ScanYellowStickerAI)
		})
	})

	return r
}

