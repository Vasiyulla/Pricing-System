package handler

import (
	"encoding/json"
	"errors"
	"net/http"

	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/dto"
	"pricebridge/backend/internal/transport/http/respond"
)

type AIHandler struct {
	aiSvc *service.AIService
}

func NewAIHandler(aiSvc *service.AIService) *AIHandler {
	return &AIHandler{aiSvc: aiSvc}
}

func (h *AIHandler) ScanReceipt(w http.ResponseWriter, r *http.Request) {
	var req dto.ImageScanRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	extracted, err := h.aiSvc.ParseReceiptImage(r.Context(), req.ImageBase64)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, extracted)
}

func (h *AIHandler) ScanYellowSticker(w http.ResponseWriter, r *http.Request) {
	var req dto.ImageScanRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.ImageBase64 == "" {
		respond.Error(w, r, http.StatusBadRequest, "image_base64 is required")
		return
	}

	deal, err := h.aiSvc.ParseYellowSticker(r.Context(), req.ImageBase64)
	if err != nil {
		if errors.Is(err, service.ErrVisionAINotConfigured) {
			respond.Error(w, r, http.StatusServiceUnavailable, err.Error())
			return
		}
		respond.Error(w, r, http.StatusBadGateway, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, deal)
}

func (h *AIHandler) ScanShelfTag(w http.ResponseWriter, r *http.Request) {
	var req dto.ImageScanRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.ImageBase64 == "" {
		respond.Error(w, r, http.StatusBadRequest, "image_base64 is required")
		return
	}

	extracted, err := h.aiSvc.ParseShelfTagImage(r.Context(), req.ImageBase64)
	if err != nil {
		if errors.Is(err, service.ErrVisionAINotConfigured) {
			respond.Error(w, r, http.StatusServiceUnavailable, err.Error())
			return
		}
		respond.Error(w, r, http.StatusBadGateway, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, extracted)
}
