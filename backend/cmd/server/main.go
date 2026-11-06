package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"

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

	// 1. Initialize Storage (PostgreSQL 16 + PostGIS if DATABASE_URL is set, else Memory fallback)
	var storeRepo repository.StoreRepository
	var prodRepo repository.ProductRepository
	var priceRepo repository.PriceRepository
	var bountyRepo repository.BountyRepository
	var stickerRepo repository.YellowStickerRepository
	var categoryRepo repository.CategoryRepository

	var pgStore *repository.PostgresStore
	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL != "" {
		log.Printf("DATABASE_URL detected. Connecting to PostgreSQL 16 + PostGIS...")
		var err error
		pgStore, err = repository.NewPostgresStore(context.Background(), databaseURL)
		if err != nil {
			log.Fatalf("Fatal: Failed to connect to PostgreSQL: %v", err)
		}
		defer pgStore.Close()

		storeRepo = pgStore.StoreRepo()
		prodRepo = pgStore.ProductRepo()
		priceRepo = pgStore.PriceRepo()
		bountyRepo = pgStore.BountyRepo()
		stickerRepo = pgStore.YellowStickerRepo()
		categoryRepo = pgStore.CategoryRepo()
		log.Printf("Storage Engine: PostgreSQL 16 + PostGIS with persistent append-only ledger")
	} else {
		log.Printf("DATABASE_URL not set. Initializing in-memory store...")
		memStore := repository.NewMemoryStore()
		storeRepo = memStore.StoreRepo()
		prodRepo = memStore.ProductRepo()
		priceRepo = memStore.PriceRepo()
		bountyRepo = memStore.BountyRepo()
		stickerRepo = memStore.YellowStickerRepo()
		categoryRepo = memStore.CategoryRepo()
		log.Printf("Storage Engine: In-memory store (10 London Supermarkets, 25 Staple Products)")
	}

	// 2. Initialize Redis Client (Distributed Geohash Cache & Concurrency Stampede Guard)
	var rdb *redis.Client
	redisURL := os.Getenv("REDIS_URL")
	if redisURL != "" {
		log.Printf("REDIS_URL detected. Connecting to Redis...")
		opt, err := redis.ParseURL(redisURL)
		if err != nil {
			log.Printf("[Redis] Warning: Failed to parse REDIS_URL: %v (falling back to in-memory caching)", err)
		} else {
			opt.PoolSize = 25
			opt.MinIdleConns = 5
			opt.DialTimeout = 5 * time.Second
			opt.ReadTimeout = 3 * time.Second
			opt.WriteTimeout = 3 * time.Second

			client := redis.NewClient(opt)
			pingCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
			if err := client.Ping(pingCtx).Err(); err != nil {
				cancel()
				client.Close()
				log.Printf("[Redis] Warning: Redis ping check failed: %v (falling back to in-memory caching)", err)
			} else {
				cancel()
				rdb = client
				defer rdb.Close()
				log.Printf("[Redis] Connected successfully to Redis at %s (SingleFlight geohash cache active)", opt.Addr)
			}
		}
	} else {
		log.Printf("REDIS_URL not set. Running with local in-memory spatial caching.")
	}

	// 3. Initialize Services
	compareSvc := service.NewCompareService(storeRepo, priceRepo, rdb)
	optimizerSvc := service.NewOptimizerService(storeRepo, priceRepo)
	trolleySvc := service.NewTrolleyService(storeRepo, prodRepo, priceRepo)
	aiSvc := service.NewAIService()
	storeVerifier := service.NewStoreVerifier(storeRepo)

	// 4. Initialize API Handler & Chi Router
	handler := api.NewAPIHandler(
		storeRepo, prodRepo, priceRepo, bountyRepo, stickerRepo, categoryRepo,
		compareSvc, optimizerSvc, trolleySvc, aiSvc, storeVerifier,
	)
	if pgStore != nil {
		handler.SetHealthProbes(pgStore.Ping, rdb)
	} else {
		handler.SetHealthProbes(nil, rdb)
	}
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
