#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

VERSION="${1:-}"
if [[ -z "${VERSION}" ]]; then
  echo "Usage: scripts/linux/package-release.sh <version>" >&2
  exit 1
fi

case "${VERSION}" in
  *[!A-Za-z0-9._-]*)
    echo "Version may only contain letters, numbers, dots, underscores, and dashes." >&2
    exit 1
    ;;
esac

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "package-release.sh must run from a git checkout." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=all)" ]]; then
  echo "Refusing to package a dirty git tree. Commit or remove all changes first." >&2
  git status --short >&2
  exit 1
fi

OUTPUT_DIR="${RELEASE_DIR:-artifacts/releases}"
ARCHIVE_NAME="ragstudio-${VERSION}.tar.gz"
ARCHIVE_PATH="${OUTPUT_DIR}/${ARCHIVE_NAME}"
PREFIX="ragstudio-${VERSION}/"

mkdir -p "${OUTPUT_DIR}"
git archive --format=tar.gz --prefix="${PREFIX}" -o "${ARCHIVE_PATH}" HEAD

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && sha256sum "${ARCHIVE_NAME}" > "${ARCHIVE_NAME}.sha256")
elif command -v shasum >/dev/null 2>&1; then
  (cd "${OUTPUT_DIR}" && shasum -a 256 "${ARCHIVE_NAME}" > "${ARCHIVE_NAME}.sha256")
fi

echo "Release archive: ${ARCHIVE_PATH}"
if [[ -f "${ARCHIVE_PATH}.sha256" ]]; then
  echo "Checksum: ${ARCHIVE_PATH}.sha256"
fi
