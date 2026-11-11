package handler

import (
	"net/http"
	"strconv"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/service"
	"pricebridge/backend/internal/transport/http/middleware"
	"pricebridge/backend/internal/transport/http/respond"
)

type UserHandler struct {
	sessionRepo repository.SessionRepository
	priceRepo   repository.PriceRepository
	productRepo repository.ProductRepository
	productSvc  *service.ProductService
}

func NewUserHandler(
	sessionRepo repository.SessionRepository,
	priceRepo repository.PriceRepository,
	productRepo repository.ProductRepository,
	productSvc ...*service.ProductService,
) *UserHandler {
	var ps *service.ProductService
	if len(productSvc) > 0 {
		ps = productSvc[0]
	}
	return &UserHandler{
		sessionRepo: sessionRepo,
		priceRepo:   priceRepo,
		productRepo: productRepo,
		productSvc:  ps,
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

func (h *UserHandler) GetProducts(w http.ResponseWriter, r *http.Request) {
	sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData)
	if !ok || sess.UserID == "" {
		respond.Error(w, r, http.StatusUnauthorized, "authentication required")
		return
	}

	limit := 50
	offset := 0
	if rawLimit := r.URL.Query().Get("limit"); rawLimit != "" {
		if parsed, err := strconv.Atoi(rawLimit); err == nil && parsed > 0 && parsed <= 100 {
			limit = parsed
		}
	}
	if rawOffset := r.URL.Query().Get("offset"); rawOffset != "" {
		if parsed, err := strconv.Atoi(rawOffset); err == nil && parsed >= 0 {
			offset = parsed
		}
	}

	var (
		products []domain.Product
		err      error
	)
	if h.productSvc != nil {
		products, err = h.productSvc.GetUserAddedProducts(r.Context(), sess.UserID, limit, offset)
	} else if h.productRepo != nil {
		products, err = h.productRepo.GetByCreator(r.Context(), sess.UserID, limit, offset)
	}
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	if products == nil {
		products = []domain.Product{}
	}
	respond.JSON(w, http.StatusOK, products)
}
