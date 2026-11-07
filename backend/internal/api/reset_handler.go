package api

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/google/uuid"
	"pricebridge/backend/internal/domain"
)

type ResetRequest struct {
	WipeStores bool    `json:"wipe_stores"`
	Lat        float64 `json:"lat"`
	Lon        float64 `json:"lon"`
	AreaName   string  `json:"area_name"`
}

type ResetResponse struct {
	Status       string `json:"status"`
	StoresCount  int    `json:"stores_count"`
	Message      string `json:"message"`
	TargetArea   string `json:"target_area"`
	ResetTimeUTC string `json:"reset_time_utc"`
}

// CheckAdminAuthorization verifies admin authentication via Bearer token or X-Admin-Secret header.
// It strictly requires ADMIN_SECRET to be configured in the environment.
// Hardcoded fallbacks are completely removed to prevent unauthorized wipes.
func CheckAdminAuthorization(r *http.Request) (bool, int, string) {
	adminSecret := strings.TrimSpace(os.Getenv("ADMIN_SECRET"))

	if adminSecret == "" {
		return false, http.StatusForbidden, "Admin database operations are strictly disabled when ADMIN_SECRET is not configured in the server environment"
	}

	token := extractAdminToken(r)
	if token == "" {
		return false, http.StatusUnauthorized, "Unauthorized: missing admin authorization token"
	}

	if subtle.ConstantTimeCompare([]byte(token), []byte(adminSecret)) != 1 {
		return false, http.StatusUnauthorized, "Unauthorized: invalid admin credentials"
	}

	return true, http.StatusOK, ""
}

func extractAdminToken(r *http.Request) string {
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	if authHeader != "" {
		if strings.HasPrefix(strings.ToLower(authHeader), "bearer ") {
			return strings.TrimSpace(authHeader[7:])
		}
		return authHeader
	}
	if xSecret := strings.TrimSpace(r.Header.Get("X-Admin-Secret")); xSecret != "" {
		return xSecret
	}
	return ""
}

// ResetDatabase wipes old test submissions, test prices, and allows re-seeding
// around the caller's live GPS coordinates anywhere in the world.
func (h *APIHandler) ResetDatabase(w http.ResponseWriter, r *http.Request) {
	// Defense-in-depth security guard: verify admin authorization before executing wipe
	if ok, status, msg := CheckAdminAuthorization(r); !ok {
		writeError(w, status, msg)
		return
	}

	var req ResetRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	log.Printf("[AdminReset] Resetting database data: wipeStores=%v, lat=%.4f, lon=%.4f, area=%s",
		req.WipeStores, req.Lat, req.Lon, req.AreaName)

	targetLat := req.Lat
	targetLon := req.Lon
	areaName := req.AreaName
	if targetLat == 0 && targetLon == 0 {
		// Default to Central London / Camden NW1 if no coordinates supplied
		targetLat = 51.5390
		targetLon = -0.1426
		if areaName == "" {
			areaName = "Camden Town, London"
		}
	} else if areaName == "" {
		areaName = fmt.Sprintf("Live Area (%.3f, %.3f)", targetLat, targetLon)
	}

	// 1. Wipe volatile tables if using PostgreSQL
	if pgStore, ok := h.storeRepo.(interface {
		ExecuteRawSQL(ctx context.Context, sql string) error
	}); ok {
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM price_records;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM yellow_stickers;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM bounties;")
		_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM outbox_events;")
		if req.WipeStores {
			_ = pgStore.ExecuteRawSQL(ctx, "DELETE FROM stores;")
		}
	}

	// 2. Discover or seed fresh local stores around target coordinates
	seededStores, err := h.seedStoresAroundCoordinates(ctx, targetLat, targetLon, areaName)
	if err != nil {
		log.Printf("[AdminReset] Warning during store seeding: %v", err)
	}

	// 3. Clear in-memory spatial cache
	h.compareSvc.ClearCache()

	resp := ResetResponse{
		Status:       "success",
		StoresCount:  len(seededStores),
		Message:      fmt.Sprintf("Database cleaned and re-seeded with %d active stores around %s", len(seededStores), areaName),
		TargetArea:   areaName,
		ResetTimeUTC: time.Now().UTC().Format(time.RFC3339),
	}
	writeJSON(w, http.StatusOK, resp)
}

// seedStoresAroundCoordinates generates 4 realistic community supermarkets around the user's GPS
func (h *APIHandler) seedStoresAroundCoordinates(ctx context.Context, lat, lon float64, areaName string) ([]domain.Store, error) {
	// Offsets in degrees (~111 km per deg latitude, ~70-111 km longitude)
	// 0.003 deg is ~330m, 0.006 deg is ~660m, 0.009 deg is ~990m
	templates := []struct {
		name    string
		chain   domain.StoreChain
		format  domain.StoreFormat
		dLat    float64
		dLon    float64
		address string
		partner bool
	}{
		{"City Express Supermarket", domain.ChainTesco, domain.FormatExpress, 0.0032, 0.0028, "High Street", true},
		{"Cornerstone Fresh Grocers", domain.ChainSainsburys, domain.FormatLocal, -0.0028, 0.0045, "Station Road", false},
		{"Metro Value Foods", domain.ChainAldi, domain.FormatSuperstore, 0.0058, -0.0040, "Commercial Crescent", true},
		{"Community Quality Market", domain.ChainLidl, domain.FormatSuperstore, -0.0065, -0.0055, "Green Lane", true},
	}

	var createdStores []domain.Store
	prods, _ := h.productRepo.GetAll(ctx)

	for i, t := range templates {
		stID := fmt.Sprintf("store_live_%d_%d", int(math.Abs(lat*100)), i+1)
		stLat := lat + t.dLat
		stLon := lon + t.dLon

		store := domain.Store{
			ID:                  stID,
			Chain:               t.chain,
			Format:              t.format,
			Name:                fmt.Sprintf("%s (%s)", t.name, areaName),
			Address:             fmt.Sprintf("%d %s, %s", (i+1)*12, t.address, areaName),
			Postcode:            "LOCAL",
			Latitude:            stLat,
			Longitude:           stLon,
			DistanceKm:          0.3 + float64(i)*0.35,
			ReliabilityScore:    95 - i*2,
			IsConfirmedPartner:  t.partner,
		}

		_ = h.storeRepo.Create(ctx, store)
		createdStores = append(createdStores, store)

		// Seed initial baseline prices for the 30 master products
		for _, p := range prods {
			baseMRP := p.TargetMRP
			variance := 0.85 + (float64((i*7+len(p.ID))%25) / 100.0) // 85% to 110% of MRP
			regularPence := int(baseMRP * variance * 100.0)
			if regularPence < 50 {
				regularPence = 50
			}

			var loyaltyPence *int
			loyaltyScheme := domain.LoyaltyNone
			if t.partner && i%2 == 0 {
				loy := int(float64(regularPence) * 0.90) // 10% loyalty discount
				loyaltyPence = &loy
				loyaltyScheme = domain.LoyaltyClubcard
			}

			priceRec := domain.PriceRecord{
				ID:                uuid.New().String(),
				ProductID:         p.ID,
				StoreID:           stID,
				RegularPricePence: regularPence,
				LoyaltyPricePence: loyaltyPence,
				LoyaltyScheme:     loyaltyScheme,
				SourceType:        "verified_shelf_tag",
				FreshnessStatus:   domain.FreshnessRecentlyObserved,
				ObservedAt:        time.Now().Add(-time.Duration(i*45) * time.Minute),
			}
			_ = h.priceRepo.SubmitPrice(ctx, priceRec)
		}

		// Seed 1 bounty and 1 yellow sticker for each store
		if len(prods) > i {
			_ = h.bountyRepo.Create(ctx, domain.Bounty{
				ID:           fmt.Sprintf("bounty_live_%d", i+1),
				StoreID:      stID,
				ProductID:    prods[i].ID,
				ProductName:  fmt.Sprintf("Verify shelf tag for %s", prods[i].Name),
				RewardPoints: 25 + i*5,
			})

			_ = h.stickerRepo.Create(ctx, domain.YellowStickerDeal{
				ID:              fmt.Sprintf("sticker_live_%d", i+1),
				StoreID:         stID,
				StoreName:       store.Name,
				ProductName:     prods[len(prods)-1-i].Name,
				WasPricePence:   int(prods[len(prods)-1-i].TargetMRP * 100),
				NowPricePence:   int(prods[len(prods)-1-i].TargetMRP * 50),
				DiscountPercent: 50,
				UseByDate:       "Midnight Tonight",
			})
		}
	}

	return createdStores, nil
}
