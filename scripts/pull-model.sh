#!/bin/zsh
set -euo pipefail

OLLAMA_BIN="${OLLAMA_BIN:-/opt/homebrew/bin/ollama}"
MODEL="${1:-qwen2.5:7b}"

if [[ ! -x "$OLLAMA_BIN" ]]; then
  echo "Ollama binary not found at $OLLAMA_BIN" >&2
  exit 1
fi

"$OLLAMA_BIN" pull "$MODEL"
