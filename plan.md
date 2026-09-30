# Price Bridge — Final Project Plan (v3, merged)

> Crowdsourced, admin-moderated price comparison. Never presents a community price as guaranteed.
> 
> **v3.1:** mobile app is now **native Kotlin (Android-first, Jetpack Compose)**. iOS is deferred.

**Changes vs. the new plan:** mobile switched from React Native to Kotlin, legal/privacy posture restored, Python ML worker added, Kafka deferred, freshness thresholds defined, seeding plan added, scope re-sequenced, Google OAuth verification started early.

---

## 1. Product

Answers one question: *"Where can I buy what I need at the best reliable nearby price?"*

- **Customer app:** search, optional barcode scan, manual product add, submit/correct prices, report discrepancies, shopping tasks, alerts.
- **Admin web:** moderation, catalog, stores, fraud, users, support, audit logs.
- **Store portal (later):** claim store, confirm prices, respond to disputes.

Core loop: choose location → search/scan/create task → compare nearby stores → confirm or correct a price → shared price graph improves for everyone nearby.

## 2. Price model

Every price carries: source, observation time, freshness, confidence, evidence status, store confirmation, contributor reputation, correction history. Price records are **append-only**; edits create a new row with a `superseded_by` pointer.

**Source types (default confidence, high → low):** store confirmation > checkout/receipt > shelf label > personal observation > verbal quote.

**Customer-facing statuses:** Community reported · Recently observed · Community corroborated (2+ independent users) · Store confirmed · Store-integrated · Price may be outdated · Flagged for review.

**Freshness thresholds (defaults, tune per category):**

| Category | Recently observed | Price may be outdated |
| --- | --- | --- |
| Staples, packaged goods | ≤ 7 days | > 14 days |
| Produce, dairy, bakery | ≤ 2 days | > 4 days |
| Electronics, durables | ≤ 14 days | > 30 days |

Outdated prices trigger a prompt ("still ₹120 at this store?") to the next nearby user viewing the item.

**Evidence policy:** images are untrusted input. Prefer in-app capture; OCR receipts/labels; compare product, store, price, date, pack size; detect reused images; AI-image detection is a risk signal only. Never approve or reject on an image alone. No receipt/barcode/photo is required; such reports start at lower confidence.

## 3. Shopping tasks (Phase 3)

One-time, saved, recurring, shared, and price-watch lists, with quantities, units, and alternatives. Store ranking uses availability, estimated basket total, distance, freshness, reliability, and missing items. Show best one-store and two-store combinations. Totals are **estimates**, never guarantees, unless store-confirmed or time-limited.

## 4. Google Tasks import (Phase 4)

Read-only scope, import only, no silent sync, user picks list and tasks, product match confirmed by user, disconnect anytime. **Start Google OAuth app verification in Phase 2** so it doesn't block launch.

## 5. Store discrepancy handling (Phase 4)

Before visiting: show price, last observed, source, confidence, store-confirmed status, guarantee status, and a "Check current price" request. After a mismatch, users report: price changed, offer expired, different amount charged, pack size differed, store refused, unavailable. Repeated *independent* discrepancies remove store-confirmed status, lower reliability, stop best-deal ranking, show a warning, and trigger operator review. One complaint never punishes a store.

## 6. Architecture

```text
Customer app ─┐
Admin web ────┼─ API Gateway ─ Customer/Admin/Store BFFs ─ Go services
Store portal ─┘                                            ├─ PostgreSQL + PostGIS (+ PgBouncer)
                                                           ├─ Redis
                                                           ├─ OpenSearch
                                                           ├─ Object storage (images)
                                                           └─ Async: outbox queue (→ Kafka in Phase 6)
                                                              └─ Python worker: OCR, image dedupe, fuzzy match, trust ML
```

Rules: frontends never touch databases; backend enforces permissions; OpenAPI contracts; API versioning; correlation IDs; idempotency keys on all submissions; audit events on every sensitive action.

**Reads vs. writes:** comparison reads hit Redis first (geohash-keyed), then Postgres. Submissions go Gateway → Ingestion → outbox → consumers (ledger, cache invalidation, moderation queue). OpenSearch failure must not break price comparison. Notification failure must not block submission.

## 7. Tech stack

| Layer | Choice |
| --- | --- |
| Mobile | Kotlin + Jetpack Compose (Android-first), MVVM, Hilt, Retrofit/OkHttp with an API client generated from OpenAPI, ML Kit barcode + CameraX, Room + WorkManager for offline queue, Fused Location (on-demand only), Coil for images, Firebase Cloud Messaging |
| Admin web | Next.js, TypeScript, MUI with custom theme, TanStack Query/Table, React Hook Form, Zod, Playwright, Vitest (types generated from the same OpenAPI spec) |
| iOS | Deferred until Android pilot succeeds. Keep networking and business logic in a UI-independent module so Kotlin Multiplatform is possible later |
| Backend | Go, REST/OpenAPI |
| ML/OCR worker | Python (FastAPI), off the hot path, Phase 5 |
| Data | PostgreSQL + PostGIS, Redis Cluster, OpenSearch, S3-compatible storage |
| Async | Postgres outbox + queue for MVP–V2; Kafka when replay/fan-out is needed (Phase 6) |
| Infra | Docker; Kubernetes (multi-AZ) from Phase 6, managed container service before that |
| Security | OAuth2/OIDC, MFA, RBAC, WAF, rate limiting, private DBs, short-lived sessions |
| Observability | OpenTelemetry, Prometheus, Grafana, structured logs, error tracking, queue-lag and freshness monitors |

## 8. Admin roles

Moderator · Catalog Operator · Store Ops · Fraud Analyst · Support · Platform Admin · Super Admin (roles, security policy, thresholds, integrations, bulk actions, emergency controls). The owner uses a normal admin account day to day.

## 9. Legal and privacy posture

- Store **names** as plain text; **logos** only via a claimed, store-uploaded profile.
- Admin role is **moderation**, not **certification** (keeps IT Act §79 intermediary posture).
- Badges: "Verified by community" (2+ corroborating users) vs. "Confirmed by \[Store\]" (only if the store confirmed). Never imply a guarantee the store didn't give.
- Location collected on demand only, DPDP-compliant consent copy, no background tracking.
- Receipts/images: consent at upload, retention limits, ability to delete, strip EXIF location.
- Google data: read-only, minimal scope, deletable on disconnect.

## 10. Reliability targets

1M requests/day (95% reads), \~250 rps sustained peak, 1,000 rps burst, 99.9% availability, single region multi-AZ. Required: cached reads survive write failures; stale reads up to 24h; durable queued writes; idempotent submissions; retry-safe consumers; dead-letter queues; request coalescing to prevent cache stampedes; PgBouncer; load test at 2–3× peak; alerts at 70% of every ceiling.

## 11. Phases

| Phase | Scope |
| --- | --- |
| **0** | Design system, user flows, admin IA, status/freshness rules, OpenAPI contracts, data model, security model |
| **1 MVP** | Login, location, search, manual product add, store selection, price submission, basic comparison, admin moderation, product/store management. **One city, one category, seeded data** |
| **2 Core quality** | Barcode scan, non-barcode products, freshness statuses, corrections, offline submissions, duplicate detection, store reliability tracking, **start Google OAuth verification** |
| **3 Shopping tasks** | Baskets, quantities, missing items, saved lists, price-watch, one/two-store recommendations |
| **4 Google + stores** | Google Tasks import, product-match confirmation, store portal, confirmation requests, discrepancy workflow |
| **5 Trust and fraud** | Reputation, evidence/OCR (Python worker), duplicate-image detection, fraud signals, coordinated-account detection, moderation prioritization |
| **6 Scale** | Kafka migration, load/chaos testing, multi-AZ, backup/restore drills, queue replay tools, runbooks, city-by-city rollout |

**Cold-start seeding (Phase 1):** admin-enter prices for \~100 staple products across 10–20 stores in the launch city before public release, so first users see value immediately.

---

## 12. Sprint plan — Phases 0 to 2 (2-week sprints, 1–2 developers; now 7 sprints)

**Sprint 1 — Foundation (Phase 0)**

- Define statuses, freshness thresholds, source-type confidence (Sections 2)
- Data model + migrations: users, products, stores, price_records (append-only), evidence
- OpenAPI spec for search, stores-near-me, submit price, admin moderation
- Design system tokens (mapped to a Compose theme) + 6 core mobile screens (home, search, product, compare, submit, profile)
- Android project skeleton: modules (app, core-network, core-data, feature-\*), Hilt, generated API client from OpenAPI, CI build
- Monorepo, CI, lint/test, environments
- *Done when:* specs reviewed, empty services deploy through CI.

**Sprint 2 — Backend core (Phase 1)**

- Auth (OIDC/JWT), roles, rate limiting
- Product + store services, PostGIS radius query
- Price ingestion with idempotency keys → outbox → ledger
- Comparison endpoint (Postgres first, Redis after)
- *Done when:* submit a price via API and see it in a ranked comparison.

**Sprint 3 — Mobile MVP, part 1 (Phase 1)**

- Compose theme and shared components, navigation, auth flow
- Location selection (runtime permission handling), search, product detail
- Loading, empty, and error states
- *Done when:* a user can sign in, pick a location, and search products on a real device.

**Sprint 3b — Mobile MVP, part 2 (Phase 1)** *(added for the Compose learning curve)*

- Manual product add, store select, price submit, comparison screen
- Room cache for last-viewed results
- *Done when:* full flow works end to end on a real Android device.

**Sprint 4 — Admin + launch prep (Phase 1)**

- Moderation queue, product/store CRUD, audit log
- Seed data import script, seed 100 products
- Legal copy: consent, terms, privacy
- Closed beta with \~20 users
- *Done when:* moderators clear a queue and beta users submit real prices.

**Sprint 5 — Data quality (Phase 2)**

- Barcode scan (ML Kit + CameraX), non-barcode/loose products, fuzzy duplicate detection
- Freshness statuses + "still ₹X?" prompts
- *Done when:* stale prices are visibly flagged and duplicates are caught at submit.

**Sprint 6 — Resilience (Phase 2)**

- Corrections workflow with history, offline submission queue (Room + WorkManager, idempotency keys reused on retry)
- Store reliability tracking (basic), Redis cache + stampede protection + PgBouncer
- Submit Google OAuth verification
- *Done when:* offline submissions sync, and killing Redis doesn't break reads.

**Exit criteria for Phase 2:** users submit useful prices without receipts/barcodes; stale prices are clear; ≥ N weekly active submitters in the pilot city (set N before launch, e.g. 50); moderation backlog under 24h.

---

## 13. Android-specific risks

- **AI-generated Compose code:** review state hoisting, lifecycle, and recomposition; generate one screen at a time from the spec.
- **Device fragmentation:** test on low-end devices (2–3 GB RAM) and Android 8+; budget for OEM battery-optimization killing background sync.
- **Camera/permissions:** handle denied, permanently denied, and revoked permissions for camera and location.
- **Play Store:** data-safety form, privacy policy URL, and location/camera permission justifications must match Section 9.



## 14. Success criteria

Customers submit useful prices without receipts or barcodes; stale prices are unmistakable; estimates are never presented as guarantees; discrepancies are easy to report; fraudulent evidence never auto-verifies; admins can resolve trust and catalog issues; Google import never modifies Google data; the system holds target traffic and recovers from tested failures; the app feels like a polished consumer product, not a generic dashboard.