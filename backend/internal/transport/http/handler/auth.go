package handler

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"

	"pricebridge/backend/internal/client"
	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
	"pricebridge/backend/internal/transport/http/middleware"
)

type AuthHandler struct {
	neonClient   *client.NeonAuthClient
	googleClient *client.GoogleOAuthClient
	sessionRepo  repository.SessionRepository
	sessionStore *middleware.SessionStore
}

func NewAuthHandler(
	neonClient *client.NeonAuthClient,
	googleClient *client.GoogleOAuthClient,
	sessionRepo repository.SessionRepository,
	sessionStore *middleware.SessionStore,
) *AuthHandler {
	return &AuthHandler{
		neonClient:   neonClient,
		googleClient: googleClient,
		sessionRepo:  sessionRepo,
		sessionStore: sessionStore,
	}
}

func (h *AuthHandler) SignUp(w http.ResponseWriter, r *http.Request) {
	var req dto.SignUpRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.Email) == "" || req.Password == "" {
		respond.Error(w, r, http.StatusBadRequest, "Invalid request: email and password are required")
		return
	}

	neonResp, err := h.neonClient.SignUp(r.Context(), req.Name, req.Email, req.Password)
	if err != nil {
		var nErr *client.NeonAuthError
		if errors.As(err, &nErr) {
			respond.Error(w, r, nErr.StatusCode, "Sign-up failed: "+nErr.Message)
			return
		}
		respond.Error(w, r, http.StatusBadGateway, err.Error())
		return
	}

	h.finalizeNeonSession(w, r, neonResp, req.Email)
}

func (h *AuthHandler) SignIn(w http.ResponseWriter, r *http.Request) {
	var req dto.SignInRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.Email) == "" || req.Password == "" {
		respond.Error(w, r, http.StatusBadRequest, "Invalid request: email and password are required")
		return
	}

	neonResp, err := h.neonClient.SignIn(r.Context(), req.Email, req.Password)
	if err != nil {
		var nErr *client.NeonAuthError
		if errors.As(err, &nErr) {
			respond.Error(w, r, nErr.StatusCode, "Sign-in failed: "+nErr.Message)
			return
		}
		respond.Error(w, r, http.StatusBadGateway, err.Error())
		return
	}

	h.finalizeNeonSession(w, r, neonResp, req.Email)
}

func (h *AuthHandler) GoogleAuth(w http.ResponseWriter, r *http.Request) {
	var req dto.GoogleAuthRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.IDToken) == "" {
		respond.Error(w, r, http.StatusUnauthorized, "Missing Google ID token")
		return
	}

	claims, err := h.googleClient.VerifyIDToken(r.Context(), req.IDToken)
	if err != nil {
		respond.Error(w, r, http.StatusUnauthorized, "Google token verification failed")
		return
	}

	displayName := claims.Name
	if displayName == "" {
		displayName = strings.Split(claims.Email, "@")[0]
	}

	token := fmt.Sprintf("pb_sess_%s_%d", uuid.New().String(), time.Now().Unix())
	h.issueSession(w, r, claims.Sub, claims.Email, displayName, claims.Picture, "GOOGLE", token)
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	if token, err := middleware.ExtractBearerToken(r); err == nil {
		h.sessionStore.Revoke(r.Context(), token)
	}
	respond.JSON(w, http.StatusOK, map[string]string{"status": "logged_out"})
}

func (h *AuthHandler) finalizeNeonSession(w http.ResponseWriter, r *http.Request, neonResp *client.NeonAuthUser, fallbackEmail string) {
	sessionToken := neonResp.Token
	if sessionToken == "" {
		sessionToken = fmt.Sprintf("neon_token_%s_%d", uuid.New().String(), time.Now().Unix())
	}
	displayName := neonResp.User.Name
	if displayName == "" {
		displayName = strings.Split(fallbackEmail, "@")[0]
	}
	h.issueSession(w, r, neonResp.User.ID, neonResp.User.Email, displayName, "", "NEON_EMAIL", sessionToken)
}

func (h *AuthHandler) issueSession(w http.ResponseWriter, r *http.Request, userID, email, displayName, avatarURL, authMode, token string) {
	karma := 50
	role := "ActiveContributor"
	if h.sessionRepo != nil {
		if upserted, err := h.sessionRepo.UpsertUser(r.Context(), domain.UserProfileRecord{
			ID:          userID,
			Email:       email,
			DisplayName: displayName,
			AvatarURL:   avatarURL,
			Role:        role,
			KarmaPoints: karma,
			AuthMode:    authMode,
		}); err == nil && upserted != nil {
			karma = upserted.KarmaPoints
			if upserted.Role != "" {
				role = upserted.Role
			}
		}
	}

	sess := domain.UserSession{
		ID:          userID,
		Email:       email,
		DisplayName: displayName,
		AvatarURL:   avatarURL,
		Role:        role,
		Token:       token,
		KarmaPoints: karma,
		AuthMode:    authMode,
	}

	h.sessionStore.Save(r.Context(), token, middleware.SessionData{
		UserID:      userID,
		Email:       email,
		DisplayName: displayName,
		Role:        role,
		ExpiresAt:   time.Now().Add(14 * 24 * time.Hour),
	})

	respond.JSON(w, http.StatusOK, sess)
}
