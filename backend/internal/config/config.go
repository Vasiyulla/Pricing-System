package config

import (
	"fmt"
	"os"
	"strings"
)

// Config holds strongly-typed runtime configuration validated once at process startup.
type Config struct {
	Env                string
	Port               string
	DatabaseURL        string
	RedisURL           string
	NeonAuthURL        string
	AdminSecret        string
	CORSAllowedOrigins []string
	GeminiAPIKey       string
	NvidiaAPIKey       string
	NvidiaModel        string
	S3Endpoint         string
	S3Bucket           string
	LocalUploadDir     string
}

// Load parses environment variables with safe defaults and startup checks.
func Load() (*Config, error) {
	port := strings.TrimSpace(os.Getenv("PORT"))
	if port == "" {
		port = "8080"
	}

	env := strings.TrimSpace(os.Getenv("APP_ENV"))
	if env == "" {
		env = "production"
	}

	neonURL := strings.TrimSpace(os.Getenv("NEON_AUTH_URL"))
	if neonURL == "" {
		neonURL = "https://ep-round-mouse-b3zuv0r3.neonauth.c-4.ap-southeast-1.aws.neon.tech/neondb/auth"
	}

	nvidiaModel := strings.TrimSpace(os.Getenv("NVIDIA_MODEL"))
	if nvidiaModel == "" {
		nvidiaModel = "meta/llama-3.2-11b-vision-instruct"
	}

	corsRaw := strings.TrimSpace(os.Getenv("CORS_ALLOWED_ORIGINS"))
	var corsOrigins []string
	if corsRaw != "" {
		for _, o := range strings.Split(corsRaw, ",") {
			if trimmed := strings.TrimSpace(o); trimmed != "" {
				corsOrigins = append(corsOrigins, trimmed)
			}
		}
	}
	if len(corsOrigins) == 0 {
		// Mobile clients do not send Origin; restrict browser origins by default unless configured
		corsOrigins = []string{"http://localhost:3000", "http://localhost:8080"}
	}

	uploadDir := strings.TrimSpace(os.Getenv("LOCAL_UPLOAD_DIR"))
	if uploadDir == "" {
		uploadDir = "./data/evidence"
	}

	cfg := &Config{
		Env:                env,
		Port:               port,
		DatabaseURL:        strings.TrimSpace(os.Getenv("DATABASE_URL")),
		RedisURL:           strings.TrimSpace(os.Getenv("REDIS_URL")),
		NeonAuthURL:        neonURL,
		AdminSecret:        strings.TrimSpace(os.Getenv("ADMIN_SECRET")),
		CORSAllowedOrigins: corsOrigins,
		GeminiAPIKey:       strings.TrimSpace(os.Getenv("GEMINI_API_KEY")),
		NvidiaAPIKey:       strings.TrimSpace(os.Getenv("NVIDIA_API_KEY")),
		NvidiaModel:        nvidiaModel,
		S3Endpoint:         strings.TrimSpace(os.Getenv("S3_ENDPOINT")),
		S3Bucket:           strings.TrimSpace(os.Getenv("S3_BUCKET")),
		LocalUploadDir:     uploadDir,
	}

	if err := cfg.Validate(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// Validate verifies required configuration constraints at startup.
func (c *Config) Validate() error {
	if c.Port == "" {
		return fmt.Errorf("PORT cannot be empty")
	}
	return nil
}

