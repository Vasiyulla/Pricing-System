package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/middleware"
)

func TestZeroCoordsReturnEmptyOrBadRequest(t *testing.T) {
	mem := repository.NewMemoryStore()
	routeSvc := service.NewRouteService("osrm", "https://router.project-osrm.org", "", nil)
	compareSvc := service.NewCompareService(mem.StoreRepo(), mem.PriceRepo())
	trolleySvc := service.NewTrolleyService(mem.StoreRepo(), mem.ProductRepo(), mem.PriceRepo())
	optSvc := service.NewOptimizerService(mem.StoreRepo(), mem.PriceRepo(), routeSvc)

	storeHandler := NewStoreHandler(mem.StoreRepo(), mem.ProductRepo(), mem.PriceRepo(), mem.BountyRepo(), mem.YellowStickerRepo(), nil, nil, routeSvc, nil)
	productHandler := NewProductHandler(mem.ProductRepo(), mem.PriceRepo(), mem.StoreRepo(), compareSvc, trolleySvc, storeHandler, routeSvc)
	bountyHandler := NewBountyHandler(mem.BountyRepo(), mem.CategoryRepo(), mem.YellowStickerRepo())
	basketHandler := NewBasketHandler(optSvc)
	aiHandler := NewAIHandler(&service.AIService{})

	// 1. stores/nearby?lat=0&lon=0 -> 200 []
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/v1/stores/nearby?lat=0&lon=0", nil)
	storeHandler.GetNearby(rec, req)
	if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != "[]" {
		t.Fatalf("expected 200 [] for stores/nearby at (0,0), got %d %q", rec.Code, rec.Body.String())
	}

	// 2. products/deals?lat=0&lon=0 -> 200 []
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/v1/products/deals?lat=0&lon=0", nil)
	productHandler.GetBestDealsBatch(rec, req)
	if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != "[]" {
		t.Fatalf("expected 200 [] for products/deals at (0,0), got %d %q", rec.Code, rec.Body.String())
	}

	// 3. bounties/nearby?lat=0&lon=0 -> 200 []
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/v1/bounties/nearby?lat=0&lon=0", nil)
	bountyHandler.GetNearby(rec, req)
	if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != "[]" {
		t.Fatalf("expected 200 [] for bounties/nearby at (0,0), got %d %q", rec.Code, rec.Body.String())
	}

	// 4. products/search?q=no_such_item -> 200 [] (never null)
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/v1/products/search?q=no_such_strawberry_xyz", nil)
	productHandler.Search(rec, req)
	if rec.Code != http.StatusOK || strings.TrimSpace(rec.Body.String()) != "[]" {
		t.Fatalf("expected 200 [] for empty products/search, got %d %q", rec.Code, rec.Body.String())
	}

	// 5. POST /basket/optimize with (0,0) -> 400 Bad Request
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/basket/optimize", bytes.NewBufferString(`{"items":[{"product_id":"prod_whole_milk","quantity":1}],"user_lat":0,"user_lon":0}`))
	basketHandler.Optimize(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for basket/optimize at (0,0), got %d", rec.Code)
	}

	// 6. POST /ai/receipt-scan without keys -> 503 Service Unavailable
	rec = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/ai/receipt-scan", bytes.NewBufferString(`{"image_base64":"dGVzdA=="}`))
	aiHandler.ScanReceipt(rec, req)
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("expected 503 for receipt-scan without keys, got %d %q", rec.Code, rec.Body.String())
	}
}

func TestUserProductsEndpoint_ReturnsUserCreatedProducts(t *testing.T) {
	mem := repository.NewMemoryStore()
	prodSvc := service.NewProductService(mem.ProductRepo(), nil, nil, nil)
	compareSvc := service.NewCompareService(mem.StoreRepo(), mem.PriceRepo())
	trolleySvc := service.NewTrolleyService(mem.StoreRepo(), mem.ProductRepo(), mem.PriceRepo())
	storeHandler := NewStoreHandler(mem.StoreRepo(), mem.ProductRepo(), mem.PriceRepo(), mem.BountyRepo(), mem.YellowStickerRepo(), nil, nil, nil, nil)
	productHandler := NewProductHandler(mem.ProductRepo(), mem.PriceRepo(), mem.StoreRepo(), compareSvc, trolleySvc, storeHandler, nil, prodSvc)
	userHandler := NewUserHandler(mem.SessionRepo(), mem.PriceRepo(), mem.ProductRepo(), prodSvc)

	sessCtx := context.WithValue(context.Background(), middleware.UserContextKey, middleware.SessionData{
		UserID:      "usr_test_1",
		Email:       "tester@pricebridge.org",
		DisplayName: "Tester",
		ExpiresAt:   time.Now().Add(time.Hour),
	})

	// Unauthenticated request fails 401
	recUnauth := httptest.NewRecorder()
	reqUnauth := httptest.NewRequest(http.MethodGet, "/api/v1/users/me/products", nil)
	userHandler.GetProducts(recUnauth, reqUnauth)
	if recUnauth.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 without session, got %d", recUnauth.Code)
	}

	// Create product as authenticated user
	createBody := `{"id":"prod_straw_usr1","name":"Strawberry Punnet 400g","category":"Fresh Produce","package_size":"400g","target_mrp_gbp":2.25}`
	recCreate := httptest.NewRecorder()
	reqCreate := httptest.NewRequest(http.MethodPost, "/api/v1/products", bytes.NewBufferString(createBody)).WithContext(sessCtx)
	reqCreate.Header.Set("Idempotency-Key", "idem_key_usr1")
	productHandler.Create(recCreate, reqCreate)
	if recCreate.Code != http.StatusCreated {
		t.Fatalf("expected 201 creating product, got %d: %s", recCreate.Code, recCreate.Body.String())
	}

	// Fetch /users/me/products as same user -> contains Strawberry Punnet 400g
	recGet := httptest.NewRecorder()
	reqGet := httptest.NewRequest(http.MethodGet, "/api/v1/users/me/products", nil).WithContext(sessCtx)
	userHandler.GetProducts(recGet, reqGet)
	if recGet.Code != http.StatusOK {
		t.Fatalf("expected 200 on /users/me/products, got %d", recGet.Code)
	}

	var prods []domain.Product
	if err := json.Unmarshal(recGet.Body.Bytes(), &prods); err != nil {
		t.Fatalf("failed parsing /users/me/products response: %v", err)
	}
	if len(prods) != 1 || prods[0].ID != "prod_straw_usr1" {
		t.Fatalf("expected [prod_straw_usr1], got %+v", prods)
	}
}

