#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8080}"
USERNAME="${APP_SECURITY_ADMIN_USERNAME:-admin}"
PASSWORD="${APP_SECURITY_ADMIN_PASSWORD:-}"
MATERIAL_COUNT="${SMOKE_MATERIAL_COUNT:-3}"
CHAT_RUN_COUNT="${SMOKE_CHAT_RUN_COUNT:-3}"
INDEXING_WAIT_SECONDS="${SMOKE_INDEXING_WAIT_SECONDS:-120}"
CHAT_WAIT_SECONDS="${SMOKE_CHAT_WAIT_SECONDS:-120}"
RETRIEVAL_MAX_MS="${SMOKE_RETRIEVAL_MAX_MS:-5000}"
RUN_ID="phase7-smoke-$(date +%Y%m%d%H%M%S)"
COOKIE_JAR="$(mktemp)"
CSRF_HEADER=""
CSRF_TOKEN=""
CREATED_MATERIAL_IDS=()

cleanup() {
  rm -f "$COOKIE_JAR"
}
trap cleanup EXIT

usage() {
  cat <<'EOF'
Usage: scripts/scalability-smoke.sh upload|indexing|chat-queue|retrieval|all

Environment:
  APP_BASE_URL                         default http://127.0.0.1:8080
  APP_SECURITY_ADMIN_USERNAME          default admin
  APP_SECURITY_ADMIN_PASSWORD          required
  SMOKE_MATERIAL_COUNT                 default 3
  SMOKE_CHAT_RUN_COUNT                 default 3
  SMOKE_INDEXING_WAIT_SECONDS          default 120
  SMOKE_CHAT_WAIT_SECONDS              default 120
  SMOKE_RETRIEVAL_MAX_MS               default 5000

Stack requirements:
  retrieval/all require the backend search API rollout to be enabled
  (APP_ROLLOUT_SEARCH_API_V1=true on the target stack).
EOF
}

json_get() {
  local expression="$1"
  python3 -c '
import json
import sys

data = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    if isinstance(data, list):
        data = data[int(part)]
    elif isinstance(data, dict):
        data = data.get(part)
    else:
        data = None
    if data is None:
        sys.exit(1)
print(data)
' "$expression"
}

json_len() {
  local expression="$1"
  python3 -c '
import json
import sys

data = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    if not part:
        continue
    if isinstance(data, list):
        data = data[int(part)]
    elif isinstance(data, dict):
        data = data.get(part)
    else:
        data = None
    if data is None:
        print(0)
        sys.exit(0)
print(len(data) if hasattr(data, "__len__") else 0)
' "$expression"
}

json_payload() {
  python3 -c 'import json, sys; print(json.dumps({"title": sys.argv[1], "content": sys.argv[2]}))' "$1" "$2"
}

chat_payload() {
  python3 -c 'import json, sys; print(json.dumps({"mode": sys.argv[1], "prompt": sys.argv[2]}))' "$1" "$2"
}

curl_json() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local args=(-fsS -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X "$method" "$BASE_URL$path")
  if [[ "$method" != "GET" && -n "$CSRF_TOKEN" ]]; then
    args+=(-H "$CSRF_HEADER: $CSRF_TOKEN")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" --data "$body")
  fi
  curl "${args[@]}"
}

require_password() {
  if [[ -z "$PASSWORD" ]]; then
    echo "APP_SECURITY_ADMIN_PASSWORD is required for authenticated smoke checks." >&2
    exit 1
  fi
}

login() {
  require_password
  echo "Logging in to $BASE_URL as $USERNAME"
  local session
  session="$(curl_json GET /api/auth/session)"
  CSRF_HEADER="$(printf '%s' "$session" | json_get csrfHeaderName)"
  CSRF_TOKEN="$(printf '%s' "$session" | json_get csrfToken)"

  local login_body
  login_body="$(python3 -c 'import json, sys; print(json.dumps({"username": sys.argv[1], "password": sys.argv[2]}))' "$USERNAME" "$PASSWORD")"
  session="$(curl_json POST /api/auth/login "$login_body")"
  if [[ "$(printf '%s' "$session" | json_get authenticated)" != "True" ]]; then
    echo "Login failed." >&2
    exit 1
  fi
}

create_material() {
  local index="$1"
  local title="$RUN_ID material $index"
  local phrase="$RUN_ID retrieval anchor $index"
  local content="Quality smoke material $index. Unique retrieval phrase: $phrase. This document is intentionally tiny."
  local response
  response="$(curl_json POST /api/materials "$(json_payload "$title" "$content")")"
  local material_id
  material_id="$(printf '%s' "$response" | json_get id)"
  local saved_id
  saved_id="$(curl_json GET "/api/materials/$material_id" | json_get id)"
  if [[ "$saved_id" != "$material_id" ]]; then
    echo "Upload smoke failed: created material $material_id was not readable after save." >&2
    exit 1
  fi
  CREATED_MATERIAL_IDS+=("$material_id")
  echo "Created and verified material $material_id"
  printf '%s' "$material_id"
}

status_for_material() {
  local material_id="$1"
  curl_json GET "/api/materials/$material_id" | json_get status
}

run_upload() {
  echo "== Upload smoke =="
  local created=0
  for index in $(seq 1 "$MATERIAL_COUNT"); do
    create_material "$index" >/dev/null
    created=$((created + 1))
  done
  if [[ "$created" -lt "$MATERIAL_COUNT" ]]; then
    echo "Upload smoke failed: expected $MATERIAL_COUNT material(s), created $created." >&2
    exit 1
  fi
  echo "Upload smoke passed ($created material(s))."
}

run_indexing() {
  echo "== Indexing smoke =="
  if [[ "${#CREATED_MATERIAL_IDS[@]}" -eq 0 ]]; then
    for index in $(seq 1 "$MATERIAL_COUNT"); do
      create_material "$index" >/dev/null
    done
  fi

  local deadline=$((SECONDS + INDEXING_WAIT_SECONDS))
  local pending
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    pending=0
    for material_id in "${CREATED_MATERIAL_IDS[@]}"; do
      local status
      status="$(status_for_material "$material_id")"
      case "$status" in
        READY|PARTIAL_READY)
          ;;
        FAILED)
          echo "Indexing smoke failed: material $material_id reached FAILED." >&2
          exit 1
          ;;
        *)
          pending=$((pending + 1))
          ;;
      esac
    done
    if [[ "$pending" -eq 0 ]]; then
      echo "Indexing smoke passed (${#CREATED_MATERIAL_IDS[@]} material(s) drained)."
      return
    fi
    sleep 3
  done

  echo "Indexing smoke failed: backlog did not drain within ${INDEXING_WAIT_SECONDS}s." >&2
  exit 1
}

run_chat_queue() {
  echo "== Chat queue smoke =="
  local run_ids=()
  for index in $(seq 1 "$CHAT_RUN_COUNT"); do
    local response
    response="$(curl_json POST /api/chat-runs "$(chat_payload direct "Reply with ok for smoke run $index.")")"
    local run_id
    run_id="$(printf '%s' "$response" | json_get id)"
    local initial_status
    initial_status="$(printf '%s' "$response" | json_get status)"
    local status_url
    status_url="$(printf '%s' "$response" | json_get statusUrl)"
    local trace_url
    trace_url="$(printf '%s' "$response" | json_get traceUrl)"
    local result_url
    result_url="$(printf '%s' "$response" | json_get resultUrl)"
    if [[ "$initial_status" != "RECEIVED" ]]; then
      echo "Chat queue smoke failed: submission $run_id returned initial status $initial_status instead of RECEIVED." >&2
      exit 1
    fi
    if [[ -z "$status_url" || -z "$trace_url" || -z "$result_url" ]]; then
      echo "Chat queue smoke failed: submission $run_id did not return statusUrl, traceUrl, and resultUrl." >&2
      exit 1
    fi
    run_ids+=("$run_id")
    echo "Submitted chat run $run_id"
  done

  local deadline=$((SECONDS + CHAT_WAIT_SECONDS))
  local pending
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    pending=0
    for run_id in "${run_ids[@]}"; do
      local status
      status="$(curl_json GET "/api/chat-runs/$run_id/status" | json_get status)"
      case "$status" in
        COMPLETED)
          ;;
        FAILED|CANCELLED)
          echo "Chat queue smoke failed: run $run_id reached $status." >&2
          exit 1
          ;;
        *)
          pending=$((pending + 1))
          ;;
      esac
    done
    if [[ "$pending" -eq 0 ]]; then
      echo "Chat queue smoke passed (${#run_ids[@]} run(s))."
      return
    fi
    sleep 3
  done

  echo "Chat queue smoke failed: runs did not complete within ${CHAT_WAIT_SECONDS}s." >&2
  exit 1
}

run_retrieval() {
  echo "== Retrieval smoke =="
  if [[ "${#CREATED_MATERIAL_IDS[@]}" -eq 0 ]]; then
    create_material 1 >/dev/null
    run_indexing
  fi

  local started_ms
  started_ms="$(python3 -c 'import time; print(int(time.time() * 1000))')"
  local body
  body="$(python3 -c 'import json, sys; print(json.dumps({"query": sys.argv[1], "limit": 5, "debug": True}))' "$RUN_ID retrieval anchor")"
  local response
  response="$(curl_json POST /api/search "$body")"
  local ended_ms
  ended_ms="$(python3 -c 'import time; print(int(time.time() * 1000))')"
  local elapsed=$((ended_ms - started_ms))
  local hit_count
  hit_count="$(printf '%s' "$response" | json_len hits)"
  if [[ "$hit_count" -le 0 ]]; then
    echo "Retrieval smoke failed: search returned no sources/hits." >&2
    exit 1
  fi
  if [[ "$elapsed" -gt "$RETRIEVAL_MAX_MS" ]]; then
    echo "Retrieval smoke failed: ${elapsed}ms exceeded threshold ${RETRIEVAL_MAX_MS}ms." >&2
    exit 1
  fi
  echo "Retrieval smoke passed ($hit_count hit(s), ${elapsed}ms)."
}

case "$MODE" in
  upload)
    login
    run_upload
    ;;
  indexing)
    login
    run_indexing
    ;;
  chat-queue)
    login
    run_chat_queue
    ;;
  retrieval)
    login
    run_retrieval
    ;;
  all)
    login
    run_upload
    run_indexing
    run_chat_queue
    run_retrieval
    ;;
  -h|--help)
    usage
    ;;
  *)
    echo "Unknown smoke mode: $MODE" >&2
    usage >&2
    exit 1
    ;;
esac
