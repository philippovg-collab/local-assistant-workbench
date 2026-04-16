#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_FILE="${LOG_FILE:-/tmp/local-model-backend.log}"
BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/health"
BACKEND_POLICY_URL="http://127.0.0.1:8080/api/materials/policy"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"

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
  local health_response
  health_response=$(curl -fsS "$BACKEND_HEALTH_URL" 2>/dev/null || true)
  [[ -n "$health_response" ]] || return 1
  [[ "$health_response" == *'"status":"UP"'* ]] || return 1
}

is_backend_compatible() {
  local policy_response

  if ! is_backend_healthy; then
    return 1
  fi

  policy_response=$(curl -fsS "$BACKEND_POLICY_URL" 2>/dev/null || true)
  [[ -n "$policy_response" ]] || return 1
  [[ "$policy_response" == *'"pdf"'* ]] || return 1
  [[ "$policy_response" == *'"mode":"'* ]] || return 1
}

if is_backend_compatible; then
  echo "Backend is already running at http://127.0.0.1:8080"
  exit 0
fi

if is_backend_healthy; then
  echo "Existing backend is incompatible with the current upload policy contract. Restarting..." >&2
  "$ROOT_DIR/scripts/stop-backend.sh"
fi

JAVA21_HOME=$(resolve_java_21_home || true)
if [[ -z "$JAVA21_HOME" ]]; then
  echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before starting backend." >&2
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

cd "$ROOT_DIR/backend"
nohup mvn spring-boot:run >"$LOG_FILE" 2>&1 &
PID=$!

for _ in {1..30}; do
  sleep 2
  if is_backend_compatible; then
    echo "Backend started successfully (pid $PID)"
    echo "API: http://127.0.0.1:8080"
    echo "Log: $LOG_FILE"
    exit 0
  fi
done

echo "Backend did not become ready. Recent log output:" >&2
tail -n 30 "$LOG_FILE" >&2 || true
exit 1
