#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

BACKUP_DIR="${1:-}"
if [[ -z "${BACKUP_DIR}" ]]; then
  echo "Usage: CONFIRM_RESTORE=restore scripts/linux/restore-compose.sh <backup-dir>" >&2
  exit 1
fi

if [[ "${CONFIRM_RESTORE:-}" != "restore" ]]; then
  echo "Refusing to restore without CONFIRM_RESTORE=restore." >&2
  exit 1
fi

if [[ ! -f "${BACKUP_DIR}/postgres.sql" || ! -f "${BACKUP_DIR}/backend-storage.tgz" ]]; then
  echo "Backup directory must contain postgres.sql and backend-storage.tgz: ${BACKUP_DIR}" >&2
  exit 1
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

compose() {
  if [[ -n "${ENV_FILE}" && -f "${ENV_FILE}" ]]; then
    docker compose --env-file "${ENV_FILE}" "$@"
    return
  fi
  docker compose "$@"
}

echo "Stopping application services..."
compose stop frontend backend || true

echo "Ensuring PostgreSQL is running..."
compose up -d postgres

echo "Resetting database schema ${POSTGRES_DB}..."
compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
  -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"

echo "Restoring PostgreSQL dump..."
compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < "${BACKUP_DIR}/postgres.sql"

echo "Restoring backend storage volume..."
compose run --rm --no-deps -T --entrypoint sh backend -c \
  'find /app/storage -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xzf - -C /app/storage' \
  < "${BACKUP_DIR}/backend-storage.tgz"

echo "Starting application services..."
compose up -d postgres backend frontend

echo "Running post-restore preflight..."
"${ROOT_DIR}/scripts/linux/preflight-compose.sh"
