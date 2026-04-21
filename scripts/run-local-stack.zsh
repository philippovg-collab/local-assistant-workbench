#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
IMPLEMENTATION_PATH=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
LAUNCHER_PATH="${RUN_LOCAL_STACK_LAUNCHER:-$0}"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/ragstudio}"
export APP_ROLLOUT_METADATA_V1="${APP_ROLLOUT_METADATA_V1:-true}"
export APP_ROLLOUT_METADATA_FILTERS_V1="${APP_ROLLOUT_METADATA_FILTERS_V1:-true}"
export APP_ROLLOUT_QUERY_HINTS_V1="${APP_ROLLOUT_QUERY_HINTS_V1:-true}"
OLLAMA_BIN="${OLLAMA_BIN:-/opt/homebrew/bin/ollama}"
OLLAMA_URL="${OLLAMA_URL:-http://127.0.0.1:11434}"
OLLAMA_LOG_FILE="${OLLAMA_LOG_FILE:-/tmp/ollama.log}"
BACKEND_LOG_FILE="${BACKEND_LOG_FILE:-/tmp/local-model-backend.log}"
FRONTEND_LOG_FILE="${FRONTEND_LOG_FILE:-/tmp/local-model-frontend.log}"
LOCAL_HOST="127.0.0.1"
BACKEND_PORT="8080"
FRONTEND_PORT="5173"
BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/liveness"
BACKEND_MODELS_URL="http://127.0.0.1:8080/api/models"
FRONTEND_URL="http://127.0.0.1:5173"
export APP_SECURITY_ADMIN_USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
export APP_SECURITY_ADMIN_PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-local-admin-password}"
STACK_START_TIMEOUT_SECONDS="${STACK_START_TIMEOUT_SECONDS:-60}"
BACKEND_START_TIMEOUT_SECONDS="${BACKEND_START_TIMEOUT_SECONDS:-90}"
STACK_POLL_INTERVAL_SECONDS="${STACK_POLL_INTERVAL_SECONDS:-2}"
REUSE_PROBE_ATTEMPTS="${REUSE_PROBE_ATTEMPTS:-3}"
REUSE_PROBE_DELAY_SECONDS="${REUSE_PROBE_DELAY_SECONDS:-1}"

extract_http_host() {
  local url="$1"
  local without_scheme authority

  without_scheme="${url#http://}"
  without_scheme="${without_scheme#https://}"
  authority="${without_scheme%%/*}"
  authority="${authority#\[}"
  authority="${authority%\]}"

  if [[ "$authority" == *":"* ]]; then
    echo "${authority%%:*}"
    return 0
  fi

  echo "$authority"
}

extract_http_port() {
  local url="$1"
  local default_port="80"
  local without_scheme authority

  if [[ "$url" == https://* ]]; then
    default_port="443"
  fi

  without_scheme="${url#http://}"
  without_scheme="${without_scheme#https://}"
  authority="${without_scheme%%/*}"
  authority="${authority#\[}"
  authority="${authority%\]}"

  if [[ "$authority" == *":"* ]]; then
    echo "${authority##*:}"
    return 0
  fi

  echo "$default_port"
}

OLLAMA_URL_HOST=$(extract_http_host "$OLLAMA_URL")
OLLAMA_URL_PORT=$(extract_http_port "$OLLAMA_URL")
OLLAMA_BIND_ADDRESS="${OLLAMA_URL_HOST}:${OLLAMA_URL_PORT}"

OLLAMA_PID=""
BACKEND_PID=""
FRONTEND_PID=""
OLLAMA_MANAGED=0
BACKEND_MANAGED=0
FRONTEND_MANAGED=0
EXIT_CODE=0
CLEANING_UP=0
OLLAMA_MODE=""
OLLAMA_MODE_DESCRIPTION=""

announce_launcher_path() {
  if [[ "$LAUNCHER_PATH" == "$IMPLEMENTATION_PATH" ]]; then
    echo "Launcher path: $LAUNCHER_PATH"
    return 0
  fi

  echo "Launcher path: $LAUNCHER_PATH -> $IMPLEMENTATION_PATH"
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

http_ready() {
  command curl --max-time 2 -fsS "$1" >/dev/null 2>&1
}

port_listening() {
  local host="$1"
  local port="$2"

  command lsof -n -P -t -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

listener_name_for_port() {
  local port="$1"

  command lsof -n -P -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | command awk 'NR==2 {print $1; exit}'
}

listener_name_matches() {
  local port="$1"
  local pattern="$2"
  local listener_name

  listener_name=$(listener_name_for_port "$port" || true)
  [[ -n "$listener_name" ]] || return 1
  [[ "$listener_name" == *"$pattern"* ]]
}

probe_ready_with_retries() {
  local ready_fn="$1"
  local attempt

  for attempt in $(seq 1 "$REUSE_PROBE_ATTEMPTS"); do
    if "$ready_fn"; then
      return 0
    fi

    if (( attempt < REUSE_PROBE_ATTEMPTS )); then
      sleep "$REUSE_PROBE_DELAY_SECONDS"
    fi
  done

  return 1
}

reuse_existing_service_if_available() {
  local label="$1"
  local url="$2"
  local host="$3"
  local port="$4"
  local ready_fn="$5"

  if probe_ready_with_retries "$ready_fn"; then
    echo "Reusing existing $label at $url"
    return 0
  fi

  if port_listening "$host" "$port"; then
    echo "Detected an existing listener on $url; waiting for $label readiness instead of starting another instance."
    return 0
  fi

  return 1
}

is_ollama_ready() {
  http_ready "$OLLAMA_URL/api/tags" || listener_name_matches "$OLLAMA_URL_PORT" "ollama"
}

rollout_flag_expected() {
  [[ "$1" == "true" || "$1" == "TRUE" ]]
}

backend_quality_layer_flags_match_expected() {
  return 0
}

backend_health_responds() {
  command curl --max-time 2 -fsS "$BACKEND_HEALTH_URL" >/dev/null 2>&1
}

is_backend_ready() {
  if http_ready "$BACKEND_HEALTH_URL"; then
    return 0
  fi

  listener_name_matches "$BACKEND_PORT" "java"
}

is_frontend_ready() {
  http_ready "$FRONTEND_URL" || listener_name_matches "$FRONTEND_PORT" "node"
}

tail_log_excerpt() {
  local log_file="$1"
  local lines="${2:-40}"

  if [[ -f "$log_file" ]]; then
    echo "Recent log output from $log_file:" >&2
    tail -n "$lines" "$log_file" >&2 || true
    return 0
  fi

  echo "Log file not found: $log_file" >&2
}

kill_descendants() {
  local parent_pid="$1"
  local signal_name="$2"
  local child_pid
  local -a child_pids

  child_pids=("${(@f)$(pgrep -P "$parent_pid" 2>/dev/null || true)}")
  for child_pid in $child_pids; do
    [[ -n "$child_pid" ]] || continue
    kill_descendants "$child_pid" "$signal_name"
    kill "-$signal_name" "$child_pid" 2>/dev/null || true
  done
}

stop_process_tree() {
  local pid="$1"
  local label="$2"
  local wait_status=0

  [[ -n "$pid" ]] || return 0
  if ! kill -0 "$pid" 2>/dev/null; then
    wait "$pid" >/dev/null 2>&1 || true
    return 0
  fi

  echo "Stopping $label (pid $pid)..." >&2
  kill_descendants "$pid" TERM
  kill -TERM "$pid" 2>/dev/null || true

  for _ in {1..10}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      wait "$pid" >/dev/null 2>&1 || wait_status=$?
      return $wait_status
    fi
    sleep 1
  done

  echo "$label did not exit after SIGTERM; sending SIGKILL." >&2
  kill_descendants "$pid" KILL
  kill -KILL "$pid" 2>/dev/null || true
  wait "$pid" >/dev/null 2>&1 || true
}

cleanup() {
  if (( CLEANING_UP )); then
    return 0
  fi

  CLEANING_UP=1

  if (( FRONTEND_MANAGED )); then
    stop_process_tree "$FRONTEND_PID" "frontend" || true
  fi
  if (( BACKEND_MANAGED )); then
    stop_process_tree "$BACKEND_PID" "backend" || true
  fi
  if (( OLLAMA_MANAGED )); then
    stop_process_tree "$OLLAMA_PID" "ollama" || true
  fi
}

report_wait_timeout() {
  local label="$1"
  local log_file="$2"
  local timeout_seconds="$3"

  if [[ "$label" == "Ollama" && "$OLLAMA_MODE" == "occupied_wait_external" ]]; then
    echo "Ollama port ${OLLAMA_BIND_ADDRESS} is already occupied, but the external listener at $OLLAMA_URL did not become healthy within ${timeout_seconds}s." >&2
    echo "Supervisor will not start a managed Ollama over an occupied port." >&2
    EXIT_CODE=1
    return 1
  fi

  echo "$label did not become ready within ${timeout_seconds}s." >&2
  tail_log_excerpt "$log_file"
  EXIT_CODE=1
  return 1
}

wait_for_service() {
  local label="$1"
  local pid="$2"
  local managed="$3"
  local ready_fn="$4"
  local log_file="$5"
  local timeout_seconds="$6"
  local elapsed=0
  local exit_status=0

  while (( elapsed < timeout_seconds )); do
    if "$ready_fn"; then
      echo "$label is ready."
      return 0
    fi

    if (( managed )) && ! kill -0 "$pid" 2>/dev/null; then
      wait "$pid" >/dev/null 2>&1 || exit_status=$?
      [[ $exit_status -eq 0 ]] && exit_status=1
      echo "$label exited before becoming ready (status $exit_status)." >&2
      tail_log_excerpt "$log_file"
      EXIT_CODE=$exit_status
      return 1
    fi

    sleep "$STACK_POLL_INTERVAL_SECONDS"
    elapsed=$(( elapsed + STACK_POLL_INTERVAL_SECONDS ))
  done

  report_wait_timeout "$label" "$log_file" "$timeout_seconds"
}

resolve_ollama_mode() {
  if probe_ready_with_retries is_ollama_ready; then
    OLLAMA_MODE="healthy_external"
    return 0
  fi

  if port_listening "$OLLAMA_URL_HOST" "$OLLAMA_URL_PORT"; then
    OLLAMA_MODE="occupied_wait_external"
    return 0
  fi

  OLLAMA_MODE="free_start_managed"
}

start_ollama() {
  resolve_ollama_mode

  case "$OLLAMA_MODE" in
    healthy_external)
      OLLAMA_MODE_DESCRIPTION="reused external"
      echo "Ollama mode: reused external listener at $OLLAMA_URL"
      return 0
      ;;
    occupied_wait_external)
      OLLAMA_MODE_DESCRIPTION="waiting for external readiness"
      echo "Ollama mode: waiting for external listener on $OLLAMA_URL to become healthy before continuing."
      return 0
      ;;
    free_start_managed)
      OLLAMA_MODE_DESCRIPTION="managed start"
      echo "Ollama mode: starting managed listener at $OLLAMA_URL"
      ;;
    *)
      echo "Unsupported Ollama mode: $OLLAMA_MODE" >&2
      exit 1
      ;;
  esac

  if [[ ! -x "$OLLAMA_BIN" ]]; then
    echo "Ollama binary not found at $OLLAMA_BIN" >&2
    exit 1
  fi

  : > "$OLLAMA_LOG_FILE"
  (
    export OLLAMA_HOST="$OLLAMA_BIND_ADDRESS"
    exec "$OLLAMA_BIN" serve
  ) >"$OLLAMA_LOG_FILE" 2>&1 &
  OLLAMA_PID=$!
  OLLAMA_MANAGED=1
  echo "Started managed Ollama under supervisor (pid $OLLAMA_PID)"
}

start_backend() {
  local java21_home

  if reuse_existing_service_if_available "backend" "http://127.0.0.1:${BACKEND_PORT}" "$LOCAL_HOST" "$BACKEND_PORT" is_backend_ready; then
    return 0
  fi

  java21_home=$(resolve_java_21_home || true)
  if [[ -z "$java21_home" ]]; then
    echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before starting backend." >&2
    exit 1
  fi

  ensure_postgres_ready

  : > "$BACKEND_LOG_FILE"
  (
    export JAVA_HOME="$java21_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    export APP_ROLLOUT_METADATA_V1="${APP_ROLLOUT_METADATA_V1:-true}"
    export APP_ROLLOUT_METADATA_FILTERS_V1="${APP_ROLLOUT_METADATA_FILTERS_V1:-true}"
    export APP_ROLLOUT_QUERY_HINTS_V1="${APP_ROLLOUT_QUERY_HINTS_V1:-true}"
    export APP_SECURITY_ADMIN_USERNAME
    export APP_SECURITY_ADMIN_PASSWORD
    cd "$ROOT_DIR/backend"
    exec mvn -Dmaven.test.skip=true spring-boot:run
  ) >"$BACKEND_LOG_FILE" 2>&1 &
  BACKEND_PID=$!
  BACKEND_MANAGED=1
  echo "Started backend under supervisor (pid $BACKEND_PID)"
}

start_frontend() {
  if reuse_existing_service_if_available "frontend" "$FRONTEND_URL" "$LOCAL_HOST" "$FRONTEND_PORT" is_frontend_ready; then
    return 0
  fi

  : > "$FRONTEND_LOG_FILE"
  (
    cd "$ROOT_DIR/frontend"
    exec npm run dev -- --host 127.0.0.1
  ) >"$FRONTEND_LOG_FILE" 2>&1 &
  FRONTEND_PID=$!
  FRONTEND_MANAGED=1
  echo "Started frontend under supervisor (pid $FRONTEND_PID)"
}

monitor_managed_services() {
  local exit_status=0

  while true; do
    if (( FRONTEND_MANAGED )) && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
      wait "$FRONTEND_PID" >/dev/null 2>&1 || exit_status=$?
      [[ $exit_status -eq 0 ]] && exit_status=1
      echo "Frontend exited unexpectedly (status $exit_status)." >&2
      tail_log_excerpt "$FRONTEND_LOG_FILE"
      EXIT_CODE=$exit_status
      exit $EXIT_CODE
    fi

    if (( BACKEND_MANAGED )) && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      wait "$BACKEND_PID" >/dev/null 2>&1 || exit_status=$?
      [[ $exit_status -eq 0 ]] && exit_status=1
      echo "Backend exited unexpectedly (status $exit_status)." >&2
      tail_log_excerpt "$BACKEND_LOG_FILE"
      EXIT_CODE=$exit_status
      exit $EXIT_CODE
    fi

    if (( OLLAMA_MANAGED )) && ! kill -0 "$OLLAMA_PID" 2>/dev/null; then
      wait "$OLLAMA_PID" >/dev/null 2>&1 || exit_status=$?
      [[ $exit_status -eq 0 ]] && exit_status=1
      echo "Ollama exited unexpectedly (status $exit_status)." >&2
      tail_log_excerpt "$OLLAMA_LOG_FILE"
      EXIT_CODE=$exit_status
      exit $EXIT_CODE
    fi

    sleep "$STACK_POLL_INTERVAL_SECONDS"
  done
}

trap 'EXIT_CODE=130; exit $EXIT_CODE' INT
trap 'EXIT_CODE=143; exit $EXIT_CODE' TERM
trap cleanup EXIT

announce_launcher_path

start_ollama
wait_for_service "Ollama" "$OLLAMA_PID" "$OLLAMA_MANAGED" is_ollama_ready "$OLLAMA_LOG_FILE" "$STACK_START_TIMEOUT_SECONDS" || exit $EXIT_CODE

if [[ "$OLLAMA_MODE" == "occupied_wait_external" ]]; then
  OLLAMA_MODE_DESCRIPTION="reused external after wait"
fi

start_backend
wait_for_service "Backend" "$BACKEND_PID" "$BACKEND_MANAGED" is_backend_ready "$BACKEND_LOG_FILE" "$BACKEND_START_TIMEOUT_SECONDS" || exit $EXIT_CODE
zsh "$ROOT_DIR/scripts/local-runtime-diagnostics.zsh" || true

start_frontend
wait_for_service "Frontend" "$FRONTEND_PID" "$FRONTEND_MANAGED" is_frontend_ready "$FRONTEND_LOG_FILE" "$STACK_START_TIMEOUT_SECONDS" || exit $EXIT_CODE

echo
echo "Local stack is ready under supervisor:"
echo "  Frontend: $FRONTEND_URL"
echo "  Backend:  http://127.0.0.1:8080"
echo "  Admin:    $APP_SECURITY_ADMIN_USERNAME / $APP_SECURITY_ADMIN_PASSWORD"
echo "  Ollama:   $OLLAMA_URL ($OLLAMA_MODE_DESCRIPTION)"
echo "  Postgres: $DATASOURCE_URL"
echo "  metadata-v1: $APP_ROLLOUT_METADATA_V1"
echo "  metadata-filters-v1: $APP_ROLLOUT_METADATA_FILTERS_V1"
echo "  query-hints-v1: $APP_ROLLOUT_QUERY_HINTS_V1"
echo "  Logs:     $OLLAMA_LOG_FILE | $BACKEND_LOG_FILE | $FRONTEND_LOG_FILE"
echo
echo "Keep this process alive while you work. Press Ctrl+C to stop managed services."

if (( FRONTEND_MANAGED || BACKEND_MANAGED || OLLAMA_MANAGED )); then
  monitor_managed_services
fi

while true; do
  sleep 3600
done
