package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"net/url"
	"strings"
	"time"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

// StoreVerifier provides existence validation and discrepancy handling
type StoreVerifier interface {
	VerifyStoreExistence(ctx context.Context, name string, lat, lon float64) (*domain.StoreVerificationResult, error)
	ReportDiscrepancy(ctx context.Context, report *domain.StoreDiscrepancyReport) (*domain.StoreVerificationResult, error)
}

type storeVerifierImpl struct {
	storeRepo       repository.StoreRepository
	discrepancyRepo repository.DiscrepancyRepository
	httpClient      *http.Client
}

func NewStoreVerifier(storeRepo repository.StoreRepository, discrepancyRepo ...repository.DiscrepancyRepository) StoreVerifier {
	var discRepo repository.DiscrepancyRepository
	if len(discrepancyRepo) > 0 {
		discRepo = discrepancyRepo[0]
	}
	return &storeVerifierImpl{
		storeRepo:       storeRepo,
		discrepancyRepo: discRepo,
		httpClient: &http.Client{
			Timeout: 6 * time.Second,
		},
	}
}

// FSA API response structures
type fsaEstablishment struct {
	FHRSID       int      `json:"FHRSID"`
	BusinessName string   `json:"BusinessName"`
	BusinessType string   `json:"BusinessType"`
	AddressLine1 string   `json:"AddressLine1"`
	PostCode     string   `json:"PostCode"`
	RatingValue  string   `json:"RatingValue"`
	Distance     *float64 `json:"Distance"`
}

type fsaResponse struct {
	Establishments []fsaEstablishment `json:"establishments"`
}

// OSM Overpass API response structures
type osmElement struct {
	ID   int64             `json:"id"`
	Lat  float64           `json:"lat"`
	Lon  float64           `json:"lon"`
	Tags map[string]string `json:"tags"`
}

type osmResponse struct {
	Elements []osmElement `json:"elements"`
}

// Haversine distance in meters
func haversineDistanceMeters(lat1, lon1, lat2, lon2 float64) float64 {
	const r = 6371000 // Earth radius in meters
	phi1 := lat1 * math.Pi / 180
	phi2 := lat2 * math.Pi / 180
	deltaPhi := (lat2 - lat1) * math.Pi / 180
	deltaLambda := (lon2 - lon1) * math.Pi / 180

	a := math.Sin(deltaPhi/2)*math.Sin(deltaPhi/2) +
		math.Cos(phi1)*math.Cos(phi2)*
			math.Sin(deltaLambda/2)*math.Sin(deltaLambda/2)
	c := 2 * math.Atan2(math.Sqrt(a), math.Sqrt(1-a))

	return r * c
}

// VerifyStoreExistence runs the 4-tier existence verification pipeline
func (v *storeVerifierImpl) VerifyStoreExistence(ctx context.Context, candidateName string, lat, lon float64) (*domain.StoreVerificationResult, error) {
	// ─────────────────────────────────────────────────────────────
	// TIER 1: Check existing Ground Truth National Chain Catalog (PostGIS)
	// ─────────────────────────────────────────────────────────────
	nearby, err := v.storeRepo.GetNearby(ctx, lat, lon, 0.15) // within 150 meters
	if err == nil && len(nearby) > 0 {
		for _, s := range nearby {
			dist := haversineDistanceMeters(lat, lon, s.Latitude, s.Longitude)
			if dist <= 80.0 {
				// Exact match with known supermarket
				return &domain.StoreVerificationResult{
					StoreID:             s.ID,
					StoreName:           s.Name,
					Status:              domain.StoreStatusVerifiedChain,
					ConfidenceScore:     100,
					MatchedSource:       "NATIONAL_CHAIN",
					MatchedEstablishment: fmt.Sprintf("%s (%s)", s.Name, s.Address),
					DistanceMeters:      dist,
					IsConfirmedLocation: true,
				}, nil
			}
		}
	}

	// ─────────────────────────────────────────────────────────────
	// TIER 2: UK Food Standards Agency (FSA) Open Hygiene API Check
	// ─────────────────────────────────────────────────────────────
	fsaResult := v.checkFSAHygieneAPI(ctx, candidateName, lat, lon)
	if fsaResult != nil && fsaResult.ConfidenceScore >= 80 {
		return fsaResult, nil
	}

	// ─────────────────────────────────────────────────────────────
	// TIER 3: OpenStreetMap (OSM) Overpass Retail API Check
	// ─────────────────────────────────────────────────────────────
	osmResult := v.checkOSMOverpassAPI(ctx, candidateName, lat, lon)
	if osmResult != nil && osmResult.ConfidenceScore >= 75 {
		return osmResult, nil
	}

	// ─────────────────────────────────────────────────────────────
	// TIER 4: Fallback to Community Pending (Requires 2+ independent corroborations)
	// ─────────────────────────────────────────────────────────────
	return &domain.StoreVerificationResult{
		StoreName:           candidateName,
		Status:              domain.StoreStatusCommunityPending,
		ConfidenceScore:     40,
		MatchedSource:       "COMMUNITY",
		DistanceMeters:      0,
		IsConfirmedLocation: false,
		Warning:             "Unverified independent merchant. Community corroboration (2 independent submitters) required before certification.",
	}, nil
}

// checkFSAHygieneAPI queries the UK Government Food Standards Agency endpoint
func (v *storeVerifierImpl) checkFSAHygieneAPI(ctx context.Context, name string, lat, lon float64) *domain.StoreVerificationResult {
	apiURL := fmt.Sprintf("https://api.ratings.food.gov.uk/Establishments?latitude=%f&longitude=%f&maxDistanceLimit=1", lat, lon)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil
	}
	req.Header.Set("x-api-version", "2")
	req.Header.Set("Accept", "application/json")

	resp, err := v.httpClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			_ = resp.Body.Close()
		}
		return nil
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil
	}

	var data fsaResponse
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		return nil
	}

	normCandidate := strings.ToLower(strings.TrimSpace(name))

	for _, est := range data.Establishments {
		normEst := strings.ToLower(est.BusinessName)
		businessType := strings.ToLower(est.BusinessType)

		// Check if it's retail / grocer / supermarket / convenience
		isFoodRetail := strings.Contains(businessType, "retail") ||
			strings.Contains(businessType, "supermarket") ||
			strings.Contains(businessType, "convenience") ||
			strings.Contains(businessType, "grocery") ||
			strings.Contains(businessType, "other catering")

		distMeters := 0.0
		if est.Distance != nil {
			distMeters = *est.Distance * 1609.34 // miles to meters
		}

		if (normCandidate != "" && (strings.Contains(normEst, normCandidate) || strings.Contains(normCandidate, normEst))) && isFoodRetail {
			return &domain.StoreVerificationResult{
				StoreName:           est.BusinessName,
				Status:              domain.StoreStatusFSAVerified,
				ConfidenceScore:     95,
				MatchedSource:       "FSA_HYGIENE",
				MatchedEstablishment: fmt.Sprintf("%s, %s (FSA ID: %d, Rating: %s)", est.BusinessName, est.AddressLine1, est.FHRSID, est.RatingValue),
				DistanceMeters:      distMeters,
				IsConfirmedLocation: true,
			}
		}

		// Proximity match within 40m even with slight name variations
		if distMeters <= 40.0 && isFoodRetail {
			return &domain.StoreVerificationResult{
				StoreName:           est.BusinessName,
				Status:              domain.StoreStatusFSAVerified,
				ConfidenceScore:     85,
				MatchedSource:       "FSA_HYGIENE",
				MatchedEstablishment: fmt.Sprintf("%s, %s (FSA ID: %d)", est.BusinessName, est.AddressLine1, est.FHRSID),
				DistanceMeters:      distMeters,
				IsConfirmedLocation: true,
			}
		}
	}

	return nil
}

// checkOSMOverpassAPI queries OpenStreetMap for retail nodes near coordinates
func (v *storeVerifierImpl) checkOSMOverpassAPI(ctx context.Context, name string, lat, lon float64) *domain.StoreVerificationResult {
	query := fmt.Sprintf(`[out:json][timeout:5];node["shop"](around:75,%f,%f);out body;`, lat, lon)
	apiURL := "https://overpass-api.de/api/interpreter?data=" + url.QueryEscape(query)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil
	}

	resp, err := v.httpClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		if resp != nil {
			_ = resp.Body.Close()
		}
		return nil
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil
	}

	var data osmResponse
	if err := json.Unmarshal(bodyBytes, &data); err != nil {
		return nil
	}

	for _, el := range data.Elements {
		shopName := el.Tags["name"]
		shopType := el.Tags["shop"]
		dist := haversineDistanceMeters(lat, lon, el.Lat, el.Lon)

		if dist <= 75.0 {
			matchedName := shopName
			if matchedName == "" {
				matchedName = fmt.Sprintf("Local %s Shop", shopType)
			}
			return &domain.StoreVerificationResult{
				StoreName:           matchedName,
				Status:              domain.StoreStatusOSMVerified,
				ConfidenceScore:     80,
				MatchedSource:       "OSM",
				MatchedEstablishment: fmt.Sprintf("%s (OSM node %d, shop=%s)", matchedName, el.ID, shopType),
				DistanceMeters:      dist,
				IsConfirmedLocation: true,
			}
		}
	}

	return nil
}

// ReportDiscrepancy handles user reporting a store discrepancy or closure.
// Per AGENTS.md policy: One complaint never punishes a store; 2+ independent reports are required.
func (v *storeVerifierImpl) ReportDiscrepancy(ctx context.Context, report *domain.StoreDiscrepancyReport) (*domain.StoreVerificationResult, error) {
	st, err := v.storeRepo.GetByID(ctx, report.StoreID)
	if err != nil {
		return nil, fmt.Errorf("store not found: %s", report.StoreID)
	}

	log.Printf("[StoreDiscrepancy] Store '%s' (%s) reported: reason=%s, reporter=%s", st.Name, st.ID, report.Reason, report.ReporterID)

	dist := haversineDistanceMeters(report.UserLatitude, report.UserLongitude, st.Latitude, st.Longitude)

	independentReporters := 1
	if v.discrepancyRepo != nil {
		if count, recErr := v.discrepancyRepo.RecordDiscrepancy(ctx, report); recErr == nil && count > 0 {
			independentReporters = count
		}
	}

	score := st.ReliabilityScore
	if score == 0 {
		score = 95
	}

	status := domain.StoreStatusVerifiedChain
	warning := ""

	// Enforce anti-abuse policy: only apply reliability penalty after 2+ independent reporters
	if independentReporters >= 2 {
		penalty := 15
		if report.Reason == "STORE_CLOSED" || report.Reason == "NOT_HERE" {
			penalty = 25
		}
		if dist <= 150.0 {
			penalty += 10
		}

		score -= penalty
		if score < 0 {
			score = 0
		}

		if v.discrepancyRepo != nil {
			_ = v.discrepancyRepo.UpdateStoreReliability(ctx, st.ID, score)
		}

		if score < 60 {
			status = domain.StoreStatusFlaggedInactive
			warning = fmt.Sprintf("%d independent shoppers reported discrepancies. Store flagged for operator review.", independentReporters)
		} else if score < 80 {
			warning = "Multiple independent visitors reported discrepancies at this store."
		}
	} else {
		warning = "Thank you for reporting. Discrepancy logged for corroboration by another shopper."
	}

	return &domain.StoreVerificationResult{
		StoreID:             st.ID,
		StoreName:           st.Name,
		Status:              status,
		ConfidenceScore:     score,
		MatchedSource:       "DISCREPANCY_AUDIT",
		DistanceMeters:      dist,
		IsConfirmedLocation: score >= 60,
		Warning:             warning,
	}, nil
}
