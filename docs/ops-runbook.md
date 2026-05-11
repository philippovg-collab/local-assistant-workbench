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
./scripts/pull-model.sh deepseek-r1:8b
./scripts/pull-model.sh nomic-embed-text
```

Optional larger DeepSeek comparison target:

```bash
./scripts/pull-deepseek-local.sh 14b
```

Practical order for a local Apple Silicon machine:

1. Keep `qwen2.5:7b` as the backend baseline.
2. Validate first DeepSeek smoke with `deepseek-r1:8b`.
3. Pull `deepseek-r1:14b` only as an optional comparison target.

The first request after pulling or restarting a larger model can take several minutes.

`/api/models` lists chat models only. `nomic-embed-text` is filtered from that catalog because it is used by the embedding client for indexing and retrieval, not for chat completion.

## UI-Managed LLM Providers

Use `Settings -> LLM подключения` to register corporate OpenAI-compatible gateways without redeploying the backend. Only `OPENAI_COMPATIBLE` providers are supported in this milestone.

Required fields:

- `Base URL`: gateway origin, for example `http://10.9.120.3:8000`.
- `Default chat model`: required before activating a provider for Chat.
- `Embedding model` and `Expected embedding dimension`: required before activating a provider for Embeddings.
- Provider paths default to `/v1/chat/completions`, `/v1/models`, and `/v1/embeddings`.

API keys saved through the UI are encrypted with `APP_LLM_PROVIDER_SECRET_KEY`. Leave the key empty only when all saved providers are keyless or when operators use env fallback exclusively. If a user tries to save an API key without `APP_LLM_PROVIDER_SECRET_KEY`, the request fails closed; existing env fallback remains usable.

Operational flow:

1. Create the provider without activating it.
2. Run `Models`; a `404` or `405` models endpoint can be acceptable if `Probe` reports `DEGRADED` and chat/embeddings are healthy.
3. Run `Probe`; responses and stored status must not include API keys or ciphertext.
4. Activate Chat and Embeddings separately. Activating Embeddings may enqueue active materials for reindexing when the embedding provider fingerprint changes.
5. Use `Chat env fallback` or `Embeddings env fallback` to roll a purpose back to `APP_LLM_*` or `APP_EMBEDDINGS_*`.

Health fields:

- `activeChatProvider` and `activeEmbeddingProvider` identify the selected DB provider or env fallback without secrets.
- `llmStatus` reports model catalog readiness for the active chat provider; unsupported model catalogs are `DEGRADED` when chat still works.
- `directStatus` reports direct chat execution readiness.
- `embeddingStatus` reports the active embedding provider readiness.

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

## Context Manager Feature Surface

Context Manager is controlled by flat Spring properties under `app.context.*`; environment variables use Spring relaxed binding. Keep the feature default-off until release proof is green.

| Env var | Spring property | Default |
| --- | --- | --- |
| `APP_CONTEXT_ENABLED` | `app.context.enabled` | `false` |
| `APP_CONTEXT_CONVERSATIONS_ENABLED` | `app.context.conversations-enabled` | `false` |
| `APP_CONTEXT_HISTORY_ENABLED` | `app.context.history-enabled` | `false` |
| `APP_CONTEXT_STICKY_STATE_ENABLED` | `app.context.sticky-state-enabled` | `false` |
| `APP_CONTEXT_RETRIEVAL_QUERY_RESOLUTION_ENABLED` | `app.context.retrieval-query-resolution-enabled` | `false` |
| `APP_CONTEXT_SUMMARY_ENABLED` | `app.context.summary-enabled` | `false` |
| `APP_CONTEXT_LONG_TERM_MEMORY_ENABLED` | `app.context.long-term-memory-enabled` | `false` |
| `APP_CONTEXT_MAX_RECENT_TURNS` | `app.context.max-recent-turns` | `6` |
| `APP_CONTEXT_MAX_HISTORY_TOKENS` | `app.context.max-history-tokens` | `2000` |
| `APP_CONTEXT_SUMMARY_REFRESH_TURNS` | `app.context.summary-refresh-turns` | `6` |
| `APP_CONTEXT_SUMMARY_MAX_INPUT_TURNS` | `app.context.summary-max-input-turns` | `12` |
| `APP_CONTEXT_SUMMARY_MAX_OUTPUT_CHARS` | `app.context.summary-max-output-chars` | `2500` |
| `APP_CONTEXT_SUMMARY_MAX_FACTS` | `app.context.summary-max-facts` | `12` |
| `APP_CONTEXT_SUMMARY_MAX_ACTIVE_ENTITIES` | `app.context.summary-max-active-entities` | `20` |
| `APP_CONTEXT_SUMMARY_MAX_SOURCE_REFS` | `app.context.summary-max-source-refs` | `20` |
| `APP_CONTEXT_SUMMARY_MAX_ATTEMPTS` | `app.context.summary-max-attempts` | `3` |
| `APP_CONTEXT_SUMMARY_RETRY_BASE_SECONDS` | `app.context.summary-retry-base-seconds` | `30` |
| `APP_CONTEXT_SUMMARY_RETRY_MAX_SECONDS` | `app.context.summary-retry-max-seconds` | `300` |
| `APP_CONTEXT_MEMORY_EXTRACTION_LEASE_SECONDS` | `app.context.memory-extraction-lease-seconds` | `120` |
| `APP_CONTEXT_MEMORY_EXTRACTION_MAX_ATTEMPTS` | `app.context.memory-extraction-max-attempts` | `3` |
| `APP_CONTEXT_MEMORY_EXTRACTION_RETRY_BASE_SECONDS` | `app.context.memory-extraction-retry-base-seconds` | `30` |
| `APP_CONTEXT_MEMORY_EXTRACTION_RETRY_MAX_SECONDS` | `app.context.memory-extraction-retry-max-seconds` | `300` |
| `APP_CONTEXT_MEMORY_EXTRACTION_WORKER_COUNT` | `app.context.memory-extraction-worker-count` | `1` |
| `APP_CONTEXT_MEMORY_EXTRACTION_DRAIN_MAX_JOBS` | `app.context.memory-extraction-drain-max-jobs` | `50` |
| `APP_CONTEXT_MEMORY_SELECTION_LIMIT` | `app.context.memory-selection-limit` | `8` |
| `APP_CONTEXT_RETENTION_ENABLED` | `app.context.retention.enabled` | `true` |
| `APP_CONTEXT_RETENTION_SNAPSHOT_DAYS` | `app.context.retention.snapshot-days` | `30` |
| `APP_CONTEXT_RETENTION_SUMMARY_JOB_DAYS` | `app.context.retention.summary-job-days` | `14` |
| `APP_CONTEXT_RETENTION_MEMORY_REJECTED_DAYS` | `app.context.retention.memory-rejected-days` | `30` |
| `APP_CONTEXT_RETENTION_MEMORY_DELETED_DAYS` | `app.context.retention.memory-deleted-days` | `30` |
| `APP_CONTEXT_RETENTION_MEMORY_JOB_DAYS` | `app.context.retention.memory-job-days` | `14` |
| `APP_CONTEXT_RETENTION_DELETED_CONVERSATION_DAYS` | `app.context.retention.deleted-conversation-days` | `30` |
| `APP_CONTEXT_RETENTION_EMPTY_CONVERSATION_DAYS` | `app.context.retention.empty-conversation-days` | `7` |
| `APP_CONTEXT_RETENTION_KEEP_LATEST_SNAPSHOTS_PER_CONVERSATION` | `app.context.retention.keep-latest-snapshots-per-conversation` | `5` |
| `APP_CONTEXT_RETENTION_BATCH_SIZE` | `app.context.retention.batch-size` | `500` |

`/api/health` exposes effective feature state in `contextFeatures`. Use that response to verify rollout after config changes:

```bash
curl -fsS http://127.0.0.1:8080/api/health | jq '.contextFeatures'
```

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

### Context Manager Rollback

Context Manager rollback is config-based; do not run destructive down migrations. Disable features from the highest-risk writers down to the compatibility surface:

1. Set `APP_CONTEXT_LONG_TERM_MEMORY_ENABLED=false`.
2. Set `APP_CONTEXT_SUMMARY_ENABLED=false`.
3. Set `APP_CONTEXT_RETRIEVAL_QUERY_RESOLUTION_ENABLED=false`.
4. Set `APP_CONTEXT_STICKY_STATE_ENABLED=false` and `APP_CONTEXT_HISTORY_ENABLED=false`.
5. Set `APP_CONTEXT_CONVERSATIONS_ENABLED=false`.
6. Set `APP_CONTEXT_ENABLED=false`.

After rollback, legacy durable `/api/chat-runs` and compatibility `/api/chat` remain available. Leave conversation, snapshot, summary, and memory rows in place for forward-compatible inspection or reviewed cleanup.

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
- `llmStatus` and `embeddingStatus`: active OpenAI-compatible provider health.
- `activeChatProvider` and `activeEmbeddingProvider`: selected DB provider or env fallback without secrets.
- `ocrStatus`: scanned PDF OCR readiness.
- `databaseStatus` and `vectorStatus`: PostgreSQL and pgvector health.
- `contextFeatures`: effective Context Manager flags for context, conversations, history, sticky state, rewrite, summary, and long-term memory.
- `indexingPendingCount`, `indexingInProgressCount`, `indexingFailedCount`, `indexingNextRetryAt`, `indexingOldestPendingAt`, `indexingOldestInProgressAt`: material indexing queue state.
- `qualityLayer.configuredChunkProfile` and `qualityLayer.effectiveChunkProfile`: configured chunking mode and rollout-effective mode.
- `qualityLayer.structuredV1ProofStatus`: `DISABLED`, `PROOF_MISSING`, `PROOF_NOT_FOUND`, `PROOF_INCOMPATIBLE`, `PROOF_FAILED`, or `COMPATIBLE` structured-v1 write proof state with compare id/reason details.

## Material Indexing

Material upload and text save persist content with a durable auto-tagging task. The API exposes `enrichmentStatus` on material summary/detail responses so operators can see whether enrichment is `PENDING`, `RUNNING`, `FAILED`, or `DONE`. Executor rejection leaves the task `PENDING`; provider and parse failures retry through the queue before surfacing as `FAILED`. Successful enrichment updates metadata through the material lifecycle and moves the material back to `PENDING` so indexing refreshes searchable metadata.

When `APP_ROLLOUT_STRUCTURED_V1=true`, material writes and rechunk operations fail closed until `APP_ROLLOUT_STRUCTURED_V1_PROOF_COMPARE_ID` points to a compatible eval compare with overall verdict `PASS`. Missing, not found, incompatible, failed, warning-only, or unavailable proof returns `material.structured_rollout_proof_required`. Read-only startup and health checks remain available so operators can inspect state and roll back config.

Material indexing uses bounded worker slots. Tune concurrent drains with:

```bash
export APP_MATERIALS_INDEXING_WORKER_COUNT=2
```

Each worker claims one material at a time and stops after `app.materials.indexing-drain-max-jobs`. Pending, in-progress, failed, retry, and oldest outstanding timestamps are visible in `/api/health`.

## Material Lineage Override

Text create and file upload can include an optional `lineageOverride` payload:

```json
{
  "lineageKey": "policy-grid-2026",
  "reason": "Operator confirmed this renamed file is the next version of the existing policy lineage.",
  "confirmSupersedeExistingLineage": true
}
```

Use it only when an operator intentionally wants a renamed title or file to continue an existing lineage. The reason is required and is surfaced on material detail/lineage responses through `lineageOverride` with `lineageKey`, `active`, `reason`, `createdBy`, and `createdAt`.

Collision rules:

- empty key or reason is rejected;
- reusing an override that already has versions requires `confirmSupersedeExistingLineage=true`;
- attempts to merge two different existing natural lineages fail with `material.lineage_override_collision`;
- override records are stored in `material_lineage_operator_overrides` with deterministic `operator:<sha256(normalizedLineageKey)>` source keys and do not rewrite historical material content.

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
| Docker UI shows only `qwen2.5:7b` | `docker exec ragstudio-ollama-1 ollama list` | Run `docker exec ragstudio-ollama-1 ollama pull deepseek-r1:8b`, then refresh `/api/models`. |
| Docker Ollama was started from another folder | `APP_SECURITY_ADMIN_PASSWORD=<current-password> ./scripts/local-runtime-diagnostics.zsh` | Restart this repository's Compose stack or pull the missing model into the running `ragstudio-ollama-1` container. |
| Model is installed but absent from UI | `curl http://127.0.0.1:8080/api/models` after login | Pull it into the Ollama instance used by backend. |
| RAG is blocked while chat model is visible | `curl http://127.0.0.1:8080/api/health` after login | Check `embeddingStatus`; RAG needs `nomic-embed-text`. |
| UI rejects API key save | Check `APP_LLM_PROVIDER_SECRET_KEY` in backend env | Set a long random secret and restart backend, or save only keyless providers. |
| Corporate `/v1/models` returns 404/405 | Run provider `Probe` | Accept `DEGRADED` only if chat/embeddings checks pass; otherwise fix the endpoint path or model names. |
| RAG changed after embedding activation | Check `/api/health` indexing queue fields | Wait for reindexing to drain; rollback with `Embeddings env fallback` if the provider was wrong. |
