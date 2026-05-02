# Review Findings Closure

Phase 5 closes the original review findings with regression tests, static gates, or both. Residual risks are limited to real external dependencies: Docker/Testcontainers availability, Elasticsearch production rollout, and operator action.

| Finding id | Phase | Code area | Regression test / gate | Residual risk |
|---|---|---|---|---|
| RF-01 duplicate durable queue race | Phase 1 | Durable chat run queue lease ownership | `PostgresChatRunQueueRepositoryIT.claimSkipsRowsLockedByAnotherTransaction`, `PostgresChatRunQueueRepositoryIT.secondClaimDoesNotReturnAlreadyClaimedRun`, `PostgresChatRunQueueRepositoryIT.oldOwnerCannotDeleteAfterExpiredLeaseIsReclaimed` | Docker/Testcontainers availability for PostgreSQL IT proof |
| RF-02 guarded completion after cancellation/requeue | Phase 1 | Chat run terminal state machine and trace writes | `PostgresChatRunTraceRepositoryIT.cancelBeatsGuardedCompletion`, `PostgresChatRunTraceRepositoryIT.completionWithWrongLeaseTokenReturnsFalseAndWritesNoResult`, `PostgresChatRunTraceRepositoryIT.requeuedRunCanOnlyBeCompletedByCurrentLeaseOwner`, `ChatRunTraceServiceTest.completeRunWithResultReturnsFalseWhenAtomicTransitionIsRejected` | Docker/Testcontainers availability for PostgreSQL IT proof |
| RF-03 scoped retrieval must not pre-query O(corpus) UUIDs | Phase 2 | RAG retrieval scope propagation | `MaterialRetrievalServiceTest.passesFilteredMaterialSearchScopeIntoSearchLayers`; `scripts/phase5-static-gate.py` gate `retrieval-scope` | None |
| RF-04 empty scoped retrieval semantics | Phase 2 | RAG retrieval no-match short-circuit | `MaterialRetrievalServiceTest.zeroScopedReadyCountDoesNotCallEmbeddingOrSearchProviders` | None |
| RF-05 service layer must not depend on infrastructure | Phase 3 | Service/infrastructure boundary | `ServiceLayerBoundaryTest.serviceLayerDoesNotImportInfrastructure`; `scripts/phase5-static-gate.py` gate `service-boundary` | None |
| RF-06 legacy material god-object repository must stay removed | Phase 3 | Material PostgreSQL runtime topology | `MaterialRuntimeTopologyIT.resolvesRuntimePortsToDedicatedPostgresAdapters`, `MaterialRuntimeTopologyIT.excludesLegacyRuntimePathWhenLegacyImportIsDisabled`; `scripts/phase5-static-gate.py` gate `material-topology` | Docker/Testcontainers availability for runtime topology IT proof |
| RF-07 stale active RAG project must not trigger project-scoped calls | Phase 4 | Frontend RAG project resolution before Materials/RAG requests | `App.test.tsx` test `resolves inactive stored RAG project before material and RAG calls`; `App.test.tsx` test `does not start project-scoped calls before active RAG project resolution completes` | Operator action to keep at least one active default RAG project configured |
| RF-08 metadata aliases must not overwrite canonical fields | Phase 4 | Material metadata snapshot normalization | `MaterialMetadataSnapshotTest.keepsLegacyProjectSeparateFromCanonicalProjectKey`, `MaterialMetadataSnapshotTest.keepsLegacyBusinessStatusSeparateFromCanonicalDocumentStatus`; `scripts/phase5-static-gate.py` gate `metadata-aliases` | None |

## Release Hardening Gates

- Static anti-regression gate: `python3 scripts/phase5-static-gate.py`.
- Local review loop: `./scripts/review-local.sh`.
- Docker-backed ES release proof: `./scripts/test-backend.sh integration -Dit.test=ElasticsearchIndexSyncIT,ElasticsearchPhase4IT,ElasticsearchPhase5IT,ElasticsearchPhase5DownIT`.
- Rollout docs pin the current Elasticsearch index version to `v3` and keep PostgreSQL as source of truth.
