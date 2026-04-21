#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_FILE="${LOG_FILE:-/tmp/local-model-backend.log}"
BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/liveness"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/ragstudio}"
export APP_SECURITY_ADMIN_USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
export APP_SECURITY_ADMIN_PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-local-admin-password}"
export APP_ROLLOUT_METADATA_V1="${APP_ROLLOUT_METADATA_V1:-true}"
export APP_ROLLOUT_METADATA_FILTERS_V1="${APP_ROLLOUT_METADATA_FILTERS_V1:-true}"
export APP_ROLLOUT_QUERY_HINTS_V1="${APP_ROLLOUT_QUERY_HINTS_V1:-true}"

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

is_backend_healthy() {
  curl -fsS "$BACKEND_HEALTH_URL" >/dev/null 2>&1
}

rollout_flag_expected() {
  [[ "$1" == "true" || "$1" == "TRUE" ]]
}

has_expected_quality_layer_flags() {
  return 0
}

is_backend_compatible() {
  if ! is_backend_healthy; then
    return 1
  fi
  has_expected_quality_layer_flags || return 1
}

extract_postgres_host() {
  local jdbc_url="$1"
  local without_prefix authority

  without_prefix="${jdbc_url#jdbc:postgresql://}"
  authority="${without_prefix%%/*}"
  if [[ "$authority" == *":"* ]]; then
    echo "${authority%%:*}"
    return 0
  fi

  echo "$authority"
}

extract_postgres_port() {
  local jdbc_url="$1"
  local without_prefix authority

  without_prefix="${jdbc_url#jdbc:postgresql://}"
  authority="${without_prefix%%/*}"
  if [[ "$authority" == *":"* ]]; then
    echo "${authority##*:}"
    return 0
  fi

  echo "5432"
}

ensure_postgres_ready() {
  if [[ "$DATASOURCE_URL" != jdbc:postgresql://* ]]; then
    echo "Skipping PostgreSQL preflight because SPRING_DATASOURCE_URL is not a jdbc:postgresql URL." >&2
    echo "Configured datasource: $DATASOURCE_URL" >&2
    return 0
  fi

  if ! command -v pg_isready >/dev/null 2>&1; then
    echo "pg_isready is not installed; skipping PostgreSQL preflight. Backend still requires reachable PostgreSQL with pgvector." >&2
    return 0
  fi

  local host port
  host=$(extract_postgres_host "$DATASOURCE_URL")
  port=$(extract_postgres_port "$DATASOURCE_URL")

  if ! pg_isready -h "$host" -p "$port" >/dev/null 2>&1; then
    echo "PostgreSQL is not reachable at ${host}:${port}." >&2
    echo "Start PostgreSQL with pgvector first or override SPRING_DATASOURCE_URL/SPRING_DATASOURCE_USERNAME/SPRING_DATASOURCE_PASSWORD." >&2
    echo "Configured datasource: $DATASOURCE_URL" >&2
    exit 1
  fi
}

if is_backend_compatible; then
  echo "Backend is already running at http://127.0.0.1:8080"
  zsh "$ROOT_DIR/scripts/local-runtime-diagnostics.zsh" || true
  exit 0
fi

if is_backend_healthy; then
  echo "Existing backend is incompatible with the current local startup contract. Restarting..." >&2
  "$ROOT_DIR/scripts/stop-backend.sh"
fi

JAVA21_HOME=$(resolve_java_21_home || true)
if [[ -z "$JAVA21_HOME" ]]; then
  echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before starting backend." >&2
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export APP_ROLLOUT_METADATA_V1="${APP_ROLLOUT_METADATA_V1:-true}"
export APP_ROLLOUT_METADATA_FILTERS_V1="${APP_ROLLOUT_METADATA_FILTERS_V1:-true}"
export APP_ROLLOUT_QUERY_HINTS_V1="${APP_ROLLOUT_QUERY_HINTS_V1:-true}"

ensure_postgres_ready

cd "$ROOT_DIR/backend"
nohup mvn -Dmaven.test.skip=true spring-boot:run >"$LOG_FILE" 2>&1 &
PID=$!

for _ in {1..30}; do
  sleep 2
  if is_backend_compatible; then
    echo "Backend started successfully (pid $PID)"
    echo "API: http://127.0.0.1:8080"
    echo "Admin login: $APP_SECURITY_ADMIN_USERNAME / $APP_SECURITY_ADMIN_PASSWORD"
    echo "PostgreSQL: $DATASOURCE_URL"
    echo "metadata-v1: $APP_ROLLOUT_METADATA_V1"
    echo "metadata-filters-v1: $APP_ROLLOUT_METADATA_FILTERS_V1"
    echo "query-hints-v1: $APP_ROLLOUT_QUERY_HINTS_V1"
    echo "Log: $LOG_FILE"
    zsh "$ROOT_DIR/scripts/local-runtime-diagnostics.zsh" || true
    exit 0
  fi
done

echo "Backend did not become ready. Recent log output:" >&2
tail -n 30 "$LOG_FILE" >&2 || true
exit 1
