package domain

import (
	"regexp"
	"strconv"
	"strings"
)

var packSizeRegex = regexp.MustCompile(`(?i)(\d+(?:\.\d+)?)\s*(kg|g|ml|l|pints?|pack|units?|wash)`)

// ComputeConfidenceScore calculates observation trust weight by evidence source type.
func ComputeConfidenceScore(sourceType string) float64 {
	switch strings.ToLower(strings.TrimSpace(sourceType)) {
	case "store_confirmation", "store_api":
		return 1.0
	case "checkout_receipt", "receipt":
		return 0.90
	case "shelf_label", "photo_scan":
		return 0.78
	case "offline_sync":
		return 0.65
	case "personal_observation", "mobile_app":
		return 0.60
	case "verbal_quote":
		return 0.45
	default:
		return 0.60
	}
}

// ComputeUnitPricePence normalizes regularPricePence into pence per 100g / 100ml / unit.
func ComputeUnitPricePence(regularPricePence int, packageSize string) int {
	if regularPricePence <= 0 {
		return 0
	}
	matches := packSizeRegex.FindStringSubmatch(packageSize)
	if len(matches) < 3 {
		return regularPricePence
	}

	qty, err := strconv.ParseFloat(matches[1], 64)
	if err != nil || qty <= 0 {
		return regularPricePence
	}

	unit := strings.ToLower(matches[2])
	switch {
	case unit == "kg":
		// Convert to per 100g: 1kg = 10 x 100g
		return int(float64(regularPricePence) / (qty * 10.0))
	case unit == "g":
		// Per 100g
		factor := qty / 100.0
		if factor <= 0 {
			return regularPricePence
		}
		return int(float64(regularPricePence) / factor)
	case unit == "l":
		// Convert to per 100ml: 1L = 10 x 100ml
		return int(float64(regularPricePence) / (qty * 10.0))
	case unit == "ml":
		// Per 100ml
		factor := qty / 100.0
		if factor <= 0 {
			return regularPricePence
		}
		return int(float64(regularPricePence) / factor)
	case strings.HasPrefix(unit, "pint"):
		// 1 UK pint = 568ml -> per 100ml
		factor := (qty * 568.0) / 100.0
		return int(float64(regularPricePence) / factor)
	default:
		return int(float64(regularPricePence) / qty)
	}
}

