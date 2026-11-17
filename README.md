# Price Bridge 🏷️

> **Hyperlocal, Crowdsourced, and Moderated Retail Price Comparison Platform**  
> *Answers one question with cryptographic and spatial integrity: "Where can I buy what I need at the best reliable nearby price?"*

[![Android](https://img.shields.io/badge/Platform-Android%20(Kotlin%20%2B%20Compose)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Go Backend](https://img.shields.io/badge/Backend-Go%201.22%2B-00ADD8?style=flat&logo=go&logoColor=white)](https://go.dev)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL%2016%20%2B%20PostGIS-336791?style=flat&logo=postgresql&logoColor=white)](https://postgis.net)
[![Redis](https://img.shields.io/badge/Cache-Redis%207%20(SingleFlight)-DC382D?style=flat&logo=redis&logoColor=white)](https://redis.io)
[![Docker](https://img.shields.io/badge/Infra-Docker%20Compose-2496ED?style=flat&logo=docker&logoColor=white)](https://docker.com)


---

## 📋 Table of Contents

- [System Overview](#-system-overview)
- [Architecture & Tech Stack](#-architecture--tech-stack)
- [Core Engineering Pillars](#-core-engineering-pillars)
- [Key Features](#-key-features)
- [System Architecture Diagram](#-system-architecture-diagram)
- [Directory Layout](#-directory-layout)
- [Getting Started & Local Setup](#-getting-started--local-setup)
  - [Prerequisites](#prerequisites)
  - [1. Environment Configuration](#1-environment-configuration)
  - [2. Running via Docker Compose (Recommended)](#2-running-via-docker-compose-recommended)
  - [3. Running Backend Services Locally](#3-running-backend-services-locally)
  - [4. Building the Android Application](#4-building-the-android-application)
- [API Reference & Contracts](#-api-reference--contracts)
- [Data Model & Ledger Mechanics](#-data-model--ledger-mechanics)
- [Security, Privacy & Legal Posture](#-security-privacy--legal-posture)
- [Contributing & Development Policy](#-contributing--development-policy)

---

## 🔍 System Overview

Price Bridge eliminates price opacity across grocery chains, local bodegas, and convenience stores. Rather than relying on unreliable web scraping or unverified crowd data:
- **Append-Only Price Ledger**: Every price observation is recorded immutably with timestamp, source confidence, and a forward pointer to its replacement (`superseded_by`).
- **Spatial Precision (PostGIS)**: Real-time queries filter stores by geospatial boundary (`GEOGRAPHY(Point, 4326)`), routing distance, and foot/drive durations.
- **Offline-First Resilience**: Shoppers with flaky supermarket cellular reception can scan barcodes and record prices offline; submissions queue locally in Room SQLite and sync in the background via Android WorkManager with idempotent retry guarantees.
- **Cache Stampede Guard**: High-throughput comparisons utilize Redis geohashed keys backed by Go `singleflight.Group` request coalescing.

---

## 🛠️ Architecture & Tech Stack

### 1. Mobile Client (Android)
- **Language & UI:** 100% Kotlin with **Jetpack Compose** (Declarative Material 3 design tokens).
- **Architecture:** Unidirectional Data Flow (MVI/MVVM) with StateFlow and Coroutines.
- **Local Persistence:** **Room SQLite** for caching catalog feeds, store metadata, and the offline submission outbox.
- **Background Sync:** **WorkManager** with exponential backoff and network-metered constraints.
- **Computer Vision:** **CameraX** paired with **Google ML Kit Barcode Scanning** (EAN-13, EAN-8, UPC-A, UPC-E, QR).
- **Location Engine:** **FusedLocationProviderClient** with dynamic walk/drive route distance matrix calculations.
- **Networking:** Retrofit 2 + OkHttp 4 + Moshi, featuring custom interceptors for Bearer tokens and Idempotency keys.

### 2. Backend Services (Go)
- **Runtime:** Go 1.22+ on Chi HTTP router.
- **Database:** **PostgreSQL 16** with **PostGIS 3.4** spatial extensions.
- **Caching:** **Redis 7** utilizing quantized geohash keys (`geo:<lat>:<lon>:prod:<id>:r:<radius>`).
- **Resilience:** Concurrency coalescing via `golang.org/x/sync/singleflight`, database connection pooling, and transactional outbox.
- **Security:** Strict RBAC middleware, JWT authentication, rate limiting, and zero hardcoded secrets.

### 3. AI / Multimodal Worker (Python)
- **Framework:** FastAPI service running off the critical path.
- **Capabilities:** Receipt OCR parsing, shelf-tag verification, perceptual image deduplication, and LLM-grounded price extraction.

---

## ⚙️ Core Engineering Pillars

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Android Client (Compose)                      │
│   [ML Kit Scanner] ── [Room Outbox Queue] ── [Fused Location Manager]  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTPS (Bearer Auth + Idempotency)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Go REST API Gateway                             │
│     [Correlation ID] ── [Rate Limiter] ── [RBAC & Auth Middleware]     │
└──────────────┬────────────────────┬────────────────────┬───────────────┘
               │                    │                    │
               ▼                    ▼                    ▼
     ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
     │  PostgreSQL 16   │ │     Redis 7      │ │ Python AI Worker │
     │  - PostGIS GIST  │ │ - SingleFlight   │ │ - Receipt OCR    │
     │  - Append-Only   │ │ - Geohash TTL    │ │ - Tag Validation │
     │  - Outbox Events │ │ - Stampede Guard │ │ - Deduplication  │
     └──────────────────┘ └──────────────────┘ └──────────────────┘
```

1. **Zero-Lock Idempotency**: All write requests require an `Idempotency-Key` UUID. Retried network requests return the original record without generating duplicate ledger entries.
2. **Append-Only Price Auditing**: Prices are never updated in-place (`UPDATE ... SET price = ...`). When a new price is observed, a new row is inserted, and the previous record's `superseded_by` column is updated inside a single atomic transaction.
3. **Cache Stampede Prevention**: If 1,000 users in the same neighborhood request price comparisons for Milk simultaneously when the Redis cache expires, Go's `singleflight` guarantees only **one** SQL query executes against PostGIS; the remaining 999 requests share the result.
4. **Spatial Deduplication**: When users register community stores, a 50-meter PostGIS spatial proximity check prevents creating duplicate store entities for the same physical merchant.

---

## 🚀 Key Features

| Feature | Surface | Description |
|---|---|---|
| **Hyperlocal Price Feed** | Android / Go | Renders nearby deals ranked by walking/driving distance, price freshness, and community confidence. |
| **Barcode Scanner** | Android | High-performance CameraX viewfinder scanning EAN-13/UPC barcodes with haptic feedback. |
| **Community Store Add** | Android / Go | Add missing local groceries directly from the UI with 50-meter spatial boundary deduplication. |
| **Offline Price Outbox** | Android | Submit prices with zero cellular coverage; Room + WorkManager syncs automatically when reconnected. |
| **2-Stop Pareto Basket** | Android / Go | Analyzes shopping lists to calculate whether splitting items between two nearby stores saves money. |
| **AI Price Copilot** | Android / AI | Natural language pricing assistant strictly grounded against database catalog facts. |
| **Bounties & Karma** | Android / Go | Community rewards prompting shoppers to verify stale prices on nearby shelf tags. |
| **Yellow-Sticker Feed** | Android / Go | Real-time clearance feed tracking expiring items and markdowns. |
| **The Trolley Ear** | Android | Hands-free audio assistance while pushing a trolley down the aisle. |

---

## 📁 Directory Layout

```text
Pricing-System/
├── app/                                 # Native Android Application
│   ├── src/main/java/com/example/
│   │   ├── core/                        # Shared Core Modules
│   │   │   ├── data/                    # Repositories, Room SQLite, WorkManager
│   │   │   ├── location/                # DeviceLocationManager & GPS tracking
│   │   │   ├── model/                   # Domain data models & entities
│   │   │   ├── network/                 # Retrofit, ApiClient, DTOs
│   │   │   └── ui/                      # Material 3 theme, components, bottom bar
│   │   ├── feature/                     # Feature Screens (MVI / MVVM)
│   │   │   ├── basket/                  # Shopping basket & 2-Stop Pareto optimizer
│   │   │   ├── bounty/                  # Verification bounties & Karma quests
│   │   │   ├── copilot/                 # Grounded AI pricing copilot
│   │   │   ├── home/                    # Dynamic deals feed, categories, nearby stores
│   │   │   ├── productdetail/           # Price history graph, store breakdown
│   │   │   ├── profile/                 # User reputation, saved lists, settings
│   │   │   ├── scan/                    # CameraX + ML Kit barcode engine
│   │   │   ├── store/                   # Store profile, inventory, directions
│   │   │   ├── submit/                  # Price reporting & community store creation
│   │   │   └── yellowsticker/           # Clearance and markdown deal tracker
│   │   └── MainActivity.kt              # Navigation Host & deep linking
│   └── build.gradle.kts                 # Android Gradle configuration
├── backend/                             # High-Performance Go Backend
│   ├── cmd/server/main.go               # Application entrypoint & dependency injection
│   ├── internal/
│   │   ├── domain/                      # Domain entities (PriceRecord, Store, Product)
│   │   ├── repository/                  # PostgreSQL + PostGIS queries & Redis client
│   │   ├── service/                     # Business logic (Comparison, Optimizer, Auth)
│   │   └── transport/http/              # HTTP routers, middleware, and REST handlers
│   ├── migrations/                      # Up/Down SQL migrations (0001_initial to 0004)
│   ├── Dockerfile                       # Production multi-stage Go container
│   ├── go.mod & go.sum                  # Go dependency specifications
│   └── Makefile                         # Build, migrate, test, and run scripts
├── ai_worker/                           # Python Multimodal AI Service (FastAPI)
│   ├── app/                             # OCR, image deduplication, LLM endpoints
│   ├── Dockerfile                       # Python container definition
│   └── requirements.txt                 # FastAPI, Uvicorn, Pillow, PyTorch dependencies
├── scripts/                             # Utility & Database Reset Scripts
├── docker-compose.yml                   # Complete local orchestration
├── AGENTS.md                            # Architecture roadmap & development guidelines
└── PROJECT.md                           # Milestone breakdown & acceptance criteria
```

---

## 🏁 Getting Started & Local Setup

### Prerequisites
- **Docker & Docker Compose** (v20.10+)
- **Go** (1.22 or higher)
- **Android Studio** (Ladybug / Jellyfish) or **Android SDK Command-Line Tools** (API Level 34, Build Tools 34.0.0)
- **JDK 17 or 21**
- **Git**

---

### 1. Environment Configuration

Clone the repository and copy the example environment file:

```bash
git clone https://github.com/your-org/Price-Bridge.git
cd Price-Bridge
cp .env.example .env
```

Key environment variables:
```dotenv
# Backend Configuration
PORT=8080
DATABASE_URL=postgres://pricebridge:secretpass@localhost:5432/pricebridge?sslmode=disable
REDIS_URL=redis://localhost:6379/0
JWT_SECRET=your-super-secure-jwt-secret-key-32-bytes
ADMIN_SECRET_TOKEN=pricebridge-dev-admin-secret-change-in-prod

# Android Client
BACKEND_BASE_URL=http://10.0.2.2:8080/ # Use 10.0.2.2 for Android Emulator, or LAN IP for physical device
```

---

### 2. Running via Docker Compose (Recommended)

Spin up the entire stack (PostgreSQL + PostGIS, Redis 7, Go Backend, and Python AI Worker) in one command:

```bash
docker-compose up --build -d
```

Verify service status:
```bash
docker-compose ps
```

Health check verification:
```bash
curl -i http://localhost:8080/health
# HTTP/1.1 200 OK
# {"status":"ok","database":"connected","redis":"connected"}
```

---

### 3. Running Backend Services Locally

If you prefer running the Go backend natively while keeping Postgres and Redis in Docker:

```bash
# Start Postgres & Redis only
docker-compose up -d postgres redis

# Run Database Migrations
cd backend
go run cmd/server/main.go
```

The Go HTTP server binds to `http://localhost:8080`.

---

### 4. Building the Android Application

#### Via Android Studio:
1. Open the project root in Android Studio.
2. Allow Gradle sync to complete using JDK 17+.
3. Select the `app` run configuration and click **Run** on an emulator or physical device.

#### Via Gradle Command Line:
```bash
# Compile debug APK
./gradlew assembleDebug

# On Windows PowerShell:
.\gradlew assembleDebug
```

The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📡 API Reference & Contracts

All requests are prefixed with `/api/v1`.

### Core Endpoints

| Method | Endpoint | Description | Headers |
|---|---|---|---|
| `GET` | `/health` | Health probe (database & cache status) | None |
| `POST` | `/api/v1/auth/register` | Register new customer or contributor | None |
| `POST` | `/api/v1/auth/login` | Authenticate user, receive JWT | None |
| `GET` | `/api/v1/stores/nearby` | Spatial query for stores within radius | `lat`, `lon`, `radius_km` |
| `POST` | `/api/v1/stores` | Register new community store (50m deduplicated) | `Authorization: Bearer <token>` |
| `GET` | `/api/v1/products/search` | Search product catalog by title or category | `q`, `category` |
| `GET` | `/api/v1/products/barcode/{code}` | Fast barcode lookup (EAN-13/UPC) | None |
| `GET` | `/api/v1/products/{id}/compare` | Ranked nearby price comparison (Cached) | `lat`, `lon`, `radius_km` |
| `POST` | `/api/v1/prices` | Submit price observation (Append-Only) | `Idempotency-Key: <UUID>`, `Bearer <token>` |
| `POST` | `/api/v1/prices/batch` | Flush queued offline submissions | `Authorization: Bearer <token>` |
| `POST` | `/api/v1/optimizer/basket` | 2-Stop Pareto basket multi-store optimizer | `Authorization: Bearer <token>` |
| `GET` | `/api/v1/bounties/active` | Active price verification bounties | `lat`, `lon` |

---

## 🗄️ Data Model & Ledger Mechanics

### Append-Only Price Flow

```sql
CREATE TABLE price_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    store_id UUID NOT NULL REFERENCES stores(id),
    user_id UUID REFERENCES users(id),
    regular_price_pence INT NOT NULL,
    loyalty_price_pence INT,
    source_type VARCHAR(32) NOT NULL,
    confidence_score FLOAT NOT NULL DEFAULT 0.5,
    idempotency_key VARCHAR(64) UNIQUE,
    superseded_by UUID REFERENCES price_records(id),
    observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- When a new price is recorded at store $S$ for product $P$:
  1. An `INSERT INTO price_records` is performed.
  2. The previously active record for $(S, P)$ receives `UPDATE price_records SET superseded_by = NEW_ID`.
  3. Historical records are preserved for auditing, inflation tracking, and price trend analysis.

---


---

