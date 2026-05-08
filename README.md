# KEGOC RAG

Локальная панель для работы с корпоративными материалами и локальной LLM.

Сервис позволяет загружать документы, индексировать их, задавать вопросы по базе знаний и видеть, какие источники попали в ответ. Отдельно есть прямой чат с моделью без поиска по материалам.

В штатном режиме все работает внутри инфраструктуры: PostgreSQL с `pgvector` хранит материалы и embeddings, Ollama запускает чат-модель и модель embeddings, а backend не ходит во внешние LLM API.

## Что умеет

- RAG-чат по загруженным материалам.
- Прямой чат с локальной моделью.
- Загрузка текстов, PDF и офисных документов.
- OCR для сканов через Tesseract (`kaz+rus+eng` по умолчанию).
- Библиотека инструкций и сценариев для запросов.
- RAG-проекты, пресеты знаний, фильтры и аудит запусков.
- Elasticsearch можно подключить отдельно для лексического поиска.

## Стек

- Frontend: React 18, Vite, TypeScript, Tailwind CSS, Radix UI.
- Backend: Java 21, Spring Boot 3.3, Maven.
- Хранилище: PostgreSQL 16 + pgvector.
- Локальные модели: Ollama.
- Парсинг документов: Apache Tika, PDFBox, Tesseract OCR.
- Опционально: Elasticsearch 8 для отдельного контура лексического поиска.

## Структура проекта

```text
backend/              Spring Boot API, миграции Flyway, логика RAG
frontend/             React-интерфейс администратора
scripts/              локальные скрипты запуска, проверки и обслуживания
scripts/linux/        упаковка, backup/restore и проверки для Linux deploy
docs/                 инструкции по эксплуатации
docker-compose.yml    серверный Docker Compose стек
.env.example          пример переменных окружения для Docker Compose
```

## Локальный запуск

Нужно заранее установить Java 21, Node.js/npm, Docker, Ollama и, если нужен OCR, Tesseract.

1. Поднять PostgreSQL с pgvector:

```bash
docker run --name ragstudio-postgres \
  -e POSTGRES_DB=ragstudio \
  -e POSTGRES_USER=ragstudio \
  -e POSTGRES_PASSWORD=ragstudio \
  -p 5432:5432 \
  -d pgvector/pgvector:pg16
```

Если PostgreSQL уже есть, достаточно создать базу `ragstudio` и включить расширение:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

2. Установить frontend-зависимости:

```bash
npm --prefix frontend install
```

3. Скачать модели Ollama:

```bash
./scripts/pull-model.sh qwen2.5:7b
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

Если `APP_SECURITY_ADMIN_PASSWORD` не задан, локальные скрипты генерируют случайный пароль на время текущего запуска. Логин и пароль можно явно переопределить через `APP_SECURITY_ADMIN_USERNAME` и `APP_SECURITY_ADMIN_PASSWORD`.

## Запуск через Docker Compose

Compose-файл предназначен для запуска проекта на одном сервере.

```bash
cp .env.example .env
```

Перед стартом обязательно заполнить в `.env`:

```dotenv
POSTGRES_PASSWORD=replace-with-a-strong-password
APP_SECURITY_ADMIN_PASSWORD=replace-with-a-strong-admin-password
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

Запуск базового контура:

```bash
docker compose up -d postgres ollama ollama-init backend frontend
```

Frontend nginx будет доступен на `127.0.0.1:8080`, если не переопределять `FRONTEND_BIND_ADDRESS` и `FRONTEND_HTTP_PORT`.

Подробная инструкция по серверному деплою лежит в [docs/deploy-linux-compose.md](docs/deploy-linux-compose.md).

## Документация

- [Architecture](docs/architecture.md) - runtime-модель, prompt policy, RAG flow и API contracts.
- [Ops Runbook](docs/ops-runbook.md) - локальный запуск, health checks, OCR, модели и типовые operator-сценарии.
- [Search Rollout](docs/search-rollout.md) - Elasticsearch sidecar, alias rollout, shadow checks и rollback.
- [Security](docs/security.md) - локальные учетные данные, compose hardening и authenticated diagnostics.
- [Linux Docker Compose Deployment](docs/deploy-linux-compose.md) - production baseline для одного Ubuntu-сервера.

## Основные настройки

Чаще всего понадобятся эти переменные:

```text
SPRING_DATASOURCE_URL          JDBC URL PostgreSQL
SPRING_DATASOURCE_USERNAME     пользователь PostgreSQL
SPRING_DATASOURCE_PASSWORD     пароль PostgreSQL
APP_LLM_BASE_URL               адрес Ollama
APP_LLM_MODEL                  чат-модель, по умолчанию qwen2.5:7b
APP_EMBEDDINGS_MODEL           модель embeddings, по умолчанию nomic-embed-text
APP_SECURITY_ADMIN_USERNAME    логин администратора
APP_SECURITY_ADMIN_PASSWORD    пароль администратора
APP_OCR_ENABLED                включить или отключить OCR
APP_RAG_LEXICAL_PROVIDER       postgres, auto или elasticsearch
APP_SEARCH_SYNC_ENABLED        синхронизация поискового индекса Elasticsearch
```

По умолчанию backend ожидает базу:

```text
jdbc:postgresql://127.0.0.1:5432/ragstudio
username: ragstudio
password: ragstudio
```

## Проверки

Backend:

```bash
./scripts/test-backend.sh fast
```

Frontend:

```bash
npm --prefix frontend run test
```

Проверка после деплоя через Docker Compose:

```bash
scripts/linux/preflight-compose.sh
```

## Полезные команды

```bash
./scripts/start-studio.sh              # обычный ручной запуск по частям
./scripts/stop-studio.sh               # остановить локальные backend/frontend/Ollama helper-скрипты
./scripts/chat.sh                      # быстрый запрос к локальному API
./scripts/local-runtime-diagnostics.zsh
```

Для Codex, agent и других non-interactive сред лучше использовать именно `./scripts/run-local-stack.sh`: он держит backend, frontend и Ollama под одним supervisor-процессом.
