package handler

import (
	"encoding/json"
	"net/http"

	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
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
	_ = json.NewDecoder(r.Body).Decode(&req)

	deal, err := h.aiSvc.ParseYellowSticker(r.Context(), req.ImageBase64)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, deal)
}
