#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-smoke}"
REPORT_DIR="${EVAL_REPORT_DIR:-$ROOT_DIR/backend/target/eval-reports}"

case "$MODE" in
  smoke|golden|compare)
    ;;
  *)
    echo "Unknown eval mode: $MODE" >&2
    echo "Usage: scripts/eval-run.sh smoke|golden|compare" >&2
    exit 1
    ;;
esac

mkdir -p "$REPORT_DIR"

cd "$ROOT_DIR/backend"
mvn -B -Dtest=EvalCiReportGenerationTest -Deval.ci.mode="$MODE" -Deval.ci.report.dir="$REPORT_DIR" test

test -s "$REPORT_DIR/$MODE-report.json"
test -s "$REPORT_DIR/$MODE-report.md"
