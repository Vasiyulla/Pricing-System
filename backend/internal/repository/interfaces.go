package repository

import (
	"context"
	"pricebridge/backend/internal/domain"
)

type StoreRepository interface {
	GetByID(ctx context.Context, id string) (*domain.Store, error)
	GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Store, error)
	GetAll(ctx context.Context) ([]domain.Store, error)
	Create(ctx context.Context, store domain.Store) error
}

type ProductRepository interface {
	GetByID(ctx context.Context, id string) (*domain.Product, error)
	GetByBarcode(ctx context.Context, ean string) (*domain.Product, error)
	Search(ctx context.Context, query string, category string) ([]domain.Product, error)
	GetAll(ctx context.Context) ([]domain.Product, error)
	Create(ctx context.Context, product domain.Product) error
}

type PriceRepository interface {
	GetStorePrices(ctx context.Context, productID string) ([]domain.PriceRecord, error)
	GetLatestPrice(ctx context.Context, productID, storeID string) (*domain.PriceRecord, error)
	SubmitPrice(ctx context.Context, record domain.PriceRecord) error
	ConfirmFreshness(ctx context.Context, productID, storeID string) error
	GetAllPrices(ctx context.Context) ([]domain.PriceRecord, error)
}

type BountyRepository interface {
	GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Bounty, error)
	Claim(ctx context.Context, bountyID string) error
	Create(ctx context.Context, bounty domain.Bounty) error
}

type YellowStickerRepository interface {
	GetActive(ctx context.Context, storeID string) ([]domain.YellowStickerDeal, error)
	Create(ctx context.Context, deal domain.YellowStickerDeal) error
}

