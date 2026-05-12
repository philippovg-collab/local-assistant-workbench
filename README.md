# KEGOC RAG

## Что это

KEGOC RAG - внутренний single-admin RAG workbench для работы с корпоративными материалами и локальными или корпоративными OpenAI-compatible LLM endpoints.

Система позволяет загружать документы, извлекать из них текст, строить embeddings, выполнять гибридный поиск по базе знаний, запускать RAG-чат и прямой чат с моделью, а также проверять качество ответов через eval-контур. Проект рассчитан на deployment внутри контролируемой инфраструктуры: по умолчанию backend не требует внешних LLM API и может работать с Ollama, PostgreSQL и локальными моделями.

PostgreSQL с `pgvector` является source of truth для материалов, chunks, embeddings, очередей, результатов chat runs, audit traces, conversation/context данных и eval-артефактов. Elasticsearch, если включен, используется только как дополнительный lexical search plane.

## Что умеет

- RAG-чат по загруженным материалам с источниками, trace и контролируемым поведением при отсутствии контекста.
- Прямой чат с активной chat-моделью без retrieval.
- Загрузка текста, PDF и офисных документов.
- OCR для сканов через Tesseract, по умолчанию `kaz+rus+eng`.
- Версионирование материалов, lineage, metadata, tags и controlled replacement uploads.
- Асинхронная индексация chunks и embeddings с retry/backoff.
- Гибридный retrieval: semantic search через `pgvector` плюс lexical search через PostgreSQL или Elasticsearch.
- Библиотека инструкций, scenarios, knowledge presets, RAG projects и reference workspaces.
- Durable `chat-runs` lifecycle с queue, lease, cancellation, status, trace и result endpoints.
- UI-managed LLM providers для корпоративных OpenAI-compatible gateways.
- Context Manager, conversations, summaries и long-term memory как default-off feature surface.
- Eval-контур: datasets, cases, corpus snapshots, retrieval/e2e runs, compares и release evidence.
- Docker Compose deployment для одного сервера и локальные скрипты запуска/диагностики.

## Как работает в двух словах

Материалы:

```text
upload/text -> extraction/Tika/PDFBox/OCR -> normalization -> segments/chunks -> embeddings -> PostgreSQL READY
```

RAG-запрос:

```text
React UI -> POST /api/chat-runs -> PostgreSQL queue/lease -> prompt policy -> retrieval -> LLM -> trace/result
```

Retrieval:

```text
query -> pgvector semantic candidates + PostgreSQL/Elasticsearch lexical candidates -> hybrid fusion -> optional rerank -> top context chunks
```

## Архитектура

Runtime состоит из пяти основных слоев:

- **Frontend workbench**: React/Vite admin UI с вкладками overview, materials, instructions, RAG, direct, eval, settings и memory.
- **Spring Boot API**: REST controllers, validation, security, prompt assembly, chat execution, ingestion, retrieval, eval и health/readiness.
- **PostgreSQL + pgvector**: основное хранилище материалов, chunks, embeddings, Flyway schema, durable queues, traces, results, context и eval state.
- **LLM/Embedding providers**: Ollama по умолчанию или UI-managed OpenAI-compatible providers для chat и embeddings.
- **Optional search sidecar**: Elasticsearch 8 для lexical search rollout, shadow checks и rollback через alias-based flow.

Код разделен по границам:

- `com.example.demo.model` - публичные DTO, enum/value types и wire contracts.
- `com.example.demo.service.*` - use cases, prompt/retrieval/chat/material/eval/context логика и ports.
- `com.example.demo.infrastructure.*` - PostgreSQL, Elasticsearch, storage adapters и repository implementations.
- `frontend/src` - TypeScript UI, API clients, hooks, generated API types и workbench components.

Подробная runtime-модель описана в [docs/architecture.md](docs/architecture.md).

## Основные runtime-потоки

### Material ingestion

Backend принимает материал через JSON text flow или multipart upload. Текст извлекается через document extractors, при необходимости включается OCR, затем контент нормализуется, хэшируется, связывается с lineage/source key и сохраняется как material record. Structured chunks/segments пишутся в PostgreSQL, после чего `MaterialIndexingService` асинхронно получает embeddings и переводит материал в `READY` или `PARTIAL_READY`.

Если включена синхронизация поиска, material lifecycle также создает события для Elasticsearch search sync queue. При изменении embedding provider активные материалы могут быть поставлены на переиндексацию.

### RAG retrieval

Retrieval учитывает effective knowledge scope, metadata filters, query hints и rollout-флаги. Сервис сначала проверяет, есть ли active/ready материалы в выбранном scope. Затем выполняются semantic candidate search через `pgvector` и lexical candidate search через PostgreSQL full-text или Elasticsearch. Результаты объединяются hybrid ranker'ом, а при активном профиле `hybrid-rerank-v1` дополнительно переоцениваются reranker'ом.

В trace сохраняются counts, support verdict, applied/suppressed capabilities, active rollout flags, lexical provider, embedding model, chunk profile и retrieval config hash.

### Chat execution

Каноничный production API:

```text
POST /api/chat-runs
GET  /api/chat-runs/{id}/status
GET  /api/chat-runs/{id}/trace
GET  /api/chat-runs/{id}/result
POST /api/chat-runs/{id}/cancel
```

`POST /api/chat-runs` возвращает `202 Accepted` и URL для status/trace/result. Worker забирает run из durable PostgreSQL queue по lease, продлевает lease heartbeat'ом, собирает prompt, выполняет retrieval при `RAG`, вызывает LLM и сохраняет immutable result. При cancel или потере lease выполнение останавливается контролируемо.

`POST /api/chat` оставлен как deprecated compatibility endpoint для старых клиентов. Он создает durable run и ждет ограниченное время. Если run еще выполняется, клиент получает `408 chat.run_still_processing` с URL durable status/result. Timeout не отменяет run.

### Prompt policy

Prompt собирается из base system prompt, runtime instructions, temporary instruction, answer mode и, для RAG, grounding rules. Категории `SYSTEM` и `SAFETY` попадают в system message; `CONTEXT` и `USER` попадают в user message. Retrieved context передается модели как untrusted source text, чтобы документы не могли подменить инструкции.

`request.model` может переопределить модель. Если активирован UI-managed chat provider, default model и endpoint берутся из выбранного provider. Если provider не активирован, используется env fallback из `APP_LLM_*`.

### Context Manager

Context Manager default-off и включается только через `app.context.*` / `APP_CONTEXT_*` флаги. Когда включены соответствующие flags, backend поддерживает conversations, recent history, sticky state, retrieval query resolution, summaries и long-term memory. Effective state виден в `/api/health.contextFeatures`.

### Eval subsystem

Eval-контур хранит datasets, cases, reviews, versions, corpus snapshots, retrieval runs, e2e runs, run item artifacts и compares. Он нужен для проверки качества retrieval/ответов при изменениях chunking, prompt policy, search provider, reranker, metadata filters или LLM provider.

## Security model

KEGOC RAG - internal single-admin tool, а не multi-user или tenant-isolated сервис.

Все бизнес-API под `/api/**` требуют `ROLE_ADMIN`, кроме:

- `GET /api/liveness`;
- `/api/auth/**`.

`workspaceKey`, `projectKey`, RAG projects, reference workspaces, knowledge presets, instruction scopes и filters используются для организации материалов, retrieval и prompt selection. Они не являются authorization boundaries и не должны трактоваться как tenant isolation.

Для production-like запуска обязательно задаются:

```dotenv
APP_SECURITY_ADMIN_PASSWORD=<strong-admin-password>
POSTGRES_PASSWORD=<strong-postgres-password>
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

UI-managed LLM provider API keys шифруются с помощью `APP_LLM_PROVIDER_SECRET_KEY`. Если этот secret не задан, сохранять providers с API key нельзя: запросы с secret-полями отклоняются fail-closed.

Подробности: [docs/security.md](docs/security.md) и [docs/adr/0010-single-admin-security-boundary.md](docs/adr/0010-single-admin-security-boundary.md).

## Стек

- Frontend: React 18, Vite, TypeScript, Tailwind CSS, Radix UI, lucide-react, Vitest.
- Backend: Java 21, Spring Boot 3.3.4, Maven, JDBC, Spring Security, Flyway.
- Storage: PostgreSQL 16, `pgvector`, HNSW vector index, PostgreSQL full-text search.
- LLM: Ollama или OpenAI-compatible chat completions endpoint.
- Embeddings: OpenAI-compatible embeddings endpoint, по умолчанию `nomic-embed-text`.
- Document parsing: Apache Tika, PDFBox, Tesseract OCR.
- Optional search: Elasticsearch 8.
- Runtime/deploy: Docker Compose, nginx frontend image, local shell scripts.

## Структура репозитория

```text
backend/                  Spring Boot API, Flyway migrations, RAG/eval/context logic
frontend/                 React/Vite admin workbench
frontend/src/generated/   API types generated from backend contract artifact
docs/                     Architecture, ops, security, rollout and release docs
docs/adr/                 Architectural decisions
scripts/                  Local run, diagnostics, gates and rollout scripts
scripts/linux/            Linux packaging, backup/restore and compose preflight
config/                   Eval gate configuration
artifacts/                Local/generated evidence artifacts
docker-compose.yml        Local/server Compose baseline
docker-compose.prod.yml   Hardened production-oriented Compose variant
.env.example              Compose env template
```

## Локальный запуск

Нужно заранее установить Java 21, Node.js/npm, Docker, Ollama и, если нужен OCR, Tesseract с language packs.

1. Поднять PostgreSQL с `pgvector`:

```bash
docker run --name ragstudio-postgres \
  -e POSTGRES_DB=ragstudio \
  -e POSTGRES_USER=ragstudio \
  -e POSTGRES_PASSWORD=ragstudio \
  -p 5432:5432 \
  -d pgvector/pgvector:pg16
```

Если PostgreSQL уже есть, создайте базу `ragstudio` и включите расширение:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

2. Установить frontend-зависимости:

```bash
npm --prefix frontend install
```

3. Скачать baseline-модели:

```bash
./scripts/pull-model.sh qwen2.5:7b
./scripts/pull-model.sh deepseek-r1:8b
./scripts/pull-model.sh nomic-embed-text
```

4. Запустить локальный стек:

```bash
./scripts/run-local-stack.sh
```

После старта:

```text
Frontend: http://127.0.0.1:5173
Backend:  http://127.0.0.1:8080
Login:    admin / пароль, показанный startup-скриптом
```

Для повторяемого локального логина можно явно задать:

```bash
export APP_SECURITY_ADMIN_PASSWORD=<strong-local-password>
./scripts/run-local-stack.sh
```

Для Codex, agents, PTY и других non-interactive сред используйте именно `./scripts/run-local-stack.sh`: он держит Ollama, backend и frontend под одним supervisor-процессом.

## Docker Compose deployment

Compose-файл предназначен для запуска проекта на одном сервере:

```bash
cp .env.example .env
```

Перед стартом обязательно заполните:

```dotenv
POSTGRES_PASSWORD=<strong-postgres-password>
APP_SECURITY_ADMIN_PASSWORD=<strong-admin-password>
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

Запуск базового контура:

```bash
docker compose up -d postgres ollama ollama-init backend frontend
```

Production baseline рассчитан на same-origin доступ через frontend nginx. Backend, PostgreSQL и Elasticsearch не публикуются наружу. Frontend nginx доступен на `127.0.0.1:8088` при значениях из `.env.example`; порт можно переопределить через `FRONTEND_BIND_ADDRESS` и `FRONTEND_HTTP_PORT`.

Elasticsearch запускается отдельно через Compose profile `search` и используется только при включенном search rollout. Подробности: [docs/search-rollout.md](docs/search-rollout.md).

Полная инструкция по серверному деплою: [docs/deploy-linux-compose.md](docs/deploy-linux-compose.md).

## Основные настройки

Чаще всего используются:

```text
SPRING_DATASOURCE_URL          JDBC URL PostgreSQL
SPRING_DATASOURCE_USERNAME     пользователь PostgreSQL
SPRING_DATASOURCE_PASSWORD     пароль PostgreSQL
APP_LLM_BASE_URL               адрес env fallback chat provider
APP_LLM_MODEL                  chat-модель, по умолчанию qwen2.5:7b
APP_LLM_EXTRA_MODELS           дополнительные chat-модели для Ollama init
APP_LLM_PROVIDER_SECRET_KEY    ключ шифрования UI-managed provider API keys
APP_EMBEDDINGS_MODEL           embeddings-модель, по умолчанию nomic-embed-text
APP_EMBEDDINGS_EXPECTED_DIMENSION ожидаемая размерность embedding-вектора
APP_SECURITY_ADMIN_USERNAME    логин администратора
APP_SECURITY_ADMIN_PASSWORD    пароль администратора
APP_OCR_ENABLED                включить/отключить OCR
APP_RAG_LEXICAL_PROVIDER       postgres, auto или elasticsearch
APP_SEARCH_SYNC_ENABLED        синхронизация Elasticsearch search plane
APP_CONTEXT_ENABLED            master-флаг Context Manager
APP_CONTEXT_CONVERSATIONS_ENABLED
APP_CONTEXT_HISTORY_ENABLED
APP_CONTEXT_STICKY_STATE_ENABLED
APP_CONTEXT_RETRIEVAL_QUERY_RESOLUTION_ENABLED
APP_CONTEXT_SUMMARY_ENABLED
APP_CONTEXT_LONG_TERM_MEMORY_ENABLED
```

Корпоративные OpenAI-compatible endpoints добавляются в UI через `Настройки -> LLM подключения`. Chat и Embeddings активируются отдельно. Для rollback можно вернуться на env fallback.

Длинные таблицы context flags, health fields и provider workflow вынесены в [docs/ops-runbook.md](docs/ops-runbook.md).

## Проверки и quality gates

Backend fast gate:

```bash
./scripts/test-backend.sh fast
```

Frontend tests:

```bash
npm --prefix frontend run test
```

Общий быстрый gate перед PR:

```bash
bash scripts/quality-gates.sh fast
```

Для изменений в backend, migrations, queues, metadata, search, security или deploy используйте полный gate:

```bash
bash scripts/quality-gates.sh full
```

Проверка после Docker Compose deployment:

```bash
scripts/linux/preflight-compose.sh
```

Подробнее: [docs/quality-gates.md](docs/quality-gates.md).

## Документация

- [docs/architecture.md](docs/architecture.md) - runtime-модель, prompt policy, RAG flow, API contracts и package boundaries.
- [docs/ops-runbook.md](docs/ops-runbook.md) - локальный запуск, diagnostics, models, providers, health и operator scenarios.
- [docs/security.md](docs/security.md) - single-admin security model, credentials, CORS, provider secrets и audit redaction.
- [docs/chat-lifecycle.md](docs/chat-lifecycle.md) - durable chat run lifecycle.
- [docs/metadata-contract.md](docs/metadata-contract.md) - material metadata contract.
- [docs/search-rollout.md](docs/search-rollout.md) - Elasticsearch sidecar, alias rollout, shadow checks и rollback.
- [docs/deploy-linux-compose.md](docs/deploy-linux-compose.md) - production baseline для Ubuntu/Docker Compose.
- [docs/eval-release-evidence.md](docs/eval-release-evidence.md) - eval release evidence.
- [docs/context-manager-roadmap.md](docs/context-manager-roadmap.md) - Context Manager roadmap.

## Важные нюансы эксплуатации

- `/api/models` и UI model selector показывают только chat-модели. Embeddings-модель намеренно не предлагается для chat completion.
- `APP_SECURITY_ADMIN_PASSWORD` должен быть задан явно для production-like запуска.
- `APP_CORS_ALLOWED_ORIGINS` должен содержать конкретные origins; wildcard не подходит для credentialed session cookies.
- `APP_LLM_PROVIDER_SECRET_KEY` нужно задать до сохранения provider API keys через UI.
- Context Manager остается default-off до включения соответствующих `APP_CONTEXT_*` flags и проверки `/api/health.contextFeatures`.
- Search API и Elasticsearch sync включаются rollout-флагами; без них PostgreSQL остается основным retrieval backend.
- Workspaces, projects, presets и scopes помогают фильтровать корпус, но не изолируют данные между пользователями.
- При смене active embedding provider материалы могут потребовать reindex.
- Generated frontend API types обновляются из backend contract artifact через `npm --prefix frontend run generate:api-types`.
