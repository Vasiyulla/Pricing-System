package handler

import (
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/transport/http/respond"
)

type BountyHandler struct {
	bountyRepo   repository.BountyRepository
	categoryRepo repository.CategoryRepository
	stickerRepo  repository.YellowStickerRepository
}

func NewBountyHandler(
	bountyRepo repository.BountyRepository,
	categoryRepo repository.CategoryRepository,
	stickerRepo repository.YellowStickerRepository,
) *BountyHandler {
	return &BountyHandler{
		bountyRepo:   bountyRepo,
		categoryRepo: categoryRepo,
		stickerRepo:  stickerRepo,
	}
}

func (h *BountyHandler) GetNearby(w http.ResponseWriter, r *http.Request) {
	lat, latErr := strconv.ParseFloat(r.URL.Query().Get("lat"), 64)
	lon, lonErr := strconv.ParseFloat(r.URL.Query().Get("lon"), 64)
	if latErr != nil || lonErr != nil || (lat == 0 && lon == 0) {
		respond.JSON(w, http.StatusOK, []domain.Bounty{})
		return
	}

	bounties, err := h.bountyRepo.GetNearby(r.Context(), lat, lon, 10.0)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if bounties == nil {
		bounties = []domain.Bounty{}
	}
	respond.JSON(w, http.StatusOK, bounties)
}

func (h *BountyHandler) Claim(w http.ResponseWriter, r *http.Request) {
	id := chi.URLParam(r, "id")
	if err := h.bountyRepo.Claim(r.Context(), id); err != nil {
		respond.Error(w, r, http.StatusNotFound, err.Error())
		return
	}
	respond.JSON(w, http.StatusOK, map[string]any{
		"status":  "claimed",
		"message": "Bounty claimed! Upload a photo of the shelf label to receive your points.",
	})
}

func (h *BountyHandler) GetActivePrompt(w http.ResponseWriter, r *http.Request) {
	prompt, err := h.bountyRepo.GetActivePrompt(r.Context())
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if prompt == nil {
		respond.JSON(w, http.StatusOK, map[string]any{})
		return
	}
	respond.JSON(w, http.StatusOK, prompt)
}

func (h *BountyHandler) GetCategories(w http.ResponseWriter, r *http.Request) {
	categories, err := h.categoryRepo.GetAll(r.Context())
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if categories == nil {
		categories = []domain.Category{}
	}
	respond.JSON(w, http.StatusOK, categories)
}

func (h *BountyHandler) GetActiveYellowStickers(w http.ResponseWriter, r *http.Request) {
	storeID := r.URL.Query().Get("store_id")
	deals, err := h.stickerRepo.GetActive(r.Context(), storeID)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if deals == nil {
		deals = []domain.YellowStickerDeal{}
	}
	respond.JSON(w, http.StatusOK, deals)
}
