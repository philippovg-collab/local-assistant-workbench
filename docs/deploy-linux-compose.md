# Linux Docker Compose Deployment

This guide deploys the application as a single-server production-like Docker Compose stack on Ubuntu 24.04. The server only needs Docker Engine, the Docker Compose plugin, and persistent volume storage. Tesseract OCR is installed inside the backend image.

## Architecture

- `frontend`: nginx serving the built React app on port `80` and proxying `/api` to `backend:8080`.
- `backend`: Spring Boot application with Java 21, PDFBox, and Tesseract OCR packages.
- `postgres`: PostgreSQL 16 with pgvector.
- `ollama`: local Ollama service with persistent model storage.
- `ollama-init`: one-shot model pull for the chat and embedding models.
- `elasticsearch`: optional `search` profile, disabled by default.

The default lexical retrieval provider remains PostgreSQL. Elasticsearch is only started when the `search` Compose profile is enabled.

## Server Bootstrap

Run the bootstrap script on a fresh Ubuntu 24.04 host:

```bash
scripts/linux/bootstrap-ubuntu-24.04.sh
```

By default it creates `/opt/ragstudio`. To use another directory:

```bash
DEPLOY_DIR=/srv/ragstudio scripts/linux/bootstrap-ubuntu-24.04.sh
```

If the script adds your user to the `docker` group, log out and back in before running Docker without `sudo`.

## First Deploy

Copy the project to the deployment directory, then create and edit the environment file:

```bash
cp .env.example .env
```

At minimum, set a strong `POSTGRES_PASSWORD` and update `APP_CORS_ALLOWED_ORIGINS` to match the public URL:

```dotenv
POSTGRES_PASSWORD=replace-with-a-strong-password
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example,http://your-server-ip
```

Build and start the default stack:

```bash
docker compose build
docker compose up -d postgres ollama ollama-init backend frontend
```

The first Ollama startup can take a while because `ollama-init` downloads:

- `qwen2.5:7b`
- `nomic-embed-text`

Run the preflight checks after the services start:

```bash
scripts/linux/preflight-compose.sh
```

Expected user entrypoint:

- UI: `http://SERVER/`
- API through nginx: `http://SERVER/api/...`

The browser should use same-origin `/api`; nginx handles the internal hop to the backend container.

## Health Checks

Backend health:

```bash
curl -fsS http://127.0.0.1/api/health
```

Expected fields:

- `databaseStatus: "UP"`
- `vectorStatus: "UP"`
- `llmStatus: "UP"`
- `embeddingStatus: "UP"`
- `ocrStatus: "UP"`

On a fresh empty database, the existing application health semantics can still report top-level `status: "DEGRADED"` with `knowledgeStatus: "EMPTY"`. That is expected until at least one active material has been indexed. After ready materials exist, top-level `status` and `ragStatus` should become `UP`.

Material policy:

```bash
curl -fsS http://127.0.0.1/api/materials/policy
```

Expected PDF policy:

- `pdf.scannedPdfSupport: true`
- `pdf.mode: "embedded_text_and_ocr"`

Backend OCR runtime check:

```bash
docker compose exec backend tesseract --version
docker compose exec backend tesseract --list-langs
```

The language list must include:

- `eng`
- `kaz`
- `rus`

## OCR Smoke Test

1. Upload a normal embedded-text PDF from the UI.
2. Confirm parsing succeeds and chunks show PDFBox extraction metadata such as `extractor=pdfbox` and `ocrUsed=false`.
3. Upload a scanned image-only PDF with no more than the configured OCR page limit, default `12`.
4. Confirm the UI no longer shows the scanned-PDF unsupported warning.
5. Confirm source metadata shows OCR usage, for example `extractor=tesseract` or `ocrUsed=true`.
6. Ask a RAG question whose answer exists only in the scanned image text.
7. Confirm the answer includes the expected value and source metadata marks OCR usage.

If OCR is not enabled, check:

```bash
docker compose exec backend tesseract --list-langs
docker compose exec backend curl -fsS http://127.0.0.1:8080/api/materials/policy
docker compose logs backend
```

## Ollama Models

List models:

```bash
docker compose exec ollama ollama list
```

Pull or change models manually:

```bash
docker compose exec ollama ollama pull qwen2.5:7b
docker compose exec ollama ollama pull nomic-embed-text
```

Then update `.env` if model names change:

```dotenv
APP_LLM_MODEL=qwen2.5:7b
APP_EMBEDDINGS_MODEL=nomic-embed-text
```

Ollama stores model data in the named `ollama-data` volume, so rebuilds and restarts do not delete downloaded models.

For NVIDIA GPU hosts, install NVIDIA Container Toolkit and add the documented `gpus: all` setting to the `ollama` service in `docker-compose.yml`.

## Optional Elasticsearch Profile

The default stack does not run Elasticsearch. To enable it intentionally:

```bash
docker compose --profile search up -d elasticsearch
```

Update `.env`:

```dotenv
APP_SEARCH_SYNC_ENABLED=true
APP_RAG_LEXICAL_PROVIDER=auto
SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200
```

Restart backend:

```bash
docker compose up -d backend
scripts/linux/preflight-compose.sh
```

The backend should report search/index-sync readiness once the profile is running and synchronization is enabled.

## Upgrade

Before upgrading, take a database backup:

```bash
mkdir -p backups
docker compose exec -T postgres pg_dump -U "${POSTGRES_USER:-ragstudio}" "${POSTGRES_DB:-ragstudio}" > "backups/ragstudio-$(date +%Y%m%d-%H%M%S).sql"
```

Deploy new code:

```bash
docker compose build
docker compose up -d postgres ollama ollama-init backend frontend
scripts/linux/preflight-compose.sh
```

Flyway remains enabled. Do not set `SPRING_FLYWAY_ENABLED=false` for production upgrades.

## Restore

Restore a SQL dump into the running database:

```bash
docker compose exec -T postgres psql -U "${POSTGRES_USER:-ragstudio}" -d "${POSTGRES_DB:-ragstudio}" < backups/ragstudio.sql
```

Application files live in the named `backend-storage` volume. Ollama models live in `ollama-data`. PostgreSQL data lives in `postgres-data`. Elasticsearch data, when enabled, lives in `elasticsearch-data`.

## Troubleshooting

Show service status:

```bash
docker compose ps
```

Follow backend logs:

```bash
docker compose logs -f backend
```

Port `80` already in use:

```dotenv
FRONTEND_HTTP_PORT=8088
```

Then restart frontend:

```bash
docker compose up -d frontend
```

Ollama models missing:

```bash
docker compose up -d ollama ollama-init
docker compose logs ollama-init
```

OCR status is down:

```bash
docker compose exec backend tesseract --version
docker compose exec backend tesseract --list-langs
docker compose exec backend curl -fsS http://127.0.0.1:8080/api/health
```

If `kaz`, `rus`, or `eng` is missing, rebuild the backend image:

```bash
docker compose build backend
docker compose up -d backend
```

Flyway migration errors usually mean the database already has a different migration history. Back up the database before any repair or manual migration action.
