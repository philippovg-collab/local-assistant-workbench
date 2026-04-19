#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
PULL_SCRIPT="$ROOT_DIR/pull-model.sh"
TARGET="${1:-recommended}"

if [[ ! -x "$PULL_SCRIPT" ]]; then
  echo "Model pull helper not found at $PULL_SCRIPT" >&2
  exit 1
fi

case "$TARGET" in
  8b|recommended)
    "$PULL_SCRIPT" deepseek-r1:8b
    ;;
  14b)
    "$PULL_SCRIPT" deepseek-r1:14b
    ;;
  all|full)
    "$PULL_SCRIPT" deepseek-r1:8b
    "$PULL_SCRIPT" deepseek-r1:14b
    ;;
  *)
    echo "Usage: $0 [recommended|8b|14b|all|full]" >&2
    exit 1
    ;;
esac
