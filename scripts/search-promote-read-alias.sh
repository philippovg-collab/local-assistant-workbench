#!/bin/zsh
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: ./scripts/search-promote-read-alias.sh <version>" >&2
  exit 1
fi

VERSION="$1"
INDEX_PREFIX="${APP_SEARCH_SYNC_INDEX_PREFIX:-rag-chunks}"
TARGET_INDEX="${INDEX_PREFIX}-${VERSION}"
READ_ALIAS="${INDEX_PREFIX}-read"

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
  [[ "$(http_status -I "${ELASTICSEARCH_BASE_URL}/_alias/${READ_ALIAS}")" == "200" ]]
}

if ! index_exists; then
  echo "Target index does not exist and cannot be promoted: ${TARGET_INDEX}" >&2
  exit 1
fi

remove_action=""
if alias_exists; then
  remove_action="{\"remove\":{\"index\":\"*\",\"alias\":\"${READ_ALIAS}\"}},"
fi

payload=$(cat <<JSON
{"actions":[${remove_action}{"add":{"index":"${TARGET_INDEX}","alias":"${READ_ALIAS}"}}]}
JSON
)

curl "${CURL_ARGS[@]}" \
  -X POST "${ELASTICSEARCH_BASE_URL}/_aliases" \
  -H 'Content-Type: application/json' \
  -d "${payload}" >/dev/null

echo "Promoted read alias to ${TARGET_INDEX}: ${READ_ALIAS}"
