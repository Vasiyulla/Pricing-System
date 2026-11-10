package handler

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"image"
	"image/jpeg"
	_ "image/png"
	"net/http"
	"strings"
	"time"

	"github.com/google/uuid"

	"pricebridge/backend/internal/domain"
	"pricebridge/backend/internal/repository"
	"pricebridge/backend/internal/repository/storage"
	"pricebridge/backend/internal/transport/http/respond"
	"pricebridge/backend/internal/transport/http/dto"
	"pricebridge/backend/internal/transport/http/middleware"
)

type EvidenceHandler struct {
	evidenceRepo repository.EvidenceRepository
	objectStore  storage.ObjectStore
}

func NewEvidenceHandler(evidenceRepo repository.EvidenceRepository, objectStore storage.ObjectStore) *EvidenceHandler {
	return &EvidenceHandler{
		evidenceRepo: evidenceRepo,
		objectStore:  objectStore,
	}
}

func (h *EvidenceHandler) Upload(w http.ResponseWriter, r *http.Request) {
	var req dto.UploadEvidenceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || strings.TrimSpace(req.ImageBase64) == "" {
		respond.Error(w, r, http.StatusBadRequest, "image_base64 is required")
		return
	}

	cleanB64 := req.ImageBase64
	if idx := strings.Index(cleanB64, ","); idx != -1 {
		cleanB64 = cleanB64[idx+1:]
	}
	rawBytes, err := base64.StdEncoding.DecodeString(cleanB64)
	if err != nil {
		respond.Error(w, r, http.StatusBadRequest, "invalid base64 image payload")
		return
	}

	// Re-encode via standard library image package to strip EXIF GPS/device metadata
	exifStripped := false
	if img, _, decErr := image.Decode(bytes.NewReader(rawBytes)); decErr == nil {
		var buf bytes.Buffer
		if encErr := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 80}); encErr == nil {
			rawBytes = buf.Bytes()
			exifStripped = true
		}
	}

	hashBytes := sha256.Sum256(rawBytes)
	imgHash := hex.EncodeToString(hashBytes[:])

	var userID string
	if sess, ok := r.Context().Value(middleware.UserContextKey).(middleware.SessionData); ok {
		userID = sess.UserID
	}

	sourceType := req.SourceType
	if sourceType == "" {
		sourceType = "shelf_label"
	}

	objectKey := fmt.Sprintf("%s/%s.jpg", time.Now().UTC().Format("2006/01/02"), uuid.New().String())
	evidenceURL := ""
	if h.objectStore != nil {
		url, storeErr := h.objectStore.PutObject(r.Context(), objectKey, "image/jpeg", rawBytes)
		if storeErr != nil {
			respond.Error(w, r, http.StatusInternalServerError, "object storage upload failed: "+storeErr.Error())
			return
		}
		evidenceURL = url
	}

	ev := domain.EvidenceRecord{
		UserID:       userID,
		ProductID:    req.ProductID,
		StoreID:      req.StoreID,
		SourceType:   sourceType,
		ObjectKey:    objectKey,
		EvidenceURL:  evidenceURL,
		ImageHash:    imgHash,
		ExifStripped: exifStripped,
		OcrRawText:   req.OcrRawText,
	}

	if h.evidenceRepo == nil {
		respond.Error(w, r, http.StatusInternalServerError, "evidence repository not configured")
		return
	}

	evID, err := h.evidenceRepo.SaveEvidence(r.Context(), ev)
	if err != nil {
		respond.Error(w, r, http.StatusInternalServerError, "failed to store evidence metadata: "+err.Error())
		return
	}

	respond.JSON(w, http.StatusCreated, map[string]any{
		"evidence_id":   evID,
		"evidence_url":  evidenceURL,
		"image_hash":    imgHash,
		"exif_stripped": exifStripped,
	})
}
