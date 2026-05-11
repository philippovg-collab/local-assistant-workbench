# Search Rollout

Elasticsearch is an opt-in lexical search plane. PostgreSQL remains the source of truth for materials, lineage, readiness, and embeddings.

Recommended production lexical mode after validation is `APP_RAG_LEXICAL_PROVIDER=auto`, where backend uses Elasticsearch when the search plane is healthy and falls back to PostgreSQL when it is degraded.

## Local Sidecar

```bash
./scripts/start-elasticsearch.sh
export APP_SEARCH_SYNC_ENABLED=true
export SPRING_ELASTICSEARCH_URIS=http://127.0.0.1:9200
```

For a protected Elasticsearch endpoint:

```bash
export SPRING_ELASTICSEARCH_USERNAME=elastic
export SPRING_ELASTICSEARCH_PASSWORD=<password>
```

For a security-enabled backend, set the admin credentials before `smoke`.
The smoke command logs in through `/api/auth/session` and `/api/auth/login`,
then reuses the session cookie for `/api/health` and `/api/search`.

```bash
export APP_BACKEND_URL=http://127.0.0.1:8080
export APP_SECURITY_ADMIN_USERNAME=admin
export APP_SECURITY_ADMIN_PASSWORD=<strong-admin-password>
```

With search sync enabled, backend manages a versioned index through aliases:

- `rag-chunks-write`
- `rag-chunks-read`

Startup creates the configured versioned index when needed, moves only the write alias, and does not automatically move an existing read alias.

## Release Checklist Before `auto`

1. Keep production defaults at `APP_SEARCH_SYNC_ENABLED=false` and `APP_RAG_LEXICAL_PROVIDER=postgres`.
2. Run Docker-backed proof:

```bash
./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT
```

3. Prepare the next index version.
4. Rebuild the write index from PostgreSQL source of truth.
5. Check `/api/health`: search plane `UP`, backlog `0`, failed `0`.
6. Run smoke search on the prepared read target.
7. Promote `rag-chunks-read`.
8. Enable `APP_RAG_LEXICAL_PROVIDER=auto`.

## Operator Steps

Unified rollout entrypoint:

```bash
./scripts/search-rollout.sh prepare v3
./scripts/search-rollout.sh rebuild
./scripts/search-rollout.sh requeue
./scripts/search-rollout.sh smoke "sample query"
# run eval compare against the pinned golden dataset and verify PASS
./scripts/search-rollout.sh promote v3
# monitor /api/health and application search traces
./scripts/search-rollout.sh rollback v2
./scripts/search-rollout.sh status
```

Copy-paste release sequence:

```bash
./scripts/search-rollout.sh prepare v3
./scripts/search-rollout.sh rebuild
./scripts/search-rollout.sh requeue
./scripts/search-rollout.sh smoke "pricing policy"
./scripts/eval-run.sh compare
./scripts/search-rollout.sh promote v3
./scripts/search-rollout.sh status
./scripts/search-rollout.sh rollback v2
```

`status`, `smoke`, `prepare`, `promote`, and `rollback` write JSON evidence under:

```text
backend/target/search-rollout/
```

Evidence captures command, arguments, timestamp, Elasticsearch URL, read/write aliases, before/after alias targets, the canonical index mapping hash, result, and error.

Prepare index:

```bash
./scripts/search-prepare-index.sh v3
```

This creates `rag-chunks-v3` when needed and moves only `rag-chunks-write`.

Rebuild the current write index:

```bash
./scripts/search-rebuild-write-index.sh
```

If only terminal failed sync events need replay:

```bash
./scripts/search-requeue-failed.sh
```

Shadow compare before cutover:

```bash
APP_RAG_LEXICAL_PROVIDER=postgres
APP_RAG_SHADOW_ENABLED=true
./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT
```

The comparison report is written to:

```text
backend/target/search-quality/phase4-shadow-report.md
```

Promote read alias:

```bash
./scripts/search-promote-read-alias.sh v3
```

Production smoke before and after promotion:

```bash
export APP_SECURITY_ADMIN_PASSWORD=<strong-admin-password>
./scripts/search-rollout.sh smoke "pricing policy"
```

The smoke command fails non-zero when Elasticsearch is unhealthy, read/write aliases are missing, the local mapping hash differs from `SEARCH_ROLLOUT_EXPECTED_MAPPING_HASH`, backend `/api/health` is unavailable, search sync has failed backlog entries, or the sample `/api/search` query returns no hits.

Rollback by provider mode:

```bash
export APP_RAG_LEXICAL_PROVIDER=postgres
```

Alias rollback:

```bash
./scripts/search-rollback-read-alias.sh v2
```

Alias rollback moves only `rag-chunks-read` back to a previous prepared target. It does not clear the current write index, does not move `rag-chunks-write`, and does not modify PostgreSQL data. If alias state is unsafe, use the provider-mode rollback above and restart the backend.

## Mapping Contract

`backend/src/main/resources/elasticsearch/searchable-chunks-index.json` is strict (`dynamic: "strict"`). Backend tests prove that every serialized `SearchableChunkDocument` field is present in the mapping and that Elasticsearch rejects unknown fields on a prepared index.

The canonical mapping hash is included in eval runtime/search snapshot metadata and in rollout alias evidence. Treat a mapping hash change as a search-state change that requires fresh smoke and eval comparison proof before promotion.

Golden datasets must not bulk-expand versioned documents before lineage override is available and populated for intentionally renamed or re-keyed material families. Versioned document expansion without explicit lineage override can make compare proof look stable while evaluating the wrong identity boundary.
