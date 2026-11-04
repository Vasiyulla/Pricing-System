package service

import (
	"context"
	"fmt"
	"math"
	"sort"
	"time"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

type CompareService struct {
	storeRepo repository.StoreRepository
	priceRepo repository.PriceRepository
}

func NewCompareService(storeRepo repository.StoreRepository, priceRepo repository.PriceRepository) *CompareService {
	return &CompareService{
		storeRepo: storeRepo,
		priceRepo: priceRepo,
	}
}

// CompareProductAcrossStores fetches and ranks prices across nearby stores for a product
func (s *CompareService) CompareProductAcrossStores(ctx context.Context, productID string, userLat, userLon, radiusKm float64, userLoyaltySchemes map[domain.LoyaltyScheme]bool) ([]domain.StorePriceComparison, error) {
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

