package main

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

const version = "1.0.0"
const protocolVersion = "1"

type Config struct {
	Port         int    `json:"port"`
	Host         string `json:"host"`
	LogFile      string `json:"log_file"`
	Workspace    string `json:"workspace"`
	MaxFileSize  int64  `json:"max_file_size"`
	AllowCommand bool   `json:"allow_command"`
	Token        string `json:"token"`
}

type FileInfo struct {
	Name    string `json:"name"`
	Path    string `json:"path"`
	Size    int64  `json:"size"`
	IsDir   bool   `json:"is_dir"`
	ModTime string `json:"mod_time"`
	Mode    string `json:"mode"`
}

type FileContentResponse struct {
	Content  string `json:"content"`
	Encoding string `json:"encoding"`
	Path     string `json:"path"`
	Size     int64  `json:"size"`
	ModTime  string `json:"mod_time"`
}

type FileWriteRequest struct {
	Path     string `json:"path"`
	Content  string `json:"content"`
	Encoding string `json:"encoding"`
}

type PathRequest struct {
	Path string `json:"path"`
}

type CopyMoveRequest struct {
	Source string `json:"source"`
	Target string `json:"target"`
}

type CommandRequest struct {
	Command string `json:"command"`
	WorkDir string `json:"work_dir"`
}

type CommandResponse struct {
	Output   string `json:"output"`
	Error    string `json:"error"`
	ExitCode int    `json:"exit_code"`
}

type ErrorResponse struct {
	Error   string `json:"error"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

type RostrumServer struct {
	config  Config
	started time.Time
}

func main() {
	configPath := flag.String("config", "", "path to JSON config file")
	showVersion := flag.Bool("version", false, "print version")
	port := flag.Int("port", 0, "listen port")
	host := flag.String("host", "", "listen host")
	workspace := flag.String("workspace", "", "workspace root")
	logFile := flag.String("log-file", "", "log file path")
	maxFileSize := flag.Int64("max-file-size", 0, "maximum file size in bytes")
	allowCommand := flag.String("allow-command", "", "allow command execution: true or false")
	token := flag.String("token", "", "bearer token required for HTTP requests")
	flag.Parse()

	if *showVersion {
		fmt.Println(version)
		return
	}

	config := defaultConfig()
	applyEnv(&config)
	if *configPath != "" {
		if err := loadConfig(*configPath, &config); err != nil {
			log.Fatalf("failed to load config: %v", err)
		}
	}
	flag.Visit(func(f *flag.Flag) {
		switch f.Name {
		case "port":
			config.Port = *port
		case "host":
			config.Host = *host
		case "workspace":
			config.Workspace = *workspace
		case "log-file":
			config.LogFile = *logFile
		case "max-file-size":
			config.MaxFileSize = *maxFileSize
		case "allow-command":
			config.AllowCommand = parseBool(*allowCommand, config.AllowCommand)
		case "token":
			config.Token = *token
		}
	})

	if config.Port <= 0 {
		log.Fatal("port must be greater than 0")
	}
	if config.Host == "" {
		config.Host = "127.0.0.1"
	}
	if config.Workspace == "" {
		config.Workspace = "/"
	}
	if config.MaxFileSize <= 0 {
		config.MaxFileSize = 10 * 1024 * 1024
	}

	absWorkspace, err := filepath.Abs(config.Workspace)
	if err != nil {
		log.Fatalf("invalid workspace: %v", err)
	}
	config.Workspace = filepath.Clean(absWorkspace)
	if err := os.MkdirAll(config.Workspace, 0755); err != nil {
		log.Fatalf("failed to create workspace: %v", err)
	}

	server := &RostrumServer{config: config, started: time.Now()}
	if err := server.Start(); err != nil {
		log.Fatalf("failed to start rostrum-server: %v", err)
	}
}

func defaultConfig() Config {
	return Config{
		Port:         8080,
		Host:         "127.0.0.1",
		LogFile:      "",
		Workspace:    "/",
		MaxFileSize:  10 * 1024 * 1024,
		AllowCommand: true,
	}
}

func applyEnv(config *Config) {
	if value := os.Getenv("ROSTRUM_PORT"); value != "" {
		if parsed, err := strconv.Atoi(value); err == nil {
			config.Port = parsed
		}
	}
	if value := os.Getenv("ROSTRUM_HOST"); value != "" {
		config.Host = value
	}
	if value := os.Getenv("ROSTRUM_WORKSPACE"); value != "" {
		config.Workspace = value
	}
	if value := os.Getenv("ROSTRUM_LOG_FILE"); value != "" {
		config.LogFile = value
	}
	if value := os.Getenv("ROSTRUM_MAX_FILE_SIZE"); value != "" {
		if parsed, err := strconv.ParseInt(value, 10, 64); err == nil {
			config.MaxFileSize = parsed
		}
	}
	if value := os.Getenv("ROSTRUM_ALLOW_COMMAND"); value != "" {
		config.AllowCommand = parseBool(value, config.AllowCommand)
	}
	if value := os.Getenv("ROSTRUM_TOKEN"); value != "" {
		config.Token = value
	}
}

func loadConfig(path string, config *Config) error {
	file, err := os.Open(path)
	if err != nil {
		return err
	}
	defer file.Close()
	return json.NewDecoder(file).Decode(config)
}

func parseBool(value string, fallback bool) bool {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}

func (rs *RostrumServer) Start() error {
	if rs.config.LogFile != "" {
		logFile, err := os.OpenFile(rs.config.LogFile, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0644)
		if err != nil {
			return fmt.Errorf("failed to open log file: %w", err)
		}
		defer logFile.Close()
		log.SetOutput(logFile)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", rs.handleHealth)
	mux.HandleFunc("/api/ping", rs.handlePing)
	mux.HandleFunc("/api/status", rs.handleStatus)
	mux.HandleFunc("/api/capabilities", rs.handleCapabilities)
	mux.HandleFunc("/api/files/list", rs.handleListFiles)
	mux.HandleFunc("/api/files/read", rs.handleReadFile)
	mux.HandleFunc("/api/files/write", rs.handleWriteFile)
	mux.HandleFunc("/api/files/delete", rs.handleDeleteFile)
	mux.HandleFunc("/api/files/mkdir", rs.handleMkdir)
	mux.HandleFunc("/api/files/copy", rs.handleCopy)
	mux.HandleFunc("/api/files/move", rs.handleMove)
	mux.HandleFunc("/api/files/watch", rs.handleWatch)
	mux.HandleFunc("/api/command/execute", rs.handleExecuteCommand)

	server := &http.Server{
		Addr:              fmt.Sprintf("%s:%d", rs.config.Host, rs.config.Port),
		Handler:           rs.authMiddleware(mux),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("starting rostrum-server %s on %s workspace=%s", version, server.Addr, rs.config.Workspace)
	listener, err := net.Listen("tcp", server.Addr)
	if err != nil {
		return err
	}
	fmt.Printf(
		"ROSTRUM_SERVER_READY host=%s port=%d version=%s protocol=%s pid=%d token=%s\n",
		rs.config.Host,
		rs.config.Port,
		version,
		protocolVersion,
		os.Getpid(),
		tokenRequirement(rs.config.Token),
	)
	return server.Serve(listener)
}

func (rs *RostrumServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":           "ok",
		"service":          "rostrum-server",
		"version":          version,
		"protocol_version": protocolVersion,
		"uptime_ms":        time.Since(rs.started).Milliseconds(),
		"workspace":        rs.config.Workspace,
		"auth":             tokenRequirement(rs.config.Token),
	})
}

func (rs *RostrumServer) handlePing(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "pong"})
}

func (rs *RostrumServer) handleStatus(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]interface{}{
		"status":        "running",
		"version":       version,
		"protocol":      protocolVersion,
		"uptime":        time.Since(rs.started).String(),
		"workspace":     rs.config.Workspace,
		"allow_command": rs.config.AllowCommand,
		"max_file_size": rs.config.MaxFileSize,
	})
}

func (rs *RostrumServer) handleCapabilities(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]interface{}{
		"version":       version,
		"file_read":     true,
		"file_write":    true,
		"copy":          true,
		"move":          true,
		"watch":         false,
		"command":       rs.config.AllowCommand,
		"base64":        true,
		"workspace":     rs.config.Workspace,
		"max_file_size": rs.config.MaxFileSize,
	})
}

func (rs *RostrumServer) handleListFiles(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	path, err := rs.resolvePath(r.URL.Query().Get("path"))
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
		return
	}

	entries, err := os.ReadDir(path)
	if err != nil {
		rs.writeError(w, http.StatusInternalServerError, "list_failed", err.Error())
		return
	}

	files := make([]FileInfo, 0, len(entries))
	for _, entry := range entries {
		info, err := entry.Info()
		if err != nil {
			continue
		}
		files = append(files, FileInfo{
			Name:    entry.Name(),
			Path:    filepath.Join(path, entry.Name()),
			Size:    info.Size(),
			IsDir:   entry.IsDir(),
			ModTime: info.ModTime().Format(time.RFC3339),
			Mode:    info.Mode().String(),
		})
	}
	sort.Slice(files, func(i, j int) bool {
		if files[i].IsDir != files[j].IsDir {
			return files[i].IsDir
		}
		return strings.ToLower(files[i].Name) < strings.ToLower(files[j].Name)
	})
	rs.writeJSON(w, http.StatusOK, files)
}

func (rs *RostrumServer) handleReadFile(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	path, err := rs.resolvePath(r.URL.Query().Get("path"))
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
		return
	}
	info, err := os.Stat(path)
	if err != nil {
		rs.writeError(w, http.StatusInternalServerError, "stat_failed", err.Error())
		return
	}
	if info.IsDir() {
		rs.writeError(w, http.StatusBadRequest, "not_file", "path is a directory")
		return
	}
	if info.Size() > rs.config.MaxFileSize {
		rs.writeError(w, http.StatusRequestEntityTooLarge, "file_too_large", "file exceeds max_file_size")
		return
	}
	content, err := os.ReadFile(path)
	if err != nil {
		rs.writeError(w, http.StatusInternalServerError, "read_failed", err.Error())
		return
	}
	encoding := r.URL.Query().Get("encoding")
	value := string(content)
	if encoding == "base64" {
		value = base64.StdEncoding.EncodeToString(content)
	}
	rs.writeJSON(w, http.StatusOK, FileContentResponse{
		Content:  value,
		Encoding: encoding,
		Path:     path,
		Size:     info.Size(),
		ModTime:  info.ModTime().Format(time.RFC3339),
	})
}

func (rs *RostrumServer) handleWriteFile(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	var req FileWriteRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	path, err := rs.resolvePath(req.Path)
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
		return
	}
	content := []byte(req.Content)
	if req.Encoding == "base64" {
		content, err = base64.StdEncoding.DecodeString(req.Content)
		if err != nil {
			rs.writeError(w, http.StatusBadRequest, "invalid_base64", err.Error())
			return
		}
	}
	if int64(len(content)) > rs.config.MaxFileSize {
		rs.writeError(w, http.StatusRequestEntityTooLarge, "file_too_large", "content exceeds max_file_size")
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		rs.writeError(w, http.StatusInternalServerError, "mkdir_failed", err.Error())
		return
	}
	if err := os.WriteFile(path, content, 0644); err != nil {
		rs.writeError(w, http.StatusInternalServerError, "write_failed", err.Error())
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "success"})
}

func (rs *RostrumServer) handleDeleteFile(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	var req PathRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	path, err := rs.resolvePath(req.Path)
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
		return
	}
	if path == rs.config.Workspace {
		rs.writeError(w, http.StatusBadRequest, "refuse_delete_workspace", "refusing to delete workspace root")
		return
	}
	if err := os.RemoveAll(path); err != nil {
		rs.writeError(w, http.StatusInternalServerError, "delete_failed", err.Error())
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "success"})
}

func (rs *RostrumServer) handleMkdir(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	var req PathRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	path, err := rs.resolvePath(req.Path)
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
		return
	}
	if err := os.MkdirAll(path, 0755); err != nil {
		rs.writeError(w, http.StatusInternalServerError, "mkdir_failed", err.Error())
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "success"})
}

func (rs *RostrumServer) handleCopy(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	var req CopyMoveRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	source, target, ok := rs.resolvePair(w, req)
	if !ok {
		return
	}
	if err := copyPath(source, target); err != nil {
		rs.writeError(w, http.StatusInternalServerError, "copy_failed", err.Error())
		return
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "success"})
}

func (rs *RostrumServer) handleMove(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	var req CopyMoveRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	source, target, ok := rs.resolvePair(w, req)
	if !ok {
		return
	}
	if err := os.Rename(source, target); err != nil {
		if err := copyPath(source, target); err != nil {
			rs.writeError(w, http.StatusInternalServerError, "move_failed", err.Error())
			return
		}
		if err := os.RemoveAll(source); err != nil {
			rs.writeError(w, http.StatusInternalServerError, "move_cleanup_failed", err.Error())
			return
		}
	}
	rs.writeJSON(w, http.StatusOK, map[string]string{"status": "success"})
}

func (rs *RostrumServer) handleWatch(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodGet) {
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	f, ok := w.(http.Flusher)
	if !ok {
		rs.writeError(w, http.StatusInternalServerError, "sse_unsupported", "streaming unsupported")
		return
	}
	deadline := time.NewTicker(30 * time.Second)
	defer deadline.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case <-deadline.C:
			fmt.Fprintf(w, "event: heartbeat\ndata: {}\n\n")
			f.Flush()
		}
	}
}

func (rs *RostrumServer) handleExecuteCommand(w http.ResponseWriter, r *http.Request) {
	if !rs.requireMethod(w, r, http.MethodPost) {
		return
	}
	if !rs.config.AllowCommand {
		rs.writeError(w, http.StatusForbidden, "command_disabled", "command execution is disabled")
		return
	}
	var req CommandRequest
	if !rs.decodeBody(w, r, &req) {
		return
	}
	if strings.TrimSpace(req.Command) == "" {
		rs.writeError(w, http.StatusBadRequest, "empty_command", "command is required")
		return
	}
	workDir := rs.config.Workspace
	if req.WorkDir != "" {
		resolved, err := rs.resolvePath(req.WorkDir)
		if err != nil {
			rs.writeError(w, http.StatusForbidden, "path_denied", err.Error())
			return
		}
		workDir = resolved
	}
	ctx, cancel := context.WithTimeout(r.Context(), 60*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, "sh", "-c", req.Command)
	cmd.Dir = workDir
	output, err := cmd.CombinedOutput()
	errorText := ""
	exitCode := 0
	if ctx.Err() == context.DeadlineExceeded {
		errorText = "command timed out"
		exitCode = -1
	} else if err != nil {
		errorText = err.Error()
		exitCode = -1
		var exitErr *exec.ExitError
		if errors.As(err, &exitErr) {
			exitCode = exitErr.ExitCode()
		}
	}
	rs.writeJSON(w, http.StatusOK, CommandResponse{Output: string(output), Error: errorText, ExitCode: exitCode})
}

func (rs *RostrumServer) resolvePair(w http.ResponseWriter, req CopyMoveRequest) (string, string, bool) {
	source, err := rs.resolvePath(req.Source)
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "source_denied", err.Error())
		return "", "", false
	}
	target, err := rs.resolvePath(req.Target)
	if err != nil {
		rs.writeError(w, http.StatusForbidden, "target_denied", err.Error())
		return "", "", false
	}
	return source, target, true
}

func (rs *RostrumServer) resolvePath(path string) (string, error) {
	if strings.TrimSpace(path) == "" {
		path = rs.config.Workspace
	}
	if !filepath.IsAbs(path) {
		path = filepath.Join(rs.config.Workspace, path)
	}
	absPath, err := filepath.Abs(path)
	if err != nil {
		return "", err
	}
	absPath = filepath.Clean(absPath)
	if rs.config.Workspace == "/" {
		return absPath, nil
	}
	rel, err := filepath.Rel(rs.config.Workspace, absPath)
	if err != nil {
		return "", err
	}
	if rel == ".." || strings.HasPrefix(rel, "../") || filepath.IsAbs(rel) {
		return "", fmt.Errorf("path outside workspace: %s", absPath)
	}
	return absPath, nil
}

func (rs *RostrumServer) decodeBody(w http.ResponseWriter, r *http.Request, dst interface{}) bool {
	r.Body = http.MaxBytesReader(w, r.Body, rs.config.MaxFileSize+1024*1024)
	defer r.Body.Close()
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		rs.writeError(w, http.StatusBadRequest, "invalid_body", err.Error())
		return false
	}
	return true
}

func (rs *RostrumServer) requireMethod(w http.ResponseWriter, r *http.Request, method string) bool {
	if r.Method != method {
		rs.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed", "method not allowed")
		return false
	}
	return true
}

func (rs *RostrumServer) authMiddleware(next http.Handler) http.Handler {
	if rs.config.Token == "" {
		return next
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if token == "" {
			token = r.URL.Query().Get("token")
		}
		if token != rs.config.Token {
			rs.writeError(w, http.StatusUnauthorized, "unauthorized", "missing or invalid bearer token")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func tokenRequirement(token string) string {
	if token == "" {
		return "none"
	}
	return "required"
}

func (rs *RostrumServer) writeJSON(w http.ResponseWriter, status int, value interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func (rs *RostrumServer) writeError(w http.ResponseWriter, status int, code string, message string) {
	rs.writeJSON(w, status, ErrorResponse{Error: code, Code: code, Message: message})
}

func copyPath(source string, target string) error {
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	if info.IsDir() {
		return copyDir(source, target)
	}
	return copyFile(source, target, info.Mode())
}

func copyDir(source string, target string) error {
	info, err := os.Stat(source)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(target, info.Mode()); err != nil {
		return err
	}
	entries, err := os.ReadDir(source)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if err := copyPath(filepath.Join(source, entry.Name()), filepath.Join(target, entry.Name())); err != nil {
			return err
		}
	}
	return nil
}

func copyFile(source string, target string, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
		return err
	}
	in, err := os.Open(source)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}
