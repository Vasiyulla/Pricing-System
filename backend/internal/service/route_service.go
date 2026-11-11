package service

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"golang.org/x/sync/singleflight"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
)

// RouteMetrics holds street or footpath routing telemetry for an origin-destination pair.
type RouteMetrics struct {
	DistanceMeters  int    `json:"distance_meters"`
	DurationSeconds int    `json:"duration_seconds"`
	Mode            string `json:"mode"` // "walking" | "driving"
	IsExactRoute    bool   `json:"is_exact_route"`
}

type routeCacheEntry struct {
	metrics   RouteMetrics
	expiresAt time.Time
}

// RouteService computes road/footpath distances and ETAs via OSRM or Google Routes Matrix,
// using ~110m origin grid caching (Redis + in-memory) and singleflight coalescing.
type RouteService struct {
	provider   string // "osrm" | "google"
	osrmURL    string
	googleKey  string
	rdb        *redis.Client
	httpClient *http.Client
	sfGroup    singleflight.Group

	mu         sync.RWMutex
	localCache map[string]routeCacheEntry
}

func NewRouteService(provider, osrmBaseURL, googleMapsKey string, rdb *redis.Client) *RouteService {
	prov := strings.ToLower(strings.TrimSpace(provider))
	if prov == "" {
		if strings.TrimSpace(googleMapsKey) != "" {
			prov = "google"
		} else {
			prov = "osrm"
		}
	}
	base := strings.TrimRight(strings.TrimSpace(osrmBaseURL), "/")
	if base == "" {
		base = "https://router.project-osrm.org"
	}
	return &RouteService{
		provider:   prov,
		osrmURL:    base,
		googleKey:  strings.TrimSpace(googleMapsKey),
		rdb:        rdb,
		httpClient: &http.Client{Timeout: 2000 * time.Millisecond},
		localCache: make(map[string]routeCacheEntry),
	}
}

// NormalizeRouteMode validates travel mode, defaulting to "walking".
func NormalizeRouteMode(mode string) string {
	switch strings.ToLower(strings.TrimSpace(mode)) {
	case "drive", "driving", "car":
		return "driving"
	default:
		return "walking"
	}
}

// EnrichStoreRoutes populates DistanceKm, DurationMinutes, RouteMode, and IsExactRoute on stores
// and re-sorts them by shortest actual route distance ascending.
func (s *RouteService) EnrichStoreRoutes(
	ctx context.Context,
	originLat, originLon float64,
	mode string,
	stores []domain.Store,
) []domain.Store {
	if len(stores) == 0 || (originLat == 0 && originLon == 0) {
		return stores
	}
	normMode := NormalizeRouteMode(mode)

	// Cap matrix size at 20 destinations per request for predictable <200ms latency.
	limit := len(stores)
	if limit > 20 {
		limit = 20
	}
	candidates := stores[:limit]

	latGrid := math.Round(originLat*1000.0) / 1000.0
	lonGrid := math.Round(originLon*1000.0) / 1000.0

	resolved := make([]*RouteMetrics, len(candidates))
	var missingIndices []int
	var missingStores []domain.Store

	// 1. Check Redis + local in-memory cache first
	keys := make([]string, len(candidates))
	for i, st := range candidates {
		keys[i] = fmt.Sprintf("route:v1:%s:%.3f:%.3f:%s", normMode, latGrid, lonGrid, st.ID)
	}
	cachedVals := s.mgetRoutes(ctx, keys)
	for i, st := range candidates {
		if m := cachedVals[i]; m != nil {
			resolved[i] = m
		} else {
			missingIndices = append(missingIndices, i)
			missingStores = append(missingStores, st)
		}
	}

	// 2. Compute missing destination routes in a single coalesced batch matrix request
	if len(missingStores) > 0 {
		sfKey := fmt.Sprintf("%s:%.3f:%.3f:%d", normMode, latGrid, lonGrid, len(missingStores))
		val, _, _ := s.sfGroup.Do(sfKey, func() (interface{}, error) {
			metricsList := s.fetchMatrix(ctx, originLat, originLon, normMode, missingStores)
			return metricsList, nil
		})
		if batchMetrics, ok := val.([]RouteMetrics); ok && len(batchMetrics) == len(missingIndices) {
			ttl := 24 * time.Hour
			if normMode == "driving" {
				ttl = 15 * time.Minute
			}
			for j, origIdx := range missingIndices {
				m := batchMetrics[j]
				resolved[origIdx] = &m
				// Persist exact routes (and brief short-TTL fallback on failure to prevent hot retries)
				storeTTL := ttl
				if !m.IsExactRoute {
					storeTTL = 60 * time.Second
				}
				s.setRoute(ctx, keys[origIdx], m, storeTTL)
			}
		}
	}

	// 3. Apply metrics onto copies of stores and sort by route distance
	out := make([]domain.Store, len(stores))
	copy(out, stores)
	for i := range candidates {
		m := resolved[i]
		if m == nil {
			fallback := fallbackRoute(originLat, originLon, out[i].Latitude, out[i].Longitude, normMode)
			m = &fallback
		}
		out[i].DistanceKm = math.Round((float64(m.DistanceMeters)/1000.0)*100.0) / 100.0
		out[i].DurationMinutes = int(math.Max(1, math.Ceil(float64(m.DurationSeconds)/60.0)))
		out[i].RouteMode = m.Mode
		out[i].IsExactRoute = m.IsExactRoute
	}

	sort.SliceStable(out[:limit], func(i, j int) bool {
		return out[i].DistanceKm < out[j].DistanceKm
	})
	return out
}

// GetPairRoute returns cached street/footpath metrics between two fixed stores.
func (s *RouteService) GetPairRoute(ctx context.Context, mode string, a, b domain.Store) RouteMetrics {
	normMode := NormalizeRouteMode(mode)
	id1, id2 := a.ID, b.ID
	if id1 > id2 {
		id1, id2 = id2, id1
	}
	cacheKey := fmt.Sprintf("route:pair:v1:%s:%s:%s", normMode, id1, id2)
	if cached := s.mgetRoutes(ctx, []string{cacheKey}); len(cached) > 0 && cached[0] != nil {
		return *cached[0]
	}

	batch := s.fetchMatrix(ctx, a.Latitude, a.Longitude, normMode, []domain.Store{b})
	var res RouteMetrics
	if len(batch) > 0 {
		res = batch[0]
	} else {
		res = fallbackRoute(a.Latitude, a.Longitude, b.Latitude, b.Longitude, normMode)
	}
	ttl := 7 * 24 * time.Hour
	if !res.IsExactRoute {
		ttl = 2 * time.Minute
	}
	s.setRoute(ctx, cacheKey, res, ttl)
	return res
}

func (s *RouteService) fetchMatrix(
	ctx context.Context,
	originLat, originLon float64,
	mode string,
	dests []domain.Store,
) []RouteMetrics {
	if s.provider == "google" && s.googleKey != "" {
		if res, err := s.fetchGoogleRouteMatrix(ctx, originLat, originLon, mode, dests); err == nil && len(res) == len(dests) {
			return res
		}
	}
	if res, err := s.fetchOSRMMatrix(ctx, originLat, originLon, mode, dests); err == nil && len(res) == len(dests) {
		return res
	}

	out := make([]RouteMetrics, len(dests))
	for i, d := range dests {
		out[i] = fallbackRoute(originLat, originLon, d.Latitude, d.Longitude, mode)
	}
	return out
}

func (s *RouteService) fetchOSRMMatrix(
	ctx context.Context,
	originLat, originLon float64,
	mode string,
	dests []domain.Store,
) ([]RouteMetrics, error) {
	profile := "foot"
	if mode == "driving" {
		profile = "driving"
	}

	var coords strings.Builder
	coords.WriteString(fmt.Sprintf("%.6f,%.6f", originLon, originLat))
	var destIdx strings.Builder
	for i, d := range dests {
		coords.WriteString(fmt.Sprintf(";%.6f,%.6f", d.Longitude, d.Latitude))
		if i > 0 {
			destIdx.WriteByte(';')
		}
		destIdx.WriteString(strconv.Itoa(i + 1))
	}

	reqURL := fmt.Sprintf("%s/table/v1/%s/%s?sources=0&destinations=%s&annotations=distance,duration",
		s.osrmURL, profile, coords.String(), destIdx.String())

	reqCtx, cancel := context.WithTimeout(ctx, 1800*time.Millisecond)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "PriceBridge/1.0")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("osrm status %d", resp.StatusCode)
	}

	var body struct {
		Code      string       `json:"code"`
		Distances [][]*float64 `json:"distances"`
		Durations [][]*float64 `json:"durations"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, err
	}
	if body.Code != "Ok" || len(body.Distances) == 0 || len(body.Durations) == 0 {
		return nil, fmt.Errorf("osrm invalid code: %s", body.Code)
	}

	dists := body.Distances[0]
	durs := body.Durations[0]
	if len(dists) != len(dests) || len(durs) != len(dests) {
		return nil, fmt.Errorf("osrm size mismatch")
	}

	out := make([]RouteMetrics, len(dests))
	for i, d := range dests {
		if dists[i] == nil || durs[i] == nil || *dists[i] <= 0 {
			out[i] = fallbackRoute(originLat, originLon, d.Latitude, d.Longitude, mode)
			continue
		}
		durSec := int(math.Round(*durs[i]))
		// OSRM public demo uses car speeds even for /foot when only car graph is mounted;
		// normalize foot duration if estimated speed exceeds realistic walking pace (> 7 km/h).
		distM := int(math.Round(*dists[i]))
		if mode == "walking" && durSec > 0 && (float64(distM)/float64(durSec)) > 2.0 {
			durSec = int(math.Ceil(float64(distM) / 1.35)) // ~4.86 km/h walking speed
		}
		out[i] = RouteMetrics{
			DistanceMeters:  distM,
			DurationSeconds: max(30, durSec),
			Mode:            mode,
			IsExactRoute:    true,
		}
	}
	return out, nil
}

func (s *RouteService) fetchGoogleRouteMatrix(
	ctx context.Context,
	originLat, originLon float64,
	mode string,
	dests []domain.Store,
) ([]RouteMetrics, error) {
	travelMode := "WALK"
	if mode == "driving" {
		travelMode = "DRIVE"
	}

	type latLng struct {
		Latitude  float64 `json:"latitude"`
		Longitude float64 `json:"longitude"`
	}
	type wp struct {
		Waypoint struct {
			Location struct {
				LatLng latLng `json:"latLng"`
			} `json:"location"`
		} `json:"waypoint"`
	}

	var origin wp
	origin.Waypoint.Location.LatLng = latLng{Latitude: originLat, Longitude: originLon}

	destinations := make([]wp, len(dests))
	for i, d := range dests {
		destinations[i].Waypoint.Location.LatLng = latLng{Latitude: d.Latitude, Longitude: d.Longitude}
	}

	payload := map[string]interface{}{
		"origins":      []wp{origin},
		"destinations": destinations,
		"travelMode":   travelMode,
	}
	if travelMode == "DRIVE" {
		payload["routingPreference"] = "TRAFFIC_AWARE"
	}

	rawBody, err := json.Marshal(payload)
	if err != nil {
		return nil, err
	}

	reqCtx, cancel := context.WithTimeout(ctx, 1800*time.Millisecond)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodPost,
		"https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix", bytes.NewReader(rawBody))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Goog-Api-Key", s.googleKey)
	req.Header.Set("X-Goog-FieldMask", "originIndex,destinationIndex,distanceMeters,duration,condition")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("google routes status %d: %s", resp.StatusCode, string(b))
	}

	var elems []struct {
		OriginIndex      int    `json:"originIndex"`
		DestinationIndex int    `json:"destinationIndex"`
		DistanceMeters   int    `json:"distanceMeters"`
		Duration         string `json:"duration"`
		Condition        string `json:"condition"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&elems); err != nil {
		return nil, err
	}

	out := make([]RouteMetrics, len(dests))
	for i, d := range dests {
		out[i] = fallbackRoute(originLat, originLon, d.Latitude, d.Longitude, mode)
	}
	for _, el := range elems {
		if el.OriginIndex == 0 && el.DestinationIndex >= 0 && el.DestinationIndex < len(dests) &&
			el.Condition == "ROUTE_EXISTS" && el.DistanceMeters > 0 {
			sec, _ := strconv.Atoi(strings.TrimSuffix(el.Duration, "s"))
			out[el.DestinationIndex] = RouteMetrics{
				DistanceMeters:  el.DistanceMeters,
				DurationSeconds: max(30, sec),
				Mode:            mode,
				IsExactRoute:    true,
			}
		}
	}
	return out, nil
}

func fallbackRoute(lat1, lon1, lat2, lon2 float64, mode string) RouteMetrics {
	straightKm := repository.HaversineDistanceKm(lat1, lon1, lat2, lon2)
	detourMeters := int(math.Round(straightKm * 1.30 * 1000.0))
	if detourMeters < 50 {
		detourMeters = 50
	}
	speedMps := 1.35 // ~4.86 km/h urban walking
	if mode == "driving" {
		speedMps = 7.8 // ~28 km/h urban London traffic
	}
	return RouteMetrics{
		DistanceMeters:  detourMeters,
		DurationSeconds: max(45, int(math.Ceil(float64(detourMeters)/speedMps))),
		Mode:            mode,
		IsExactRoute:    false,
	}
}

func (s *RouteService) mgetRoutes(ctx context.Context, keys []string) []*RouteMetrics {
	res := make([]*RouteMetrics, len(keys))
	var missIdx []int
	var missKeys []string

	s.mu.RLock()
	now := time.Now()
	for i, k := range keys {
		if entry, ok := s.localCache[k]; ok && now.Before(entry.expiresAt) {
			m := entry.metrics
			res[i] = &m
		} else {
			missIdx = append(missIdx, i)
			missKeys = append(missKeys, k)
		}
	}
	s.mu.RUnlock()

	if len(missKeys) > 0 && s.rdb != nil {
		vals, err := s.rdb.MGet(ctx, missKeys...).Result()
		if err == nil && len(vals) == len(missKeys) {
			s.mu.Lock()
			for j, raw := range vals {
				str, ok := raw.(string)
				if !ok || str == "" {
					continue
				}
				var m RouteMetrics
				if err := json.Unmarshal([]byte(str), &m); err == nil {
					res[missIdx[j]] = &m
					s.localCache[missKeys[j]] = routeCacheEntry{
						metrics:   m,
						expiresAt: time.Now().Add(5 * time.Minute),
					}
				}
			}
			s.mu.Unlock()
		}
	}
	return res
}

func (s *RouteService) setRoute(ctx context.Context, key string, m RouteMetrics, ttl time.Duration) {
	s.mu.Lock()
	localTTL := ttl
	if localTTL > 5*time.Minute {
		localTTL = 5 * time.Minute
	}
	s.localCache[key] = routeCacheEntry{
		metrics:   m,
		expiresAt: time.Now().Add(localTTL),
	}
	s.mu.Unlock()

	if s.rdb != nil {
		if data, err := json.Marshal(m); err == nil {
			_ = s.rdb.Set(ctx, key, data, ttl).Err()
		}
	}
}

