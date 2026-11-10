package storage

import (
	"bytes"
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// ObjectStore abstracts binary blob storage (S3/R2 in production, local disk in dev).
type ObjectStore interface {
	PutObject(ctx context.Context, key, contentType string, data []byte) (string, error)
}

// NewObjectStore selects an S3/R2-compatible HTTP store if configured, otherwise LocalDiskObjectStore.
func NewObjectStore(s3Endpoint, s3Bucket, localDir string) ObjectStore {
	if s3Endpoint != "" && s3Bucket != "" {
		return &HTTPPutObjectStore{
			endpoint:   strings.TrimRight(s3Endpoint, "/"),
			bucket:     strings.Trim(s3Bucket, "/"),
			httpClient: &http.Client{Timeout: 10 * time.Second},
		}
	}
	return &LocalDiskObjectStore{baseDir: localDir}
}

// LocalDiskObjectStore stores EXIF-stripped evidence images cleanly on filesystem outside PostgreSQL.
type LocalDiskObjectStore struct {
	baseDir string
}

func (s *LocalDiskObjectStore) PutObject(_ context.Context, key, _ string, data []byte) (string, error) {
	cleanKey := filepath.Clean(key)
	if strings.Contains(cleanKey, "..") {
		return "", fmt.Errorf("invalid object key")
	}
	fullPath := filepath.Join(s.baseDir, cleanKey)
	if err := os.MkdirAll(filepath.Dir(fullPath), 0o750); err != nil {
		return "", err
	}
	if err := os.WriteFile(fullPath, data, 0o640); err != nil {
		return "", err
	}
	return "/media/evidence/" + filepath.ToSlash(cleanKey), nil
}

// HTTPPutObjectStore uploads objects to an S3/R2-compatible HTTP endpoint.
type HTTPPutObjectStore struct {
	endpoint   string
	bucket     string
	httpClient *http.Client
}

func (s *HTTPPutObjectStore) PutObject(ctx context.Context, key, contentType string, data []byte) (string, error) {
	targetURL := fmt.Sprintf("%s/%s/%s", s.endpoint, s.bucket, strings.TrimLeft(key, "/"))
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, targetURL, bytes.NewReader(data))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", contentType)
	resp, err := s.httpClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		return "", fmt.Errorf("object store returned status %d", resp.StatusCode)
	}
	return targetURL, nil
}

