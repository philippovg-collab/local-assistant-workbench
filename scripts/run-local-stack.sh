#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
export RUN_LOCAL_STACK_LAUNCHER="${RUN_LOCAL_STACK_LAUNCHER:-$0}"

exec /bin/zsh "$SCRIPT_DIR/run-local-stack.zsh" "$@"
