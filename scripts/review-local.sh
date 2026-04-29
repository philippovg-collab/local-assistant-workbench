#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
RUN_CONTRACT=1
RUN_SCAN=0
SONAR_ARGS=(--skip-scan)
CONTRACT_ARGS=()

usage() {
  cat <<'EOF'
Usage: ./scripts/review-local.sh [options]

Runs the local review workflow:
  1. review contract diff scan
  2. backend coverage via scripts/sonar-review.sh
  3. frontend coverage via scripts/sonar-review.sh
  4. frontend production build

Options:
  --integration       Run backend mvn -Pcoverage verify instead of test.
  --full-sonar        Run Sonar Scanner and wait for Quality Gate.
  --base-ref REF      Base ref for local anti-sprawl diff scanning.
  --skip-contract     Skip the local review contract diff scan.
  -h, --help          Show this help.

Environment:
  SONAR_TOKEN is required only when --full-sonar is used.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --integration)
      SONAR_ARGS+=(--integration)
      shift
      ;;
    --full-sonar)
      RUN_SCAN=1
      SONAR_ARGS=("${SONAR_ARGS[@]:#--skip-scan}")
      shift
      ;;
    --base-ref)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "--base-ref requires a ref value." >&2
        exit 1
      fi
      CONTRACT_ARGS+=(--base-ref "$2")
      shift 2
      ;;
    --skip-contract)
      RUN_CONTRACT=0
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

cd "$ROOT_DIR"

if [[ "$RUN_CONTRACT" -eq 1 ]]; then
  echo "Running local review contract diff scan"
  python3 scripts/review-contract.py --skip-body "${CONTRACT_ARGS[@]}"
fi

if [[ "$RUN_SCAN" -eq 1 ]]; then
  echo "Running coverage + Sonar Quality Gate"
else
  echo "Running coverage without remote Sonar scan"
fi

"$ROOT_DIR/scripts/sonar-review.sh" "${SONAR_ARGS[@]}"

echo "Building frontend production bundle"
cd "$ROOT_DIR/frontend"
npm run build

echo "Local review workflow completed."
