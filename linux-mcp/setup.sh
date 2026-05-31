#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MCP_DIR="/opt/omnimaster/mcp-servers"

echo "[setup] Installing Node.js environment..."

# Check and install Node.js. In offline environments apt may fail;
# we print a warning and continue so that pre-bundled Node binaries can still work.
if ! command -v node &>/dev/null; then
    echo "[setup] Node.js not found, attempting install via apt..."
    apt-get update -qq 2>/dev/null || echo "[setup] WARNING: apt-get update failed (offline?)"
    apt-get install -y -qq nodejs npm curl 2>/dev/null || echo "[setup] WARNING: apt install nodejs failed (offline?)"
fi

NODE_VERSION=$(node --version 2>/dev/null || echo "none")
echo "[setup] Node.js version: $NODE_VERSION"

if ! command -v npm &>/dev/null; then
    echo "[setup] npm not found, attempting install..."
    apt-get install -y -qq npm 2>/dev/null || echo "[setup] WARNING: apt install npm failed (offline?)"
fi

# Verify npm is available before proceeding; if not, the caller (McpBridgeManager)
# will fall back to npx tsx at runtime, but node_modules must already exist.
if ! command -v npm &>/dev/null; then
    echo "[setup] ERROR: npm is not available and dependencies cannot be installed."
    echo "[setup] If running offline, ensure node_modules are pre-bundled or Node is pre-installed."
    exit 1
fi

echo "[setup] Copying MCP servers to $MCP_DIR..."
mkdir -p "$MCP_DIR"
cp -r "$SCRIPT_DIR/servers/filesystem-server" "$MCP_DIR/"
cp -r "$SCRIPT_DIR/servers/terminal-server" "$MCP_DIR/"

# Install production dependencies. tsx is included in dependencies (not devDependencies)
# so that npx tsx works even when TypeScript is not compiled to dist/.
echo "[setup] Installing filesystem-server dependencies..."
cd "$MCP_DIR/filesystem-server"
npm install --production 2>&1 | tail -3 || {
    echo "[setup] WARNING: filesystem-server npm install failed (offline?). tsx may be missing."
}

echo "[setup] Installing terminal-server dependencies..."
cd "$MCP_DIR/terminal-server"
npm install --production 2>&1 | tail -3 || {
    echo "[setup] WARNING: terminal-server npm install failed (offline?). tsx may be missing."
}

# Attempt TypeScript compilation. This requires typescript (devDependency), which is NOT
# installed by --production, so this will usually fail in production / offline environments.
# The fallback is to run src/index.ts directly with tsx at runtime.
echo "[setup] Building filesystem-server..."
cd "$MCP_DIR/filesystem-server"
npx tsc 2>&1 || echo "[setup] TypeScript build skipped (will use tsx at runtime)"

echo "[setup] Building terminal-server..."
cd "$MCP_DIR/terminal-server"
npx tsc 2>&1 || echo "[setup] TypeScript build skipped (will use tsx at runtime)"

echo "[setup] Done. MCP servers installed at $MCP_DIR"
