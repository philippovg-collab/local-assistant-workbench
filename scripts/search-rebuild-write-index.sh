#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
BACKEND_DIR="$ROOT_DIR/backend"
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

JAVA21_HOME=$(resolve_java_21_home || true)
if [[ -z "$JAVA21_HOME" ]]; then
  echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before rebuilding the search write index." >&2
  exit 1
fi

export JAVA_HOME="$JAVA21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export APP_SEARCH_SYNC_ENABLED=true
export SPRING_MAIN_WEB_APPLICATION_TYPE=none

echo "Using JAVA_HOME=$JAVA_HOME"
cd "$BACKEND_DIR"
mvn -q -DskipTests spring-boot:run \
  "-Dspring-boot.run.arguments=--app.search-sync.operator.command=rebuild-write-index"
