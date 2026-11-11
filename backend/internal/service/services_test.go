package service

import (
	"context"
	"errors"
	"testing"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/telemetry"
)

func TestProductService_CreateAndGetUserProducts(t *testing.T) {
	ctx := context.Background()
	mem := repository.NewMemoryStore()
	metrics := telemetry.NewMetrics()
	svc := NewProductService(mem.ProductRepo(), nil, telemetry.NewLogger("local"), metrics)

	p1 := domain.Product{
		ID:              "prod_strawberry_1",
		Name:            "Fresh Strawberries 400g",
		Brand:           "BerryFarm",
		Category:        "Fresh Produce",
		PackageSize:     "400g",
		UnitMetric:      "100g",
		TargetMRP:       2.50,
		CreatedByUserID: "user_123",
		IdempotencyKey:  "idem_straw_1",
	}
	if err := svc.CreateProduct(ctx, p1); err != nil {
		t.Fatalf("CreateProduct failed: %v", err)
	}

	// Duplicate idempotency key should not create a duplicate product
	pDup := p1
	pDup.ID = "prod_strawberry_dup"
	if err := svc.CreateProduct(ctx, pDup); err != nil {
		t.Fatalf("idempotent CreateProduct failed: %v", err)
	}

	userProds, err := svc.GetUserAddedProducts(ctx, "user_123", 50, 0)
	if err != nil {
		t.Fatalf("GetUserAddedProducts failed: %v", err)
	}
	if len(userProds) != 1 || userProds[0].ID != "prod_strawberry_1" {
		t.Fatalf("expected 1 product prod_strawberry_1, got %+v", userProds)
	}

	otherProds, err := svc.GetUserAddedProducts(ctx, "user_other", 50, 0)
	if err != nil {
		t.Fatalf("GetUserAddedProducts for other user failed: %v", err)
	}
	if otherProds == nil || len(otherProds) != 0 {
		t.Fatalf("expected non-nil empty slice for other user, got %+v", otherProds)
	}
}

func TestProductService_SearchNonNilEmpty(t *testing.T) {
	ctx := context.Background()
	mem := repository.NewMemoryStore()
	svc := NewProductService(mem.ProductRepo(), nil, nil, nil)

	res, err := svc.SearchProducts(ctx, "nonexistent_unicorn_item_xyz", "")
	if err != nil {
		t.Fatalf("SearchProducts failed: %v", err)
	}
	if res == nil {
		t.Fatalf("expected non-nil empty slice for empty search result")
	}
	if len(res) != 0 {
		t.Fatalf("expected 0 results, got %d", len(res))
	}
}

func TestAIService_ParseReceiptNoKeysFailsLoudly(t *testing.T) {
	svc := &AIService{}
	_, err := svc.ParseReceiptImage(context.Background(), "ZmFrZV9iYXNlNjQ=")
	if !errors.Is(err, ErrVisionAINotConfigured) {
		t.Fatalf("expected ErrVisionAINotConfigured when no API keys exist, got %v", err)
	}
}

func TestTrolleyAndOptimizer_NoCamdenOrEmptyFail(t *testing.T) {
	ctx := context.Background()
	mem := repository.NewMemoryStore()

	opt := NewOptimizerService(mem.StoreRepo(), mem.PriceRepo())
	// Outside seeded pilot area (Gujarat coordinates) should return error, not Camden Town
	_, err := opt.OptimizeTwoStopBasket(ctx, []domain.BasketItem{{ProductID: "prod_whole_milk", Quantity: 1}}, 20.82, 72.98)
	if err == nil {
		t.Fatalf("expected error when no tracked stores exist near (20.82, 72.98)")
	}

	// Single-query TrolleyWhisper inside seeded area should compute SKIP when cheaper nearby exists
	_ = mem.PriceRepo().SubmitPrice(ctx, domain.PriceRecord{
		ID:                "pr_test_waitrose",
		ProductID:         "prod_lurpak",
		StoreID:           "store_waitrose_bloomsbury",
		RegularPricePence: 475,
		FreshnessStatus:   domain.FreshnessRecentlyObserved,
		SourceType:        "shelf_label",
	})
	_ = mem.PriceRepo().SubmitPrice(ctx, domain.PriceRecord{
		ID:                "pr_test_lidl",
		ProductID:         "prod_lurpak",
		StoreID:           "store_lidl_tottenham",
		RegularPricePence: 375,
		FreshnessStatus:   domain.FreshnessRecentlyObserved,
		SourceType:        "shelf_label",
	})

	trolley := NewTrolleyService(mem.StoreRepo(), mem.ProductRepo(), mem.PriceRepo())
	whisper, err := trolley.GenerateWhisper(ctx, "prod_lurpak", "store_waitrose_bloomsbury", 51.5240, -0.1245)
	if err != nil {
		t.Fatalf("GenerateWhisper failed: %v", err)
	}
	if whisper == nil || whisper.ProductID != "prod_lurpak" || whisper.Recommendation != "SKIP" {
		t.Fatalf("expected SKIP recommendation for prod_lurpak, got %+v", whisper)
	}
}

