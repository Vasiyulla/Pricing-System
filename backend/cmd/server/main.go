package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"pricebridge/backend/internal/api"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	log.Printf("----------------------------------------------------------------")
	log.Printf("Starting Price Bridge Go Backend (UK Retail & AI Engine)")
	log.Printf("Target Market: United Kingdom (London, GBP £ / pence)")
	log.Printf("----------------------------------------------------------------")

	// 1. Initialize Storage with London Supermarket Seed Data
	memStore := repository.NewMemoryStore()
	storeRepo := memStore.StoreRepo()
	prodRepo := memStore.ProductRepo()
	priceRepo := memStore.PriceRepo()
	bountyRepo := memStore.BountyRepo()
	stickerRepo := memStore.YellowStickerRepo()

	log.Printf("Initialized in-memory store with 10 London Supermarkets and 25 Staple Products")

	// 2. Initialize Services
	compareSvc := service.NewCompareService(storeRepo, priceRepo)
	optimizerSvc := service.NewOptimizerService(storeRepo, priceRepo)
	trolleySvc := service.NewTrolleyService(storeRepo, prodRepo, priceRepo)
	aiSvc := service.NewAIService()

	// 3. Initialize API Handler & Chi Router
	handler := api.NewAPIHandler(
		storeRepo, prodRepo, priceRepo, bountyRepo, stickerRepo,
		compareSvc, optimizerSvc, trolleySvc, aiSvc,
	)
	router := api.NewRouter(handler)

	// 4. Start HTTP Server
	server := &http.Server{
		Addr:         ":" + port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("Price Bridge API Server is listening on http://localhost:%s", port)
		log.Printf("Endpoints:")
		log.Printf("  - Health:              http://localhost:%s/health", port)
		log.Printf("  - Nearby Stores:       http://localhost:%s/api/v1/stores/nearby", port)
		log.Printf("  - Product Search:      http://localhost:%s/api/v1/products/search?q=milk", port)
		log.Printf("  - Compare Prices:      http://localhost:%s/api/v1/products/prod_lurpak/compare", port)
		log.Printf("  - Trolley Ear Whisper: http://localhost:%s/api/v1/products/prod_lurpak/trolley-whisper", port)
		log.Printf("  - Basket Optimizer:    http://localhost:%s/api/v1/basket/optimize", port)
		log.Printf("  - AI Bounties:         http://localhost:%s/api/v1/bounties/nearby", port)
		log.Printf("  - AI Receipt Scan:     http://localhost:%s/api/v1/ai/receipt-scan", port)

		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v", err)
		}
	}()

	// 5. Graceful Shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Printf("Shutting down Price Bridge API Server...")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}
	log.Printf("Server exited gracefully.")
}
