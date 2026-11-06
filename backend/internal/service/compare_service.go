package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"golang.org/x/sync/singleflight"
	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

type spatialCacheEntry struct {
	comparisons []domain.StorePriceComparison
	expiresAt   time.Time
}

type CompareService struct {
	storeRepo    repository.StoreRepository
	priceRepo    repository.PriceRepository
	rdb          *redis.Client
	requestGroup singleflight.Group
	cacheMu      sync.RWMutex
	cache        map[string]spatialCacheEntry
}

func NewCompareService(storeRepo repository.StoreRepository, priceRepo repository.PriceRepository, rdb ...*redis.Client) *CompareService {
	var redisClient *redis.Client
	if len(rdb) > 0 {
		redisClient = rdb[0]
	}
	return &CompareService{
		storeRepo: storeRepo,
		priceRepo: priceRepo,
		rdb:       redisClient,
		cache:     make(map[string]spatialCacheEntry),
	}
}

// SetRedis updates or initializes the Redis client instance
func (s *CompareService) SetRedis(rdb *redis.Client) {
	s.rdb = rdb
}

// ClearCache clears both Redis geohash cache and in-memory spatial cache
func (s *CompareService) ClearCache(ctx ...context.Context) {
	c := context.Background()
	if len(ctx) > 0 && ctx[0] != nil {
		c = ctx[0]
	}

	if s.rdb != nil {
		iter := s.rdb.Scan(c, 0, "geo:*", 200).Iterator()
		for iter.Next(c) {
			_ = s.rdb.Del(c, iter.Val()).Err()
		}
	}

	s.cacheMu.Lock()
	defer s.cacheMu.Unlock()
	s.cache = make(map[string]spatialCacheEntry)
}

// InvalidateProductCache invalidates cached comparison entries across Redis and in-memory for a product
func (s *CompareService) InvalidateProductCache(ctx context.Context, productID, storeID string) error {
	// 1. Invalidate Redis keys matching product
	if s.rdb != nil {
		pattern := fmt.Sprintf("*prod:%s*", productID)
		iter := s.rdb.Scan(ctx, 0, pattern, 100).Iterator()
		for iter.Next(ctx) {
			_ = s.rdb.Del(ctx, iter.Val()).Err()
		}
		if err := iter.Err(); err != nil {
			log.Printf("[CompareService] Warning: Redis cache invalidation error: %v", err)
		}
	}

	// 2. Invalidate in-memory cache
	s.cacheMu.Lock()
	defer s.cacheMu.Unlock()
	prodMatch := fmt.Sprintf("prod:%s:", productID)
	for k := range s.cache {
		if strings.Contains(k, prodMatch) {
			delete(s.cache, k)
		}
	}
	return nil
}

// CompareProductAcrossStores fetches and ranks prices across nearby stores for a product,
// protected by spatial geohash caching and singleflight request coalescing to prevent thundering herd.
func (s *CompareService) CompareProductAcrossStores(ctx context.Context, productID string, userLat, userLon, radiusKm float64, userLoyaltySchemes map[domain.LoyaltyScheme]bool) ([]domain.StorePriceComparison, error) {
	// Quantize coordinates to ~1.1km grid cells (2 decimal places) for spatial cache sharing
	quantLat := math.Round(userLat*100) / 100
	quantLon := math.Round(userLon*100) / 100
	cacheKey := fmt.Sprintf("geo:%.2f:%.2f:prod:%s:r:%.1f", quantLat, quantLon, productID, radiusKm)

	// 1. Fast path: check distributed Redis cache first
	if s.rdb != nil {
		val, err := s.rdb.Get(ctx, cacheKey).Result()
		if err == nil && val != "" {
			var cachedComps []domain.StorePriceComparison
			if err := json.Unmarshal([]byte(val), &cachedComps); err == nil {
				return cachedComps, nil
			}
		}
	}

	// 2. Check in-memory spatial cache fallback
	s.cacheMu.RLock()
	if entry, found := s.cache[cacheKey]; found && time.Now().Before(entry.expiresAt) {
		s.cacheMu.RUnlock()
		return entry.comparisons, nil
	}
	s.cacheMu.RUnlock()

	// 3. Request coalescing via singleflight to prevent cache stampedes under high concurrency
	res, err, _ := s.requestGroup.Do(cacheKey, func() (interface{}, error) {
		// Double check Redis inside singleflight guard in case concurrent caller filled it
		if s.rdb != nil {
			val, err := s.rdb.Get(ctx, cacheKey).Result()
			if err == nil && val != "" {
				var cachedComps []domain.StorePriceComparison
				if err := json.Unmarshal([]byte(val), &cachedComps); err == nil {
					return cachedComps, nil
				}
			}
		}

		comps, err := s.computeComparison(ctx, productID, userLat, userLon, radiusKm, userLoyaltySchemes)
		if err != nil {
			return nil, err
		}

		// Save to Redis distributed cache with 300s TTL (5 minutes)
		if s.rdb != nil {
			if data, err := json.Marshal(comps); err == nil {
				_ = s.rdb.Set(ctx, cacheKey, data, 300*time.Second).Err()
			}
		}

		// Save to in-memory spatial cache with 5-minute TTL
		s.cacheMu.Lock()
		s.cache[cacheKey] = spatialCacheEntry{
			comparisons: comps,
			expiresAt:   time.Now().Add(5 * time.Minute),
		}
		s.cacheMu.Unlock()

		return comps, nil
	})

	if err != nil {
		return nil, err
	}
	return res.([]domain.StorePriceComparison), nil
}

func (s *CompareService) computeComparison(ctx context.Context, productID string, userLat, userLon, radiusKm float64, userLoyaltySchemes map[domain.LoyaltyScheme]bool) ([]domain.StorePriceComparison, error) {
	nearbyStores, err := s.storeRepo.GetNearby(ctx, userLat, userLon, radiusKm)
	if err != nil {
		return nil, err
	}

	prices, err := s.priceRepo.GetStorePrices(ctx, productID)
	if err != nil {
		return nil, err
	}

	storePriceMap := make(map[string]domain.PriceRecord)
	for _, p := range prices {
		storePriceMap[p.StoreID] = p
	}

	var comparisons []domain.StorePriceComparison

	for _, store := range nearbyStores {
		priceRec, exists := storePriceMap[store.ID]
		if !exists {
			continue
		}

		effectivePrice := priceRec.RegularPricePence
		// If user has the loyalty card and loyalty price exists, apply loyalty price
		if priceRec.LoyaltyPricePence != nil && userLoyaltySchemes[priceRec.LoyaltyScheme] {
			effectivePrice = *priceRec.LoyaltyPricePence
		}

		regGBP := formatPenceToGBP(priceRec.RegularPricePence)
		var loyGBP *string
		if priceRec.LoyaltyPricePence != nil {
			formatted := formatPenceToGBP(*priceRec.LoyaltyPricePence)
			loyGBP = &formatted
		}

		unitFormatted := fmt.Sprintf("%dp / %s", priceRec.UnitPricePence, "100g")
		observedAgo := timeAgo(priceRec.ObservedAt)

		comparisons = append(comparisons, domain.StorePriceComparison{
			Store:               store,
			RegularPricePence:   priceRec.RegularPricePence,
			RegularPriceGBP:     regGBP,
			LoyaltyPricePence:   priceRec.LoyaltyPricePence,
			LoyaltyPriceGBP:     loyGBP,
			LoyaltyScheme:       priceRec.LoyaltyScheme,
			EffectivePricePence: effectivePrice,
			UnitPricePence:      priceRec.UnitPricePence,
			UnitPriceFormatted:  unitFormatted,
			FreshnessStatus:     priceRec.FreshnessStatus,
			ObservedAgo:         observedAgo,
		})
	}

	// Sort by effective price ascending (cheapest first)
	sort.Slice(comparisons, func(i, j int) bool {
		return comparisons[i].EffectivePricePence < comparisons[j].EffectivePricePence
	})

	if len(comparisons) > 0 {
		comparisons[0].IsBestDeal = true
	}

	return comparisons, nil
}

func formatPenceToGBP(pence int) string {
	pounds := float64(pence) / 100.0
	return fmt.Sprintf("£%.2f", pounds)
}

func timeAgo(t time.Time) string {
	diff := time.Since(t)
	if diff.Hours() < 1 {
		mins := int(math.Max(1, diff.Minutes()))
		return fmt.Sprintf("%d mins ago", mins)
	}
	if diff.Hours() < 24 {
		return fmt.Sprintf("%d hours ago", int(diff.Hours()))
	}
	days := int(diff.Hours() / 24)
	return fmt.Sprintf("%d days ago", days)
}
