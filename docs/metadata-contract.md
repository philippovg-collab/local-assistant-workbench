# Material Metadata Contract

Этот контракт фиксирует сохранность metadata между create, edit, version upload, reindex, storage и frontend. Phase 3 не добавляет новый продуктовый metadata editor; цель фазы - не терять уже существующие значения.

## Canonical Editable Fields

Обычные frontend create/edit/version flows отправляют только эти поля:

- `documentType`
- `workspaceKey`
- `documentStatus`
- `projectKey`
- `documentNumber`
- `languageCode`
- `manualTags`
- `periodStart`
- `periodEnd`

## Compatibility-Only Input Fields

Backend продолжает принимать эти поля для legacy payload/import сценариев. Frontend форма не обязана отправлять их без отдельного product decision:

- `knowledgeDocumentClass`
- `documentDate`
- `author`
- `department`
- `versionLabel`
- `language`
- `tags`
- `sourceTrust`
- `project`
- `counterparty`
- `businessStatus`

`businessStatus` не является alias для `documentStatus`. `project` не является alias для `projectKey` без reference lookup.

## Stored, Displayed, And Inferred Fields

`MaterialMetadataSnapshot` может хранить и отдавать:

- canonical editable fields;
- compatibility-only fields;
- `provenance`;
- `autoTags`;
- `effectiveTags`.

`manualTags` остаются отдельным слоем от `autoTags`; `effectiveTags` является display/retrieval union.

## Precedence Rules

Metadata собирается в таком порядке:

1. Explicit request metadata.
2. Existing stored manual metadata from current or previous active version.
3. Existing stored inferred/display metadata.
4. Resolver/parser inferred metadata.
5. Snapshot defaults.

Resolver не должен заменять meaningful stored value на `null`, пустое значение или default. Resolver может заполнить поле только если более приоритетные источники не дали значения.

## Reindex Preservation

Reindex использует сохраненную metadata текущей active версии как input для resolver. Он сохраняет canonical fields, compatibility/display fields, `sourceTrust`, language fields, manual tags and period fields. Resolver может обновить inferred enrichment, но не должен стирать stored/manual/display metadata default-значениями.

## Version Upload And Edit Preservation

Новая версия строит metadata из partial override поверх metadata предыдущей active версии, затем применяет resolver enrichment. Explicit non-empty override побеждает previous metadata. Unrelated stored fields сохраняются. Empty/null optional values do not clear meaningful stored values in Phase 3.

## Frontend Contract

`MaterialMetadataInput` в TypeScript соответствует только canonical editable fields. Compatibility fields живут в отдельном `CompatibilityMetadataInput` для контрактных тестов и legacy/import awareness. UI может отображать stored/display/inferred snapshot fields, но ordinary submit path не отправляет display-only fields случайно.
