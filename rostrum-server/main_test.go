package main

import (
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"
)

func TestResolvePathRejectsOutsideWorkspace(t *testing.T) {
	workspace := t.TempDir()
	server := &RostrumServer{config: Config{Workspace: workspace}, started: time.Now()}

	if _, err := server.resolvePath(filepath.Join(workspace, "child.txt")); err != nil {
		t.Fatalf("expected workspace child path to resolve: %v", err)
	}

	outside := filepath.Join(workspace, "..", "outside.txt")
	if _, err := server.resolvePath(outside); err == nil {
		t.Fatal("expected path outside workspace to be rejected")
	}
}

func TestAuthMiddlewareRequiresBearerToken(t *testing.T) {
	server := &RostrumServer{config: Config{Token: "secret"}, started: time.Now()}
	called := false
	handler := server.authMiddleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		w.WriteHeader(http.StatusNoContent)
	}))

	unauthorized := httptest.NewRecorder()
	handler.ServeHTTP(unauthorized, httptest.NewRequest(http.MethodGet, "/api/ping", nil))
	if unauthorized.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 without token, got %d", unauthorized.Code)
	}
	if called {
		t.Fatal("handler should not be called without a valid token")
	}

	authorized := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodGet, "/api/ping", nil)
	request.Header.Set("Authorization", "Bearer secret")
	handler.ServeHTTP(authorized, request)
	if authorized.Code != http.StatusNoContent {
		t.Fatalf("expected authorized request to pass, got %d", authorized.Code)
	}
	if !called {
		t.Fatal("handler should be called with a valid token")
	}
}

func TestDefaultWorkspaceIsCurrentDirectory(t *testing.T) {
	config := defaultConfig()
	if config.Workspace != "." {
		t.Fatalf("expected default workspace '.', got %q", config.Workspace)
	}
}
