#!/bin/zsh
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: ./scripts/search-prepare-index.sh <version>" >&2
  exit 1
fi

VERSION="$1"
SCRIPT_DIR="${0:A:h}"
INDEX_DEFINITION_FILE="${SCRIPT_DIR}/../backend/src/main/resources/elasticsearch/searchable-chunks-index.json"
INDEX_PREFIX="${APP_SEARCH_SYNC_INDEX_PREFIX:-rag-chunks}"
TARGET_INDEX="${INDEX_PREFIX}-${VERSION}"
WRITE_ALIAS="${INDEX_PREFIX}-write"
READ_ALIAS="${INDEX_PREFIX}-read"

if [[ ! -f "$INDEX_DEFINITION_FILE" ]]; then
  echo "Index definition file not found: ${INDEX_DEFINITION_FILE}" >&2
  exit 1
fi

resolve_elasticsearch_url() {
  local raw_url base_url
  raw_url="${SPRING_ELASTICSEARCH_URIS:-${ELASTICSEARCH_URL:-http://127.0.0.1:9200}}"
  base_url="${raw_url%%,*}"
  base_url="${base_url//[[:space:]]/}"
  if [[ -z "$base_url" ]]; then
    echo "Elasticsearch URL is empty. Set SPRING_ELASTICSEARCH_URIS or ELASTICSEARCH_URL." >&2
    exit 1
  fi
  echo "${base_url%/}"
}

ELASTICSEARCH_BASE_URL="$(resolve_elasticsearch_url)"

typeset -a CURL_ARGS
CURL_ARGS=(-fsS)
if [[ -n "${SPRING_ELASTICSEARCH_USERNAME:-}" || -n "${SPRING_ELASTICSEARCH_PASSWORD:-}" ]]; then
  CURL_ARGS+=(-u "${SPRING_ELASTICSEARCH_USERNAME:-}:${SPRING_ELASTICSEARCH_PASSWORD:-}")
fi

http_status() {
  curl "${CURL_ARGS[@]}" -o /dev/null -w '%{http_code}' "$@"
}

index_exists() {
  [[ "$(http_status -I "${ELASTICSEARCH_BASE_URL}/${TARGET_INDEX}")" == "200" ]]
}

alias_exists() {
  local alias_name="$1"
  [[ "$(http_status -I "${ELASTICSEARCH_BASE_URL}/_alias/${alias_name}")" == "200" ]]
}

create_index_if_missing() {
  if index_exists; then
    echo "Index already exists: ${TARGET_INDEX}"
    return
  fi

  curl "${CURL_ARGS[@]}" \
    -X PUT "${ELASTICSEARCH_BASE_URL}/${TARGET_INDEX}" \
    -H 'Content-Type: application/json' \
    --data-binary "@${INDEX_DEFINITION_FILE}" >/dev/null

  echo "Created index: ${TARGET_INDEX}"
}

move_write_alias() {
  local payload remove_action
  remove_action=""
  if alias_exists "${WRITE_ALIAS}"; then
    remove_action="{\"remove\":{\"index\":\"*\",\"alias\":\"${WRITE_ALIAS}\"}},"
  fi

  payload=$(cat <<JSON
{"actions":[${remove_action}{"add":{"index":"${TARGET_INDEX}","alias":"${WRITE_ALIAS}","is_write_index":true}}]}
JSON
)

  curl "${CURL_ARGS[@]}" \
    -X POST "${ELASTICSEARCH_BASE_URL}/_aliases" \
    -H 'Content-Type: application/json' \
    -d "${payload}" >/dev/null

  echo "Write alias now points to ${TARGET_INDEX}: ${WRITE_ALIAS}"
}

create_read_alias_if_missing() {
  local payload
  if alias_exists "${READ_ALIAS}"; then
    echo "Read alias already exists and was left unchanged: ${READ_ALIAS}"
    return
  fi

  payload=$(cat <<JSON
{"actions":[{"add":{"index":"${TARGET_INDEX}","alias":"${READ_ALIAS}"}}]}
JSON
)

  curl "${CURL_ARGS[@]}" \
    -X POST "${ELASTICSEARCH_BASE_URL}/_aliases" \
    -H 'Content-Type: application/json' \
    -d "${payload}" >/dev/null

  echo "Created initial read alias on ${TARGET_INDEX}: ${READ_ALIAS}"
}

create_index_if_missing
move_write_alias
create_read_alias_if_missing

echo "Prepared Elasticsearch write index safely at ${ELASTICSEARCH_BASE_URL}"
