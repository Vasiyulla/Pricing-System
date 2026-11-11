package dto

import "pricebridge/backend/internal/domain"

type SignUpRequest struct {
	Name     string `json:"name"`
	Email    string `json:"email"`
	Password string `json:"password"`
}

type SignInRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type GoogleAuthRequest struct {
	IDToken string `json:"id_token"`
}

type CreateProductRequest struct {
	ID             string  `json:"id"`
	Name           string  `json:"name"`
	Brand          string  `json:"brand"`
	Barcode        string  `json:"barcode"`
	Category       string  `json:"category"`
	PackageSize    string  `json:"package_size"`
	UnitMetric     string  `json:"unit_metric"`
	TargetMRP      float64 `json:"target_mrp_gbp"`
	IdempotencyKey string  `json:"idempotency_key,omitempty"`
}

type PriceSubmissionRequest struct {
	ProductID         string  `json:"product_id"`
	StoreID           string  `json:"store_id"`
	RegularPricePence int     `json:"regular_price_pence"`
	LoyaltyPricePence *int    `json:"loyalty_price_pence,omitempty"`
	LoyaltyScheme     string  `json:"loyalty_scheme,omitempty"`
	SourceType        string  `json:"source_type"`
	IdempotencyKey    string  `json:"idempotency_key,omitempty"`
	EvidenceID        *string `json:"evidence_id,omitempty"`
}

type ConfirmFreshnessRequest struct {
	ProductID string `json:"product_id"`
	StoreID   string `json:"store_id"`
}

type UploadEvidenceRequest struct {
	ProductID   string `json:"product_id"`
	StoreID     string `json:"store_id"`
	SourceType  string `json:"source_type"`
	ImageBase64 string `json:"image_base64"`
	OcrRawText  string `json:"ocr_raw_text"`
}

type ReportDiscrepancyRequest struct {
	ReporterID    string  `json:"reporter_id"`
	Reason        string  `json:"reason"`
	Details       string  `json:"details"`
	UserLatitude  float64 `json:"user_latitude"`
	UserLongitude float64 `json:"user_longitude"`
}

type VerifyStoreRequest struct {
	Name      string  `json:"name"`
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

type BasketOptimizeRequest struct {
	Items   []domain.BasketItem `json:"items"`
	UserLat float64             `json:"user_lat"`
	UserLon float64             `json:"user_lon"`
}

type ImageScanRequest struct {
	ImageBase64 string `json:"image_base64"`
}

type AdminResetRequest struct {
	WipeStores bool    `json:"wipe_stores"`
	Lat        float64 `json:"lat"`
	Lon        float64 `json:"lon"`
	AreaName   string  `json:"area_name"`
}

type AdminResetResponse struct {
	Status       string `json:"status"`
	StoresCount  int    `json:"stores_count"`
	Message      string `json:"message"`
	TargetArea   string `json:"target_area"`
	ResetTimeUTC string `json:"reset_time_utc"`
}

