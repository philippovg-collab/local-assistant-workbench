#!/usr/bin/env bash
set -euo pipefail

DEPLOY_DIR="${DEPLOY_DIR:-/opt/ragstudio}"
DOCKER_APT_KEYRING="/etc/apt/keyrings/docker.asc"

if [[ ! -r /etc/os-release ]]; then
  echo "Cannot detect Linux distribution: /etc/os-release is missing." >&2
  exit 1
fi

# shellcheck disable=SC1091
. /etc/os-release

if [[ "${ID:-}" != "ubuntu" || "${VERSION_ID:-}" != "24.04" ]]; then
  echo "This bootstrap script is intended for Ubuntu 24.04. Detected: ${PRETTY_NAME:-unknown}." >&2
  echo "Set ALLOW_NON_UBUNTU=1 to continue anyway." >&2
  if [[ "${ALLOW_NON_UBUNTU:-0}" != "1" ]]; then
    exit 1
  fi
fi

if [[ "${EUID}" -eq 0 ]]; then
  SUDO=()
else
  if ! command -v sudo >/dev/null 2>&1; then
    echo "sudo is required when running as a non-root user." >&2
    exit 1
  fi
  SUDO=(sudo)
fi

echo "Installing Docker Engine and Compose plugin..."
"${SUDO[@]}" apt-get update
"${SUDO[@]}" apt-get install -y ca-certificates curl gnupg
"${SUDO[@]}" install -m 0755 -d /etc/apt/keyrings

if [[ ! -f "${DOCKER_APT_KEYRING}" ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | "${SUDO[@]}" tee "${DOCKER_APT_KEYRING}" >/dev/null
  "${SUDO[@]}" chmod a+r "${DOCKER_APT_KEYRING}"
fi

ARCH="$(dpkg --print-architecture)"
CODENAME="${VERSION_CODENAME:-noble}"
DOCKER_LIST="/etc/apt/sources.list.d/docker.list"

echo "deb [arch=${ARCH} signed-by=${DOCKER_APT_KEYRING}] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  | "${SUDO[@]}" tee "${DOCKER_LIST}" >/dev/null

"${SUDO[@]}" apt-get update
"${SUDO[@]}" apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "Creating deployment directory: ${DEPLOY_DIR}"
"${SUDO[@]}" install -d -m 0755 "${DEPLOY_DIR}"

if [[ "${EUID}" -ne 0 ]] && getent group docker >/dev/null 2>&1; then
  "${SUDO[@]}" usermod -aG docker "${USER}"
  echo "Added ${USER} to the docker group. Log out and back in before running Docker without sudo."
fi

docker --version || true
docker compose version || true

cat <<EOF

Bootstrap complete.

Next steps:
  1. Copy project files to ${DEPLOY_DIR}.
  2. Copy .env.example to .env and set production secrets.
  3. Run: docker compose build
  4. Run: docker compose up -d postgres ollama ollama-init backend frontend
  5. Run: scripts/linux/preflight-compose.sh
EOF
