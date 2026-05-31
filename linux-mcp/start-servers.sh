#!/bin/bash
set -e

MCP_DIR="/opt/omnimaster/mcp-servers"
LOG_DIR="/var/log/omnimaster"
PID_DIR="/var/run/omnimaster"

mkdir -p "$LOG_DIR" "$PID_DIR"

FS_PORT="${FS_PORT:-18700}"
TERM_PORT="${TERM_PORT:-18701}"

stop_servers() {
    echo "[mcp] Stopping MCP servers..."
    for pidfile in "$PID_DIR"/*.pid; do
        [ -f "$pidfile" ] || continue
        pid=$(cat "$pidfile")
        if kill -0 "$pid" 2>/dev/null; then
            kill "$pid"
            echo "[mcp] Stopped PID $pid"
        fi
        rm -f "$pidfile"
    done
}

start_server() {
    local name="$1"
    local dir="$2"
    local port="$3"

    echo "[mcp] Starting $name on port $port..."

    if [ -f "$dir/dist/index.js" ]; then
        PORT=$port node "$dir/dist/index.js" \
            >"$LOG_DIR/$name.log" 2>&1 &
    else
        PORT=$port npx tsx "$dir/src/index.ts" \
            >"$LOG_DIR/$name.log" 2>&1 &
    fi

    local pid=$!
    echo $pid > "$PID_DIR/$name.pid"
    echo "[mcp] $name started with PID $pid"
}

case "${1:-start}" in
    start)
        stop_servers 2>/dev/null || true
        start_server "filesystem-server" "$MCP_DIR/filesystem-server" "$FS_PORT"
        start_server "terminal-server" "$MCP_DIR/terminal-server" "$TERM_PORT"

        sleep 1
        echo "[mcp] Health check..."
        curl -sf "http://127.0.0.1:$FS_PORT/health" && echo " filesystem OK" || echo " filesystem FAILED"
        curl -sf "http://127.0.0.1:$TERM_PORT/health" && echo " terminal OK" || echo " terminal FAILED"
        echo "[mcp] All servers started."
        ;;
    stop)
        stop_servers
        ;;
    restart)
        stop_servers
        sleep 1
        exec "$0" start
        ;;
    status)
        for pidfile in "$PID_DIR"/*.pid; do
            [ -f "$pidfile" ] || continue
            name=$(basename "$pidfile" .pid)
            pid=$(cat "$pidfile")
            if kill -0 "$pid" 2>/dev/null; then
                echo "$name: running (PID $pid)"
            else
                echo "$name: stopped"
            fi
        done
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac
