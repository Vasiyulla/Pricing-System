package service

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"pricebridge/backend/internal/domain"
)

// ErrVisionAINotConfigured indicates no upstream multimodal AI API key is configured.
var ErrVisionAINotConfigured = errors.New("vision AI API key is not configured")

type AIService struct {
	geminiAPIKey string
	nvidiaAPIKey string
	nvidiaModel  string
	httpClient   *http.Client
}

func NewAIService() *AIService {
	geminiKey := os.Getenv("GEMINI_API_KEY")
	nvidiaKey := os.Getenv("NVIDIA_API_KEY")
	model := os.Getenv("NVIDIA_MODEL")
	if model == "" {
		model = "meta/llama-3.2-11b-vision-instruct"
	}
	return &AIService{
		geminiAPIKey: geminiKey,
		nvidiaAPIKey: nvidiaKey,
		nvidiaModel:  model,
		httpClient: &http.Client{
			Timeout: 45 * time.Second,
		},
	}
}

// ParseReceiptImage extracts items, prices, VAT, and loyalty discounts from a supermarket receipt
func (s *AIService) ParseReceiptImage(ctx context.Context, imageBase64 string) (*domain.ExtractedReceipt, error) {
	if imageBase64 != "" {
		// Priority 1: NVIDIA NIM (Open-Source Llama 3.2 Vision / Qwen 2.5 VL via Cloud API)
		if s.nvidiaAPIKey != "" {
			result, err := s.callNvidiaNIMForReceipt(ctx, imageBase64)
			if err == nil && result != nil {
				return result, nil
			}
			log.Printf("[AIService] NVIDIA NIM error: %v. Attempting fallback...", err)
		}

		// Priority 2: Google Gemini Vision
		if s.geminiAPIKey != "" {
			result, err := s.callGeminiVisionForReceipt(ctx, imageBase64)
			if err == nil && result != nil {
				return result, nil
			}
			log.Printf("[AIService] Gemini Vision error: %v. Falling back to mock...", err)
		}
	}

	// High-fidelity fallback / mock data for UK Supermarket receipt testing
	return &domain.ExtractedReceipt{
		StoreName:      "Tesco Express",
		BranchNumber:   "2214 - Camden High St",
		VATNumber:      "GB 220 4302 31",
		Date:           time.Now().Format("02/01/2006 14:32"),
		TotalPence:     1185,
		VATAmountPence: 45,
		IsMathValid:    true,
		Items: []domain.ExtractedLineItem{
			{
				Name:              "Lurpak Slightly Salted Spreadable Butter 500g",
				Quantity:          1,
				RegularPricePence: 450,
				LoyaltyPricePence: intPtr(300),
				LoyaltyScheme:     domain.LoyaltyClubcard,
				VATRatePercent:    0,
			},
			{
				Name:              "Cathedral City Mature Cheddar 350g",
				Quantity:          1,
				RegularPricePence: 380,
				LoyaltyPricePence: intPtr(250),
				LoyaltyScheme:     domain.LoyaltyClubcard,
				VATRatePercent:    0,
			},
			{
				Name:              "British Whole Milk 4 Pints 2.27L",
				Quantity:          1,
				RegularPricePence: 165,
				LoyaltyPricePence: nil,
				LoyaltyScheme:     domain.LoyaltyNone,
				VATRatePercent:    0,
			},
			{
				Name:              "Warburtons Toastie Thick White 800g",
				Quantity:          1,
				RegularPricePence: 145,
				LoyaltyPricePence: nil,
				LoyaltyScheme:     domain.LoyaltyNone,
				VATRatePercent:    0,
			},
			{
				Name:              "Tesco Everyday Carrier Bag",
				Quantity:          1,
				RegularPricePence: 45,
				LoyaltyPricePence: nil,
				LoyaltyScheme:     domain.LoyaltyNone,
				VATRatePercent:    20, // Carrier bags carry standard 20% VAT in the UK
			},
		},
	}, nil
}

// ParseYellowSticker extracts clearance markdown information from a yellow sticker photo using Vision AI.
// Never returns fake mock data when Vision AI keys are unconfigured or fail.
func (s *AIService) ParseYellowSticker(ctx context.Context, imageBase64 string) (*domain.YellowStickerDeal, error) {
	cleanBase64 := strings.TrimSpace(imageBase64)
	if cleanBase64 == "" {
		return nil, fmt.Errorf("empty image payload")
	}
	if s.nvidiaAPIKey == "" && s.geminiAPIKey == "" {
		return nil, ErrVisionAINotConfigured
	}

	prompt := `You are an expert UK supermarket yellow-sticker clearance markdown parser.
Extract accurate product and markdown pricing details from the sticker photo:
- store_name: supermarket name if visible (e.g. "Tesco", "Sainsbury's", "M&S", "Waitrose", "Asda"), else ""
- product_name: full product title on sticker
- was_price_pence: original price in pence (e.g. £6.50 -> 650)
- now_price_pence: reduced clearance price in pence (e.g. £1.95 -> 195)
- discount_percent: integer 0-99
- use_by_date: e.g. "Today (Reduced to clear)" or printed date
Respond strictly with valid JSON without markdown formatting:
{
  "store_name": "",
  "product_name": "...",
  "was_price_pence": 0,
  "now_price_pence": 0,
  "discount_percent": 0,
  "use_by_date": "Today"
}`

	type yellowStickerAI struct {
		StoreName       string `json:"store_name"`
		ProductName     string `json:"product_name"`
		WasPricePence   int    `json:"was_price_pence"`
		NowPricePence   int    `json:"now_price_pence"`
		DiscountPercent int    `json:"discount_percent"`
		UseByDate       string `json:"use_by_date"`
	}

	parseResp := func(raw []byte) *domain.YellowStickerDeal {
		var parsed yellowStickerAI
		if err := json.Unmarshal(raw, &parsed); err != nil || strings.TrimSpace(parsed.ProductName) == "" || parsed.NowPricePence <= 0 {
			return nil
		}
		disc := parsed.DiscountPercent
		if disc <= 0 && parsed.WasPricePence > parsed.NowPricePence {
			disc = ((parsed.WasPricePence - parsed.NowPricePence) * 100) / parsed.WasPricePence
		}
		return &domain.YellowStickerDeal{
			ID:              fmt.Sprintf("ys_%d", time.Now().UnixNano()),
			StoreName:       parsed.StoreName,
			ProductName:     parsed.ProductName,
			WasPricePence:   parsed.WasPricePence,
			NowPricePence:   parsed.NowPricePence,
			DiscountPercent: disc,
			UseByDate:       parsed.UseByDate,
			ExpiresAt:       time.Now().Add(3 * time.Hour),
		}
	}

	if s.nvidiaAPIKey != "" {
		if raw, err := s.callNvidiaVisionJSON(ctx, cleanBase64, prompt, 500); err == nil {
			if deal := parseResp(raw); deal != nil {
				return deal, nil
			}
		} else {
			log.Printf("[AIService] NVIDIA NIM yellow-sticker error: %v", err)
		}
	}

	if s.geminiAPIKey != "" {
		if raw, err := s.callGeminiVisionJSON(ctx, cleanBase64, prompt); err == nil {
			if deal := parseResp(raw); deal != nil {
				return deal, nil
			}
		} else {
			log.Printf("[AIService] Gemini Vision yellow-sticker error: %v", err)
		}
	}

	return nil, fmt.Errorf("yellow sticker vision AI extraction failed")
}

// ParseShelfTagImage extracts product, single-unit price, pack size, and store/loyalty info from a shelf tag or product screenshot.
// Unlike ParseReceiptImage, it NEVER returns mock data when Vision AI keys are unconfigured or fail.
func (s *AIService) ParseShelfTagImage(ctx context.Context, imageBase64 string) (*domain.ExtractedShelfTag, error) {
	cleanBase64 := strings.TrimSpace(imageBase64)
	if cleanBase64 == "" {
		return nil, fmt.Errorf("empty image payload")
	}
	if s.nvidiaAPIKey == "" && s.geminiAPIKey == "" {
		return nil, ErrVisionAINotConfigured
	}

	prompt := `You are an expert UK supermarket shelf-tag and product-listing vision parser.
Extract accurate product and single-unit price details from the image.
Rules:
- product_name: full clear product title including flavour/variant and pack size if printed (ignore browser tabs, ratings, "Add" buttons, or clipped background text).
- brand: manufacturer/brand name if obvious, else "".
- unit: pack size such as "200g", "500g", "1kg", "1L", "250ml", "4 pack", or "".
- price_pence: regular single-item selling price in pence (e.g. £1.50 -> 150, 75p -> 75). NEVER pick per-unit comparison rates like "£7.50/kg", star ratings ("4 stars"), review counts ("59 Reviews"), or multi-buy denominators ("5 for £5") as price_pence.
- loyalty_price_pence: integer pence if a distinct loyalty single-unit price is shown, else null.
- loyalty_scheme: "CLUBCARD", "NECTAR", "LIDL_PLUS", or "NONE".
- store_name: supermarket name if visible OR inferred from loyalty branding (e.g. "Clubcard" -> "Tesco", "Nectar" -> "Sainsbury's", "Lidl Plus" -> "Lidl"), else "".
- confidence: integer 0-100 reflecting true legibility.
Respond strictly with valid JSON without markdown code blocks:
{
  "product_name": "...",
  "brand": "",
  "unit": "",
  "price_pence": 0,
  "loyalty_price_pence": null,
  "loyalty_scheme": "NONE",
  "store_name": "",
  "confidence": 90
}`

	if s.nvidiaAPIKey != "" {
		raw, err := s.callNvidiaVisionJSON(ctx, cleanBase64, prompt, 600)
		if err == nil {
			var tag domain.ExtractedShelfTag
			if uErr := json.Unmarshal(raw, &tag); uErr == nil && strings.TrimSpace(tag.ProductName) != "" {
				tag.Source = "nvidia_nim"
				return &tag, nil
			} else if uErr != nil {
				log.Printf("[AIService] NVIDIA shelf-tag JSON parse error: %v", uErr)
			}
		} else {
			log.Printf("[AIService] NVIDIA NIM shelf-tag error: %v", err)
		}
	}

	if s.geminiAPIKey != "" {
		raw, err := s.callGeminiVisionJSON(ctx, cleanBase64, prompt)
		if err == nil {
			var tag domain.ExtractedShelfTag
			if uErr := json.Unmarshal(raw, &tag); uErr == nil && strings.TrimSpace(tag.ProductName) != "" {
				tag.Source = "gemini_vision"
				return &tag, nil
			} else if uErr != nil {
				log.Printf("[AIService] Gemini shelf-tag JSON parse error: %v", uErr)
			}
		} else {
			log.Printf("[AIService] Gemini Vision shelf-tag error: %v", err)
		}
	}

	return nil, fmt.Errorf("vision AI extraction failed")
}

func (s *AIService) callGeminiVisionForReceipt(ctx context.Context, imageBase64 string) (*domain.ExtractedReceipt, error) {
	prompt := `You are an expert UK supermarket receipt OCR parser.
Extract the store name, branch/store number, VAT number, receipt date, total in pence, and line items.
For each line item extract:
- name: string
- quantity: int
- regular_price_pence: int
- loyalty_price_pence: int or null (e.g. Tesco Clubcard / Sainsbury's Nectar savings)
- loyalty_scheme: 'CLUBCARD', 'NECTAR', 'LIDL_PLUS', or 'NONE'
- vat_rate_percent: 0 for food/produce, 20 for non-food
Respond strictly with valid JSON matching this schema:
{
  "store_name": "...",
  "branch_number": "...",
  "vat_number": "...",
  "date": "...",
  "total_pence": 0,
  "vat_amount_pence": 0,
  "items": [
    {
      "name": "...",
      "quantity": 1,
      "regular_price_pence": 0,
      "loyalty_price_pence": null,
      "loyalty_scheme": "NONE",
      "vat_rate_percent": 0
    }
  ],
  "is_math_valid": true
}`

	raw, err := s.callGeminiVisionJSON(ctx, imageBase64, prompt)
	if err != nil {
		return nil, err
	}
	var extracted domain.ExtractedReceipt
	if err := json.Unmarshal(raw, &extracted); err != nil {
		return nil, err
	}
	return &extracted, nil
}

func (s *AIService) callNvidiaNIMForReceipt(ctx context.Context, imageBase64 string) (*domain.ExtractedReceipt, error) {
	prompt := `You are an expert UK supermarket receipt OCR parser.
Extract the store name, branch/store number, VAT number, receipt date, total in pence, and line items.
For each line item extract:
- name: string
- quantity: int
- regular_price_pence: int
- loyalty_price_pence: int or null (e.g. Tesco Clubcard / Sainsbury's Nectar savings)
- loyalty_scheme: 'CLUBCARD', 'NECTAR', 'LIDL_PLUS', or 'NONE'
- vat_rate_percent: 0 for food/produce, 20 for non-food
Respond strictly with valid JSON without any markdown formatting or code blocks:
{
  "store_name": "...",
  "branch_number": "...",
  "vat_number": "...",
  "date": "...",
  "total_pence": 0,
  "vat_amount_pence": 0,
  "items": [
    {
      "name": "...",
      "quantity": 1,
      "regular_price_pence": 0,
      "loyalty_price_pence": null,
      "loyalty_scheme": "NONE",
      "vat_rate_percent": 0
    }
  ],
  "is_math_valid": true
}`

	raw, err := s.callNvidiaVisionJSON(ctx, imageBase64, prompt, 1500)
	if err != nil {
		return nil, err
	}
	var extracted domain.ExtractedReceipt
	if err := json.Unmarshal(raw, &extracted); err != nil {
		return nil, fmt.Errorf("failed to unmarshal nvidia nim json: %w (raw: %s)", err, string(raw))
	}
	return &extracted, nil
}

func stripBase64DataPrefix(imageBase64 string) string {
	if idx := strings.Index(imageBase64, ","); idx != -1 {
		return imageBase64[idx+1:]
	}
	return imageBase64
}

func stripMarkdownJSONFence(content string) string {
	content = strings.TrimSpace(content)
	if strings.HasPrefix(content, "```json") {
		content = strings.TrimPrefix(content, "```json")
		content = strings.TrimSuffix(content, "```")
	} else if strings.HasPrefix(content, "```") {
		content = strings.TrimPrefix(content, "```")
		content = strings.TrimSuffix(content, "```")
	}
	return strings.TrimSpace(content)
}

func (s *AIService) callGeminiVisionJSON(ctx context.Context, imageBase64, prompt string) ([]byte, error) {
	cleanBase64 := stripBase64DataPrefix(imageBase64)
	url := fmt.Sprintf("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=%s", s.geminiAPIKey)

	requestBody := map[string]interface{}{
		"contents": []map[string]interface{}{
			{
				"parts": []map[string]interface{}{
					{"text": prompt},
					{
						"inlineData": map[string]string{
							"mimeType": "image/jpeg",
							"data":     cleanBase64,
						},
					},
				},
			},
		},
		"generationConfig": map[string]interface{}{
			"responseMimeType": "application/json",
		},
	}

	bodyBytes, err := json.Marshal(requestBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("gemini api error status %d: %s", resp.StatusCode, string(respBytes))
	}

	var geminiResp struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
		} `json:"candidates"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&geminiResp); err != nil {
		return nil, err
	}
	if len(geminiResp.Candidates) == 0 || len(geminiResp.Candidates[0].Content.Parts) == 0 {
		return nil, fmt.Errorf("empty response from gemini")
	}

	return []byte(stripMarkdownJSONFence(geminiResp.Candidates[0].Content.Parts[0].Text)), nil
}

func (s *AIService) callNvidiaVisionJSON(ctx context.Context, imageBase64, prompt string, maxTokens int) ([]byte, error) {
	cleanBase64 := stripBase64DataPrefix(imageBase64)
	url := "https://integrate.api.nvidia.com/v1/chat/completions"
	imageURI := fmt.Sprintf("data:image/jpeg;base64,%s", cleanBase64)

	requestBody := map[string]interface{}{
		"model": s.nvidiaModel,
		"messages": []map[string]interface{}{
			{
				"role": "user",
				"content": []map[string]interface{}{
					{"type": "text", "text": prompt},
					{
						"type": "image_url",
						"image_url": map[string]string{
							"url": imageURI,
						},
					},
				},
			},
		},
		"temperature": 0.1,
		"max_tokens":  maxTokens,
	}

	bodyBytes, err := json.Marshal(requestBody)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(bodyBytes))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+s.nvidiaAPIKey)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		respBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("nvidia nim api error status %d: %s", resp.StatusCode, string(respBytes))
	}

	var openAIResp struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&openAIResp); err != nil {
		return nil, err
	}
	if len(openAIResp.Choices) == 0 {
		return nil, fmt.Errorf("empty response from nvidia nim")
	}

	return []byte(stripMarkdownJSONFence(openAIResp.Choices[0].Message.Content)), nil
}

func intPtr(v int) *int {
	return &v
}

