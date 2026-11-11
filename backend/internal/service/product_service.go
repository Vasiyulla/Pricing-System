package service

import (
	"context"
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
	"golang.org/x/sync/singleflight"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/telemetry"
)

const (
	userProductsCacheTTL   = 5 * time.Minute
	productSearchCacheTTL  = 2 * time.Minute
	userProductsKeyPrefix  = "user:products:"
	productSearchKeyPrefix = "products:search:"
)

// ProductService orchestrates catalog reads/writes with Redis read-through caching,
// singleflight stampede protection, cache invalidation, and structured audit telemetry.
type ProductService struct {
	productRepo  repository.ProductRepository
	rdb          *redis.Client
	logger       *slog.Logger
	metrics      *telemetry.Metrics
	requestGroup singleflight.Group
}

func NewProductService(
	productRepo repository.ProductRepository,
	rdb *redis.Client,
	logger *slog.Logger,
	metrics *telemetry.Metrics,
) *ProductService {
	return &ProductService{
		productRepo: productRepo,
		rdb:         rdb,
		logger:      logger,
		metrics:     metrics,
	}
}

// CreateProduct persists a product idempotently and invalidates user & search caches.
func (s *ProductService) CreateProduct(ctx context.Context, product domain.Product) error {
	if err := s.productRepo.Create(ctx, product); err != nil {
		return err
	}

	s.InvalidateUserProductsCache(ctx, product.CreatedByUserID)
	s.InvalidateSearchCache(ctx)

	if s.logger != nil {
		s.logger.Info("product_created",
			"request_id", telemetry.RequestIDFromContext(ctx),
			"user_id", product.CreatedByUserID,
			"product_id", product.ID,
			"name", product.Name,
			"category", product.Category,
		)
	}
	return nil
}

// GetUserAddedProducts returns products created or price-contributed by a user using
// Redis read-through caching + singleflight request coalescing.
func (s *ProductService) GetUserAddedProducts(ctx context.Context, userID string, limit, offset int) ([]domain.Product, error) {
	if userID == "" {
		return []domain.Product{}, nil
	}
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	cacheKey := fmt.Sprintf("%s%s:%d:%d", userProductsKeyPrefix, userID, limit, offset)
	if s.rdb != nil {
		if raw, err := s.rdb.Get(ctx, cacheKey).Result(); err == nil {
			var cached []domain.Product
			if json.Unmarshal([]byte(raw), &cached) == nil {
				if s.metrics != nil {
					s.metrics.CacheHits.Add(1)
				}
				return cached, nil
			}
		}
		if s.metrics != nil {
			s.metrics.CacheMisses.Add(1)
		}
	}

	val, err, _ := s.requestGroup.Do(cacheKey, func() (interface{}, error) {
		prods, repoErr := s.productRepo.GetByCreator(ctx, userID, limit, offset)
		if repoErr != nil {
			return nil, repoErr
		}
		if prods == nil {
			prods = []domain.Product{}
		}
		if s.rdb != nil {
			if data, marshalErr := json.Marshal(prods); marshalErr == nil {
				_ = s.rdb.Set(ctx, cacheKey, data, userProductsCacheTTL).Err()
			}
		}
		return prods, nil
	})
	if err != nil {
		return nil, err
	}
	return val.([]domain.Product), nil
}

// SearchProducts queries the catalog via Redis + singleflight coalescing and guarantees a non-nil slice.
func (s *ProductService) SearchProducts(ctx context.Context, query, category string) ([]domain.Product, error) {
	normQuery := strings.ToLower(strings.TrimSpace(query))
	normCat := strings.TrimSpace(category)
	sum := sha1.Sum([]byte(normQuery + "|" + normCat))
	cacheKey := productSearchKeyPrefix + hex.EncodeToString(sum[:12])

	if s.rdb != nil {
		if raw, err := s.rdb.Get(ctx, cacheKey).Result(); err == nil {
			var cached []domain.Product
			if json.Unmarshal([]byte(raw), &cached) == nil {
				if s.metrics != nil {
					s.metrics.CacheHits.Add(1)
				}
				return cached, nil
			}
		}
		if s.metrics != nil {
			s.metrics.CacheMisses.Add(1)
		}
	}

	val, err, _ := s.requestGroup.Do(cacheKey, func() (interface{}, error) {
		prods, repoErr := s.productRepo.Search(ctx, query, category)
		if repoErr != nil {
			return nil, repoErr
		}
		if prods == nil {
			prods = []domain.Product{}
		}
		if s.rdb != nil {
			if data, marshalErr := json.Marshal(prods); marshalErr == nil {
				_ = s.rdb.Set(ctx, cacheKey, data, productSearchCacheTTL).Err()
			}
		}
		return prods, nil
	})
	if err != nil {
		return nil, err
	}
	return val.([]domain.Product), nil
}

// InvalidateUserProductsCache clears cached product contribution pages for a specific user.
func (s *ProductService) InvalidateUserProductsCache(ctx context.Context, userID string) {
	if s.rdb == nil || userID == "" {
		return
	}
	pattern := fmt.Sprintf("%s%s:*", userProductsKeyPrefix, userID)
	iter := s.rdb.Scan(ctx, 0, pattern, 100).Iterator()
	for iter.Next(ctx) {
		_ = s.rdb.Del(ctx, iter.Val()).Err()
	}
}

// InvalidateSearchCache clears cached search results after catalog mutations.
func (s *ProductService) InvalidateSearchCache(ctx context.Context) {
	if s.rdb == nil {
		return
	}
	iter := s.rdb.Scan(ctx, 0, productSearchKeyPrefix+"*", 200).Iterator()
	for iter.Next(ctx) {
		_ = s.rdb.Del(ctx, iter.Val()).Err()
	}
}

