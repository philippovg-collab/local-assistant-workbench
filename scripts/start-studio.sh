#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)

"$ROOT_DIR/start-ollama.sh"
"$ROOT_DIR/start-backend.sh"
"$ROOT_DIR/start-frontend.sh"

echo
echo "Studio is ready:"
echo "  Frontend: http://127.0.0.1:5173"
echo "  Backend:  http://127.0.0.1:8080"
echo "  Ollama:   http://127.0.0.1:11434"
