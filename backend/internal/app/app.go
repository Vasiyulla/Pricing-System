package app

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/redis/go-redis/v9"

	"pricebridge/backend/internal/cache"
	"pricebridge/backend/internal/client"
	"pricebridge/backend/internal/config"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/repository/storage"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/telemetry"
	httptransport "pricebridge/backend/internal/transport/http"
	"pricebridge/backend/internal/transport/http/handler"
	"pricebridge/backend/internal/transport/http/middleware"
)

type repositories struct {
	store       repository.StoreRepository
	product     repository.ProductRepository
	price       repository.PriceRepository
	bounty      repository.BountyRepository
	sticker     repository.YellowStickerRepository
	category    repository.CategoryRepository
	session     repository.SessionRepository
	evidence    repository.EvidenceRepository
	discrepancy repository.DiscrepancyRepository
	outbox      repository.OutboxRepository
	pgPing      func(ctx context.Context) error
	closeFn     func()
}

func initRepositories(ctx context.Context, cfg *config.Config, logger *slog.Logger) (*repositories, error) {
	if cfg.DatabaseURL != "" {
		logger.Info("connecting to PostgreSQL + PostGIS")
		pgStore, err := repository.NewPostgresStore(ctx, cfg.DatabaseURL)
		if err != nil {
			return nil, err
		}
		return &repositories{
			store:       pgStore.StoreRepo(),
			product:     pgStore.ProductRepo(),
			price:       pgStore.PriceRepo(),
			bounty:      pgStore.BountyRepo(),
			sticker:     pgStore.YellowStickerRepo(),
			category:    pgStore.CategoryRepo(),
			session:     pgStore.SessionRepo(),
			evidence:    pgStore.EvidenceRepo(),
			discrepancy: pgStore.DiscrepancyRepo(),
			outbox:      pgStore.OutboxRepo(),
			pgPing:      pgStore.Ping,
			closeFn:     pgStore.Close,
		}, nil
	}

	logger.Warn("DATABASE_URL not set; initializing in-memory store")
	mem := repository.NewMemoryStore()
	return &repositories{
		store:       mem.StoreRepo(),
		product:     mem.ProductRepo(),
		price:       mem.PriceRepo(),
		bounty:      mem.BountyRepo(),
		sticker:     mem.YellowStickerRepo(),
		category:    mem.CategoryRepo(),
		session:     mem.SessionRepo(),
		evidence:    mem.EvidenceRepo(),
		discrepancy: mem.DiscrepancyRepo(),
		outbox:      mem.OutboxRepo(),
		closeFn:     func() {},
	}, nil
}

func initRedis(cfg *config.Config, logger *slog.Logger) *redis.Client {
	if cfg.RedisURL == "" {
		return nil
	}
	opt, err := redis.ParseURL(cfg.RedisURL)
	if err != nil {
		logger.Warn("invalid REDIS_URL; falling back to local cache", "error", err)
		return nil
	}
	opt.PoolSize = 25
	opt.MinIdleConns = 5
	opt.DialTimeout = 5 * time.Second
	opt.ReadTimeout = 3 * time.Second
	opt.WriteTimeout = 3 * time.Second

	rdb := redis.NewClient(opt)
	pingCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	if err := rdb.Ping(pingCtx).Err(); err != nil {
		_ = rdb.Close()
		logger.Warn("Redis ping failed; falling back to local cache", "error", err)
		return nil
	}
	logger.Info("connected to Redis", "addr", opt.Addr)
	return rdb
}

// RunAPI runs the HTTP API Server together with a managed in-process Outbox Worker.
func RunAPI() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}
	logger := telemetry.NewLogger(cfg.Env)
	metrics := telemetry.NewMetrics()

	rootCtx, cancelRoot := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancelRoot()

	repos, err := initRepositories(rootCtx, cfg, logger)
	if err != nil {
		logger.Error("database initialization failed", "error", err)
		os.Exit(1)
	}
	defer repos.closeFn()

	rdb := initRedis(cfg, logger)
	if rdb != nil {
		defer rdb.Close()
	}

	var geoCache *cache.GeoSpatialCache
	if rdb != nil {
		geoCache = cache.NewGeoSpatialCache(rdb)
		if err := geoCache.PurgeSyntheticStores(rootCtx); err != nil {
			logger.Warn("failed to purge synthetic live stores from Redis", "error", err)
		}
	}

	routeSvc := service.NewRouteService(cfg.RoutingProvider, cfg.OSRMBaseURL, cfg.GoogleMapsAPIKey, rdb)
	compareSvc := service.NewCompareService(repos.store, repos.price, rdb)
	compareSvc.SetRouteService(routeSvc)
	optimizerSvc := service.NewOptimizerService(repos.store, repos.price, routeSvc)
	trolleySvc := service.NewTrolleyService(repos.store, repos.product, repos.price)
	aiSvc := service.NewAIService()
	storeVerifier := service.NewStoreVerifier(repos.store, repos.discrepancy)
	objectStore := storage.NewObjectStore(cfg.S3Endpoint, cfg.S3Bucket, cfg.LocalUploadDir)

	outboxWorker := service.NewOutboxWorker(repos.outbox, repos.session, compareSvc, logger, metrics)
	outboxWorker.Start(rootCtx)

	var bgWG sync.WaitGroup
	sessionStore := middleware.NewSessionStore(repos.session)
	rateLimiter := middleware.NewDistributedRateLimiter(rdb, metrics)

	storeHandler := handler.NewStoreHandler(
		repos.store, repos.product, repos.price, repos.bounty, repos.sticker,
		storeVerifier, geoCache, routeSvc, &bgWG,
	)

	router := httptransport.NewRouter(
		cfg, logger, metrics, sessionStore, rateLimiter,
		httptransport.Handlers{
			Health:   handler.NewHealthHandler(repos.pgPing, rdb, metrics),
			Auth:     handler.NewAuthHandler(client.NewNeonAuthClient(cfg.NeonAuthURL), client.NewGoogleOAuthClient(), repos.session, sessionStore),
			Store:    storeHandler,
			Product:  handler.NewProductHandler(repos.product, repos.price, repos.store, compareSvc, trolleySvc, storeHandler, routeSvc),
			Price:    handler.NewPriceHandler(repos.price, repos.product, repos.session, compareSvc),
			Basket:   handler.NewBasketHandler(optimizerSvc),
			Bounty:   handler.NewBountyHandler(repos.bounty, repos.category, repos.sticker),
			User:     handler.NewUserHandler(repos.session, repos.price),
			Evidence: handler.NewEvidenceHandler(repos.evidence, objectStore),
			AI:       handler.NewAIHandler(aiSvc),
			Admin:    handler.NewAdminHandler(repos.store, compareSvc, storeHandler, logger),
		},
	)

	server := &http.Server{
		Addr:         ":" + cfg.Port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		logger.Info("Price Bridge API server listening", "port", cfg.Port, "env", cfg.Env)
		if srvErr := server.ListenAndServe(); srvErr != nil && srvErr != http.ErrServerClosed {
			logger.Error("HTTP server crashed", "error", srvErr)
			os.Exit(1)
		}
	}()

	<-rootCtx.Done()
	logger.Info("shutdown signal received; draining HTTP connections and workers")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer shutdownCancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful HTTP shutdown timed out", "error", err)
	}

	outboxWorker.Wait()
	bgWG.Wait()
	logger.Info("Price Bridge API server exited cleanly")
}

// RunWorker executes the standalone Outbox Dispatcher & Session Cleanup Worker.
func RunWorker() {
	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "error", err)
		os.Exit(1)
	}
	logger := telemetry.NewLogger(cfg.Env)
	metrics := telemetry.NewMetrics()

	rootCtx, cancelRoot := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancelRoot()

	repos, err := initRepositories(rootCtx, cfg, logger)
	if err != nil {
		logger.Error("worker database initialization failed", "error", err)
		os.Exit(1)
	}
	defer repos.closeFn()

	rdb := initRedis(cfg, logger)
	if rdb != nil {
		defer rdb.Close()
	}

	compareSvc := service.NewCompareService(repos.store, repos.price, rdb)
	worker := service.NewOutboxWorker(repos.outbox, repos.session, compareSvc, logger, metrics)
	worker.Start(rootCtx)

	<-rootCtx.Done()
	worker.Wait()
	logger.Info("Price Bridge background worker exited cleanly")
}

