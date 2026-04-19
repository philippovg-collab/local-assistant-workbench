#!/bin/zsh
set -euo pipefail

CONTAINER_NAME="${ELASTICSEARCH_CONTAINER_NAME:-ragstudio-elasticsearch}"
ELASTICSEARCH_IMAGE="${ELASTICSEARCH_IMAGE:-docker.elastic.co/elasticsearch/elasticsearch:8.13.4}"
ELASTICSEARCH_PORT="${ELASTICSEARCH_PORT:-9200}"
ELASTICSEARCH_URL="http://127.0.0.1:${ELASTICSEARCH_PORT}"

container_is_running() {
  local running
  running=$(docker ps --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}')
  [[ "$running" == "$CONTAINER_NAME" ]]
}

container_exists() {
  local existing
  existing=$(docker ps -a --filter "name=^/${CONTAINER_NAME}$" --format '{{.Names}}')
  [[ "$existing" == "$CONTAINER_NAME" ]]
}

wait_for_elasticsearch() {
  for _ in {1..30}; do
    if curl -fsS "$ELASTICSEARCH_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done

  return 1
}

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required to start the Elasticsearch sidecar." >&2
  exit 1
fi

if container_is_running; then
  echo "Elasticsearch is already running at ${ELASTICSEARCH_URL}"
elif container_exists; then
  docker start "$CONTAINER_NAME" >/dev/null
else
  docker run \
    --name "$CONTAINER_NAME" \
    -p "${ELASTICSEARCH_PORT}:9200" \
    -e "discovery.type=single-node" \
    -e "xpack.security.enabled=false" \
    -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
    -d \
    "$ELASTICSEARCH_IMAGE" >/dev/null
fi

if ! wait_for_elasticsearch; then
  echo "Elasticsearch did not become ready at ${ELASTICSEARCH_URL}." >&2
  exit 1
fi

echo "Elasticsearch sidecar is ready at ${ELASTICSEARCH_URL}"
echo "To enable shadow indexing for backend startup:"
echo "  export APP_SEARCH_SYNC_ENABLED=true"
echo "  export SPRING_ELASTICSEARCH_URIS=${ELASTICSEARCH_URL}"
