#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/ragstudio}"

if [[ ! -t 0 || ! -t 1 ]]; then
  echo "Warning: non-interactive runtimes may reap detached child processes after script exit." >&2
  echo "Use $ROOT_DIR/run-local-stack.sh directly to keep Ollama, backend, and frontend alive under Codex/agent/PTY workflows." >&2
fi

"$ROOT_DIR/start-ollama.sh"
"$ROOT_DIR/start-backend.sh"
"$ROOT_DIR/start-frontend.sh"

echo
echo "Studio is ready:"
echo "  Frontend: http://127.0.0.1:5173"
echo "  Backend:  http://127.0.0.1:8080"
echo "  Ollama:   http://127.0.0.1:11434"
echo "  Postgres: $DATASOURCE_URL"
