# Аудит Реализации И План Следующих Доработок

## Что было проверено
- Исходный план из `/Users/gleb-imac/Downloads/PLAN.md`.
- Backend и frontend код в текущем репозитории.
- Автотесты:
  - `backend`: `mvn -Dmaven.repo.local=/tmp/codex-m2 test`
  - `frontend`: `npm test`

## Итоговая оценка реализации

### Общая картина
- План реализован в существенной степени.
- По состоянию на проверку можно считать закрытыми примерно `80-85%` исходных изменений.
- Наиболее хорошо закрыты: instruction contract, transport/polling, разделение material-репозиториев, runtime probes.
- Наиболее заметные хвосты: knowledge lifecycle вокруг `source_key`, поведение при удалении активной версии материала, несколько UI-веток всё ещё опираются на общее число материалов, а не на число активных версий.

### Степень реализации по разделам
| Раздел | Статус | Оценка |
|---|---|---:|
| 1. Архитектурные границы backend | Частично завершён | 85% |
| 2. Instruction-модель и контракт | Практически завершён | 95% |
| 3. Health и status plane | В основном завершён | 85% |
| 4. Knowledge lifecycle и frontend transport | Частично завершён | 70% |

## Детальная сверка с исходным планом

### 1. Выпрямить архитектурные границы backend
- Сделано: `MaterialService` больше не содержит ручной composition path и работает через `MaterialQueryService`, `MaterialIngestionService`, `MaterialRetrievalService`.
- Сделано: `MaterialRepository` разрезан на `MaterialCatalogRepository`, `MaterialSearchRepository`, `MaterialIndexingQueueRepository`.
- Сделано: production-реализация сведена к `PostgresMaterialRepository`.
- Сделано: `FileMaterialRepository` переведён в legacy-reader роль и используется импортёром, а не как runtime store.
- Частично сделано: появились `@SpringBootTest`/IT-тесты, но часть unit-тестов всё ещё собирает сервисы вручную вокруг in-memory зависимостей.

### 2. Довести instruction-модель до реального контракта
- Сделано: backend enum `InstructionCategory` со значениями `system`, `user`, `context`, `safety`.
- Сделано: миграция `V5__instruction_category_contract.sql` с нормализацией старых значений и DB check constraint.
- Сделано: `PromptPolicyResolver` разделяет `system`, `safety`, `context`, `user` по разным блокам и по-разному применяет их в `DIRECT` и `RAG`.
- Сделано: frontend использует те же значения категорий.
- Сделано: batch-load инструкций с сохранением порядка и валидацией UUID/unknown ids на backend boundary.
- Остался хвост: нет отдельного edit/update сценария для существующих инструкций, только create/delete/read.

### 3. Пересобрать health и status plane
- Сделано: `RuntimeReadinessService` больше не полагается на `listModels()` и использует активные probes через `chat(...)` и `embed(...)`.
- Сделано: `HealthController` разделяет `status`, `directStatus`, `ragStatus`, `ocrStatus`, а OCR не валит общий backend status.
- Сделано: response shape расширена обратно-совместимо.
- Сделано: frontend polling для `useHealth` и `useModels` раз в `15s`, для `useMaterials` раз в `5s`, пока есть активная индексация.
- Сделано: добавлен общий derived readiness helper `frontend/src/utils/readiness.ts`.
- Частично сделано: не все UI-ветки используют derived readiness одинаково; часть условий в `App.tsx` всё ещё смотрит на общее число материалов.

### 4. Исправить knowledge lifecycle и frontend transport
- Сделано: `source_key` и `version_state` появились в runtime, БД и API.
- Сделано: при новой активной версии старые активные версии переводятся в `SUPERSEDED`.
- Сделано: retrieval и ready-count отфильтрованы по `ACTIVE`.
- Сделано: `MaterialSummary` и frontend знают про `versionState`.
- Сделано: единый frontend base URL resolver использован и для `fetch`, и для `curl` preview.
- Сделано: `useChatExecution` сбрасывает stale response на новом submit и хранит `lastSubmittedRequest`.
- Частично сделано: версия источника всё ещё определяется эвристикой по `title/originalFileName`, а не устойчивым source identity.
- Частично сделано: lifecycle удаления активной версии не закрыт.
- Частично сделано: нет отдельного regression-контура на version lifecycle, удаление активной версии и восстановление предыдущей версии.

## Найденные пробелы, которые стоит считать следующей очередью работ

### 1. Stabilize Material Lineage
Цель: сделать versioning детерминированным и безопасным.

Задачи:
- Ввести более устойчивый `source_key` contract.
- Для file-материалов строить ключ не только из имени файла.
- Добавить явный lineage-identity для text/file материалов.
- При удалении `ACTIVE`-версии либо:
  - промотировать последнюю `SUPERSEDED`-версию обратно в `ACTIVE`,
  - либо явно помечать lineage как archived/empty и корректно отражать это в retrieval/UI.
- Добавить API/репозиторный метод для подсчёта `activeMaterialsCount`, а не только общего `countMaterials()`.

Критерии готовности:
- Два разных документа с одинаковым именем файла не supersede друг друга.
- Удаление активной версии не оставляет систему в ложном состоянии "индекс ещё строится".
- Есть интеграционные тесты на create -> supersede -> delete active -> fallback/promotion.

### 2. Align RAG Readiness UX
Цель: чтобы UI везде говорил правду про активную knowledge base.

Задачи:
- Перевести `App.tsx` empty-state и helper logic на `ragReadiness.activeMaterialsCount`.
- Блокировать или явно деградировать RAG UI, если активных материалов нет, даже если в каталоге есть только `SUPERSEDED`.
- Добавить отдельный текстовый сценарий для состояния "есть история версий, но нет активной версии".
- Синхронизировать overview, materials и rag helper по одной модели derived readiness.

Критерии готовности:
- При наличии только `SUPERSEDED`-материалов UI не предлагает RAG как готовый сценарий.
- Helper text различает:
  - пустую базу,
  - только исторические версии,
  - индексацию активной версии,
  - готовый активный контекст.

### 3. Strengthen Versioning Test Contour
Цель: закрепить knowledge lifecycle тестами, а не договорённостями.

Задачи:
- Добавить backend tests на:
  - false-supersede сценарии,
  - promotion/archival после удаления active версии,
  - retrieval только по active lineage,
  - корректные ready counts для active-only модели.
- Добавить frontend tests на helper/gating при only-superseded состоянии.
- Добавить один bean-graph smoke test, подтверждающий production topology без legacy runtime path.

Критерии готовности:
- Все critical lifecycle переходы покрыты unit + integration слоями.
- Нельзя незаметно сломать version lifecycle без падения тестов.

### 4. Functional Follow-up Roadmap
Цель: после стабилизации фундамента наращивать полезный продуктовый функционал.

Задачи:
- Добавить `retry indexing`/`reindex` action для `FAILED` и `PARTIAL_READY`.
- Добавить редактирование инструкций.
- Добавить фильтры по материалам:
  - только активные,
  - показать исторические,
  - только проблемные.
- Добавить lineage view:
  - текущая активная версия,
  - предыдущие версии,
  - причина supersede,
  - timestamps.
- Добавить более явную observability по runtime:
  - последний успешный probe,
  - причина degraded state,
  - индексирующая очередь и попытки.

Критерии готовности:
- Оператор может сам восстановить знания, переиндексировать материал и понять, почему RAG недоступен.

## Приоритетный порядок следующих фаз
1. `Phase A`: Stabilize Material Lineage
2. `Phase B`: Align RAG Readiness UX
3. `Phase C`: Strengthen Versioning Test Contour
4. `Phase D`: Functional Follow-up Roadmap

## Короткий вывод
- Основа новой архитектуры уже внедрена и подтверждается тестами.
- Исходный план в целом реализован хорошо.
- Дальнейшие доработки стоит строить не вокруг новых экранов, а вокруг доведения knowledge lifecycle до надёжного состояния.
