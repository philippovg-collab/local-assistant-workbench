#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

COMPOSE_FILE_PATH="${COMPOSE_FILE_PATH:-}"
if [[ -z "${COMPOSE_FILE_PATH}" ]]; then
  if [[ -f "${ROOT_DIR}/docker-compose.prod.yml" ]]; then
    COMPOSE_FILE_PATH="${ROOT_DIR}/docker-compose.prod.yml"
  elif [[ -f "${ROOT_DIR}/docker-compose.yml" ]]; then
    COMPOSE_FILE_PATH="${ROOT_DIR}/docker-compose.yml"
  fi
fi

detect_deploy_base() {
  local root_parent
  root_parent="$(basename "$(dirname "${ROOT_DIR}")")"
  if [[ "${root_parent}" == "releases" ]]; then
    cd "${ROOT_DIR}/../.." && pwd
    return 0
  fi
  if [[ "$(basename "${ROOT_DIR}")" == "current" ]]; then
    cd "${ROOT_DIR}/.." && pwd
    return 0
  fi
  printf '%s\n' "${ROOT_DIR}"
}

DEPLOY_BASE="${DEPLOY_BASE:-$(detect_deploy_base)}"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/.env.production" ]]; then
    ENV_FILE="${ROOT_DIR}/.env.production"
  elif [[ -f "${ROOT_DIR}/.env" ]]; then
    ENV_FILE="${ROOT_DIR}/.env"
  elif [[ -f "${DEPLOY_BASE}/shared/.env" ]]; then
    ENV_FILE="${DEPLOY_BASE}/shared/.env"
  fi
fi

if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "${ENV_FILE}"
  set +a
fi

FRONTEND_HTTP_PORT="${FRONTEND_HTTP_PORT:-${FRONTEND_PORT:-8088}}"
FRONTEND_CHECK_HOST="${FRONTEND_CHECK_HOST:-127.0.0.1}"
FRONTEND_PUBLIC_URL="${FRONTEND_PUBLIC_URL:-http://${FRONTEND_CHECK_HOST}:${FRONTEND_HTTP_PORT}}"
POSTGRES_DB="${POSTGRES_DB:-ragstudio}"
POSTGRES_USER="${POSTGRES_USER:-ragstudio}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-}"
APP_LLM_MODEL="${APP_LLM_MODEL:-qwen2.5:7b}"
APP_EMBEDDINGS_MODEL="${APP_EMBEDDINGS_MODEL:-nomic-embed-text}"
APP_OCR_ENABLED="${APP_OCR_ENABLED:-false}"
APP_OCR_LANGUAGES="${APP_OCR_LANGUAGES:-kaz+rus+eng}"
APP_SECURITY_ADMIN_USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
APP_SECURITY_ADMIN_PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-}"
PREFLIGHT_REQUIRE_LLM="${PREFLIGHT_REQUIRE_LLM:-true}"
PREFLIGHT_REQUIRE_EMBEDDINGS="${PREFLIGHT_REQUIRE_EMBEDDINGS:-false}"
BACKEND_INTERNAL_URL="http://127.0.0.1:8080"
BACKEND_COOKIE_JAR="/tmp/ragstudio-preflight-cookies.txt"
BACKEND_CSRF_HEADER=""
BACKEND_CSRF_TOKEN=""
STARTUP_WAIT_SECONDS="${STARTUP_WAIT_SECONDS:-120}"

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1" >&2
  exit 1
}

is_placeholder_secret() {
  local value
  value="$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')"
  case "${value}" in
    ""|admin|password|secret|local-admin-password|change-me|change-me-admin|ragstudio|replace-with-*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

wait_until() {
  local timeout_seconds="$1"
  local description="$2"
  shift 2

  local elapsed=0
  until "$@"; do
    if (( elapsed >= timeout_seconds )); then
      fail "${description}"
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is not installed or not in PATH"
}

compose() {
  local compose_args=()
  if [[ -n "${COMPOSE_FILE_PATH}" ]]; then
    compose_args+=(-f "${COMPOSE_FILE_PATH}")
  fi
  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    docker compose "${compose_args[@]}" --env-file "${ENV_FILE}" "$@"
    return
  fi
  docker compose "${compose_args[@]}" "$@"
}

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "${value}"
}

extract_json_string() {
  local key="$1"
  sed -nE "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"([^\"]*)\".*/\1/p"
}

backend_login() {
  local session_json login_json username_json password_json login_payload

  session_json="$(compose exec -T backend curl -fsS -c "${BACKEND_COOKIE_JAR}" "${BACKEND_INTERNAL_URL}/api/auth/session")" \
    || fail "Could not reach /api/auth/session"
  BACKEND_CSRF_HEADER="$(printf '%s' "${session_json}" | extract_json_string "csrfHeaderName")"
  BACKEND_CSRF_TOKEN="$(printf '%s' "${session_json}" | extract_json_string "csrfToken")"
  [[ -n "${BACKEND_CSRF_HEADER}" && -n "${BACKEND_CSRF_TOKEN}" ]] || fail "Could not obtain CSRF token from /api/auth/session"

  username_json="$(json_escape "${APP_SECURITY_ADMIN_USERNAME}")"
  password_json="$(json_escape "${APP_SECURITY_ADMIN_PASSWORD}")"
  login_payload="{\"username\":\"${username_json}\",\"password\":\"${password_json}\"}"
  login_json="$(
    printf '%s' "${login_payload}" | compose exec -T backend curl -fsS \
      -b "${BACKEND_COOKIE_JAR}" \
      -c "${BACKEND_COOKIE_JAR}" \
      -H "Content-Type: application/json" \
      -H "${BACKEND_CSRF_HEADER}: ${BACKEND_CSRF_TOKEN}" \
      --data-binary @- \
      "${BACKEND_INTERNAL_URL}/api/auth/login"
  )" || fail "Could not complete admin login request"
  grep -q '"authenticated"[[:space:]]*:[[:space:]]*true' <<<"${login_json}" || fail "Admin login failed"
}

backend_get() {
  compose exec -T backend curl -fsS -b "${BACKEND_COOKIE_JAR}" "${BACKEND_INTERNAL_URL}$1"
}

require_command docker
require_command curl
docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"
compose version >/dev/null 2>&1 || fail "Docker Compose plugin is not available"
pass "Docker and Compose are available"

is_placeholder_secret "${POSTGRES_PASSWORD}" && fail "POSTGRES_PASSWORD must be set to a non-placeholder production secret"
is_placeholder_secret "${APP_SECURITY_ADMIN_PASSWORD}" && fail "APP_SECURITY_ADMIN_PASSWORD must be set to a non-placeholder production secret"

compose ps --services --status running | grep -qx postgres || fail "postgres service is not running"
compose ps --services --status running | grep -qx backend || fail "backend service is not running"
compose ps --services --status running | grep -qx frontend || fail "frontend service is not running"
pass "Core services are running"

POSTGRES_VECTOR="$(
  compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc \
    "select extname from pg_extension where extname = 'vector';" | tr -d '[:space:]'
)"
[[ "${POSTGRES_VECTOR}" == "vector" ]] || fail "PostgreSQL vector extension is not installed in ${POSTGRES_DB}"
pass "PostgreSQL vector extension is ready"

TESSERACT_LANGS="$(compose exec -T backend tesseract --list-langs 2>/dev/null)"
IFS='+' read -r -a OCR_LANG_ARRAY <<<"${APP_OCR_LANGUAGES}"
for lang in "${OCR_LANG_ARRAY[@]}"; do
  grep -qx "${lang}" <<<"${TESSERACT_LANGS}" || fail "Tesseract language is missing in backend image: ${lang}"
done
pass "Tesseract runtime and OCR languages are available"

wait_until "${STARTUP_WAIT_SECONDS}" \
  "Public backend liveness endpoint is not reachable" \
  compose exec -T backend curl -fsS "${BACKEND_INTERNAL_URL}/api/liveness" >/dev/null
pass "Backend liveness is public"

if compose exec -T backend curl -fsS "${BACKEND_INTERNAL_URL}/api/health" >/dev/null 2>&1; then
  fail "Backend health endpoint is reachable without authentication"
fi
pass "Backend health endpoint requires authentication"

wait_until "${STARTUP_WAIT_SECONDS}" \
  "Admin API login is not ready" \
  backend_login
pass "Admin API login works"

HEALTH_JSON="$(backend_get /api/health)" || fail "Authenticated backend health endpoint is not reachable"
grep -q '"databaseStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Database status is not UP: ${HEALTH_JSON}"
grep -q '"vectorStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Vector status is not UP: ${HEALTH_JSON}"
if [[ "${PREFLIGHT_REQUIRE_LLM}" == "true" ]]; then
  grep -q '"llmStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "LLM status is not UP: ${HEALTH_JSON}"
else
  if grep -q '"llmStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}"; then
    pass "LLM health is UP"
  else
    printf 'WARN LLM readiness is degraded; continuing because PREFLIGHT_REQUIRE_LLM=false.\n'
  fi
fi
if [[ "${PREFLIGHT_REQUIRE_EMBEDDINGS}" == "true" ]]; then
  grep -q '"embeddingStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Embedding status is not UP: ${HEALTH_JSON}"
else
  if grep -q '"embeddingStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}"; then
    pass "Embedding health is UP"
  else
    printf 'WARN Embedding readiness is degraded; continuing because PREFLIGHT_REQUIRE_EMBEDDINGS=false.\n'
  fi
fi
if [[ "${APP_OCR_ENABLED}" == "true" ]]; then
  grep -q '"ocrStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "OCR status is not UP: ${HEALTH_JSON}"
else
  if grep -q '"ocrStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}"; then
    pass "OCR health is UP"
  else
    printf 'WARN OCR is disabled by configuration; skipping strict OCR health requirement.\n'
  fi
fi
if grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}"; then
  pass "Backend top-level health is UP"
elif grep -q '"knowledgeStatus"[[:space:]]*:[[:space:]]*"EMPTY"' <<<"${HEALTH_JSON}"; then
  printf 'WARN Backend top-level health is DEGRADED because the knowledge base is empty; infrastructure checks are UP.\n'
else
  printf 'WARN Backend top-level health is not UP. Component checks passed; inspect /api/health for deployment context.\n'
fi
if [[ "${APP_OCR_ENABLED}" == "true" ]]; then
  pass "Backend infrastructure health is UP, including OCR"
else
  pass "Backend infrastructure health is UP"
fi

POLICY_JSON="$(backend_get /api/materials/policy)" || fail "Authenticated material policy endpoint is not reachable"
if [[ "${APP_OCR_ENABLED}" == "true" ]]; then
  grep -q '"scannedPdfSupport"[[:space:]]*:[[:space:]]*true' <<<"${POLICY_JSON}" || fail "Scanned PDF support is not enabled: ${POLICY_JSON}"
  grep -q '"mode"[[:space:]]*:[[:space:]]*"embedded_text_and_ocr"' <<<"${POLICY_JSON}" || fail "PDF mode is not embedded_text_and_ocr: ${POLICY_JSON}"
  pass "Material policy enables scanned PDF OCR"
else
  printf 'WARN OCR is disabled by configuration; skipping scanned PDF OCR policy requirement.\n'
fi

wait_until "${STARTUP_WAIT_SECONDS}" \
  "Frontend health endpoint is not reachable at ${FRONTEND_PUBLIC_URL}/healthz" \
  curl -fsS "${FRONTEND_PUBLIC_URL}/healthz" >/dev/null
pass "Frontend nginx is reachable"

wait_until "${STARTUP_WAIT_SECONDS}" \
  "Nginx /api proxy liveness endpoint is not reachable at ${FRONTEND_PUBLIC_URL}/api/liveness" \
  curl -fsS "${FRONTEND_PUBLIC_URL}/api/liveness" >/dev/null
pass "Frontend nginx proxies /api/liveness"

if curl -fsS "${FRONTEND_PUBLIC_URL}/api/health" >/dev/null 2>&1; then
  fail "Proxied backend health endpoint is reachable without authentication"
fi
pass "Proxied backend health endpoint requires authentication"

if compose ps --services --status running | grep -qx elasticsearch; then
  compose exec -T elasticsearch curl -fsS http://127.0.0.1:9200/_cluster/health >/dev/null \
    || fail "Elasticsearch profile is running but cluster health is not reachable"
  pass "Optional Elasticsearch profile is reachable"
fi

echo "Preflight complete."
