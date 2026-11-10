package handler

import (
	"encoding/json"
	"net/http"

	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
)

type BasketHandler struct {
	optimizerSvc *service.OptimizerService
}

func NewBasketHandler(optimizerSvc *service.OptimizerService) *BasketHandler {
	return &BasketHandler{optimizerSvc: optimizerSvc}
}

func (h *BasketHandler) Optimize(w http.ResponseWriter, r *http.Request) {
	var req dto.BasketOptimizeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid basket request")
		return
	}

	if req.UserLat == 0 && req.UserLon == 0 {
		req.UserLat = 51.5390
		req.UserLon = -0.1426
	}

	result, err := h.optimizerSvc.OptimizeTwoStopBasket(r.Context(), req.Items, req.UserLat, req.UserLon)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}

	respond.JSON(w, http.StatusOK, result)
}
