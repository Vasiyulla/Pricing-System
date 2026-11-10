package respond

import (
	"encoding/json"
	"errors"
	"net/http"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/telemetry"
)

// JSON serializes a JSON payload with the given HTTP status code.
func JSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

// Error writes a standardized JSON error response with optional request correlation ID.
func Error(w http.ResponseWriter, r *http.Request, status int, message string) {
	resp := map[string]string{"error": message}
	if r != nil {
		if reqID := telemetry.RequestIDFromContext(r.Context()); reqID != "" {
			resp["request_id"] = reqID
		}
	}
	JSON(w, status, resp)
}

// DomainError maps sentinel domain errors to standard HTTP status codes.
func DomainError(w http.ResponseWriter, r *http.Request, err error) {
	switch {
	case errors.Is(err, domain.ErrNotFound):
		Error(w, r, http.StatusNotFound, err.Error())
	case errors.Is(err, domain.ErrValidation):
		Error(w, r, http.StatusBadRequest, err.Error())
	case errors.Is(err, domain.ErrUnauthorized):
		Error(w, r, http.StatusUnauthorized, err.Error())
	case errors.Is(err, domain.ErrForbidden):
		Error(w, r, http.StatusForbidden, err.Error())
	case errors.Is(err, domain.ErrConflict):
		Error(w, r, http.StatusConflict, err.Error())
	case errors.Is(err, domain.ErrRateLimited):
		Error(w, r, http.StatusTooManyRequests, err.Error())
	default:
		Error(w, r, http.StatusInternalServerError, err.Error())
	}
}

