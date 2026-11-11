package domain

import "time"

// Category represents a product category taxonomy
type Category struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// BountyPrompt represents an interactive prompt for user price verification
type BountyPrompt struct {
	BountyID          string `json:"bounty_id"`
	ProductName       string `json:"product_name"`
	StoreName         string `json:"store_name"`
	LastObservedPrice string `json:"last_observed_price"`
	RewardPoints      int    `json:"reward_points"`
}

// StoreChain represents major UK supermarket chains
type StoreChain string

const (
	ChainTesco          StoreChain = "Tesco"
	ChainSainsburys     StoreChain = "Sainsbury's"
	ChainAldi           StoreChain = "Aldi"
	ChainLidl           StoreChain = "Lidl"
	ChainAsda           StoreChain = "Asda"
	ChainMorrisons      StoreChain = "Morrisons"
	ChainMarksAndSpencer StoreChain = "M&S"
	ChainWaitrose       StoreChain = "Waitrose"
	ChainCoop           StoreChain = "Co-op"
)

// StoreFormat indicates standard superstore vs convenience format
type StoreFormat string

const (
	FormatSuperstore StoreFormat = "Superstore"
	FormatExpress    StoreFormat = "Express"
	FormatLocal      StoreFormat = "Local"
)

// LoyaltyScheme indicates UK loyalty price programmes
type LoyaltyScheme string

const (
	LoyaltyNone        LoyaltyScheme = "NONE"
	LoyaltyClubcard    LoyaltyScheme = "CLUBCARD"
	LoyaltyNectar      LoyaltyScheme = "NECTAR"
	LoyaltyAsdaRewards LoyaltyScheme = "ASDA_REWARDS"
	LoyaltyLidlPlus    LoyaltyScheme = "LIDL_PLUS"
	LoyaltyMore        LoyaltyScheme = "MORE"
)

// FreshnessStatus indicates how reliable the price is
type FreshnessStatus string

const (
	FreshnessRecentlyObserved      FreshnessStatus = "RECENTLY_OBSERVED"
	FreshnessCommunityCorroborated FreshnessStatus = "COMMUNITY_CORROBORATED"
	FreshnessStoreConfirmed        FreshnessStatus = "STORE_CONFIRMED"
	FreshnessPriceOutdated         FreshnessStatus = "PRICE_MAY_BE_OUTDATED"
	FreshnessFlagged               FreshnessStatus = "FLAGGED_FOR_REVIEW"
)

// StoreVerificationStatus represents the verification tier of a store
type StoreVerificationStatus string

const (
	StoreStatusVerifiedChain         StoreVerificationStatus = "VERIFIED_CHAIN"
	StoreStatusFSAVerified           StoreVerificationStatus = "FSA_VERIFIED"
	StoreStatusOSMVerified           StoreVerificationStatus = "OSM_VERIFIED"
	StoreStatusCommunityPending      StoreVerificationStatus = "COMMUNITY_PENDING"
	StoreStatusCommunityCorroborated StoreVerificationStatus = "COMMUNITY_CORROBORATED"
	StoreStatusFlaggedInactive       StoreVerificationStatus = "FLAGGED_INACTIVE"
)

// Store represents a physical supermarket branch
type Store struct {
	ID                 string                  `json:"id"`
	Chain              StoreChain              `json:"chain"`
	Format             StoreFormat             `json:"format"`
	Name               string                  `json:"name"`
	Latitude           float64                 `json:"latitude"`
	Longitude          float64                 `json:"longitude"`
	Address            string                  `json:"address"`
	Postcode           string                  `json:"postcode"`
	DistanceKm         float64                 `json:"distance_km,omitempty"`
	DurationMinutes    int                     `json:"duration_minutes,omitempty"`
	RouteMode          string                  `json:"route_mode,omitempty"`
	IsExactRoute       bool                    `json:"is_exact_route,omitempty"`
	VerificationStatus StoreVerificationStatus `json:"verification_status,omitempty"`
	ReliabilityScore   int                     `json:"reliability_score,omitempty"`
	IsConfirmedPartner bool                    `json:"is_confirmed_partner,omitempty"`
	OperatingHours     string                  `json:"operating_hours,omitempty"`
	Phone              string                  `json:"phone,omitempty"`
}

// StoreVerificationResult represents existence verification outcome
type StoreVerificationResult struct {
	StoreID             string                  `json:"store_id,omitempty"`
	StoreName           string                  `json:"store_name"`
	Status              StoreVerificationStatus `json:"status"`
	ConfidenceScore     int                     `json:"confidence_score"` // 0 - 100
	MatchedSource       string                  `json:"matched_source"`   // "NATIONAL_CHAIN", "FSA_HYGIENE", "OSM", "COMMUNITY"
	MatchedEstablishment string                 `json:"matched_establishment,omitempty"`
	DistanceMeters      float64                 `json:"distance_meters"`
	IsConfirmedLocation bool                    `json:"is_confirmed_location"`
	Warning             string                  `json:"warning,omitempty"`
}

// StoreDiscrepancyReport models user reporting a missing/closed store
type StoreDiscrepancyReport struct {
	StoreID       string    `json:"store_id"`
	ReporterID    string    `json:"reporter_id"`
	Reason        string    `json:"reason"` // "STORE_CLOSED", "NOT_HERE", "WRONG_NAME", "PRICE_MISMATCH"
	Details       string    `json:"details,omitempty"`
	UserLatitude  float64   `json:"user_latitude"`
	UserLongitude float64   `json:"user_longitude"`
	ReportedAt    time.Time `json:"reported_at"`
}

// Product represents a catalog SKU
type Product struct {
	ID          string  `json:"id"`
	Name        string  `json:"name"`
	Brand       string  `json:"brand"`
	Barcode     string  `json:"barcode"` // EAN-13
	Category    string  `json:"category"`
	PackageSize string  `json:"package_size"`
	UnitMetric  string  `json:"unit_metric"` // e.g. "100g", "kg", "100ml", "L", "wash"
	TargetMRP   float64 `json:"target_mrp_gbp"`
}

// PriceRecord represents an append-only observed price at a store
type PriceRecord struct {
	ID                string          `json:"id"`
	ProductID         string          `json:"product_id"`
	ProductName       string          `json:"product_name,omitempty"`
	StoreID           string          `json:"store_id"`
	StoreName         string          `json:"store_name"`
	RegularPricePence int             `json:"regular_price_pence"` // in pence (e.g. 245 = £2.45)
	LoyaltyPricePence *int            `json:"loyalty_price_pence,omitempty"`
	LoyaltyScheme     LoyaltyScheme   `json:"loyalty_scheme"`
	UnitPricePence    int             `json:"unit_price_pence"` // e.g. p per 100g
	FreshnessStatus   FreshnessStatus `json:"freshness_status"`
	SourceType        string          `json:"source_type"` // e.g. "shelf_label", "checkout_receipt"
	ConfidenceScore   float64         `json:"confidence_score"`
	ObservedAt        time.Time       `json:"observed_at"`
	SupersededBy      *string         `json:"superseded_by,omitempty"`
	IdempotencyKey    string          `json:"idempotency_key,omitempty"`
	SubmittedByUserID string          `json:"submitted_by_user_id,omitempty"`
	EvidenceID        *string         `json:"evidence_id,omitempty"`
}

// StorePriceComparison summarizes price comparison across stores for a product
type StorePriceComparison struct {
	Store              Store           `json:"store"`
	RegularPricePence  int             `json:"regular_price_pence"`
	RegularPriceGBP    string          `json:"regular_price_gbp"`
	LoyaltyPricePence  *int            `json:"loyalty_price_pence,omitempty"`
	LoyaltyPriceGBP    *string         `json:"loyalty_price_gbp,omitempty"`
	LoyaltyScheme      LoyaltyScheme   `json:"loyalty_scheme"`
	EffectivePricePence int            `json:"effective_price_pence"`
	UnitPricePence     int             `json:"unit_price_pence"`
	UnitPriceFormatted string          `json:"unit_price_formatted"`
	FreshnessStatus    FreshnessStatus `json:"freshness_status"`
	IsBestDeal         bool            `json:"is_best_deal"`
	ObservedAgo        string          `json:"observed_ago"`
}

// BasketItem represents an item requested in a shopping list
type BasketItem struct {
	ProductID   string `json:"product_id"`
	ProductName string `json:"product_name"`
	Quantity    int    `json:"quantity"`
}

// TwoStopBasketResult represents the Pareto 2-Stop optimization outcome
type TwoStopBasketResult struct {
	Store1              Store        `json:"store_1"` // Usually discounter (Aldi/Lidl)
	Store1Items         []BasketItem `json:"store_1_items"`
	Store1TotalPence    int          `json:"store_1_total_pence"`
	Store1TotalGBP      string       `json:"store_1_total_gbp"`

	Store2              Store        `json:"store_2"` // Usually supermarket (Tesco/M&S)
	Store2Items         []BasketItem `json:"store_2_items"`
	Store2TotalPence    int          `json:"store_2_total_pence"`
	Store2TotalGBP      string       `json:"store_2_total_gbp"`

	CombinedTotalPence  int          `json:"combined_total_pence"`
	CombinedTotalGBP    string       `json:"combined_total_gbp"`

	SingleStoreBestStore Store       `json:"single_store_best_store"`
	SingleStoreBestPence int         `json:"single_store_best_pence"`
	SingleStoreBestGBP   string      `json:"single_store_best_gbp"`

	NetSavingsPence     int          `json:"net_savings_pence"`
	NetSavingsGBP       string       `json:"net_savings_gbp"`
	WalkingDistanceMeters int        `json:"walking_distance_meters"`
	EstimatedExtraMinutes int        `json:"estimated_extra_minutes"`
}

// TrolleyWhisper represents audio advice for in-store earphone TTS
type TrolleyWhisper struct {
	ProductID        string `json:"product_id"`
	ProductName      string `json:"product_name"`
	CurrentStoreID   string `json:"current_store_id"`
	CurrentPricePence int   `json:"current_price_pence"`
	WhisperText      string `json:"whisper_text"`
	Recommendation   string `json:"recommendation"` // "BUY", "SKIP", "CHECK_LOYALTY"
	AlternativeStore *Store `json:"alternative_store,omitempty"`
	PotentialSavingsPence int `json:"potential_savings_pence,omitempty"`
}

// Bounty represents an automated micro-quest for missing/stale store prices
type Bounty struct {
	ID           string    `json:"id"`
	StoreID      string    `json:"store_id"`
	StoreName    string    `json:"store_name"`
	ProductID    string    `json:"product_id"`
	ProductName  string    `json:"product_name"`
	RewardPoints int       `json:"reward_points"`
	ExpiresAt    time.Time `json:"expires_at"`
	Status       string    `json:"status"` // "OPEN", "CLAIMED"
	DistanceKm   float64   `json:"distance_km"`
}

// ExtractedReceipt represents the result of multimodal receipt parsing
type ExtractedReceipt struct {
	StoreName       string                 `json:"store_name"`
	BranchNumber    string                 `json:"branch_number,omitempty"`
	VATNumber       string                 `json:"vat_number,omitempty"`
	Date            string                 `json:"date"`
	TotalPence      int                    `json:"total_pence"`
	VATAmountPence  int                    `json:"vat_amount_pence"`
	Items           []ExtractedLineItem    `json:"items"`
	IsMathValid     bool                   `json:"is_math_valid"`
}

type ExtractedLineItem struct {
	Name              string        `json:"name"`
	Quantity          int           `json:"quantity"`
	RegularPricePence int           `json:"regular_price_pence"`
	LoyaltyPricePence *int          `json:"loyalty_price_pence,omitempty"`
	LoyaltyScheme     LoyaltyScheme `json:"loyalty_scheme,omitempty"`
	VATRatePercent    int           `json:"vat_rate_percent"` // 0 for food, 20 for non-food
}

// ExtractedShelfTag represents multimodal shelf-tag or product-page extraction
type ExtractedShelfTag struct {
	ProductName       string `json:"product_name"`
	Brand             string `json:"brand,omitempty"`
	Unit              string `json:"unit,omitempty"`
	PricePence        int    `json:"price_pence"`
	LoyaltyPricePence *int   `json:"loyalty_price_pence,omitempty"`
	LoyaltyScheme     string `json:"loyalty_scheme,omitempty"`
	StoreName         string `json:"store_name,omitempty"`
	Confidence        int    `json:"confidence"`
	Source            string `json:"source"`
}

// YellowStickerDeal represents a clearance markdown deal
type YellowStickerDeal struct {
	ID             string    `json:"id"`
	StoreID        string    `json:"store_id"`
	StoreName      string    `json:"store_name"`
	ProductName    string    `json:"product_name"`
	WasPricePence  int       `json:"was_price_pence"`
	NowPricePence  int       `json:"now_price_pence"`
	DiscountPercent int      `json:"discount_percent"`
	UseByDate      string    `json:"use_by_date"`
	ExpiresAt      time.Time `json:"expires_at"`
}

// UserSession represents an authenticated session profile
type UserSession struct {
	ID          string `json:"id"`
	Email       string `json:"email"`
	DisplayName string `json:"display_name"`
	AvatarURL   string `json:"avatar_url,omitempty"`
	Role        string `json:"role"`
	Token       string `json:"token"`
	KarmaPoints int    `json:"karma_points"`
	AuthMode    string `json:"auth_mode"` // "GOOGLE", "PHONE", "GUEST"
}

// ProductDealSummary represents a product with its best nearby supermarket deal
type ProductDealSummary struct {
	Product             Product                `json:"product"`
	BestPricePence      int                    `json:"best_price_pence"`
	BestPriceGBP        string                 `json:"best_price_gbp"`
	BestStoreName         string                 `json:"best_store_name"`
	BestStoreID           string                 `json:"best_store_id"`
	BestStoreDistanceKm   float64                `json:"best_store_distance_km"`
	BestStoreDurationMins int                    `json:"best_store_duration_minutes,omitempty"`
	BestStoreRouteMode    string                 `json:"best_store_route_mode,omitempty"`
	IsExactRoute          bool                   `json:"is_exact_route,omitempty"`
	ComparedStoreCount    int                    `json:"compared_store_count"`
	FreshnessStatus     FreshnessStatus        `json:"freshness_status"`
	Alternatives        []StorePriceComparison `json:"alternatives,omitempty"`
}

// UserProfileRecord represents persisted user reputation and karma telemetry
type UserProfileRecord struct {
	ID             string `json:"id"`
	Email          string `json:"email"`
	DisplayName    string `json:"display_name"`
	AvatarURL      string `json:"avatar_url"`
	Role           string `json:"role"`
	KarmaPoints    int    `json:"karma_points"`
	PricesReported int    `json:"prices_reported"`
	PricesVerified int    `json:"prices_verified"`
	AuthMode       string `json:"auth_mode"`
}

// AuthSessionRecord persists active session bearer tokens across server restarts
type AuthSessionRecord struct {
	Token       string    `json:"token"`
	UserID      string    `json:"user_id"`
	Email       string    `json:"email"`
	DisplayName string    `json:"display_name"`
	Role        string    `json:"role"`
	ExpiresAt   time.Time `json:"expires_at"`
}

// EvidenceRecord represents an EXIF-stripped shelf tag or receipt photo stored in Object Storage
type EvidenceRecord struct {
	ID           string `json:"id"`
	UserID       string `json:"user_id"`
	ProductID    string `json:"product_id,omitempty"`
	StoreID      string `json:"store_id,omitempty"`
	SourceType   string `json:"source_type"`
	ObjectKey    string `json:"object_key"`
	EvidenceURL  string `json:"evidence_url"`
	ImageHash    string `json:"image_hash"`
	ExifStripped bool   `json:"exif_stripped"`
	OcrRawText   string `json:"ocr_raw_text,omitempty"`
}

// OutboxEvent represents a transactional event awaiting async processing
type OutboxEvent struct {
	ID            string    `json:"id"`
	EventType     string    `json:"event_type"`
	AggregateType string    `json:"aggregate_type"`
	AggregateID   string    `json:"aggregate_id"`
	PayloadJSON   string    `json:"payload"`
	CreatedAt     time.Time `json:"created_at"`
}


