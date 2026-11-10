package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// NeonAuthUser represents a verified user record returned by Neon Auth.
type NeonAuthUser struct {
	Token string `json:"token"`
	User  struct {
		ID    string `json:"id"`
		Name  string `json:"name"`
		Email string `json:"email"`
	} `json:"user"`
}

// NeonAuthError carries the HTTP status code and message from Neon Auth.
type NeonAuthError struct {
	StatusCode int
	Message    string
}

func (e *NeonAuthError) Error() string {
	return e.Message
}

// NeonAuthClient encapsulates HTTP communication with Neon PostgreSQL Auth.
type NeonAuthClient struct {
	baseURL    string
	httpClient *http.Client
}

func NewNeonAuthClient(baseURL string) *NeonAuthClient {
	return &NeonAuthClient{
		baseURL: strings.TrimRight(baseURL, "/"),
		httpClient: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func (c *NeonAuthClient) SignUp(ctx context.Context, name, email, password string) (*NeonAuthUser, error) {
	payload, _ := json.Marshal(map[string]string{
		"name":     name,
		"email":    email,
		"password": password,
	})
	return c.postAuth(ctx, "/sign-up/email", payload)
}

func (c *NeonAuthClient) SignIn(ctx context.Context, email, password string) (*NeonAuthUser, error) {
	payload, _ := json.Marshal(map[string]string{
		"email":    email,
		"password": password,
	})
	return c.postAuth(ctx, "/sign-in/email", payload)
}

func (c *NeonAuthClient) postAuth(ctx context.Context, path string, payload []byte) (*NeonAuthUser, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+path, bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Origin", "http://localhost:8080")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("neon auth connection failed: %w", err)
	}
	defer resp.Body.Close()

	bodyBytes, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode >= 400 {
		var errData struct {
			Message string `json:"message"`
		}
		_ = json.Unmarshal(bodyBytes, &errData)
		msg := errData.Message
		if msg == "" {
			msg = string(bodyBytes)
		}
		return nil, &NeonAuthError{StatusCode: resp.StatusCode, Message: msg}
	}

	var parsed NeonAuthUser
	if err := json.Unmarshal(bodyBytes, &parsed); err != nil {
		return nil, fmt.Errorf("malformed response from neon auth: %w", err)
	}
	return &parsed, nil
}

// GoogleClaims holds verified Google OAuth ID token claims.
type GoogleClaims struct {
	Email   string `json:"email"`
	Name    string `json:"name"`
	Picture string `json:"picture"`
	Sub     string `json:"sub"`
}

// GoogleOAuthClient verifies Google ID tokens against Google's tokeninfo endpoint.
type GoogleOAuthClient struct {
	httpClient *http.Client
}

func NewGoogleOAuthClient() *GoogleOAuthClient {
	return &GoogleOAuthClient{
		httpClient: &http.Client{Timeout: 5 * time.Second},
	}
}

func (c *GoogleOAuthClient) VerifyIDToken(ctx context.Context, idToken string) (*GoogleClaims, error) {
	verifyURL := "https://oauth2.googleapis.com/tokeninfo?id_token=" + url.QueryEscape(strings.TrimSpace(idToken))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, verifyURL, nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("tokeninfo returned status %d", resp.StatusCode)
	}

	var claims GoogleClaims
	if err := json.NewDecoder(io.LimitReader(resp.Body, 1<<16)).Decode(&claims); err != nil {
		return nil, err
	}
	if claims.Email == "" || claims.Sub == "" {
		return nil, fmt.Errorf("incomplete token claims")
	}
	return &claims, nil
}

