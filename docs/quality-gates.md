# Quality Gates

Phase 7 закрепляет локальные и CI-проверки, которые защищают архитектурные границы, security, metadata contracts, durable queues, search/retrieval quality и размер модулей.

## Быстрый запуск

```bash
bash scripts/quality-gates.sh fast
```

`fast` не требует Docker. Он запускает:

- `scripts/phase5-static-gate.py`;
- `scripts/architecture-boundary-gate.py`;
- `scripts/test_architecture_boundary_gate.py`;
- `scripts/complexity-budget-gate.py`;
- `scripts/review-contract.py --skip-body`;
- targeted backend tests для auth/security, metadata, indexing, durable chat, Context Manager contracts/unit coverage и LLM-provider runtime/contracts;
- targeted frontend contract tests для API/types/hooks/utils, conversation/context UI, memory review gating и LLM-provider settings UI.
- Phase 8 targeted proof для strict Elasticsearch mapping, structured-v1 rollout proof gating, lineage override ingestion semantics, generated API contract drift, and health UI exposure.

Полный локальный прогон:

```bash
bash scripts/quality-gates.sh full
```

`full` добавляет backend coverage, frontend coverage/build и Docker-backed Maven verify. Для него нужен Docker/Testcontainers.
`full` запускает backend proof как `mvn -B clean verify -Pcoverage`, чтобы локальные stale Failsafe reports и stale JaCoCo data не смешивались со свежим результатом.

CI-режим:

```bash
bash scripts/quality-gates.sh ci
```

Он повторяет CI-shaped proof: static gates, backend/frontend coverage ratchets, frontend build и Docker-backed backend verification.

## Что защищают gates

| Gate | Команда | Защищает |
| --- | --- | --- |
| Static anti-regression | `python3 scripts/phase5-static-gate.py` | слабые runtime credentials, service/infrastructure boundaries, metadata alias invariants, durable chat execution path |
| Architecture boundary | `python3 scripts/architecture-boundary-gate.py` | backend layer boundaries: model не импортирует service/api/config/infrastructure, service/provider/infrastructure не импортируют API layer, infrastructure не импортирует concrete services, config/service debt stays explicit |
| Architecture gate fixtures | `python3 scripts/test_architecture_boundary_gate.py` | fixture-proof для boundary rules, stale baseline detection, frontend direct `apiClient` rule |
| Complexity budget | `python3 scripts/complexity-budget-gate.py` | запрет роста известных больших файлов и новых god-files |
| Review contract | `python3 scripts/review-contract.py` | заполненность PR evidence, anti-sprawl declaration, high-risk path proof |
| Backend coverage | `mvn -B clean verify -Pcoverage` + `scripts/coverage-ratchet.py backend` | unit + Docker-backed integration coverage для global line coverage и critical packages: controller, config, service, audit/material infrastructure |
| Frontend coverage | `npm --prefix frontend run test:coverage` + `scripts/coverage-ratchet.py frontend` | global coverage и focused groups: `src/api`, `src/hooks`, `src/utils` |
| Frontend build | `npm --prefix frontend run build` | TypeScript/build regressions |
| Integration | `mvn -B clean verify` | Docker-backed repository, migration, queue, Elasticsearch/search integration paths |

Context Manager regression coverage in `fast` includes disabled feature-state contracts, idempotent `clientTurnId` submission, durable run cancel/lease guards, context assembly/history/prompt placement, sticky state, summary/memory redaction, inspector status payloads, frontend stateless fallback, thread reload, explicit cancel, and memory review UI paths. Docker-backed conversation/context repository checks remain in `full`/targeted `verify` because they require Testcontainers.

LLM-provider regression coverage in `fast` includes the controller contract, service activation/secret behavior, crypto, probe/model fallback, active provider resolution, readiness persistence, prompt policy model resolution, chat/embedding transport clients, frontend API routes, generated DTO/enums, and settings UI flows. Docker-backed LLM provider repository checks remain in `full`/targeted `verify` because they require Testcontainers.

Search rollout regression coverage in `fast` includes `SearchableChunkDocumentTest` for strict mapping/field coverage, `StructuredV1ProofServiceTest` for fail-closed structured-v1 writes, `MaterialServiceTest` lineage override behavior, generated API contract drift checks, and Status Summary health rendering for chunk profile/proof status. Docker-backed unknown-field rejection and alias/index integration remain in `full`/targeted `verify` because they require Testcontainers and Elasticsearch.

## Coverage Ratchet

Coverage thresholds are intentionally conservative. They are stored in:

```text
scripts/coverage-baseline.json
```

Backend JaCoCo also checks the same minimums during `verify` in `backend/pom.xml`, so `mvn -B clean verify -Pcoverage` fails before CI can publish a misleading green report. Backend coverage is intentionally measured across unit and integration tests because the audit/material infrastructure packages are mostly exercised by Docker-backed tests.

When coverage improves, raise the matching baseline in the same PR. Do not lower a baseline unless the PR explicitly documents the debt, owner scenario, and recovery plan in the PR template.

Baseline update is forbidden when:

- the only reason is to make a regression pass;
- a critical package lost meaningful behavior coverage;
- a high-risk path changed without integration evidence;
- a file moved or split and the old baseline entry can be removed instead.

## Complexity Baseline

Complexity baseline lives in:

```text
scripts/complexity-budget-baseline.json
```

The gate fails when a tracked file grows. It also fails when a tracked file shrinks or disappears, because the baseline must be lowered or removed immediately.

Add a new baseline entry only for known pre-existing debt. New files should be split before merge instead of allowlisted.

## Architecture Baseline

Architecture baseline lives in:

```text
scripts/architecture-boundary-baseline.json
```

The baseline uses schema version 2. Each entry must include rule, path, imported symbol/call, risk id, owner phase, reason, and removal criterion. The gate reports source line numbers for current violations, but the baseline identity intentionally excludes line numbers so harmless formatting does not churn the debt ledger.

The same gate also blocks direct frontend component/App network calls through `apiClient`. `frontend/src/api/**` and `frontend/src/hooks/**` remain the allowed network layers; `apiClient.buildCurlExample(...)` is allowed as a non-network preview helper.

Do not add architecture baseline entries for new code. A temporary exception requires an explicit risk id, owner phase, removal criterion, and reviewer-visible justification in the PR.

## Review Contract

PRs must fill:

- risk level and touched boundaries;
- risk ids from `docs/refactor-risk-register.md`, or `N/A` only for trivial changes outside the refactor ledger;
- tests/evidence;
- security, data integrity, migration/deploy, and docs/runbook impact;
- anti-sprawl declaration for new `legacy`, `fallback`, `rollout`, `bestEffort`, or compatibility paths;
- complexity delta;
- rollback plan.

High-risk paths include security/config/auth, migrations, material metadata, ingestion/indexing/chat queue, docs/runbook deletion, scripts/deploy, compose/env, and CI workflows. These paths require concrete evidence in the PR body; backend/deploy high-risk paths must also check or explain `mvn -B clean verify -Pcoverage` or `mvn -B clean verify`.

## Scalability Smoke

Smoke checks are intentionally lightweight and are not production load tests:

```bash
APP_ROLLOUT_SEARCH_API_V1=true APP_SECURITY_ADMIN_PASSWORD=<password> bash scripts/scalability-smoke.sh all
```

Modes:

- `upload`: create several tiny text materials and verify they are saved;
- `indexing`: wait for created materials to drain to `READY` or `PARTIAL_READY`;
- `chat-queue`: submit durable chat runs through `/api/chat-runs`, verify initial `RECEIVED` queue status plus polling URLs, then poll status;
- `retrieval`: query seeded smoke data and enforce a small latency ceiling;
- `all`: run the sequence above.

Use `SMOKE_MATERIAL_COUNT`, `SMOKE_CHAT_RUN_COUNT`, `SMOKE_INDEXING_WAIT_SECONDS`, `SMOKE_CHAT_WAIT_SECONDS`, and `SMOKE_RETRIEVAL_MAX_MS` to tune local proof without turning it into a benchmark.
`retrieval` and `all` require the target stack to run with `APP_ROLLOUT_SEARCH_API_V1=true`; otherwise `/api/search` correctly returns `search.api_disabled`.

## Local Docker Notes

`fast` mode is Docker-free. `full`, `ci`, `mvn -B clean verify`, and `mvn -B clean verify -Pcoverage` need Docker/Testcontainers.

On local Colima, start the daemon first and point Testcontainers at the Colima socket if the default `/var/run/docker.sock` is not available:

```bash
colima start
export DOCKER_HOST=unix:///Users/gleb-imac/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

CI remains the authority for Java compatibility and runs the required gates on Temurin Java 21. Local Java 25 can emit JaCoCo instrumentation warnings; treat those as local runtime noise unless the build fails.

## Failure Triage

1. Read the first failing gate. The earliest failure usually points to the cheapest fix.
2. If static or architecture gates fail, fix the boundary/regression before running slower tests.
3. If coverage fails, add focused tests or raise the baseline only when the PR clearly explains why coverage debt is intentional.
4. If complexity fails because a file shrank, lower/remove the baseline entry.
5. If smoke fails, check `/api/health`, Ollama, PostgreSQL, embeddings, and worker queue fields before assuming a code regression.
