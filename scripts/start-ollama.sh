#!/bin/zsh
set -euo pipefail

OLLAMA_BIN="${OLLAMA_BIN:-/opt/homebrew/bin/ollama}"
OLLAMA_URL="${OLLAMA_URL:-http://127.0.0.1:11434}"
LOG_FILE="${LOG_FILE:-/tmp/ollama.log}"

if [[ ! -x "$OLLAMA_BIN" ]]; then
  echo "Ollama binary not found at $OLLAMA_BIN" >&2
  exit 1
fi

if curl -fsS "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
  echo "Ollama is already running at $OLLAMA_URL"
  exit 0
fi

nohup "$OLLAMA_BIN" serve >"$LOG_FILE" 2>&1 &
PID=$!

sleep 2

if curl -fsS "$OLLAMA_URL/api/tags" >/dev/null 2>&1; then
  echo "Ollama started successfully (pid $PID)"
  echo "API: $OLLAMA_URL"
  echo "Log: $LOG_FILE"
  exit 0
fi

echo "Ollama did not become ready. Recent log output:" >&2
tail -n 20 "$LOG_FILE" >&2 || true
exit 1
