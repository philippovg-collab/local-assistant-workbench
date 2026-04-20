#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

FRONTEND_HTTP_PORT="${FRONTEND_HTTP_PORT:-80}"
POSTGRES_DB="${POSTGRES_DB:-ragstudio}"
POSTGRES_USER="${POSTGRES_USER:-ragstudio}"
APP_LLM_MODEL="${APP_LLM_MODEL:-qwen2.5:7b}"
APP_EMBEDDINGS_MODEL="${APP_EMBEDDINGS_MODEL:-nomic-embed-text}"
APP_OCR_LANGUAGES="${APP_OCR_LANGUAGES:-kaz+rus+eng}"
APP_SECURITY_ADMIN_USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
APP_SECURITY_ADMIN_PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-}"
BACKEND_INTERNAL_URL="http://127.0.0.1:8080"
BACKEND_COOKIE_JAR="/tmp/ragstudio-preflight-cookies.txt"
BACKEND_CSRF_HEADER=""
BACKEND_CSRF_TOKEN=""

pass() {
  printf 'PASS %s\n' "$1"
}

fail() {
  printf 'FAIL %s\n' "$1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is not installed or not in PATH"
}

compose() {
  docker compose "$@"
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

  session_json="$(compose exec -T backend curl -fsS -c "${BACKEND_COOKIE_JAR}" "${BACKEND_INTERNAL_URL}/api/auth/session")"
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
  )"
  grep -q '"authenticated"[[:space:]]*:[[:space:]]*true' <<<"${login_json}" || fail "Admin login failed"
}

backend_get() {
  compose exec -T backend curl -fsS -b "${BACKEND_COOKIE_JAR}" "${BACKEND_INTERNAL_URL}$1"
}

require_command docker
docker info >/dev/null 2>&1 || fail "Docker daemon is not reachable"
compose version >/dev/null 2>&1 || fail "Docker Compose plugin is not available"
pass "Docker and Compose are available"

[[ -n "${APP_SECURITY_ADMIN_PASSWORD}" ]] || fail "APP_SECURITY_ADMIN_PASSWORD must be set for authenticated preflight checks"

compose ps --services --status running | grep -qx postgres || fail "postgres service is not running"
compose ps --services --status running | grep -qx ollama || fail "ollama service is not running"
compose ps --services --status running | grep -qx backend || fail "backend service is not running"
compose ps --services --status running | grep -qx frontend || fail "frontend service is not running"
pass "Core services are running"

POSTGRES_VECTOR="$(
  compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc \
    "select extname from pg_extension where extname = 'vector';" | tr -d '[:space:]'
)"
[[ "${POSTGRES_VECTOR}" == "vector" ]] || fail "PostgreSQL vector extension is not installed in ${POSTGRES_DB}"
pass "PostgreSQL vector extension is ready"

OLLAMA_MODELS="$(compose exec -T ollama ollama list)"
grep -Fq "${APP_LLM_MODEL}" <<<"${OLLAMA_MODELS}" || fail "Ollama model is missing: ${APP_LLM_MODEL}"
grep -Fq "${APP_EMBEDDINGS_MODEL}" <<<"${OLLAMA_MODELS}" || fail "Ollama embedding model is missing: ${APP_EMBEDDINGS_MODEL}"
pass "Ollama models are present"

TESSERACT_LANGS="$(compose exec -T backend tesseract --list-langs 2>/dev/null)"
IFS='+' read -r -a OCR_LANG_ARRAY <<<"${APP_OCR_LANGUAGES}"
for lang in "${OCR_LANG_ARRAY[@]}"; do
  grep -qx "${lang}" <<<"${TESSERACT_LANGS}" || fail "Tesseract language is missing in backend image: ${lang}"
done
pass "Tesseract runtime and OCR languages are available"

compose exec -T backend curl -fsS "${BACKEND_INTERNAL_URL}/api/liveness" >/dev/null || fail "Public backend liveness endpoint is not reachable"
pass "Backend liveness is public"

if compose exec -T backend curl -fsS "${BACKEND_INTERNAL_URL}/api/health" >/dev/null 2>&1; then
  fail "Backend health endpoint is reachable without authentication"
fi
pass "Backend health endpoint requires authentication"

backend_login
pass "Admin API login works"

HEALTH_JSON="$(backend_get /api/health)"
grep -q '"databaseStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Database status is not UP: ${HEALTH_JSON}"
grep -q '"vectorStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Vector status is not UP: ${HEALTH_JSON}"
grep -q '"llmStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "LLM status is not UP: ${HEALTH_JSON}"
grep -q '"embeddingStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "Embedding status is not UP: ${HEALTH_JSON}"
grep -q '"ocrStatus"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}" || fail "OCR status is not UP: ${HEALTH_JSON}"
if grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_JSON}"; then
  pass "Backend top-level health is UP"
elif grep -q '"knowledgeStatus"[[:space:]]*:[[:space:]]*"EMPTY"' <<<"${HEALTH_JSON}"; then
  printf 'WARN Backend top-level health is DEGRADED because the knowledge base is empty; infrastructure checks are UP.\n'
else
  printf 'WARN Backend top-level health is not UP. Component checks passed; inspect /api/health for deployment context.\n'
fi
pass "Backend infrastructure health is UP, including OCR"

POLICY_JSON="$(backend_get /api/materials/policy)"
grep -q '"scannedPdfSupport"[[:space:]]*:[[:space:]]*true' <<<"${POLICY_JSON}" || fail "Scanned PDF support is not enabled: ${POLICY_JSON}"
grep -q '"mode"[[:space:]]*:[[:space:]]*"embedded_text_and_ocr"' <<<"${POLICY_JSON}" || fail "PDF mode is not embedded_text_and_ocr: ${POLICY_JSON}"
pass "Material policy enables scanned PDF OCR"

curl -fsS "http://127.0.0.1:${FRONTEND_HTTP_PORT}/healthz" >/dev/null || fail "Frontend health endpoint is not reachable on port ${FRONTEND_HTTP_PORT}"
pass "Frontend nginx is reachable"

if compose ps --services --status running | grep -qx elasticsearch; then
  compose exec -T elasticsearch curl -fsS http://127.0.0.1:9200/_cluster/health >/dev/null \
    || fail "Elasticsearch profile is running but cluster health is not reachable"
  pass "Optional Elasticsearch profile is reachable"
fi

echo "Preflight complete."
