# Актуальный аудит реализации и остаточный план

Обновлено по состоянию текущего рабочего дерева на `2026-04-19`.

## Итоговая оценка

Исходный план больше не является активным backlog-документом: основная архитектурная и продуктовая часть закрыта. Текущий статус стоит считать не `80-85%`, а примерно `95%+` по исходному объёму.

Закрыты ключевые хвосты прошлого ревью:
- устойчивый material lineage через `material_lineage_identities`;
- инварианты `ACTIVE/SUPERSEDED` и single-active-version per lineage;
- удаление active-версии с promotion последней `SUPERSEDED`-версии;
- active-only retrieval, ready-count и historical-only readiness;
- frontend readiness/gating для пустой базы, indexing, historical-only и degraded состояний;
- edit/revision/restore flow для инструкций;
- material reindex, фильтры active/all/problematic и lineage view в UI;
- search/health observability для PostgreSQL/Elasticsearch lexical plane.

## Статус по направлениям

| Направление | Актуальность | Статус |
|---|---|---|
| Backend boundaries | `MaterialService` разделён на ingestion/query/retrieval/indexing контуры, repository interfaces разнесены | Done |
| Instruction lifecycle | create/update/delete/revisions/diff/restore реализованы, категории нормализованы | Done |
| Health/status plane | direct/LLM/embedding/RAG/knowledge/OCR/search статусы разделены; `/api/health` теперь refresh-ит search health | Done |
| Material lineage | source identity, active/superseded, promotion после удаления и active-only counts реализованы | Done |
| Frontend readiness | UI опирается на backend truth-model и различает active/history/indexing/degraded | Done |
| Search operations | reindex, rechunk-active, search sync recovery/rebuild scripts и operator runner присутствуют | Mostly done |

## Реальные остаточные доработки

1. **Production rollout Elasticsearch**
   - Оставить безопасные defaults: `app.search-sync.enabled=false`, `app.rag.lexical-provider=postgres`.
   - Перед включением `auto` прогнать Docker-backed ES tests из обычного shell/CI:
     `./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT`.
   - После успешного proof подготовить write index, rebuild, smoke search, затем promote read alias.

2. **Operator runbook**
   - Зафиксировать порядок использования:
     - `scripts/search-prepare-index.sh`
     - `scripts/search-rebuild-write-index.sh`
     - `scripts/search-requeue-failed.sh`
     - `scripts/search-promote-read-alias.sh`
   - Описать rollback: вернуть `app.rag.lexical-provider=postgres`, оставить ES sync включённым только для догоняющего shadow/rebuild режима.

3. **User-facing lineage override для файлов**
   - Текущий file lineage уже защищён от false-supersede по одному имени файла через `FILE_STEM_AND_CONTENT_ANCHOR`.
   - Остаётся продуктовая доработка: дать пользователю явное поле lineage/title override, если разные файлы должны считаться версиями одного документа несмотря на отличающийся content anchor.

4. **Production quality rollout**
   - `hybrid-rerank-v1` и quality reports уже есть.
   - `structured-v1` остаётся за rollout flag, поэтому включение для production-корпуса должно идти отдельным controlled rollout с quality report до/после.

## Актуальный порядок работ

1. Закрыть Elasticsearch release gate: strict mapping, health refresh, Docker-backed ES integration proof.
2. Обновить operator runbook и smoke checklist для VM.
3. Принять product decision по lineage override для файлов.
4. Отдельно включать `structured-v1`/quality improvements по результатам quality report.

## Проверки

Локальный non-Docker proof:
- `./scripts/test-backend.sh`
- `npm test` в `frontend`
- `./scripts/test-backend.sh fast -Dtest=Phase6RetrievalQualityIT`

Docker-backed proof для финального Elasticsearch rollout:
- `./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT`

## Короткий вывод

Базовый план выполнен. Оставшиеся задачи уже не про восстановление архитектурной целостности, а про production rollout Elasticsearch, операторскую документацию и продуктовый выбор вокруг явного lineage override.
