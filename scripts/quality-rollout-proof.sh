#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
REPORT_DIR="$ROOT_DIR/backend/target/search-quality"
MD_REPORT="$REPORT_DIR/phase6-quality-report.md"
JSON_REPORT="$REPORT_DIR/phase6-quality-report.json"
LEGACY_REPORT="$REPORT_DIR/phase6-rerank-report.md"

echo "Running Phase 6 quality rollout proof..."

if "$ROOT_DIR/scripts/test-backend.sh" fast -Dtest=Phase6RetrievalQualityIT; then
  test_status=0
else
  test_status=$?
fi

missing_artifacts=()
for artifact in "$MD_REPORT" "$JSON_REPORT" "$LEGACY_REPORT"; do
  if [[ ! -s "$artifact" ]]; then
    missing_artifacts+=("$artifact")
  fi
done

if [[ "$test_status" -eq 0 && "${#missing_artifacts[@]}" -eq 0 ]]; then
  echo "PASS Phase 6 quality rollout proof"
  echo "Artifacts:"
  echo "  $MD_REPORT"
  echo "  $JSON_REPORT"
  echo "  $LEGACY_REPORT"
  exit 0
fi

echo "FAIL Phase 6 quality rollout proof"
if [[ "$test_status" -ne 0 ]]; then
  echo "Backend quality gate exited with status $test_status"
fi
if [[ "${#missing_artifacts[@]}" -gt 0 ]]; then
  echo "Missing artifacts:"
  for artifact in "$missing_artifacts[@]"; do
    echo "  $artifact"
  done
fi
exit 1
