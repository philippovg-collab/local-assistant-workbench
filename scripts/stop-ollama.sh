#!/bin/zsh
set -euo pipefail

if ! pgrep -f '/opt/homebrew/bin/ollama serve' >/dev/null 2>&1; then
  echo "Ollama is not running"
  exit 0
fi

pkill -f '/opt/homebrew/bin/ollama serve' || true
pkill -f 'ollama runner' || true
echo "Ollama stopped"
