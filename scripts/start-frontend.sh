#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_FILE="${LOG_FILE:-/tmp/local-model-frontend.log}"

if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
  echo "Frontend is already running at http://127.0.0.1:5173"
  exit 0
fi

cd "$ROOT_DIR/frontend"
nohup npm run dev -- --host 127.0.0.1 >"$LOG_FILE" 2>&1 &
PID=$!

sleep 4

if curl -fsS http://127.0.0.1:5173 >/dev/null 2>&1; then
  echo "Frontend started successfully (pid $PID)"
  echo "UI: http://127.0.0.1:5173"
  echo "Log: $LOG_FILE"
  exit 0
fi

echo "Frontend did not become ready. Recent log output:" >&2
tail -n 30 "$LOG_FILE" >&2 || true
exit 1
