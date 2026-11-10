package handler

import (
	"net/http"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/middleware"
)

type UserHandler struct {
	sessionRepo repository.SessionRepository
	priceRepo   repository.PriceRepository
}

func NewUserHandler(sessionRepo repository.SessionRepository, priceRepo repository.PriceRepository) *UserHandler {
	return &UserHandler{
		sessionRepo: sessionRepo,
		priceRepo:   priceRepo,
	}
}

func (h *UserHandler) GetProfile(w http.ResponseWriter, r *http.Request) {
	sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData)
	if !ok || sess.UserID == "" {
		respond.Error(w, r, http.StatusUnauthorized, "authentication required")
		return
	}

	if h.sessionRepo != nil {
		if prof, err := h.sessionRepo.GetUserProfile(r.Context(), sess.UserID); err == nil && prof != nil {
			respond.JSON(w, http.StatusOK, prof)
			return
		}
	}

	respond.JSON(w, http.StatusOK, domain.UserProfileRecord{
		ID:          sess.UserID,
		Email:       sess.Email,
		DisplayName: sess.DisplayName,
		Role:        sess.Role,
		KarmaPoints: 50,
	})
}

func (h *UserHandler) GetSubmissions(w http.ResponseWriter, r *http.Request) {
	sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData)
	if !ok || sess.UserID == "" {
		respond.Error(w, r, http.StatusUnauthorized, "authentication required")
		return
	}

	records, err := h.priceRepo.GetSubmissionsByUser(r.Context(), sess.UserID)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if records == nil {
		records = []domain.PriceRecord{}
	}
	respond.JSON(w, http.StatusOK, records)
}
