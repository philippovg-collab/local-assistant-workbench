#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
BACKEND_DIR="$ROOT_DIR/backend"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
CRITICAL_MATERIAL_IT_CLASSES="PostgresMaterialRepositoryIT,MaterialLineageVersionMigrationIT,MaterialSearchSyncLifecycleIT,MaterialRuntimeTopologyIT"
FAST_METADATA_FLOW_TESTS="MaterialControllerMetadataFlowTest,MaterialControllerContractTest,MaterialServiceTest"
NON_DOCKER_FAST_IT_CLASSES="Phase6RetrievalQualityIT"

is_non_docker_fast_it_selector() {
  local candidate="$1"
  local allowed
  for allowed in ${(s:,:)NON_DOCKER_FAST_IT_CLASSES}; do
    if [[ "$candidate" == "$allowed" || "$candidate" == ${allowed}\[* || "$candidate" == ${allowed}\(* ]]; then
      return 0
    fi
  done
  return 1
}

resolve_java_21_home() {
  if [[ -n "${JAVA_21_HOME:-}" && -x "${JAVA_21_HOME}/bin/java" ]]; then
    echo "$JAVA_21_HOME"
    return 0
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    local current_version
    current_version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)
    if [[ "$current_version" == *'"21.'* || "$current_version" == *'"21"'* ]]; then
      echo "$JAVA_HOME"
      return 0
    fi
  fi

  if [[ -x "${DEFAULT_JAVA_21_HOME}/bin/java" ]]; then
    echo "$DEFAULT_JAVA_21_HOME"
    return 0
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local detected_home
    detected_home=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if [[ -n "$detected_home" && -x "$detected_home/bin/java" ]]; then
      echo "$detected_home"
      return 0
    fi
  fi

  return 1
}

require_docker_for_testcontainers() {
  local current_context
  local current_endpoint
  local effective_docker_host
  local socket_path
  local server_min_api

  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker-backed backend proof requires the docker CLI in PATH. Run this mode from a shell or CI runtime with Docker/Testcontainers access." >&2
    exit 1
  fi

  current_context=$(docker context show 2>/dev/null || true)
  current_endpoint=""
  if [[ -n "$current_context" ]]; then
    current_endpoint=$(docker context inspect "$current_context" --format '{{ (index .Endpoints "docker").Host }}' 2>/dev/null || true)
  fi
  effective_docker_host="${DOCKER_HOST:-$current_endpoint}"
  if [[ -z "$effective_docker_host" ]]; then
    effective_docker_host="unix:///var/run/docker.sock"
  fi

  if [[ -z "${DOCKER_HOST:-}" && "$effective_docker_host" != "unix:///var/run/docker.sock" ]]; then
    echo "Docker CLI is using context '${current_context:-unknown}' via ${effective_docker_host}, but Testcontainers will not inherit Docker CLI contexts automatically in this runtime. Export DOCKER_HOST=${effective_docker_host} before running this mode, or use a shell/CI environment where Testcontainers already sees Docker." >&2
    exit 1
  fi

  if [[ "$effective_docker_host" == unix://* ]]; then
    socket_path="${effective_docker_host#unix://}"
    if [[ ! -S "$socket_path" ]]; then
      echo "Docker-backed backend proof requires a reachable Unix socket for Testcontainers, but ${socket_path} is not available in this runtime." >&2
      exit 1
    fi
  fi

  if [[ -n "${CODEX_SHELL:-}" && "$effective_docker_host" != "unix:///var/run/docker.sock" ]]; then
    server_min_api=$(DOCKER_HOST="$effective_docker_host" docker version --format '{{.Server.MinAPIVersion}}' 2>/dev/null || true)
    if [[ -n "$server_min_api" ]]; then
      echo "Docker-backed backend proof is known to fail in this Codex runtime when Docker is exposed through ${effective_docker_host} (context '${current_context:-unknown}'): Testcontainers in this repo negotiates an API version older than the daemon minimum ${server_min_api}. Run ./scripts/test-backend.sh here for repo logic proof, and run Docker-backed proof from a normal shell or CI with working Testcontainers." >&2
      exit 1
    fi
  fi

  if ! DOCKER_HOST="$effective_docker_host" docker info >/dev/null 2>&1; then
    echo "Docker-backed backend proof requires a live Docker daemon/socket that Testcontainers can reach. This runtime cannot currently access Docker, so use a normal shell or CI with working Docker/Testcontainers." >&2
    exit 1
  fi
}

find_docker_backed_test_selector() {
  local arg
  local selector
  local raw_selector
  local candidate

  for arg in "$@"; do
    case "$arg" in
      -Dtest=*)
        selector="${arg#-Dtest=}"
        for raw_selector in ${(s:,:)selector}; do
          candidate="${raw_selector%%#*}"
          candidate="${candidate//[[:space:]]/}"
          if [[ -z "$candidate" ]]; then
            continue
          fi
          if is_non_docker_fast_it_selector "$candidate"; then
            continue
          fi
          case "$candidate" in
            *IT|*IT\[*|*IT\(*)
              echo "$candidate"
              return 0
              ;;
          esac
        done
        ;;
    esac
  done

  return 1
}

reject_docker_backed_fast_selectors() {
  local selector

  selector=$(find_docker_backed_test_selector "$@") || return 0

  echo "Fast mode only runs non-Docker mvn test proof. Selector '${selector}' is Docker-backed and cannot run through ./scripts/test-backend.sh fast." >&2
  echo "Use ./scripts/test-backend.sh integration -Dit.test=<docker-backed-it-class> from a normal shell or CI with working Docker/Testcontainers. Example: ./scripts/test-backend.sh integration -Dit.test=MaterialControllerIT" >&2
  echo "For the local metadata gate use ./scripts/test-backend.sh fast -Dtest=${FAST_METADATA_FLOW_TESTS}" >&2
  exit 1
}

JAVA21_HOME=$(resolve_java_21_home || true)
if [[ -z "$JAVA21_HOME" ]]; then
  echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before running backend tests." >&2
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"
cd "$BACKEND_DIR"

if [[ $# -eq 0 ]]; then
  mvn test
  exit 0
fi

MODE="$1"
shift

case "$MODE" in
  fast)
    reject_docker_backed_fast_selectors "$@"
    mvn test "$@"
    ;;
  critical-materials)
    require_docker_for_testcontainers
    mvn verify -Dit.test="$CRITICAL_MATERIAL_IT_CLASSES" "$@"
    ;;
  full|integration)
    require_docker_for_testcontainers
    mvn verify "$@"
    ;;
  *)
    if [[ "$MODE" == "test" ]]; then
      reject_docker_backed_fast_selectors "$@"
    fi
    mvn "$MODE" "$@"
    ;;
esac
