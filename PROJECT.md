# Project: Price Bridge Enterprise Production Hardening

## Architecture
- **Android Client (`app/src/main/java/com/example/`)**: Native Kotlin + Jetpack Compose, single-module architecture with M3 design tokens.
  - `core/ui/`: TopAppBar (Location + Search/Scan), BottomBar, CategoryPillStrip, ProductDealCard, PriceStatusBadgeComponent.
  - `core/data/local/`: Room SQLite database (`PriceBridgeDatabase`) caching deals, products, stores, and queued submissions.
  - `core/data/offline/`: `OfflineSubmissionManager` and `PriceSyncWorker` (WorkManager) for background synchronization with idempotency keys.
  - `core/network/`: Retrofit + Moshi API client communicating with Go backend.
  - `feature/`: Feature screens with MVVM architecture (`home`, `search`, `scan`, `basket`, `profile`, `productdetail`, `store`, `submit`, `yellowsticker`, `bounty`, `trolley`).
- **Go Backend (`backend/`)**: High-performance REST service built on Go Chi.
  - `cmd/server/main.go`: Entrypoint initializing PostgreSQL connection pool, Redis client connection, and API router.
  - `internal/api/`: HTTP handlers with CORS, structured JSON responses, idempotency header handling, and admin authorization middleware.
  - `internal/repository/`: PostgreSQL + PostGIS repository (`GEOGRAPHY(Point, 4326)` with `ST_DWithin` and `ST_Distance` radius queries), append-only `price_records` with `superseded_by` pointer and `idempotency_key` unique index, transactional `outbox_events`.
  - `internal/service/`: Business logic services:
    - `CompareService`: Multi-layer caching (Redis geohash keys `geo:<lat>:<lon>:prod:<id>:r:<radius>` with TTL 300s) protected by `singleflight.Group` to prevent cache stampedes under high concurrency.
    - `OptimizerService`: Basket optimization and Pareto split.
    - `TrolleyService`: In-store audio price comparison.
- **Data & Caching Layer**:
  - PostgreSQL 16 + PostGIS: Relational spatial store for stores, products, price ledger, outbox events.
  - Redis Cluster / Upstash: Distributed geohash cache for price comparison with SingleFlight stampede guard.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Zero APK Secrets | Remove `secrets-gradle-plugin` and eliminate `.env` backend secrets from Android `BuildConfig.java` | Milestone 1 | Survey 3 & R2 |
| 2 | Admin API Security Guard | Protect `/api/v1/admin/db/reset` and sensitive admin endpoints behind authorization token | Milestone 1 | Survey 3 & R2 |
| 3 | HomeScreen UI Decluttering | Remove `ShoppingIntentHero`, `HeroSavingsCard`, `ai_superpowers_strip`, and `KarmaContributionCard` from `HomeScreen` | Milestone 2 | Survey 1 & R1 |
| 4 | Consumer Sequence Flow | Organize `HomeScreen` strictly into Location -> Search/Scan -> Categories -> Nearby Stores -> Best Deals comparison | Milestone 2 | Survey 1 & R1 |
| 5 | Secondary Features Relocation | Relocate Baskets & 2-Stop Split to `BasketScreen`, Yellow Stickers to Categories/Search, Bounties & Karma to `ProfileScreen` and `ScanScreen` | Milestone 2 | Survey 1 & R1 |
| 6 | Go Redis Integration | Add `github.com/redis/go-redis/v9` to `backend/go.mod` and initialize connection pool in `main.go` | Milestone 3 | Survey 2 & R2 |
| 7 | Redis SingleFlight Caching | Wrap Redis geohash cache miss in `singleflight.Group` for concurrency stampede protection with TTL and cache invalidation | Milestone 3 | Survey 2 & R2 |
| 8 | PostGIS Spatial Completeness | Fix `pgStoreRepo.GetNearby` to scan all store fields (`operating_hours`, `phone`, `reliability_score`, `is_confirmed_partner`) and add `ST_DWithin` to bounties | Milestone 3 | Survey 2 & R2 |
| 9 | Backend Idempotency Handling | Add `IdempotencyKey` to Go domain models, API handlers, and `INSERT INTO price_records ... ON CONFLICT (idempotency_key) DO NOTHING` | Milestone 3 | Survey 2 & R2 |
| 10 | Append-Only Ledger Pointer | Update `superseded_by` pointer on prior price records in transaction upon new price submission | Milestone 3 | Survey 2 & AGENTS.md |
| 11 | Backend Health Probe | Implement live PostgreSQL and Redis ping in `/health` endpoint | Milestone 3 | Survey 2 & R2 |
| 12 | WorkManager Dependency & Worker | Add `androidx.work:work-runtime-ktx` and implement `PriceSyncWorker` with network constraints and retry backoff | Milestone 4 | Survey 3 & R2 |
| 13 | End-to-End Idempotency Plumbing | Thread idempotency key through Android Room entity, Moshi DTO, Retrofit `@Header("Idempotency-Key")`, and WorkManager | Milestone 4 | Survey 3 & R2 |
| 14 | Room SQLite Offline Caching | Expand Room database to cache products, stores, and prices for offline access | Milestone 4 | Survey 3 & R2 |
| 15 | Compilation & Forensic Verification | Verify clean compilation across Android and Go backend with zero warnings and forensic integrity audit | Milestone 5 | AC & Policy |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Security, Secret Removal & Config Hardening | Strip secrets-gradle-plugin from Android, ensure zero APK secrets, protect admin endpoints | none | DONE |
| M2 | Mobile UI Decluttering & Consumer Flow | Declutter HomeScreen.kt, enforce Location -> Search/Scan -> Categories -> Comparison, relocate secondary features | none | DONE |
| M3 | Go Backend Hardening (PostGIS, Redis, Idempotency) | Add go-redis, implement Redis SingleFlight caching, PostGIS completeness, idempotency, superseded_by | M1 | DONE |
| M4 | Android Offline Resilience & WorkManager Sync | Add WorkManager, implement PriceSyncWorker, Room catalog caching, plumb idempotency key | M2, M3 | DONE |
| M5 | Integration, Clean Compilation & Audit Gate | Clean compilation check (no tests per policy), end-to-end integration, forensic integrity verification | M1, M2, M3, M4 | DONE |

## Interface Contracts
### Android Client ↔ Go Backend API
- **Base URL**: Configured via `BuildConfig.BACKEND_BASE_URL` (default: `https://pricing-system-4nhl.onrender.com/` or local `http://10.0.2.2:8080/`).
- **Submit Price**:
  - `POST /api/v1/prices`
  - Headers: `Content-Type: application/json`, `Idempotency-Key: <UUID>`
  - Request Body:
    ```json
    {
      "product_id": "string",
      "store_id": "string",
      "regular_price_pence": 150,
      "loyalty_price_pence": 130,
      "loyalty_scheme": "Clubcard",
      "source_type": "receipt_scan",
      "idempotency_key": "string"
    }
    ```
  - Response: `201 Created` with `PriceRecord` object or `200 OK` if duplicate idempotent submission.
- **Batch Sync Prices (Offline Queue)**:
  - `POST /api/v1/prices/batch`
  - Headers: `Content-Type: application/json`
  - Request Body: array of price submission objects with `idempotency_key`.
- **Compare Product Prices**:
  - `GET /api/v1/products/{id}/compare?lat={lat}&lon={lon}&radius_km={radius}`
  - Response: Cached in Redis under key `geo:<quantLat>:<quantLon>:prod:<id>:r:<radius>` with SingleFlight coalescing.
- **Stores Near Me**:
  - `GET /api/v1/stores/nearby?lat={lat}&lon={lon}&radius_km={radius}`
  - Uses PostGIS `ST_DWithin(geom, ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography, radiusMeters)`.
- **Admin Database Reset**:
  - `POST /api/v1/admin/db/reset`
  - Headers: `Authorization: Bearer <ADMIN_SECRET>`
  - Rejects unauthorized requests with `401 Unauthorized` or `403 Forbidden`.

## Code Layout
- Android App: `app/src/main/java/com/example/`
  - `core/ui/`: UI components and design system
  - `core/data/local/`: Room SQLite database, DAOs, entities
  - `core/data/offline/`: WorkManager workers, sync manager
  - `core/network/`: Retrofit client, Moshi models
  - `feature/home/`: HomeScreen, ViewModel, contracts
  - `feature/basket/`: BasketScreen, TwoStopBasketScreen
  - `feature/profile/`: ProfileScreen, karma, bounties
  - `feature/yellowsticker/`: YellowStickerScreen
  - `feature/scan/`: ScanScreen, ML Kit scanner
- Backend: `backend/`
  - `cmd/server/main.go`: Entrypoint
  - `internal/api/`: Handlers, router, middleware
  - `internal/domain/`: Core models
  - `internal/repository/`: Postgres, PostGIS, schema
  - `internal/service/`: CompareService, OptimizerService, Redis cache
