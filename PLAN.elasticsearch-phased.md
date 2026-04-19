# Elasticsearch Rollout Completion Checklist

Обновлено по состоянию текущего рабочего дерева на `2026-04-19`.

## Короткий статус

Фазовый план внедрения Elasticsearch в целом реализован как gated rollout:
- Elasticsearch остаётся sidecar lexical read-model, PostgreSQL остаётся source of truth;
- semantic search остаётся в PostgreSQL/pgvector;
- runtime retrieval сохраняет текущий hybrid pipeline: `semantic + lexical -> RRF/rerank -> top-K`;
- production defaults остаются безопасными: `app.search-sync.enabled=false`, `app.rag.lexical-provider=postgres`.

Финальное production-переключение ещё не закрыто, пока не пройдены Docker-backed ES integration tests в обычном shell/CI.

## Completion Matrix

| Фаза | Актуальный статус | Комментарий |
|---|---|---|
| Phase 1. Search boundary | Done | Есть `SemanticSearchRepository`, `LexicalSearchProvider`, provider strategy/router и PostgreSQL default path. |
| Phase 2. Sync model | Done | Событийная модель сведена к collapsed per-material queue `material_search_sync_queue`; lifecycle enqueue покрывает searchable-state changes. |
| Phase 3. ES sidecar/shadow indexing | Implemented, release-gated | Есть index admin, aliases, bulk upsert/delete, stale cleanup, recovery/rebuild. Mapping обновлён под strict schema, default index version bumped to `v3`. |
| Phase 4. Shadow comparison/observability | Implemented, release-gated | Есть ES lexical provider, shadow comparison service/report, `/api/health` search plane и frontend presentation. |
| Phase 5. Production auto fallback | Implemented, release-gated | `auto` route умеет ES -> PostgreSQL fallback по health/backlog/runtime failure; default всё ещё PostgreSQL. |
| Phase 6. Quality track | Partially done/gated | `hybrid-rerank-v1` и quality IT есть; `structured-v1` зарегистрирован, но production flag выключен. |

## Что изменено после ревью

- ES strict mapping приведён в соответствие с `SearchableChunkDocument`:
  - `sectionPath`
  - `headingTrail`
  - `tableId`
  - `slideId`
  - `parserConfidence`
- `app.search-sync.index-version` поднят до `v3`, чтобы новый mapping не конфликтовал с уже созданными индексами.
- `/api/health` снова использует refreshed search decision, чтобы UI и routing не показывали stale `searchStatus=UP`.
- Добавлен regression-test, который сверяет сериализуемые поля `SearchableChunkDocument` с ES mapping при `dynamic: strict`.

## Release Gates Before Enabling `auto`

1. Из обычного shell/CI с рабочим Docker/Testcontainers прогнать:
   `./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT`
2. Подготовить новый write index:
   `./scripts/search-prepare-index.sh v3`
3. Пересобрать write index из PostgreSQL source of truth:
   `./scripts/search-rebuild-write-index.sh`
4. Проверить `/api/health`:
   - `searchStatus=UP`
   - `searchProvider=postgres` при default mode или `elasticsearch` при controlled `auto`
   - `searchSyncBacklog.pendingCount=0`
   - `searchSyncBacklog.failedCount=0`
5. После smoke search и quality check выполнить read alias promotion:
   `./scripts/search-promote-read-alias.sh v3`

## Rollback Rules

- Если ES degraded/down, вернуть или оставить `app.rag.lexical-provider=postgres`.
- Не выключать PostgreSQL lexical path: он остаётся обязательным fallback.
- Если write index загрязнён или mapping несовместим, создать новый versioned index, rebuild write alias, затем promote read alias только после smoke proof.
- Failed sync entries восстанавливать через `scripts/search-requeue-failed.sh` или rebuild write index, если нужна полная переигровка source of truth.

## Test Plan

Локальный non-Docker proof:
- `./scripts/test-backend.sh`
- `npm test` в `frontend`
- `./scripts/test-backend.sh fast -Dtest=Phase6RetrievalQualityIT`

Docker-backed Elasticsearch proof:
- `./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT`

## Короткий вывод

Elasticsearch rollout уже не является большим будущим проектом. Он реализован как controlled rollout, но production-включение должно оставаться заблокированным до Docker-backed proof, operator runbook и успешного rebuild/promote цикла для `v3`.
