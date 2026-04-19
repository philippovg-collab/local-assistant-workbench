# Фазовый план внедрения Elasticsearch для RAG

Обновлено по состоянию кода на `2026-04-17`.
Исходный референс-план: `/Users/gleb-imac/Downloads/PLAN.md`.

## Review Findings

### `[P1]` Исходный план не учитывает текущую модель `ACTIVE/SUPERSEDED` и lifecycle версий
- Retrieval, readiness и lexical/semantic queries уже жёстко фильтруют только `ACTIVE` + `READY/PARTIAL_READY` материалы, поэтому Elasticsearch нельзя встраивать как "просто ещё один индекс по чанкам".
- Любая интеграция должна корректно обрабатывать `reindex`, реактивацию superseded-версии при удалении active-версии и исключение historical-версий из выдачи.
- Опорные места в коде:
  - [MaterialRetrievalService.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java:56)
  - [PostgresMaterialRepository.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialRepository.java:382)
  - [MaterialQueryService.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialQueryService.java:85)
  - [MaterialQueryService.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialQueryService.java:147)
  - [readiness.ts](/Users/gleb-imac/Documents/Тест ИИ/frontend/src/utils/readiness.ts:50)

### `[P1]` Hybrid retrieval уже существует, поэтому не нужно переписывать retrieval flow с нуля
- Сейчас backend уже делает `semantic + lexical + RRF fusion`, а не отдельные независимые ветки. Значит, безопасная стратегия для первой итерации — заменить только lexical provider, сохранив существующий ranking-контур.
- Опорные места в коде:
  - [MaterialRetrievalService.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java:71)
  - [HybridChunkRanker.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/HybridChunkRanker.java:16)

### `[P1]` Исходный план недооценивает уже существующую асинхронную очередь индексации
- Сейчас материал не становится searchable в request path: сначала он попадает в `PENDING/IN_PROGRESS`, потом отдельный worker завершает embeddings и только после этого переводит материал в `READY/PARTIAL_READY`.
- Elasticsearch-синк должен встраиваться в этот контур, а не обходить его, иначе появится рассинхрон между Postgres readiness и search readiness.
- Опорные места в коде:
  - [MaterialIndexingService.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialIndexingService.java:53)
  - [PostgresMaterialRepository.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialRepository.java:465)

### `[P2]` Текущий health/readiness plane уже богаче, чем предполагал исходный план
- В `/api/health` уже есть `directStatus`, `ragStatus`, причины деградации и snapshot indexing queue. Elasticsearch нужно добавить в этот контракт как ещё один search-signal, не ломая существующую семантику `ragStatus`.
- Если `auto`-режим фоллбэчится на PostgreSQL lexical path, общий `ragStatus` не должен падать только из-за недоступности Elasticsearch.
- Опорные места в коде:
  - [HealthController.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/controller/HealthController.java:39)
  - [HealthResponse.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/model/HealthResponse.java:6)

### `[P2]` Проблема chunking остаётся актуальной, но её не стоит смешивать с rollout Elasticsearch
- Chunking по-прежнему режет текст фиксированными символами с overlap, и это действительно влияет на качество retrieval.
- Но менять chunking и storage backend в одной фазе слишком рискованно: станет трудно понять, из-за чего изменилось качество выдачи.
- Опорное место в коде:
  - [MaterialContentSupport.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/main/java/com/example/demo/service/MaterialContentSupport.java:65)

### `[P2]` Тестовая опора уже есть, значит план должен опираться на расширение текущих тестов, а не на создание контура с нуля
- На момент ревью проходят:
  - `backend`: `./scripts/test-backend.sh` -> `88` тестов
  - `frontend`: `npm test` -> `50` тестов
- Уже есть тесты на active-only retrieval, retries/backoff индексации, readiness и material lifecycle.
- Опорные места в тестах:
  - [MaterialRetrievalServiceTest.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/test/java/com/example/demo/service/MaterialRetrievalServiceTest.java:23)
  - [MaterialIndexingServiceTest.java](/Users/gleb-imac/Documents/Тест ИИ/backend/src/test/java/com/example/demo/service/MaterialIndexingServiceTest.java:22)

## Обновлённое архитектурное направление

- `PostgreSQL` остаётся source of truth для материалов, lineage, статусов индексации и `pgvector` semantic search.
- `Elasticsearch` вводится как sidecar read-model только для lexical retrieval.
- В первой продуктовой итерации semantic search остаётся в `PostgreSQL`.
- Retrieval pipeline не переписывается: сохраняем текущий `semantic -> lexical -> RRF fusion -> top-K`, меняем только источник lexical matches.
- В индекс Elasticsearch попадают только searchable-чанки:
  - материал `ACTIVE`
  - статус `READY` или `PARTIAL_READY`
- Historical и not-ready версии не должны участвовать в ES search path.
- Для rollout используется feature flag `app.rag.lexical-provider=postgres|auto|elasticsearch`.
  - `postgres`: старый lexical path
  - `elasticsearch`: только ES lexical path
  - `auto`: ES, если он healthy и синк не деградировал критически; иначе fallback на PostgreSQL

## Что нужно изменить относительно исходного плана

### Оставить без изменений
- `PostgreSQL` не заменяется в первой итерации.
- `Elasticsearch` вводится как sidecar на одном `VM`.
- Нужен alias-based rollout для индекса.
- Нужен shadow comparison до перевода production read path на ES.

### Скорректировать
- Вместо абстрактного "перестроить retrieval flow" нужно сохранить текущий `HybridChunkRanker` и вынести только lexical provider.
- Вместо синка только при `markIndexingReady` нужны события на все lifecycle-переходы, которые меняют searchability:
  - `READY/PARTIAL_READY`
  - `PENDING/FAILED` после reindex
  - `ACTIVE -> SUPERSEDED`
  - `SUPERSEDED -> ACTIVE`
  - `DELETE`
- Health нужно расширять отдельно от `ragStatus`, а не заменять текущую модель readiness.
- Chunking нужно вынести в отдельную позднюю фазу, а не тащить в тот же rollout, что и Elasticsearch.

## Фазовый план

### Phase 1. Разрезать search boundary без изменения поведения
Цель: подготовить код к pluggable lexical backend, не меняя пользовательский runtime.

Объём:
- Разделить текущий `MaterialSearchRepository` на:
  - `SemanticSearchRepository`
  - `LexicalSearchProvider`
- Оставить `PostgresSemanticSearchRepository` и `PostgresLexicalSearchProvider` как текущую реализацию по умолчанию.
- Добавить `LexicalSearchStrategy`, читающую `app.rag.lexical-provider`.
- Сохранить текущий `MaterialRetrievalService` и `HybridChunkRanker`, заменив только источник lexical matches.
- Добавить в лог retrieval имя lexical provider.

Почему эта фаза первой:
- Она дешёвая по риску.
- Она создаёт точку подключения для ES без переписывания retrieval semantics.

Тесты фазы:
- unit на strategy selection
- regression на active-only retrieval
- regression на `PARTIAL_READY`
- regression на `historical-only` поведение

Критерий завершения:
- При `lexical-provider=postgres` поведение и тесты полностью совпадают с текущими.

### Phase 2. Ввести search sync model и outbox
Цель: подготовить безопасную асинхронную доставку изменений в ES, не включая ES в runtime read path.

Объём:
- Добавить таблицу outbox для search sync, например `material_search_sync_events`.
- События должны описывать material lifecycle, а не только факт upload:
  - `UPSERT_SEARCHABLE`
  - `REMOVE_SEARCHABLE`
- Генерировать события в тех же транзакционных границах, где меняется searchability:
  - завершение индексации
  - reindex -> `PENDING`
  - delete active + promotion previous version
  - supersede/reactivation
- Вынести сбор payload для синка в отдельный сервис, который умеет по `materialId` восстановить актуальное searchable-состояние из Postgres.

Почему это отдельно:
- Так можно проверить корректность lifecycle-событий ещё до поднятия Elasticsearch.

Тесты фазы:
- unit на event emission
- integration на delete/promote/reindex lifecycle
- проверка, что duplicate/late events не ломают финальное searchable-state

Критерий завершения:
- Любое изменение searchability порождает предсказуемое событие в outbox.

### Phase 3. Поднять Elasticsearch sidecar и включить shadow indexing
Цель: начать наполнять ES, не влияя на production retrieval.

Объём:
- Поднять `Elasticsearch` с versioned index и aliases:
  - `rag-chunks-v1`
  - `rag-chunks-read`
  - `rag-chunks-write`
- Реализовать `ElasticsearchIndexSyncService`, который читает outbox и делает `bulk upsert/delete`.
- Индексировать один документ на searchable-чанк с минимально достаточными полями:
  - `chunkId`
  - `materialId`
  - `sourceKey`
  - `title`
  - `chunkText`
  - `page`
  - `extractor`
  - `ocrUsed`
  - `sourceType`
  - `updatedAt`
- Стартовать с BM25 + lowercase + synonym set; отдельно заложить место под русский analyzer, не меняя read semantics в той же фазе.
- Добавить `ElasticsearchHealthIndicator` или аналогичный сервис health/readiness для sync plane.

Важно:
- ES не должен быть hard dependency для upload, reindex и chat path.
- При падении ES outbox должен копиться, а Postgres path — продолжать работать.

Тесты фазы:
- integration с Testcontainers на bulk upsert/delete
- retry/backoff для failed sync
- idempotency на повторную доставку событий

Критерий завершения:
- ES индекс догоняет Postgres до консистентного searchable-state, но runtime retrieval всё ещё читает lexical matches из PostgreSQL.

### Phase 4. Shadow comparison и observability
Цель: проверить качество и стабильность до переключения production read path.

Объём:
- Добавить `ElasticsearchLexicalSearchProvider`, но использовать его только в shadow-mode.
- На части запросов выполнять двойной lexical retrieval:
  - production answer продолжает идти через Postgres
  - ES результаты пишутся в логи/метрики для сравнения
- Расширить `/api/health` и frontend-модель:
  - `searchStatus`
  - `searchProvider`
  - `searchReasonMessage`
  - `searchSyncBacklog`
- Не менять смысл `ragStatus`: если есть fallback на PostgreSQL, RAG не считается down только из-за ES.
- Собрать quality set для `ru/kz/en`, typo, synonym-heavy, OCR-noisy кейсов.

Тесты фазы:
- contract tests на новые поля health
- regression на frontend readiness/presentation
- quality comparison report: Postgres lexical vs Elasticsearch lexical

Критерий завершения:
- Есть фактические данные, что ES lexical path не хуже текущего по критичным сценариям и лучше хотя бы на части multilingual/typo/synonym кейсов.

### Phase 5. Включить Elasticsearch в production lexical path с безопасным fallback
Цель: начать использовать ES в реальном retrieval, не ломая существующий RAG.

Объём:
- Включить `app.rag.lexical-provider=auto` как рекомендуемый rollout mode.
- В `auto`:
  - использовать ES при `searchStatus=UP`
  - fallback на Postgres lexical path при деградации ES или отставании синка выше безопасного порога
- Добавить явные логи и метрики по причинам fallback.
- Сохранить semantic search в Postgres.

Тесты фазы:
- integration на `ES -> Postgres` fallback
- integration на reindex/delete/reactivation при включённом `auto`
- chaos-style тест: ES временно недоступен, RAG остаётся рабочим

Критерий завершения:
- Пользовательский RAG не теряет доступность при кратковременных сбоях ES.

### Phase 6. Отдельный quality track после стабилизации rollout
Цель: улучшать качество retrieval, не смешивая это с storage migration.

Объём:
- Вынести новый `ChunkingStrategy` и перейти к sentence/token-aware chunking.
- Откалибровать relevance gating вместо жёсткого `semanticDistance <= 0.72`.
- Рассмотреть optional `dense_vector/kNN` в Elasticsearch только после quality report и только как отдельную инициативу.
- Отдельно оценить ускорение query embeddings или caching, потому что ES не решает этот latency hotspot сам по себе.

Критерий завершения:
- Изменения качества измеряются отдельно от изменений инфраструктуры.

## Рекомендуемый порядок реализации

1. `Phase 1` — boundaries и provider strategy
2. `Phase 2` — outbox и lifecycle sync model
3. `Phase 3` — ES sidecar и shadow indexing
4. `Phase 4` — comparison и observability
5. `Phase 5` — production rollout с fallback
6. `Phase 6` — quality improvements отдельным треком

## Что даст такой порядок

- Каждая фаза оставляет систему в рабочем состоянии.
- Мы не смешиваем change of backend и change of retrieval quality в одном шаге.
- Уже существующие инварианты `ACTIVE-only`, `PARTIAL_READY`, `reindex`, `delete -> promote previous version` не теряются во время внедрения.
- При необходимости rollout можно остановить после любой из первых пяти фаз без деградации текущего продукта.

## Короткий вывод

Изначальное направление из `/Users/gleb-imac/Downloads/PLAN.md` в целом верное: Elasticsearch здесь полезен именно как sidecar lexical/hybrid слой. Но текущий код уже ушёл вперёд в сторону lineage/versioning, асинхронной индексации, RRF fusion и richer readiness plane, поэтому внедрение теперь нужно делать не одним большим "ES rollout", а через поэтапное подключение к уже существующим доменным инвариантам.
