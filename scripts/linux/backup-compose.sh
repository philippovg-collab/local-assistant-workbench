#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

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
BACKUP_ROOT="${BACKUP_ROOT:-${DEPLOY_BASE}/backups}"
ENV_FILE="${ENV_FILE:-}"
if [[ -z "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/.env" ]]; then
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

POSTGRES_DB="${POSTGRES_DB:-ragstudio}"
POSTGRES_USER="${POSTGRES_USER:-ragstudio}"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%SZ)"
BACKUP_DIR="${BACKUP_ROOT}/${TIMESTAMP}"

compose() {
  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    docker compose --env-file "${ENV_FILE}" "$@"
    return
  fi
  docker compose "$@"
}

mkdir -p "${BACKUP_DIR}"

echo "Writing PostgreSQL dump..."
compose exec -T postgres pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" > "${BACKUP_DIR}/postgres.sql"

echo "Archiving backend storage volume..."
compose run --rm --no-deps -T --entrypoint tar backend -czf - -C /app/storage . > "${BACKUP_DIR}/backend-storage.tgz"

{
  echo "created_at=${TIMESTAMP}"
  echo "root_dir=${ROOT_DIR}"
  echo "deploy_base=${DEPLOY_BASE}"
  echo "env_file=${ENV_FILE:-}"
  echo "postgres_db=${POSTGRES_DB}"
  echo "postgres_user=${POSTGRES_USER}"
  echo "git_commit=$(git rev-parse --short=12 HEAD 2>/dev/null || true)"
  echo
  echo "[docker_compose_ps]"
  compose ps || true
} > "${BACKUP_DIR}/manifest.txt"

echo "Backup complete: ${BACKUP_DIR}"
