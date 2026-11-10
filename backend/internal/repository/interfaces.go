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
	ConfirmFreshness(ctx context.Context, productID, storeID, userID string) (int, error)
	GetAllPrices(ctx context.Context) ([]domain.PriceRecord, error)
	GetSubmissionsByUser(ctx context.Context, userID string) ([]domain.PriceRecord, error)
	GetBestDealsSingleQuery(ctx context.Context, lat, lon, radiusKm float64, category string) ([]domain.ProductDealSummary, error)
}

type BountyRepository interface {
	GetNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Bounty, error)
	Claim(ctx context.Context, bountyID string) error
	Create(ctx context.Context, bounty domain.Bounty) error
	GetActivePrompt(ctx context.Context) (*domain.BountyPrompt, error)
}

type CategoryRepository interface {
	GetAll(ctx context.Context) ([]domain.Category, error)
}

type YellowStickerRepository interface {
	GetActive(ctx context.Context, storeID string) ([]domain.YellowStickerDeal, error)
	Create(ctx context.Context, deal domain.YellowStickerDeal) error
}

type SessionRepository interface {
	UpsertUser(ctx context.Context, user domain.UserProfileRecord) (*domain.UserProfileRecord, error)
	GetUserProfile(ctx context.Context, userID string) (*domain.UserProfileRecord, error)
	IncrementUserStats(ctx context.Context, userID string, karmaDelta, reportedDelta, verifiedDelta int) error
	SaveSession(ctx context.Context, sess domain.AuthSessionRecord) error
	GetSession(ctx context.Context, token string) (*domain.AuthSessionRecord, error)
	DeleteSession(ctx context.Context, token string) error
	PurgeExpiredSessions(ctx context.Context) (int64, error)
}

type EvidenceRepository interface {
	SaveEvidence(ctx context.Context, ev domain.EvidenceRecord) (string, error)
}

type DiscrepancyRepository interface {
	RecordDiscrepancy(ctx context.Context, report *domain.StoreDiscrepancyReport) (int, error)
	UpdateStoreReliability(ctx context.Context, storeID string, score int) error
}

type OutboxRepository interface {
	FetchPending(ctx context.Context, limit int) ([]domain.OutboxEvent, error)
	MarkProcessed(ctx context.Context, id string) error
}

