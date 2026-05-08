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

Rollback by provider mode:

```bash
export APP_RAG_LEXICAL_PROVIDER=postgres
```

Alias rollback is manual: promote `rag-chunks-read` back to a previous prepared target. This does not clear the current write index and does not move `rag-chunks-write`.
