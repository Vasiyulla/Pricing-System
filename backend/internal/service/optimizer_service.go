package service

import (
	"context"
	"fmt"
	"math"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

type OptimizerService struct {
	storeRepo repository.StoreRepository
	priceRepo repository.PriceRepository
	routeSvc  *RouteService
}

func NewOptimizerService(storeRepo repository.StoreRepository, priceRepo repository.PriceRepository, routeSvc ...*RouteService) *OptimizerService {
	var rs *RouteService
	if len(routeSvc) > 0 {
		rs = routeSvc[0]
	}
	return &OptimizerService{
		storeRepo: storeRepo,
		priceRepo: priceRepo,
		routeSvc:  rs,
	}
}

// OptimizeTwoStopBasket finds the best combination of two stores within walking distance
func (s *OptimizerService) OptimizeTwoStopBasket(ctx context.Context, items []domain.BasketItem, userLat, userLon float64) (*domain.TwoStopBasketResult, error) {
	if len(items) == 0 {
		return nil, fmt.Errorf("basket cannot be empty")
	}

	stores, err := s.storeRepo.GetNearby(ctx, userLat, userLon, 5.0) // 5km search radius
	if err != nil {
		return nil, err
	}
	if len(stores) == 0 {
		return nil, fmt.Errorf("no nearby tracked stores available for basket calculation")
	}

	productIDs := make([]string, 0, len(items))
	for _, item := range items {
		if item.ProductID != "" {
			productIDs = append(productIDs, item.ProductID)
		}
	}
	storeIDs := make([]string, 0, len(stores))
	for _, st := range stores {
		storeIDs = append(storeIDs, st.ID)
	}

	allPrices, err := s.priceRepo.GetLatestPricesForProductsAndStores(ctx, productIDs, storeIDs)
	if err != nil {
		return nil, err
	}

	// priceMap: productID -> storeID -> effectivePricePence
	priceMap := make(map[string]map[string]int)
	for _, p := range allPrices {
		if _, exists := priceMap[p.ProductID]; !exists {
			priceMap[p.ProductID] = make(map[string]int)
		}
		eff := p.RegularPricePence
		if p.LoyaltyPricePence != nil {
			eff = *p.LoyaltyPricePence
		}
		priceMap[p.ProductID][p.StoreID] = eff
	}

	// 1. Calculate Single-Store Basket Totals
	bestSinglePence := math.MaxInt32
	var bestSingleStore domain.Store

	for _, st := range stores {
		total := 0
		hasAll := true
		for _, item := range items {
			if price, ok := priceMap[item.ProductID][st.ID]; ok {
				total += price * item.Quantity
			} else {
				hasAll = false
				break
			}
		}
		if hasAll && total < bestSinglePence {
			bestSinglePence = total
			bestSingleStore = st
		}
	}

	// 2. Evaluate all 2-Store pairs within 1.5km walking distance of each other
	bestPairSavings := -1
	var result domain.TwoStopBasketResult

	for i := 0; i < len(stores); i++ {
		for j := i + 1; j < len(stores); j++ {
			s1 := stores[i]
			s2 := stores[j]

			straightKm := repository.HaversineDistanceKm(s1.Latitude, s1.Longitude, s2.Latitude, s2.Longitude)
			if straightKm > 1.5 { // Quick straight-line pre-filter before street routing
				continue
			}

			var walkingMeters, extraMins int
			if s.routeSvc != nil {
				pairRoute := s.routeSvc.GetPairRoute(ctx, "walking", s1, s2)
				walkingMeters = pairRoute.DistanceMeters
				extraMins = max(1, int(math.Ceil(float64(pairRoute.DurationSeconds)/60.0)))
				if walkingMeters > 2100 { // Max 2.1km real street walk between stops
					continue
				}
			} else {
				walkingMeters = int(straightKm * 1000)
				extraMins = int(math.Ceil(float64(walkingMeters) / 80.0))
			}

			s1Items := []domain.BasketItem{}
			s2Items := []domain.BasketItem{}
			s1Total := 0
			s2Total := 0
			canCoverAll := true

			for _, item := range items {
				p1, hasP1 := priceMap[item.ProductID][s1.ID]
				p2, hasP2 := priceMap[item.ProductID][s2.ID]

				if !hasP1 && !hasP2 {
					canCoverAll = false
					break
				}

				if hasP1 && (!hasP2 || p1 <= p2) {
					s1Items = append(s1Items, item)
					s1Total += p1 * item.Quantity
				} else {
					s2Items = append(s2Items, item)
					s2Total += p2 * item.Quantity
				}
			}

			if !canCoverAll {
				continue
			}

			combinedTotal := s1Total + s2Total
			savings := bestSinglePence - combinedTotal

			// We want substantial savings (£1.00+) and both stores utilized
			if savings > bestPairSavings && len(s1Items) > 0 && len(s2Items) > 0 {
				bestPairSavings = savings

				result = domain.TwoStopBasketResult{
					Store1:                s1,
					Store1Items:           s1Items,
					Store1TotalPence:      s1Total,
					Store1TotalGBP:        formatPenceToGBP(s1Total),
					Store2:                s2,
					Store2Items:           s2Items,
					Store2TotalPence:      s2Total,
					Store2TotalGBP:        formatPenceToGBP(s2Total),
					CombinedTotalPence:    combinedTotal,
					CombinedTotalGBP:      formatPenceToGBP(combinedTotal),
					SingleStoreBestStore:  bestSingleStore,
					SingleStoreBestPence:  bestSinglePence,
					SingleStoreBestGBP:    formatPenceToGBP(bestSinglePence),
					NetSavingsPence:       savings,
					NetSavingsGBP:         formatPenceToGBP(savings),
					WalkingDistanceMeters: walkingMeters,
					EstimatedExtraMinutes: extraMins,
				}
			}
		}
	}

	if bestPairSavings <= 0 {
		// Fallback: If no 2-stop pair beat the single store, return single store coverage
		return &domain.TwoStopBasketResult{
			Store1:               bestSingleStore,
			Store1Items:          items,
			Store1TotalPence:     bestSinglePence,
			Store1TotalGBP:       formatPenceToGBP(bestSinglePence),
			CombinedTotalPence:   bestSinglePence,
			CombinedTotalGBP:     formatPenceToGBP(bestSinglePence),
			SingleStoreBestStore: bestSingleStore,
			SingleStoreBestPence: bestSinglePence,
			SingleStoreBestGBP:   formatPenceToGBP(bestSinglePence),
			NetSavingsPence:      0,
			NetSavingsGBP:        "£0.00",
		}, nil
	}

	return &result, nil
}

