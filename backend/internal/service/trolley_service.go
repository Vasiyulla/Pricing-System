package service

import (
	"context"
	"fmt"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

type TrolleyService struct {
	storeRepo   repository.StoreRepository
	productRepo repository.ProductRepository
	priceRepo   repository.PriceRepository
}

func NewTrolleyService(storeRepo repository.StoreRepository, productRepo repository.ProductRepository, priceRepo repository.PriceRepository) *TrolleyService {
	return &TrolleyService{
		storeRepo:   storeRepo,
		productRepo: productRepo,
		priceRepo:   priceRepo,
	}
}

// GenerateWhisper evaluates a product at the current store and generates high-conviction audio advice
func (s *TrolleyService) GenerateWhisper(ctx context.Context, productID, currentStoreID string, userLat, userLon float64) (*domain.TrolleyWhisper, error) {
	prod, err := s.productRepo.GetByID(ctx, productID)
	if err != nil {
		return nil, err
	}

	_, err = s.storeRepo.GetByID(ctx, currentStoreID)
	if err != nil {
		return nil, err
	}

	currentPriceRec, err := s.priceRepo.GetLatestPrice(ctx, productID, currentStoreID)
	if err != nil {
		return nil, fmt.Errorf("current store price not found: %w", err)
	}

	effectiveCurrentPence := currentPriceRec.RegularPricePence
	if currentPriceRec.LoyaltyPricePence != nil {
		effectiveCurrentPence = *currentPriceRec.LoyaltyPricePence
	}

	// Compare with stores in 2.5km radius
	nearbyStores, err := s.storeRepo.GetNearby(ctx, userLat, userLon, 2.5)
	if err != nil {
		return nil, err
	}

	// Fetch all active store prices for this product in one query to avoid N+1 DB calls.
	allProductPrices, err := s.priceRepo.GetStorePrices(ctx, productID)
	if err != nil {
		return nil, err
	}
	priceByStore := make(map[string]domain.PriceRecord, len(allProductPrices))
	for _, pr := range allProductPrices {
		priceByStore[pr.StoreID] = pr
	}

	cheapestPricePence := effectiveCurrentPence
	var cheapestStore *domain.Store

	for _, st := range nearbyStores {
		if st.ID == currentStoreID {
			continue
		}
		pr, ok := priceByStore[st.ID]
		if !ok {
			continue
		}
		eff := pr.RegularPricePence
		if pr.LoyaltyPricePence != nil {
			eff = *pr.LoyaltyPricePence
		}
		if eff < cheapestPricePence {
			cheapestPricePence = eff
			stCopy := st
			cheapestStore = &stCopy
		}
	}

	savingsPence := effectiveCurrentPence - cheapestPricePence
	var whisperText string
	var rec string

	if cheapestStore != nil && savingsPence >= 40 {
		// Meaningful savings elsewhere!
		rec = "SKIP"
		whisperText = fmt.Sprintf("%s is %s here. Skip it — %s has it for %s, saving you %s.",
			prod.Name,
			formatPenceToGBP(effectiveCurrentPence),
			cheapestStore.Name,
			formatPenceToGBP(cheapestPricePence),
			formatPenceToGBP(savingsPence),
		)
	} else if currentPriceRec.LoyaltyPricePence != nil && currentPriceRec.RegularPricePence > *currentPriceRec.LoyaltyPricePence {
		rec = "CHECK_LOYALTY"
		whisperText = fmt.Sprintf("Great deal on %s at %s with your %s card. Regular is %s, make sure to scan your card at checkout.",
			prod.Name,
			formatPenceToGBP(*currentPriceRec.LoyaltyPricePence),
			currentPriceRec.LoyaltyScheme,
			formatPenceToGBP(currentPriceRec.RegularPricePence),
		)
	} else {
		rec = "BUY"
		whisperText = fmt.Sprintf("Put it in the trolley! %s is at the best nearby price here at %s.",
			prod.Name,
			formatPenceToGBP(effectiveCurrentPence),
		)
	}

	return &domain.TrolleyWhisper{
		ProductID:             productID,
		ProductName:           prod.Name,
		CurrentStoreID:        currentStoreID,
		CurrentPricePence:     effectiveCurrentPence,
		WhisperText:           whisperText,
		Recommendation:        rec,
		AlternativeStore:      cheapestStore,
		PotentialSavingsPence: savingsPence,
	}, nil
}
