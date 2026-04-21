#!/bin/zsh
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
BACKEND_AUTH_SESSION_URL="${BACKEND_URL}/api/auth/session"
BACKEND_AUTH_LOGIN_URL="${BACKEND_URL}/api/auth/login"
BACKEND_HEALTH_URL="${BACKEND_URL}/api/health"
BACKEND_MODELS_URL="${BACKEND_URL}/api/models"
APP_SECURITY_ADMIN_USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
APP_SECURITY_ADMIN_PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-local-admin-password}"
DEFAULT_LLM_MODEL="${APP_LLM_MODEL:-qwen2.5:7b}"
DEEPSEEK_MODEL="${APP_LLM_DEEPSEEK_MODEL:-deepseek-r1:8b}"
LLM_BASE_URL="${APP_LLM_BASE_URL:-http://127.0.0.1:11434}"
OLLAMA_MODELS_DIR="${OLLAMA_MODELS:-$HOME/.ollama/models}"
COOKIE_JAR="${TMPDIR:-/tmp}/ragstudio-runtime-diagnostics-cookies.$$"

cleanup() {
  rm -f "$COOKIE_JAR"
}

trap cleanup EXIT

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

json_string_field() {
  local field="$1"
  sed -nE "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\"([^\"]*)\".*/\1/p" | head -n 1
}

backend_login() {
  local session_json csrf_header csrf_token username_json password_json login_payload login_json

  session_json="$(curl -sS -c "$COOKIE_JAR" "$BACKEND_AUTH_SESSION_URL" 2>/dev/null || true)"
  csrf_header="$(printf '%s' "$session_json" | json_string_field "csrfHeaderName")"
  csrf_token="$(printf '%s' "$session_json" | json_string_field "csrfToken")"
  if [[ -z "$csrf_header" || -z "$csrf_token" ]]; then
    echo "Runtime diagnostics: could not obtain CSRF token from ${BACKEND_AUTH_SESSION_URL}." >&2
    return 1
  fi

  username_json="$(json_escape "$APP_SECURITY_ADMIN_USERNAME")"
  password_json="$(json_escape "$APP_SECURITY_ADMIN_PASSWORD")"
  login_payload="{\"username\":\"${username_json}\",\"password\":\"${password_json}\"}"
  login_json="$(
    curl -sS \
      -b "$COOKIE_JAR" \
      -c "$COOKIE_JAR" \
      -H "Content-Type: application/json" \
      -H "${csrf_header}: ${csrf_token}" \
      --data-binary "$login_payload" \
      "$BACKEND_AUTH_LOGIN_URL" 2>/dev/null || true
  )"

  if ! grep -q '"authenticated"[[:space:]]*:[[:space:]]*true' <<<"$login_json"; then
    echo "Runtime diagnostics: admin API login failed; skipping authenticated model/health checks." >&2
    return 1
  fi
}

backend_get() {
  curl -sS -b "$COOKIE_JAR" "$1" 2>/dev/null || true
}

json_has_model() {
  local json="$1"
  local model="$2"
  grep -Fq "\"name\":\"${model}\"" <<<"$json"
}

deepseek_manifest_exists() {
  local model_name="$DEEPSEEK_MODEL"
  local tag="latest"

  if [[ "$DEEPSEEK_MODEL" == *":"* ]]; then
    model_name="${DEEPSEEK_MODEL%%:*}"
    tag="${DEEPSEEK_MODEL#*:}"
  fi

  [[ -f "${OLLAMA_MODELS_DIR}/manifests/registry.ollama.ai/library/${model_name}/${tag}" ]]
}

warn_docker_deepseek_missing() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi
  if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'ragstudio-ollama-1'; then
    return 0
  fi
  if docker exec ragstudio-ollama-1 ollama list 2>/dev/null | grep -Fq "$DEEPSEEK_MODEL"; then
    return 0
  fi

  echo "Runtime diagnostics: Docker Ollama container ragstudio-ollama-1 is running without ${DEEPSEEK_MODEL}." >&2
  echo "  Pull it explicitly: docker exec ragstudio-ollama-1 ollama pull ${DEEPSEEK_MODEL}" >&2
  echo "  Or start Compose with: APP_LLM_EXTRA_MODELS=${DEEPSEEK_MODEL} docker compose up -d ollama ollama-init backend frontend" >&2
}

main() {
  local models_json health_json models_error_code llm_status embedding_status default_model_present="false"

  if ! backend_login; then
    warn_docker_deepseek_missing
    return 0
  fi

  models_json="$(backend_get "$BACKEND_MODELS_URL")"
  health_json="$(backend_get "$BACKEND_HEALTH_URL")"
  models_error_code="$(printf '%s' "$models_json" | json_string_field "code")"
  llm_status="$(printf '%s' "$health_json" | json_string_field "llmStatus")"
  embedding_status="$(printf '%s' "$health_json" | json_string_field "embeddingStatus")"

  if json_has_model "$models_json" "$DEFAULT_LLM_MODEL"; then
    default_model_present="true"
  fi

  if [[ "$models_error_code" == "llm.provider_unavailable" || "$llm_status" == "DOWN" ]]; then
    echo "Runtime diagnostics: backend cannot reach the local LLM provider at ${LLM_BASE_URL}." >&2
    if deepseek_manifest_exists; then
      echo "Runtime diagnostics: ${DEEPSEEK_MODEL} is downloaded in host Ollama storage, but backend does not see Ollama at ${LLM_BASE_URL}." >&2
    fi
  fi

  if [[ "$embedding_status" == "DOWN" ]]; then
    echo "Runtime diagnostics: backend cannot reach the embedding provider at ${LLM_BASE_URL}; RAG indexing and retrieval will stay degraded." >&2
  fi

  if [[ -n "$models_json" && -z "$models_error_code" && "$default_model_present" != "true" ]]; then
    echo "Runtime diagnostics: default chat model ${DEFAULT_LLM_MODEL} is missing from /api/models." >&2
  fi

  if [[ -z "$models_error_code" && "$default_model_present" == "true" ]]; then
    echo "Runtime diagnostics: Ollama catalog is reachable and contains ${DEFAULT_LLM_MODEL}."
  fi

  warn_docker_deepseek_missing
}

main || true
