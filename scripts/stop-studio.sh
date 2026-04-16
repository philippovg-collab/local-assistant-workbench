#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)

"$ROOT_DIR/stop-backend.sh"
pkill -f 'vite --host 127.0.0.1' || true
pkill -f '/vite/bin/vite.js' || true

echo "Frontend and backend stopped"
