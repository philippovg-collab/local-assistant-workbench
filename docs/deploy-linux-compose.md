# Linux Docker Compose Deployment

This guide deploys Ragstudio as a single-server Docker Compose stack on Ubuntu
24.04. The production baseline is intentionally conservative:

- HTTPS is terminated by the customer's reverse proxy.
- Compose binds the frontend nginx only to `127.0.0.1:8088` by default.
- Backend, PostgreSQL, and Elasticsearch are not published to the host.
- PostgreSQL/pgvector remains the production retrieval path.
- Elasticsearch remains disabled unless a separate controlled rollout enables it.
- Chat and embeddings are expected from an external vLLM or other OpenAI-compatible gateway.

## Architecture

- `frontend`: nginx serving the built React app and proxying `/api` to `backend:8080`.
- `backend`: Spring Boot Java 21 app with PDFBox and Tesseract OCR packages.
- `postgres`: PostgreSQL 16 with pgvector.
- `elasticsearch`: optional `search` profile, disabled by default.

The browser should talk to the public HTTPS domain. The reverse proxy forwards to
`http://127.0.0.1:8088`, and frontend nginx handles the internal `/api` hop.

## Server Bootstrap

Run on a fresh Ubuntu 24.04 host:

```bash
scripts/linux/bootstrap-ubuntu-24.04.sh
```

By default the script creates:

```text
/opt/ragstudio/releases
/opt/ragstudio/shared
/opt/ragstudio/backups
```

To use another base directory:

```bash
DEPLOY_DIR=/srv/ragstudio scripts/linux/bootstrap-ubuntu-24.04.sh
```

If the script adds your user to the `docker` group, log out and back in before
running Docker without `sudo`.

## Build A Release Archive

Create release archives from a clean git checkout only:

```bash
scripts/linux/package-release.sh 1.0.0
```

The command writes:

```text
artifacts/releases/ragstudio-1.0.0.tar.gz
artifacts/releases/ragstudio-1.0.0.tar.gz.sha256
```

It refuses to package uncommitted or untracked files so the server artifact is
reproducible.

## First Deploy

Copy the archive to the server and unpack it under `releases`:

```bash
cd /opt/ragstudio
mkdir -p releases/1.0.0
tar -xzf ragstudio-1.0.0.tar.gz -C releases/1.0.0 --strip-components=1
ln -sfn /opt/ragstudio/releases/1.0.0 /opt/ragstudio/current
```

Create the shared environment file once:

```bash
cp /opt/ragstudio/current/.env.example /opt/ragstudio/shared/.env
ln -sfn /opt/ragstudio/shared/.env /opt/ragstudio/current/.env
```

Edit `/opt/ragstudio/shared/.env` before starting services:

```dotenv
FRONTEND_BIND_ADDRESS=127.0.0.1
FRONTEND_HTTP_PORT=8088
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example

POSTGRES_PASSWORD=replace-with-a-strong-password
BACKEND_IMAGE=ghcr.io/kazinsys-ai/rag-backend:latest
FRONTEND_IMAGE=ghcr.io/kazinsys-ai/rag-frontend:latest
APP_SECURITY_ADMIN_USERNAME=admin
APP_SECURITY_ADMIN_PASSWORD=replace-with-a-strong-admin-password

APP_LLM_BASE_URL=http://10.9.120.3:8000
APP_LLM_API_KEY=replace-with-ai-gateway-api-key
APP_LLM_MODEL=qwen
APP_LLM_TOP_P=0.9
APP_EMBEDDINGS_BASE_URL=http://10.9.120.3:8000
APP_EMBEDDINGS_API_KEY=replace-with-ai-gateway-api-key
APP_EMBEDDINGS_MODEL=nomic-embed-text

APP_SEARCH_SYNC_ENABLED=false
APP_RAG_LEXICAL_PROVIDER=postgres
```

Pull and start the baseline stack:

```bash
cd /opt/ragstudio/current
docker login ghcr.io
docker compose pull
docker compose up -d postgres backend frontend
```

Run the preflight checks after services start:

```bash
scripts/linux/preflight-compose.sh
```

## Reverse Proxy

Terminate HTTPS in the customer's reverse proxy and forward to the loopback
frontend port:

```nginx
location / {
    proxy_pass http://127.0.0.1:8088;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 650s;
}
```

Keep `APP_CORS_ALLOWED_ORIGINS` aligned with the public HTTPS origin. Do not
publish backend, PostgreSQL, or Elasticsearch ports to the internet.

## Health Checks

Public liveness through frontend nginx:

```bash
curl -fsS http://127.0.0.1:8088/healthz
curl -fsS http://127.0.0.1:8088/api/liveness
```

Authenticated backend health:

```bash
COOKIE_JAR=/tmp/ragstudio-cookies.txt
SESSION_JSON="$(curl -fsS -c "$COOKIE_JAR" http://127.0.0.1:8088/api/auth/session)"
CSRF_HEADER="$(printf '%s' "$SESSION_JSON" | sed -nE 's/.*"csrfHeaderName"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/p')"
CSRF_TOKEN="$(printf '%s' "$SESSION_JSON" | sed -nE 's/.*"csrfToken"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/p')"
curl -fsS -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -H "$CSRF_HEADER: $CSRF_TOKEN" \
  --data-binary @- \
  http://127.0.0.1:8088/api/auth/login <<JSON
{"username":"${APP_SECURITY_ADMIN_USERNAME:-admin}","password":"$APP_SECURITY_ADMIN_PASSWORD"}
JSON
curl -fsS -b "$COOKIE_JAR" http://127.0.0.1:8088/api/health
```

Expected infrastructure fields:

- `databaseStatus: "UP"`
- `vectorStatus: "UP"`
- `llmStatus: "UP"`
- `embeddingStatus: "UP"`
- `ocrStatus: "UP"`

On a fresh empty database, top-level `status` may be `DEGRADED` with
`knowledgeStatus: "EMPTY"`. That is expected until at least one active material
has been indexed.

## OCR Smoke Test

1. Upload a normal embedded-text PDF from the UI.
2. Confirm parsing succeeds and chunks show PDFBox metadata such as
   `extractor=pdfbox` and `ocrUsed=false`.
3. Upload a scanned image-only PDF within `APP_OCR_MAX_PAGES`, default `12`.
4. Confirm the UI no longer shows the scanned-PDF unsupported warning.
5. Confirm source metadata shows OCR usage, such as `extractor=tesseract` or
   `ocrUsed=true`.
6. Ask a RAG question whose answer exists only in the scanned image text.
7. Confirm the answer includes the expected value and source metadata marks OCR usage.

Runtime OCR checks:

```bash
docker compose exec backend tesseract --version
docker compose exec backend tesseract --list-langs
```

The language list must include `eng`, `kaz`, and `rus`.

## Backup

Back up PostgreSQL and backend file storage before every upgrade:

```bash
cd /opt/ragstudio/current
scripts/linux/backup-compose.sh
```

The backup directory is created under `/opt/ragstudio/backups/<timestamp>` and
contains:

- `postgres.sql`
- `backend-storage.tgz`
- `manifest.txt`

External gateway model state is intentionally not part of the required backup
because it lives outside this stack.

## Upgrade

1. Create a backup:

```bash
cd /opt/ragstudio/current
scripts/linux/backup-compose.sh
```

2. Unpack the new release and keep shared `.env` outside the release:

```bash
cd /opt/ragstudio
mkdir -p releases/1.0.1
tar -xzf ragstudio-1.0.1.tar.gz -C releases/1.0.1 --strip-components=1
ln -sfn /opt/ragstudio/shared/.env /opt/ragstudio/releases/1.0.1/.env
ln -sfn /opt/ragstudio/releases/1.0.1 /opt/ragstudio/current
```

3. Pull, restart, and preflight:

```bash
cd /opt/ragstudio/current
docker login ghcr.io
docker compose pull
docker compose up -d postgres backend frontend
scripts/linux/preflight-compose.sh
```

Flyway remains enabled. Do not set `SPRING_FLYWAY_ENABLED=false` for production
upgrades.

## Restore

Restore requires an explicit confirmation environment variable:

```bash
cd /opt/ragstudio/current
CONFIRM_RESTORE=restore scripts/linux/restore-compose.sh /opt/ragstudio/backups/<timestamp>
```

The restore flow stops `frontend` and `backend`, restores the SQL dump, restores
`backend-storage`, starts the baseline services again, and runs preflight.

Use restore only when you intentionally want to roll database and uploaded file
state back to a prior backup.

## Optional Elasticsearch Profile

The baseline stack does not run Elasticsearch. To enable it intentionally after
separate validation:

```bash
docker compose --profile search up -d elasticsearch
```

Update `/opt/ragstudio/shared/.env`:

```dotenv
APP_SEARCH_SYNC_ENABLED=true
APP_RAG_LEXICAL_PROVIDER=auto
SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200
```

Restart backend and run preflight:

```bash
docker compose up -d backend
scripts/linux/preflight-compose.sh
```

## Troubleshooting

Show service status:

```bash
docker compose ps
```

Follow backend logs:

```bash
docker compose logs -f backend
```

Frontend port already in use:

```dotenv
FRONTEND_HTTP_PORT=8088
```

Then restart frontend:

```bash
docker compose up -d frontend
```

External LLM gateway is unavailable:

```bash
curl -fsS -H 'Authorization: Bearer <AI_GATEWAY_API_KEY>' http://10.9.120.3:8000/v1/models
curl -fsS -X POST http://10.9.120.3:8000/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <AI_GATEWAY_API_KEY>' \
  -d '{"model":"qwen","messages":[{"role":"user","content":"ping"}],"temperature":0.2,"top_p":0.9,"stream":false}'
docker compose logs -f backend
```

OCR status is down:

```bash
docker compose exec backend tesseract --version
docker compose exec backend tesseract --list-langs
scripts/linux/preflight-compose.sh
```

If `kaz`, `rus`, or `eng` is missing, rebuild the backend image:

```bash
docker compose build backend
docker compose up -d backend
```

Flyway migration errors usually mean the database already has a different
migration history. Back up the database before any repair or manual migration
action.
