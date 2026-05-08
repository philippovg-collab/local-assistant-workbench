# Ops Runbook

## Local Runtime

For Codex, agents, PTY sessions, and other non-interactive runtimes, use the supervising stack entrypoint:

```bash
./scripts/run-local-stack.sh
```

It keeps Ollama, backend, and frontend alive under one process and stops only the child processes it started.

For ordinary manual shell usage, detached helpers are still available:

```bash
./scripts/start-studio.sh
./scripts/stop-studio.sh
```

Low-level helpers:

```bash
./scripts/start-ollama.sh
./scripts/start-backend.sh
./scripts/start-frontend.sh
```

## Local Prerequisites

- Java 21.
- Node.js/npm.
- Docker.
- Ollama.
- PostgreSQL 16 with `pgvector`.
- Tesseract and language packs if scanned PDF OCR is needed.

Local PostgreSQL example:

```bash
docker run --name ragstudio-postgres \
  -e POSTGRES_DB=ragstudio \
  -e POSTGRES_USER=ragstudio \
  -e POSTGRES_PASSWORD=ragstudio \
  -p 5432:5432 \
  -d pgvector/pgvector:pg16
```

If using an existing PostgreSQL instance:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Backend datasource defaults:

```text
jdbc:postgresql://127.0.0.1:5432/ragstudio
username: ragstudio
password: ragstudio
```

Override with:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ragstudio
export SPRING_DATASOURCE_USERNAME=ragstudio
export SPRING_DATASOURCE_PASSWORD=ragstudio
```

## Models

Baseline models:

```bash
./scripts/pull-model.sh qwen2.5:7b
./scripts/pull-model.sh nomic-embed-text
```

Optional DeepSeek local fast path:

```bash
./scripts/pull-deepseek-local.sh
```

Practical order for a local Apple Silicon machine:

1. Keep `qwen2.5:7b` as the backend baseline.
2. Validate first DeepSeek smoke with `deepseek-r1:8b`.
3. Pull `deepseek-r1:14b` only as an optional comparison target.

The first request after pulling or restarting a larger model can take several minutes.

## Chat Run Lifecycle

Production chat execution is durable:

```bash
POST /api/chat-runs
GET  /api/chat-runs/{id}/status
GET  /api/chat-runs/{id}/result
POST /api/chat-runs/{id}/cancel
```

`POST /api/chat` is retained only as a deprecated compatibility endpoint for older clients. It submits a durable run, waits up to `APP_CHAT_EXECUTION_COMPATIBILITY_WAIT_TIMEOUT_SECONDS` (default `30`, capped at `120`), and returns `408 chat.run_still_processing` with the durable status/result URLs if the worker has not completed yet. That timeout does not cancel the run.

Use `/api/chat-runs/{id}/cancel` for cancellation. Remove `/api/chat` only in a later release after legacy caller usage is verified to be zero.

## Health Checks

Public liveness:

```bash
curl -fsS http://127.0.0.1:8080/api/liveness
```

Authenticated runtime diagnostics:

```bash
APP_SECURITY_ADMIN_PASSWORD=<current-password> ./scripts/local-runtime-diagnostics.zsh
```

If `APP_SECURITY_ADMIN_PASSWORD` is not set, diagnostics skip authenticated model and health checks and print a warning.

## Pre-Merge Quality Checklist

Run the fast gate before opening a PR:

```bash
bash scripts/quality-gates.sh fast
```

For medium/high-risk backend, queue, metadata, search, security, migration, or deploy changes, also run:

```bash
bash scripts/quality-gates.sh full
```

If the full gate is too expensive locally, the PR body must explain the skipped proof and provide the closest evidence, for example targeted backend tests plus CI integration.

Before merge, confirm:

- PR template has risk, touched boundaries, tests/evidence, anti-sprawl declaration, complexity delta, security/data/deploy impact, and rollback plan.
- Coverage baselines were raised when coverage improved.
- Complexity baselines were lowered or removed when files shrank or disappeared.
- Docs/runbook changes match any new operator-facing behavior.

## Release Smoke Checklist

After deployment or local stack changes:

```bash
APP_SECURITY_ADMIN_PASSWORD=<current-password> ./scripts/local-runtime-diagnostics.zsh
APP_ROLLOUT_SEARCH_API_V1=true APP_SECURITY_ADMIN_PASSWORD=<current-password> bash scripts/scalability-smoke.sh all
```

Use the smoke script to catch obvious regressions in upload, indexing drain, durable chat queue, and retrieval sources. Keep Elasticsearch rollout proof separate; use `docs/search-rollout.md` and the `scripts/search-*` commands when validating Elasticsearch alias promotion or rollback.
The `retrieval` and `all` smoke modes require `/api/search`, so run the target stack with `APP_ROLLOUT_SEARCH_API_V1=true` for that proof.

## Rollback Checklist

1. Capture the failing command, service logs, and `/api/health` response.
2. Stop new deploy rollout or alias promotion.
3. Revert the application image, config, or PR commit according to the PR rollback plan.
4. For Elasticsearch-only regressions, roll back read/write aliases through the search rollout runbook instead of changing PostgreSQL data.
5. For migration or data integrity risk, stop writers first and restore from the documented database backup path before restarting workers.
6. Re-run liveness, diagnostics, and the relevant smoke mode.

## Local vs Docker-Backed Checks

`bash scripts/quality-gates.sh fast` is local-only and does not require Docker.

`bash scripts/quality-gates.sh full`, `mvn -B clean verify`, `mvn -B clean verify -Pcoverage`, and `bash scripts/scalability-smoke.sh all` require the relevant local services or Docker/Testcontainers support.

For Colima-backed Testcontainers on this machine:

```bash
colima start
export DOCKER_HOST=unix:///Users/gleb-imac/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

CI runs the authoritative Java 21 gates. Local Java 25 can print JaCoCo instrumentation warnings during coverage; investigate only if the Maven command itself fails.

## Security Operations

Keep `APP_CORS_ALLOWED_ORIGINS` aligned with the exact HTTPS origin used by the browser. Do not use `*` with session authentication.

Audit traces are retained for 30 days by default. They redact obvious secrets and omit raw LLM responses unless `APP_AUDIT_STORE_RAW_LLM_RESPONSE=true` is set for a controlled diagnostic session. Set `APP_AUDIT_MAX_STORED_TEXT_CHARS` if prompt or response fields need a stricter storage cap.

Important `/api/health` fields:

- `status`: core backend/runtime status.
- `directStatus`: readiness of direct chat.
- `ragStatus`: readiness of RAG chat.
- `knowledgeStatus`: `EMPTY`, `HISTORICAL_ONLY`, `INDEXING`, `READY`, or `DEGRADED`.
- `llmStatus` and `embeddingStatus`: Ollama catalog/provider health.
- `ocrStatus`: scanned PDF OCR readiness.
- `databaseStatus` and `vectorStatus`: PostgreSQL and pgvector health.
- `indexingPendingCount`, `indexingInProgressCount`, `indexingFailedCount`, `indexingNextRetryAt`, `indexingOldestPendingAt`, `indexingOldestInProgressAt`: material indexing queue state.

## Material Indexing

Material upload and text save persist content first, then run LLM auto-tag enrichment as best-effort follow-up. If auto-tagging is slow or unavailable, the material remains saved and indexable; successful enrichment moves the material back to `PENDING` so indexing refreshes searchable metadata.

Material indexing uses bounded worker slots. Tune concurrent drains with:

```bash
export APP_MATERIALS_INDEXING_WORKER_COUNT=2
```

Each worker claims one material at a time and stops after `app.materials.indexing-drain-max-jobs`. Pending, in-progress, failed, retry, and oldest outstanding timestamps are visible in `/api/health`.

## OCR

For server OCR install:

```bash
sudo apt-get install -y tesseract-ocr tesseract-ocr-kaz tesseract-ocr-rus tesseract-ocr-eng
```

Backend checks `app.ocr.enabled`, the configured binary path, and required languages. If OCR is unavailable, PDF upload policy remains enabled for embedded text but scanned PDF support is reported as unavailable.

## Material Recovery

Use `POST /api/materials/{id}/reindex` only for `ACTIVE` versions in `FAILED` or `PARTIAL_READY`.

This action:

- moves the material back to `PENDING`;
- clears claim/retry scheduling;
- wakes the indexing drain;
- reuses already stored normalized content and chunk model.

If the original document changed or raw extraction must run again, upload a new version instead of reindexing.

## Troubleshooting Models

| Symptom | Check | Action |
| --- | --- | --- |
| Model exists on host but `/api/models` returns `llm.provider_unavailable` | `curl http://127.0.0.1:11434/api/tags` | Start host Ollama or check `APP_LLM_BASE_URL`. |
| Docker UI shows only baseline model | `docker exec ragstudio-ollama-1 ollama list` | Pull the model inside the same container or start Compose with `APP_LLM_EXTRA_MODELS`. |
| Model is installed but absent from UI | `curl http://127.0.0.1:8080/api/models` after login | Pull it into the Ollama instance used by backend. |
| RAG is blocked while chat model is visible | `curl http://127.0.0.1:8080/api/health` after login | Check `embeddingStatus`; RAG needs `nomic-embed-text`. |
