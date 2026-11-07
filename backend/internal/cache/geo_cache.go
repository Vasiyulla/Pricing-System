package cache

import (
	"context"
	"encoding/json"
	"time"

	"github.com/redis/go-redis/v9"
	"pricebridge/backend/internal/domain"
)

const (
	GeoStoresKey  = "geo:stores"
	StorePrefix   = "store:info:"
	StoreCacheTTL = 24 * time.Hour
)

// GeoSpatialCache provides Uber-style in-memory spatial indexing using Redis GEO commands.
type GeoSpatialCache struct {
	rdb *redis.Client
}

func NewGeoSpatialCache(rdb *redis.Client) *GeoSpatialCache {
	return &GeoSpatialCache{rdb: rdb}
}

// IndexStores registers a list of stores into the Redis geospatial index with 52-bit spatial geohashes.
func (c *GeoSpatialCache) IndexStores(ctx context.Context, stores []domain.Store) error {
	if c.rdb == nil || len(stores) == 0 {
		return nil
	}

	geoLocs := make([]*redis.GeoLocation, 0, len(stores))
	pipe := c.rdb.Pipeline()

	for _, s := range stores {
		if s.Latitude == 0 && s.Longitude == 0 {
			continue
		}
		geoLocs = append(geoLocs, &redis.GeoLocation{
			Name:      s.ID,
			Longitude: s.Longitude,
			Latitude:  s.Latitude,
		})

		// Cache store metadata for fast sub-millisecond retrieval
		if data, err := json.Marshal(s); err == nil {
			pipe.Set(ctx, StorePrefix+s.ID, data, StoreCacheTTL)
		}
	}

	if len(geoLocs) > 0 {
		pipe.GeoAdd(ctx, GeoStoresKey, geoLocs...)
	}

	_, err := pipe.Exec(ctx)
	return err
}

// SearchNearby queries the in-memory Redis spatial index using GEOSEARCH in < 2ms.
func (c *GeoSpatialCache) SearchNearby(ctx context.Context, lat, lon, radiusKm float64) ([]domain.Store, error) {
	if c.rdb == nil || (lat == 0 && lon == 0) {
		return nil, nil
	}

	locations, err := c.rdb.GeoSearchLocation(ctx, GeoStoresKey, &redis.GeoSearchLocationQuery{
		GeoSearchQuery: redis.GeoSearchQuery{
			Longitude:  lon,
			Latitude:   lat,
			Radius:     radiusKm,
			RadiusUnit: "km",
			Sort:       "ASC",
			Count:      25,
		},
		WithDist:  true,
		WithCoord: true,
	}).Result()

	if err != nil || len(locations) == 0 {
		return nil, err
	}

	pipe := c.rdb.Pipeline()
	cmds := make(map[string]*redis.StringCmd, len(locations))
	for _, loc := range locations {
		cmds[loc.Name] = pipe.Get(ctx, StorePrefix+loc.Name)
	}

	_, _ = pipe.Exec(ctx)

	stores := make([]domain.Store, 0, len(locations))
	for _, loc := range locations {
		cmd, ok := cmds[loc.Name]
		if !ok {
			continue
		}
		val, err := cmd.Result()
		if err != nil {
			continue
		}
		var s domain.Store
		if err := json.Unmarshal([]byte(val), &s); err == nil {
			s.DistanceKm = loc.Dist
			stores = append(stores, s)
		}
	}

	return stores, nil
}
