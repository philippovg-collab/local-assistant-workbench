#!/bin/zsh
set -euo pipefail

BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/liveness"

pkill -f 'spring-boot:run' || true
pkill -f 'org.springframework.boot.loader.launch.JarLauncher' || true

for _ in {1..20}; do
  if ! curl -fsS "$BACKEND_HEALTH_URL" >/dev/null 2>&1; then
    echo "Backend stopped"
    exit 0
  fi
  sleep 1
done

echo "Backend stop requested, but $BACKEND_HEALTH_URL is still responding" >&2
exit 1
