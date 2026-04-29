#!/bin/zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"

RUN_BACKEND=1
RUN_FRONTEND=1
RUN_SCAN=1
BACKEND_GOAL="test"
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonarcloud.io}"
SONAR_QUALITYGATE_TIMEOUT="${SONAR_QUALITYGATE_TIMEOUT:-600}"

usage() {
  cat <<'EOF'
Usage: ./scripts/sonar-review.sh [options]

Runs the local Sonar review workflow:
  1. backend coverage with Maven + JaCoCo
  2. frontend coverage with Vitest LCOV
  3. Sonar Scanner with Quality Gate waiting enabled

Options:
  --integration       Run backend mvn -Pcoverage verify instead of test.
  --skip-backend      Do not regenerate backend coverage.
  --skip-frontend     Do not regenerate frontend coverage.
  --skip-scan         Generate coverage only; do not call sonar-scanner.
  --host-url URL      Override SONAR_HOST_URL for this run.
  -h, --help          Show this help.

Environment:
  SONAR_TOKEN                 Required unless --skip-scan is used.
  SONAR_HOST_URL              Optional; defaults to https://sonarcloud.io.
  SONAR_QUALITYGATE_TIMEOUT   Optional; defaults to 600 seconds.
  JAVA_21_HOME                Optional explicit Java 21 home for backend tests.
EOF
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

require_command() {
  local command_name="$1"
  local install_hint="$2"

  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command '$command_name' was not found. $install_hint" >&2
    exit 1
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --integration)
      BACKEND_GOAL="verify"
      shift
      ;;
    --skip-backend)
      RUN_BACKEND=0
      shift
      ;;
    --skip-frontend)
      RUN_FRONTEND=0
      shift
      ;;
    --skip-scan)
      RUN_SCAN=0
      shift
      ;;
    --host-url)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "--host-url requires a URL value." >&2
        exit 1
      fi
      SONAR_HOST_URL="$2"
      shift 2
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

if [[ "$RUN_BACKEND" -eq 1 ]]; then
  require_command mvn "Install Maven or run this from a shell where Maven is available."

  JAVA21_HOME=$(resolve_java_21_home || true)
  if [[ -z "$JAVA21_HOME" ]]; then
    echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before running backend coverage." >&2
    exit 1
  fi

  export JAVA_HOME="$JAVA21_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"

  echo "Using JAVA_HOME=$JAVA_HOME"
  echo "Generating backend coverage: mvn -B -Pcoverage $BACKEND_GOAL"
  cd "$BACKEND_DIR"
  mvn -B -Pcoverage "$BACKEND_GOAL"
fi

if [[ "$RUN_FRONTEND" -eq 1 ]]; then
  require_command npm "Install Node.js/npm, then run npm ci in frontend if dependencies are missing."

  echo "Generating frontend coverage: npm run test:coverage"
  cd "$FRONTEND_DIR"
  if [[ ! -d node_modules ]]; then
    echo "frontend/node_modules is missing. Run 'npm ci' in frontend before this script, or use CI to install dependencies." >&2
    exit 1
  fi
  npm run test:coverage
fi

if [[ "$RUN_SCAN" -eq 1 ]]; then
  require_command sonar-scanner "Install SonarScanner CLI, or run the GitHub Actions SonarCloud job."

  if [[ -z "${SONAR_TOKEN:-}" ]]; then
    echo "SONAR_TOKEN is required for Sonar analysis. Create a token in Sonar and export SONAR_TOKEN before running this script." >&2
    exit 1
  fi

  echo "Running Sonar analysis against $SONAR_HOST_URL"
  cd "$ROOT_DIR"
  sonar-scanner \
    -Dsonar.host.url="$SONAR_HOST_URL" \
    -Dsonar.token="$SONAR_TOKEN" \
    -Dsonar.qualitygate.wait=true \
    -Dsonar.qualitygate.timeout="$SONAR_QUALITYGATE_TIMEOUT"
fi

echo "Sonar review workflow completed."
