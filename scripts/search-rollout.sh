#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
INDEX_DEFINITION_FILE="${BACKEND_DIR}/src/main/resources/elasticsearch/searchable-chunks-index.json"
EVIDENCE_DIR="${BACKEND_DIR}/target/search-rollout"
DEFAULT_JAVA_21_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
declare -a ORIGINAL_ARGS
ORIGINAL_ARGS=("$@")

source "${SCRIPT_DIR}/search-rollout-common.sh"

usage() {
  cat >&2 <<USAGE
Usage:
  ./scripts/search-rollout.sh prepare <version>
  ./scripts/search-rollout.sh rebuild
  ./scripts/search-rollout.sh requeue
  ./scripts/search-rollout.sh smoke [query]
  ./scripts/search-rollout.sh promote <version>
  ./scripts/search-rollout.sh rollback <version>
  ./scripts/search-rollout.sh status
USAGE
}

COMMAND="${1:-}"
VERSION="${2:-}"
INDEX_PREFIX="${APP_SEARCH_SYNC_INDEX_PREFIX:-rag-chunks}"
WRITE_ALIAS="${INDEX_PREFIX}-write"
READ_ALIAS="${INDEX_PREFIX}-read"

if [[ -z "${COMMAND}" ]]; then
  usage
  exit 1
fi

if [[ ! -f "${INDEX_DEFINITION_FILE}" ]]; then
  echo "Index definition file not found: ${INDEX_DEFINITION_FILE}" >&2
  exit 1
fi

resolve_elasticsearch_url() {
  local raw_url base_url
  raw_url="${SPRING_ELASTICSEARCH_URIS:-${ELASTICSEARCH_URL:-http://127.0.0.1:9200}}"
  base_url="${raw_url%%,*}"
  base_url="${base_url//[[:space:]]/}"
  if [[ -z "${base_url}" ]]; then
    echo "Elasticsearch URL is empty. Set SPRING_ELASTICSEARCH_URIS or ELASTICSEARCH_URL." >&2
    exit 1
  fi
  echo "${base_url%/}"
}

ELASTICSEARCH_BASE_URL="$(resolve_elasticsearch_url)"

declare -a CURL_ARGS
declare -a CURL_FAIL_ARGS
CURL_ARGS=(-sS)
CURL_FAIL_ARGS=(-fsS)
if [[ -n "${SPRING_ELASTICSEARCH_USERNAME:-}" || -n "${SPRING_ELASTICSEARCH_PASSWORD:-}" ]]; then
  CURL_ARGS+=(-u "${SPRING_ELASTICSEARCH_USERNAME:-}:${SPRING_ELASTICSEARCH_PASSWORD:-}")
  CURL_FAIL_ARGS+=(-u "${SPRING_ELASTICSEARCH_USERNAME:-}:${SPRING_ELASTICSEARCH_PASSWORD:-}")
fi

BACKEND_COOKIE_JAR=""
BACKEND_CSRF_HEADER=""
BACKEND_CSRF_TOKEN=""

cleanup_backend_auth() {
  if [[ -n "${BACKEND_COOKIE_JAR}" ]]; then
    rm -f "${BACKEND_COOKIE_JAR}"
  fi
}
trap cleanup_backend_auth EXIT

require_version() {
  if [[ -z "${VERSION}" ]]; then
    echo "Command '${COMMAND}' requires <version>." >&2
    usage
    exit 1
  fi
}

target_index() {
  echo "${INDEX_PREFIX}-${VERSION}"
}

http_status() {
  curl "${CURL_ARGS[@]}" -o /dev/null -w '%{http_code}' "$@"
}

index_exists() {
  local index_name="$1"
  [[ "$(http_status -I "${ELASTICSEARCH_BASE_URL}/${index_name}")" == "200" ]]
}

alias_exists() {
  local alias_name="$1"
  [[ "$(http_status -I "${ELASTICSEARCH_BASE_URL}/_alias/${alias_name}")" == "200" ]]
}

alias_targets() {
  local alias_name="$1"
  local targets
  targets="$(curl "${CURL_FAIL_ARGS[@]}" "${ELASTICSEARCH_BASE_URL}/_cat/aliases/${alias_name}?h=index&s=index" 2>/dev/null || true)"
  targets="${targets//$'\n'/,}"
  targets="${targets%,}"
  if [[ -z "${targets}" ]]; then
    echo "(none)"
  else
    echo "${targets}"
  fi
}

mapping_hash() {
  local digest
  if command -v node >/dev/null 2>&1; then
    node -e 'const fs=require("fs");const crypto=require("crypto");function canonical(value){if(Array.isArray(value)){return "["+value.map(canonical).join(",")+"]";}if(value&&typeof value==="object"){return "{"+Object.keys(value).sort().map((key)=>JSON.stringify(key)+":"+canonical(value[key])).join(",")+"}";}return JSON.stringify(value);}const value=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));process.stdout.write(crypto.createHash("sha256").update(canonical(value),"utf8").digest("hex"));' "${INDEX_DEFINITION_FILE}"
    return
  fi

  digest="$(shasum -a 256 "${INDEX_DEFINITION_FILE}")"
  echo "${digest%% *}"
}

json_escape() {
  local value="${1:-}"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  echo "${value}"
}

backend_base_url() {
  echo "${APP_BACKEND_URL:-http://127.0.0.1:8080}" | sed 's:/*$::'
}

ensure_backend_auth() {
  if [[ -z "${APP_SECURITY_ADMIN_PASSWORD:-}" ]]; then
    return 0
  fi
  if [[ -n "${BACKEND_COOKIE_JAR}" ]]; then
    return 0
  fi

  local base_url session_json login_payload login_json authenticated
  local username_json password_json
  base_url="$(backend_base_url)"
  BACKEND_COOKIE_JAR="$(mktemp)"

  echo "Authenticating backend API as ${APP_SECURITY_ADMIN_USERNAME:-admin}"
  session_json="$(curl -fsS -c "${BACKEND_COOKIE_JAR}" "${base_url}/api/auth/session")" || {
    echo "Backend auth session request failed." >&2
    return 1
  }
  BACKEND_CSRF_HEADER="$(json_value "${session_json}" "csrfHeaderName")"
  BACKEND_CSRF_TOKEN="$(json_value "${session_json}" "csrfToken")"
  if [[ -z "${BACKEND_CSRF_HEADER}" || -z "${BACKEND_CSRF_TOKEN}" ]]; then
    echo "Backend auth session did not return CSRF fields." >&2
    return 1
  fi

  username_json="$(json_escape "${APP_SECURITY_ADMIN_USERNAME:-admin}")"
  password_json="$(json_escape "${APP_SECURITY_ADMIN_PASSWORD}")"
  login_payload="{\"username\":\"${username_json}\",\"password\":\"${password_json}\"}"
  login_json="$(curl -fsS \
    -b "${BACKEND_COOKIE_JAR}" \
    -c "${BACKEND_COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -H "${BACKEND_CSRF_HEADER}: ${BACKEND_CSRF_TOKEN}" \
    -d "${login_payload}" \
    "${base_url}/api/auth/login")" || {
    echo "Backend admin login failed." >&2
    return 1
  }
  authenticated="$(json_value "${login_json}" "authenticated")"
  if [[ "${authenticated}" != "true" ]]; then
    echo "Backend admin login did not return authenticated=true." >&2
    return 1
  fi
}

backend_curl() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local base_url
  local -a args
  base_url="$(backend_base_url)"
  args=(-fsS -X "${method}")
  if [[ -n "${BACKEND_COOKIE_JAR}" ]]; then
    args+=(-b "${BACKEND_COOKIE_JAR}" -c "${BACKEND_COOKIE_JAR}")
  fi
  if [[ "${method}" != "GET" && -n "${BACKEND_CSRF_TOKEN}" ]]; then
    args+=(-H "${BACKEND_CSRF_HEADER}: ${BACKEND_CSRF_TOKEN}")
  fi
  if [[ -n "${body}" ]]; then
    args+=(-H 'Content-Type: application/json' -d "${body}")
  fi

  curl "${args[@]}" "${base_url}${path}"
}

write_evidence() {
  local command_name="$1"
  local version_value="$2"
  local before_read="$3"
  local before_write="$4"
  local after_read="$5"
  local after_write="$6"
  local result="$7"
  local error_message="$8"
  rollout_write_evidence \
    "${command_name}" \
    "${version_value}" \
    "${before_read}" \
    "${before_write}" \
    "${after_read}" \
    "${after_write}" \
    "${result}" \
    "${error_message}"
}

create_index_if_missing() {
  local index_name="$1"
  if index_exists "${index_name}"; then
    echo "Index already exists: ${index_name}"
    return
  fi

  curl "${CURL_FAIL_ARGS[@]}" \
    -X PUT "${ELASTICSEARCH_BASE_URL}/${index_name}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${INDEX_DEFINITION_FILE}" >/dev/null

  echo "Created index: ${index_name}"
}

move_alias() {
  local alias_name="$1"
  local index_name="$2"
  local write_alias="$3"
  local add_action payload
  if [[ "${write_alias}" == "true" ]]; then
    add_action="{\"add\":{\"index\":\"${index_name}\",\"alias\":\"${alias_name}\",\"is_write_index\":true}}"
  else
    add_action="{\"add\":{\"index\":\"${index_name}\",\"alias\":\"${alias_name}\"}}"
  fi

  if alias_exists "${alias_name}"; then
    payload="{\"actions\":[{\"remove\":{\"index\":\"*\",\"alias\":\"${alias_name}\"}},${add_action}]}"
  else
    payload="{\"actions\":[${add_action}]}"
  fi

  curl "${CURL_FAIL_ARGS[@]}" \
    -X POST "${ELASTICSEARCH_BASE_URL}/_aliases" \
    -H 'Content-Type: application/json' \
    -d "${payload}" >/dev/null
}

create_read_alias_if_missing() {
  local index_name="$1"
  if alias_exists "${READ_ALIAS}"; then
    echo "Read alias already exists and was left unchanged: ${READ_ALIAS}"
    return
  fi
  move_alias "${READ_ALIAS}" "${index_name}" false
  echo "Created initial read alias on ${index_name}: ${READ_ALIAS}"
}

run_prepare() {
  require_version
  local index_name
  index_name="$(target_index)"
  create_index_if_missing "${index_name}"
  move_alias "${WRITE_ALIAS}" "${index_name}" true
  echo "Write alias now points to ${index_name}: ${WRITE_ALIAS}"
  create_read_alias_if_missing "${index_name}"
  echo "Prepared Elasticsearch write index safely at ${ELASTICSEARCH_BASE_URL}"
}

run_promote() {
  require_version
  local index_name
  index_name="$(target_index)"
  if ! index_exists "${index_name}"; then
    echo "Target index does not exist and cannot be promoted: ${index_name}" >&2
    return 1
  fi
  move_alias "${READ_ALIAS}" "${index_name}" false
  echo "Promoted read alias to ${index_name}: ${READ_ALIAS}"
}

run_rollback() {
  require_version
  local index_name
  index_name="$(target_index)"
  if ! index_exists "${index_name}"; then
    echo "Rollback target index does not exist: ${index_name}" >&2
    echo "Fast fallback: set APP_RAG_LEXICAL_PROVIDER=postgres and restart backend." >&2
    return 1
  fi
  move_alias "${READ_ALIAS}" "${index_name}" false
  echo "Rolled read alias back to ${index_name}: ${READ_ALIAS}"
  echo "PostgreSQL was not modified. If aliases remain unsafe, set APP_RAG_LEXICAL_PROVIDER=postgres."
}

with_rollout_evidence() {
  local command_name="$1"
  local runner_name="$2"
  local before_read before_write after_read after_write status result error_message
  before_read="$(alias_targets "${READ_ALIAS}")"
  before_write="$(alias_targets "${WRITE_ALIAS}")"
  set +e
  "${runner_name}"
  status=$?
  set -e
  after_read="$(alias_targets "${READ_ALIAS}")"
  after_write="$(alias_targets "${WRITE_ALIAS}")"
  if [[ ${status} -eq 0 ]]; then
    result="success"
    error_message=""
  else
    result="failed"
    error_message="${command_name} exited with status ${status}"
  fi
  write_evidence "${command_name}" "${VERSION}" "${before_read}" "${before_write}" "${after_read}" "${after_write}" "${result}" "${error_message}"
  return ${status}
}

resolve_java_21_home() {
  if [[ -n "${JAVA_21_HOME:-}" && -x "${JAVA_21_HOME}/bin/java" ]]; then
    echo "${JAVA_21_HOME}"
    return 0
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    local current_version
    current_version="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)"
    if [[ "${current_version}" == *'"21.'* || "${current_version}" == *'"21"'* ]]; then
      echo "${JAVA_HOME}"
      return 0
    fi
  fi

  if [[ -x "${DEFAULT_JAVA_21_HOME}/bin/java" ]]; then
    echo "${DEFAULT_JAVA_21_HOME}"
    return 0
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local detected_home
    detected_home="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [[ -n "${detected_home}" && -x "${detected_home}/bin/java" ]]; then
      echo "${detected_home}"
      return 0
    fi
  fi

  return 1
}

run_backend_operator() {
  local operator_command="$1"
  local java21_home
  java21_home="$(resolve_java_21_home || true)"
  if [[ -z "${java21_home}" ]]; then
    echo "Java 21 not found. Install OpenJDK 21 or export JAVA_21_HOME before running search rollout." >&2
    exit 1
  fi

  export JAVA_HOME="${java21_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  export APP_SEARCH_SYNC_ENABLED=true
  export APP_SEARCH_SYNC_OPERATOR_ENABLED=true
  export SPRING_MAIN_WEB_APPLICATION_TYPE=none

  echo "Using JAVA_HOME=${JAVA_HOME}"
  cd "${BACKEND_DIR}"
  mvn -q -DskipTests spring-boot:run \
    "-Dspring-boot.run.arguments=--app.search-sync.operator.command=${operator_command}"
}

run_status() {
  echo "Elasticsearch URL: ${ELASTICSEARCH_BASE_URL}"
  echo "Index prefix: ${INDEX_PREFIX}"
  echo "Mapping hash: $(mapping_hash)"
  echo "Read alias ${READ_ALIAS}: $(alias_targets "${READ_ALIAS}")"
  echo "Write alias ${WRITE_ALIAS}: $(alias_targets "${WRITE_ALIAS}")"
  echo "Cluster health:"
  curl "${CURL_FAIL_ARGS[@]}" "${ELASTICSEARCH_BASE_URL}/_cluster/health?pretty"
}

json_value() {
  local payload="$1"
  local path="$2"
  node -e 'const payload = process.argv[1]; const path = process.argv[2].split("."); let value = JSON.parse(payload); for (const part of path) { if (part === "length" && Array.isArray(value)) { value = value.length; continue; } value = value == null ? undefined : value[part]; } if (value == null) process.exit(0); if (typeof value === "object") process.stdout.write(JSON.stringify(value)); else process.stdout.write(String(value));' "${payload}" "${path}"
}

require_node_for_smoke() {
  if ! command -v node >/dev/null 2>&1; then
    echo "Node.js is required for search rollout smoke JSON assertions." >&2
    return 1
  fi
}

run_smoke() {
  local query="${VERSION:-${SEARCH_ROLLOUT_SMOKE_QUERY:-}}"
  local cluster_json health_json search_json cluster_status failed_count hit_count first_material_id expected_mapping_hash actual_mapping_hash
  require_node_for_smoke
  echo "Elasticsearch URL: ${ELASTICSEARCH_BASE_URL}"
  echo "Index prefix: ${INDEX_PREFIX}"
  ensure_backend_auth || {
    echo "Backend authentication failed. Set APP_SECURITY_ADMIN_PASSWORD or run against a security-disabled local backend." >&2
    return 1
  }
  actual_mapping_hash="$(mapping_hash)"
  expected_mapping_hash="${SEARCH_ROLLOUT_EXPECTED_MAPPING_HASH:-${actual_mapping_hash}}"
  echo "Mapping hash: ${actual_mapping_hash}"
  if [[ "${actual_mapping_hash}" != "${expected_mapping_hash}" ]]; then
    echo "Mapping hash mismatch: expected ${expected_mapping_hash}, got ${actual_mapping_hash}" >&2
    return 1
  fi

  if [[ "$(alias_targets "${READ_ALIAS}")" == "(none)" ]]; then
    echo "Read alias is missing: ${READ_ALIAS}" >&2
    return 1
  fi
  if [[ "$(alias_targets "${WRITE_ALIAS}")" == "(none)" ]]; then
    echo "Write alias is missing: ${WRITE_ALIAS}" >&2
    return 1
  fi

  cluster_json="$(curl "${CURL_FAIL_ARGS[@]}" "${ELASTICSEARCH_BASE_URL}/_cluster/health")" || {
    echo "Elasticsearch cluster health request failed." >&2
    return 1
  }
  cluster_status="$(json_value "${cluster_json}" "status")"
  if [[ -z "${cluster_status}" || "${cluster_status}" == "red" ]]; then
    echo "Elasticsearch cluster is unhealthy: ${cluster_status:-unknown}" >&2
    return 1
  fi
  echo "Cluster health: ${cluster_status}"

  echo "Backend health:"
  health_json="$(backend_curl GET /api/health)" || {
    echo "Backend health request failed. Check APP_BACKEND_URL or auth/network access." >&2
    return 1
  }
  failed_count="$(json_value "${health_json}" "searchSyncBacklog.failedCount")"
  failed_count="${failed_count:-0}"
  if (( failed_count > 0 )); then
    echo "Search sync failed backlog is non-zero: ${failed_count}" >&2
    return 1
  fi
  echo "${health_json}"
  echo

  if [[ -z "${query}" ]]; then
    echo "Sample search query is required. Pass a query argument or set SEARCH_ROLLOUT_SMOKE_QUERY." >&2
    return 1
  fi

  echo "Sample search for query: ${query}"
  search_json="$(backend_curl POST /api/search "{\"query\":\"$(rollout_json_escape "${query}")\",\"limit\":5}")" || {
    echo "Sample search request failed." >&2
    return 1
  }
  hit_count="$(json_value "${search_json}" "hits.length")"
  hit_count="${hit_count:-0}"
  if (( hit_count < 1 )); then
    echo "Sample search returned no hits." >&2
    return 1
  fi
  first_material_id="$(json_value "${search_json}" "hits.0.materialId")"
  if [[ -z "${first_material_id}" ]]; then
    echo "Sample search hit is missing source material id." >&2
    return 1
  fi
  echo "${search_json}"
  echo
}

case "${COMMAND}" in
  prepare)
    with_rollout_evidence prepare run_prepare
    ;;
  rebuild)
    run_backend_operator rebuild-write-index
    ;;
  requeue)
    run_backend_operator requeue-failed
    ;;
  smoke)
    with_rollout_evidence smoke run_smoke
    ;;
  promote)
    with_rollout_evidence promote run_promote
    ;;
  rollback)
    with_rollout_evidence rollback run_rollback
    ;;
  status)
    with_rollout_evidence status run_status
    ;;
  *)
    echo "Unknown search rollout command: ${COMMAND}" >&2
    usage
    exit 1
    ;;
esac
