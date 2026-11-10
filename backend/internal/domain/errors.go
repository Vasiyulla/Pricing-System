package domain

import "errors"

// Typed domain errors mapped cleanly to HTTP status codes at transport boundaries.
var (
	ErrNotFound     = errors.New("resource not found")
	ErrUnauthorized = errors.New("unauthorized")
	ErrForbidden    = errors.New("forbidden")
	ErrValidation   = errors.New("validation failed")
	ErrConflict     = errors.New("resource conflict")
	ErrRateLimited  = errors.New("rate limit exceeded")
)

