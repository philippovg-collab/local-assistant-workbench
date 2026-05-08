#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-fast}"

BACKEND_CONTRACT_TESTS=(
  SecurityConfigTest
  SecurityPropertiesTest
  WebConfigCorsTest
  MaterialMetadataSnapshotTest
  MaterialMetadataResolverTest
  MaterialIndexingServiceTest
  MaterialSearchSyncLifecycleServiceTest
  ChatControllerContractTest
  ChatRunCommandControllerContractTest
  ChatRunQueryControllerContractTest
  ChatRunExecutionServiceTest
  ChatRunTraceServiceTest
)

FRONTEND_CONTRACT_TESTS=(
  src/types.contract.test.ts
  src/api/client.test.ts
  src/hooks/useChatExecution.test.tsx
  src/utils/materialMetadata.test.ts
  src/utils/readiness.test.ts
  src/utils/retrievalHints.test.ts
)

usage() {
  cat <<'EOF'
Usage: scripts/quality-gates.sh fast|full|ci

Modes:
  fast  Static gates plus targeted backend/frontend contract tests. Does not require Docker.
  full  Fast gates plus clean backend coverage/verify, frontend coverage, and frontend build.
  ci    CI-shaped proof: static gates, clean backend coverage/verify, frontend coverage, and frontend build.
EOF
}

run_static_gates() {
  echo "== Static quality gates =="
  cd "$ROOT_DIR"
  python3 scripts/phase5-static-gate.py
  python3 scripts/architecture-boundary-gate.py
  python3 scripts/complexity-budget-gate.py
  python3 scripts/review-contract.py --skip-body
}

run_backend_contract_tests() {
  echo "== Backend contract quality tests =="
  cd "$ROOT_DIR/backend"
  local tests
  tests="$(IFS=,; echo "${BACKEND_CONTRACT_TESTS[*]}")"
  mvn -B -Dtest="$tests" test
}

run_frontend_contract_tests() {
  echo "== Frontend contract quality tests =="
  cd "$ROOT_DIR/frontend"
  npm run test -- "${FRONTEND_CONTRACT_TESTS[@]}"
}

run_backend_coverage() {
  echo "== Backend coverage ratchet =="
  cd "$ROOT_DIR"
  python3 scripts/coverage-ratchet.py backend
}

run_frontend_coverage_and_build() {
  echo "== Frontend coverage ratchet =="
  cd "$ROOT_DIR/frontend"
  npm run test:coverage
  cd "$ROOT_DIR"
  python3 scripts/coverage-ratchet.py frontend

  echo "== Frontend production build =="
  cd "$ROOT_DIR/frontend"
  npm run build
}

run_backend_verify() {
  echo "== Backend Docker-backed integration verification =="
  cd "$ROOT_DIR/backend"
  mvn -B clean verify -Pcoverage
}

case "$MODE" in
  fast)
    run_static_gates
    run_backend_contract_tests
    run_frontend_contract_tests
    ;;
  full)
    run_static_gates
    run_backend_contract_tests
    run_frontend_contract_tests
    run_backend_verify
    run_backend_coverage
    run_frontend_coverage_and_build
    ;;
  ci)
    run_static_gates
    run_backend_verify
    run_backend_coverage
    run_frontend_coverage_and_build
    ;;
  -h|--help)
    usage
    ;;
  *)
    echo "Unknown quality gate mode: $MODE" >&2
    usage >&2
    exit 1
    ;;
esac
