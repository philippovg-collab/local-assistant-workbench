# Refactor Risk Register

This register is the source of truth for the phased refactor work. Phase 0 does not fix production behavior; it freezes the review findings as scoped work items so later phases can reduce risk without opportunistic cleanup.

## Current Baseline

- Phase 0 Context Manager readiness evidence is recorded in `docs/context-manager-roadmap.md`.
- Architecture boundary gate: `python3 scripts/architecture-boundary-gate.py` passes with 5 baseline violations tracked in V2 metadata.
- Complexity budget gate: `python3 scripts/complexity-budget-gate.py` passes with 12 over-budget baseline entries.
- Static anti-regression gate: `python3 scripts/phase5-static-gate.py` passes with 15 checks.
- Review contract: `python3 scripts/review-contract.py --skip-body` passes; the local diff currently touches 58 high-risk paths and contains 5 anti-sprawl keyword hits.

## Status Rules

- `Open`: risk is accepted into the refactor ledger and still needs owner-phase proof.
- `In progress`: owner phase has started and the risk is being reduced behind tests/gates.
- `Closed`: acceptance criteria are met and proof is linked.
- `Deferred`: the risk remains valid, but the current product decision explicitly accepts it.

## CTX-001 — Context Manager release proof is incomplete

- Status: Open
- Severity: P0
- Type: Release risk
- Evidence:
  - `docs/chat-lifecycle.md:3` defines `POST /api/chat-runs` as the canonical execution API.
  - `docs/architecture.md:90` lists durable chat-run endpoints as the production execution flow.
  - `docs/context-manager-roadmap.md` records implemented Context Manager artifacts through `V47` and keeps default-on blocked pending full proof.
- Consequence:
  - Context Manager could be enabled without Docker-backed repository proof, full quality gate evidence, or exact CI-equivalent evidence.
- Owner phase:
  - Context Manager hardening/release.
- Not in scope:
  - Do not turn Context Manager default-on in this pass.
- Acceptance criteria:
  - `POST /api/chat-runs` remains canonical and `/api/chat` remains compatibility-only.
  - All effective `APP_CONTEXT_*` rollout controls are documented in env examples and runbook.
  - Backend and frontend targeted context tests are green.
  - Docker-backed context repository ITs are green.
  - `bash scripts/quality-gates.sh full` is green or release notes link exact CI-equivalent proof.
- Required proof:
  - `bash scripts/quality-gates.sh fast`
  - Targeted Maven context/memory tests.
  - Frontend contract/conversation tests.
  - `PostgresContextHardeningRepositoryIT`
  - `PostgresContextMaintenanceRepositoryIT`
  - Full quality gate or documented blocker with CI replacement.

## CTX-002 — Long-term memory can be exposed while disabled

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - Memory review endpoints exist under `/api/memory-entries`.
  - Frontend exposes a Memory workspace tab.
  - `app.context.long-term-memory-enabled` is default-off.
- Consequence:
  - Operators could review or mutate memory while the feature is disabled, or the frontend could call disabled endpoints.
- Owner phase:
  - Context Manager hardening.
- Not in scope:
  - Do not remove memory tables or change memory review payload shape.
- Acceptance criteria:
  - Backend returns `memory.disabled` before repository access when effective long-term memory is false.
  - Frontend hides/disables memory review unless `contextFeatures.longTermMemory=true`.
  - Frontend does not call `/api/memory-entries` while disabled.
- Required proof:
  - `MemoryEntryServiceTest`
  - Frontend memory gating test.

## CTX-003 — Snapshot absence semantics can mislead operators

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - Context inspector previously could label any missing snapshot for an existing run as expired.
  - Runs need to distinguish snapshots that were never created from snapshots deleted by retention.
- Consequence:
  - Operators cannot tell disabled/not-created context from retention-expired context.
- Owner phase:
  - Context Manager hardening.
- Not in scope:
  - Do not restore deleted snapshots or add destructive down migrations.
- Acceptance criteria:
  - `chat_conversation_runs.context_assembly_status` records `NONE`, `AVAILABLE`, or `EXPIRED`.
  - Snapshot save marks `AVAILABLE`.
  - Retention marks affected runs `EXPIRED` before deleting snapshots.
  - Inspector/API distinguishes `EXPIRED`, `DISABLED`, and `NOT_AVAILABLE`.
- Required proof:
  - `ContextAssemblyQueryServiceTest`
  - `PostgresContextMaintenanceRepositoryIT`
  - `PostgresContextHardeningRepositoryIT`

## CTX-004 — Summary context can pollute RAG follow-up resolution

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - Conversation summary is synthetic continuity context, not an actual prior user/assistant RAG turn.
  - Follow-up resolution uses prior RAG turns and source traces.
- Consequence:
  - A summary-only context could be treated as a prior RAG prompt and produce unsupported source references.
- Owner phase:
  - Context Manager hardening.
- Not in scope:
  - Do not remove summaries from prompt assembly.
- Acceptance criteria:
  - Summary context is synthetic with no prior run id or is represented separately.
  - Retrieval follow-up resolution ignores summary-only context.
- Required proof:
  - `RetrievalQueryResolutionServiceTest`

## CTX-005 — Context feature state can drift between backend and frontend

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - Context features are controlled server-side by `app.context.*`.
  - Frontend needs feature state to hide memory and thread actions safely.
- Consequence:
  - UI controls could offer unavailable actions or send persistence fields when conversations are disabled.
- Owner phase:
  - Context Manager hardening.
- Not in scope:
  - Do not add a new execution endpoint or duplicate feature flag config in frontend builds.
- Acceptance criteria:
  - `/api/health` publishes effective `contextFeatures`.
  - Frontend uses `contextFeatures.conversations` for thread/sidebar actions and stateless submit behavior.
  - Frontend uses `contextFeatures.longTermMemory` for memory review visibility and calls.
- Required proof:
  - `HealthControllerTest`
  - `useConversationChatExecution.test.tsx`
  - `App.test.tsx`

## ARCH-001 — Layering collapse through service/infrastructure/API imports

- Status: Open
- Severity: P0
- Type: Confirmed defect
- Evidence:
  - `scripts/architecture-boundary-baseline.json`: V2 baseline entries link current backend boundary debt to risk ids, owner phases, reasons, and removal criteria.
  - `backend/src/main/java/com/example/demo/service/ChatExecutionService.java:3` imports `com.example.demo.api.ApiException`.
  - `backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialCatalogAdapter.java:6` imports `com.example.demo.api.ApiException`.
  - Gate result: `Architecture boundary gate passed (48 baseline violation(s)).`
- Consequence:
  - Application and infrastructure layers remain coupled to HTTP/API concerns, making error cleanup and module splits riskier.
- Owner phase:
  - Phase 1, with reductions continuing in Phase 2 where error-model imports are removed.
- Not in scope:
  - Do not change public API payloads, HTTP status mapping, migrations, or runtime behavior while tightening the gate.
- Acceptance criteria:
  - Architecture baseline is categorized by owner/target phase and cannot grow.
  - New service/infrastructure imports of API classes fail the gate.
- Required proof:
  - `python3 scripts/architecture-boundary-gate.py`
  - `python3 scripts/phase5-static-gate.py`
  - Targeted tests for any moved imports.

## ARCH-002 — Public model depends on service internals

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `backend/src/main/java/com/example/demo/model/MaterialSearchHit.java:3` imports `com.example.demo.service.material.DocumentBlockType`.
  - `backend/src/main/java/com/example/demo/model/MaterialSearchHitNeighbor.java:3` imports `com.example.demo.service.material.DocumentBlockType`.
  - `backend/src/main/java/com/example/demo/model/ChatSource.java:3` imports `com.example.demo.service.material.DocumentBlockType`.
- Consequence:
  - Public DTOs leak service package types, so service decomposition can silently become a wire-contract change.
- Owner phase:
  - Phase 3, with Phase 1 gate ownership if the import remains temporarily allowlisted.
- Not in scope:
  - Do not rename JSON fields or alter serialized enum values without a contract migration note.
- Acceptance criteria:
  - Public DTOs no longer import service-internal material types.
  - Serialization compatibility is explicitly tested.
- Required proof:
  - DTO serialization/contract tests.
  - `python3 scripts/architecture-boundary-gate.py`
  - Relevant frontend contract/type tests.

## ERR-001 — Mixed API and application exception model

- Status: Open
- Severity: P0
- Type: Confirmed defect
- Evidence:
  - `backend/src/main/java/com/example/demo/service/ApplicationException.java:3` defines an application exception model.
  - `backend/src/main/java/com/example/demo/api/ApiExceptionHandler.java:41` maps `ApplicationException`.
  - `backend/src/main/java/com/example/demo/service/MaterialIngestionService.java:18` still imports `ApiException`.
  - `backend/src/main/java/com/example/demo/llm/OllamaLlmClient.java:3` still imports `ApiException`.
  - `backend/src/main/java/com/example/demo/infrastructure/reference/PostgresReferenceDataRepository.java:4` imports `ApplicationException` from infrastructure.
- Consequence:
  - HTTP concerns, application failures, provider failures, and storage failures cannot be reasoned about independently.
- Owner phase:
  - Phase 2.
- Not in scope:
  - Do not change externally visible error codes/messages/statuses except through documented compatibility tests.
- Acceptance criteria:
  - `ApiException` is limited to API/controller mapping code.
  - Service/provider/storage failures use typed application/provider/storage exceptions.
  - Chat trace reason mapping preserves typed failure codes.
- Required proof:
  - `ApiExceptionHandlerTest`
  - Service tests updated away from `HttpStatus` assertions.
  - Trace/state-machine tests for failure reason codes.
  - `python3 scripts/architecture-boundary-gate.py`

## CHAT-001 — Chat execution god-service and legacy audit residue

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `scripts/complexity-budget-baseline.json` tracks `backend/src/main/java/com/example/demo/service/ChatExecutionService.java` at 902 lines.
  - `backend/src/main/java/com/example/demo/service/ChatExecutionService.java:37` defines the orchestration service.
  - `backend/src/main/java/com/example/demo/service/ChatExecutionService.java:60` still receives `ChatAuditService`.
  - `backend/src/main/java/com/example/demo/service/ChatAuditService.java:17` remains a runtime service.
- Consequence:
  - Chat orchestration, prompt assembly, retrieval, LLM execution, trace recording, and legacy audit concerns are hard to change independently.
- Owner phase:
  - Phase 5.
- Not in scope:
  - Do not rewrite durable queue semantics or remove compatibility endpoints while splitting responsibilities.
- Acceptance criteria:
  - Chat execution responsibilities are split behind characterization tests.
  - Legacy audit execution residue is either removed or explicitly retained with owner/removal criteria.
- Required proof:
  - `ChatExecutionServiceTest`
  - `ChatRunExecutionServiceTest`
  - Relevant chat-run repository integration tests.
  - `python3 scripts/complexity-budget-gate.py`

## CHAT-002 — Cancellation is durable in DB but provider abort is process-local

- Status: Open
- Severity: P1
- Type: Accepted limitation
- Evidence:
  - `backend/src/main/java/com/example/demo/service/ChatRunExecutionService.java:117` exposes cancellation.
  - `backend/src/main/java/com/example/demo/service/ChatRunExecutionService.java:178` keeps running cancellation handles in local memory.
  - `backend/src/main/java/com/example/demo/service/cancellation/ChatCancellationHandle.java:50` documents cancellation callbacks as best-effort cleanup hooks.
  - `backend/src/main/java/com/example/demo/service/ChatRunStateMachine.java:104` persists cancelled terminal state.
- Consequence:
  - Multi-instance state integrity can be DB-safe while provider transport abort only works for the process that owns the active run.
- Owner phase:
  - Phase 5.
- Not in scope:
  - Do not introduce distributed cancellation infrastructure unless Phase 5 explicitly chooses it.
- Acceptance criteria:
  - Semantics are documented: terminal state is durable; provider abort is best-effort unless the same worker owns the transport.
  - Expensive chat steps check cancellation between phases.
  - Completed work cannot overwrite `CANCELLED`.
- Required proof:
  - Two-worker lease/cancel tests.
  - `complete-after-cancel` regression tests.
  - LLM same-process abort test.

## CHAT-003 — Deprecated `/api/chat` lacks telemetry-based removal path

- Status: Open
- Severity: P2
- Type: Accepted limitation
- Evidence:
  - `backend/src/main/java/com/example/demo/controller/ChatController.java:26` keeps the compatibility controller.
  - `docs/architecture.md:85` documents `POST /api/chat` as compatibility-only.
  - `docs/architecture.md:87` says removal depends on client usage confirming no remaining legacy callers.
  - No telemetry counter or usage ledger is currently linked to that removal condition.
- Consequence:
  - The endpoint may remain indefinitely because deprecation has no measurable exit signal.
- Owner phase:
  - Phase 5.
- Not in scope:
  - Do not remove `/api/chat` in a refactor-only phase.
- Acceptance criteria:
  - Compatibility calls are counted/logged in an operator-visible way.
  - Removal criteria are stated in docs/runbook and test-covered.
- Required proof:
  - `ChatControllerContractTest`
  - Usage metric/log assertion or documented operator query.
  - `python3 scripts/phase5-static-gate.py`

## MAT-001 — Material auto-tagging is non-durable best-effort mutation

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `docs/ops-runbook.md:203` documents material auto-tag enrichment as best-effort follow-up.
  - `backend/src/main/java/com/example/demo/service/MaterialIngestionService.java:871` schedules auto-tagging.
  - `backend/src/main/java/com/example/demo/service/MaterialIngestionService.java:881` runs enrichment on an executor.
  - `backend/src/main/java/com/example/demo/service/MaterialIngestionService.java:884` logs rejected executor tasks while material remains saved.
  - `backend/src/main/java/com/example/demo/service/MaterialIngestionService.java:927` mutates metadata and requeues indexing after enrichment.
- Consequence:
  - Enrichment can be lost on executor rejection, crash, or restart, and operators cannot inspect task state.
- Owner phase:
  - Phase 6.
- Not in scope:
  - Do not change save/upload response shape until the durable enrichment lifecycle is specified.
- Acceptance criteria:
  - Auto-tagging has durable task state with attempts, retry timing, and failure reason.
  - Metadata is updated only through an observable lifecycle service.
  - Search reindexing is triggered exactly once after successful enrichment.
- Required proof:
  - Executor rejection/recovery tests.
  - Manual tag preservation tests.
  - Indexing requeue tests.

## MAT-002 — Language and metadata parsing are duplicated

- Status: Open
- Severity: P2
- Type: Confirmed defect
- Evidence:
  - `backend/src/main/java/com/example/demo/service/MaterialMetadataMergePolicy.java:299` parses language hints.
  - `backend/src/main/java/com/example/demo/model/MaterialMetadataSnapshot.java:482` parses legacy language codes separately.
  - `frontend/src/utils/materialMetadata.ts:69` maintains frontend language labels.
  - `frontend/src/utils/materialMetadata.ts:288` formats language display separately.
- Consequence:
  - Metadata normalization can drift across ingestion, legacy snapshots, and frontend presentation.
- Owner phase:
  - Phase 6.
- Not in scope:
  - Do not collapse user-facing language labels into backend-only canonicalization without product review.
- Acceptance criteria:
  - One backend canonical parser/normalizer owns language code mapping.
  - Frontend display uses backend-owned codes and only owns labels/presentation.
  - Legacy metadata migration paths are covered.
- Required proof:
  - Backend metadata resolver/snapshot tests.
  - Frontend material metadata tests.
  - Contract tests for metadata payload shape.

## RAG-001 — Reference/RAG project boundary and material counts are mixed

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `backend/src/main/java/com/example/demo/service/RagProjectService.java:64` calls `countMaterialsByWorkspace`.
  - `backend/src/main/java/com/example/demo/service/RagProjectService.java:65` calls `countReadyMaterialsByWorkspace`.
  - `backend/src/main/java/com/example/demo/infrastructure/reference/PostgresReferenceDataRepository.java:194` counts materials inside the reference repository.
  - `backend/src/main/java/com/example/demo/infrastructure/reference/PostgresReferenceDataRepository.java:212` counts ready materials inside the reference repository.
- Consequence:
  - Reference data ownership, RAG project summaries, and material read-model concerns are coupled and may scale as `1 + 2N` queries.
- Owner phase:
  - Phase 4.
- Not in scope:
  - Do not rename public `RagProject`/`ReferenceWorkspace` concepts until a single terminology decision is made.
- Acceptance criteria:
  - Project summary counts move to a read model or material-owned query path.
  - List summaries use bounded SQL, not per-project count calls.
  - Existing UI behavior is preserved.
- Required proof:
  - `/api/rag-projects` contract tests.
  - Repository integration test for grouped counts.
  - Query-count or performance-style assertion.

## PERF-001 — Retrieval rerank can load chunks per material

- Status: Open
- Severity: P1
- Type: Hypothesis
- Evidence:
  - `backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java:463` passes `chunkingRepository::findChunks` into source mapping.
  - `backend/src/main/java/com/example/demo/service/RetrievalResultMapper.java:107` uses `computeIfAbsent(materialId, chunkingRepository::findChunks)`.
  - `backend/src/main/java/com/example/demo/service/material/port/MaterialChunkingRepository.java:11` exposes only `findChunks(String materialId)`.
- Consequence:
  - Retrieval/rerank paths may issue one chunk query per material in the candidate set.
- Owner phase:
  - Phase 7.
- Not in scope:
  - Do not change ranking output while only proving query count.
- Acceptance criteria:
  - Retrieval loads chunks in a bounded/bulk way for candidate material ids.
  - Ranking output changes, if any, are explicitly expected and tested.
- Required proof:
  - Retrieval query-count/performance smoke.
  - Existing retrieval tests with filters.
  - `EXPLAIN` or logged query proof where applicable.

## PERF-002 — Hot-path SQL predicates may not match indexes

- Status: Open
- Severity: P1
- Type: Hypothesis
- Evidence:
  - `backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialFilterSqlBuilder.java:24` filters `LOWER(m.document_number)`.
  - `backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialFilterSqlBuilder.java:27` filters `LOWER(COALESCE(m.project_key, ''))`.
  - `backend/src/main/java/com/example/demo/infrastructure/material/PostgresMaterialFilterSqlBuilder.java:127` filters `LOWER(COALESCE(m.workspace_key, ''))`.
  - `backend/src/main/resources/db/migration/V34__material_retrieval_scope_indexes.sql:30` defines a functional document-number index.
  - `backend/src/main/resources/db/migration/V19__backfill_material_scope_columns.sql:17` defines a lower workspace index.
- Consequence:
  - Functional predicates and indexes may diverge, forcing scans or non-obvious plans in retrieval filters.
- Owner phase:
  - Phase 7.
- Not in scope:
  - Do not add indexes without `EXPLAIN` proof and migration review.
- Acceptance criteria:
  - Each hot filter predicate has a matching index strategy or a documented reason it is not indexed.
  - Query plans are captured for project/workspace/language/tag filters.
- Required proof:
  - `EXPLAIN ANALYZE` notes for representative filters.
  - Repository tests for filtered retrieval correctness.
  - Migration review for any index changes.

## FE-001 — Frontend manual API contract drift via `types.ts`

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `scripts/complexity-budget-baseline.json` tracks `frontend/src/types.ts` at 867 lines.
  - `frontend/src/api/client.ts:241` exports the manual API client surface.
  - `frontend/src/types.ts` is the current frontend contract source rather than generated/shared backend-owned schema.
- Consequence:
  - Backend API shape can drift from frontend assumptions without a schema-owned contract proof.
- Owner phase:
  - Phase 8.
- Not in scope:
  - Do not generate/replace all frontend types without a staged backend contract decision.
- Acceptance criteria:
  - Backend-owned schema or generated/shared contract becomes the source of truth for API types.
  - Frontend tests compare generated/shared types against expected payloads.
- Required proof:
  - Frontend API/client contract tests.
  - Backend schema/serialization proof.
  - `npm --prefix frontend run build`

## FE-002 — Components can bypass hooks and call `apiClient` directly

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `frontend/src/App.tsx:3` imports `apiClient`.
  - `frontend/src/components/RagChatPanel.tsx:2` imports `apiClient`.
  - `frontend/src/components/ChatAuditPanel.tsx:3` imports `apiClient`.
  - `frontend/src/components/materials/MaterialEditDialog.tsx:3` imports `apiClient`.
  - `frontend/src/components/DirectChatPanel.tsx:2` imports `apiClient` for curl preview only.
- Consequence:
  - Network lifecycle and cache/polling/error semantics can spread into components instead of hooks/state layer.
- Owner phase:
  - Phase 8.
- Not in scope:
  - Do not break preview-only `buildCurlExample` usage until the hook/static rule distinguishes non-network helpers.
- Acceptance criteria:
  - Components call hooks/query layer for network reads/mutations.
  - Static gate blocks new direct component `apiClient` network calls.
  - Preview/helper-only exceptions are explicit and tested.
- Required proof:
  - Frontend hook/component tests.
  - Static import gate.
  - `npm --prefix frontend run test:coverage`

## FE-003 — Cancel-on-unmount is an implicit server-side mutation

- Status: Open
- Severity: P1
- Type: Confirmed defect
- Evidence:
  - `frontend/src/hooks/useChatExecution.ts:70` cancels the current durable run during local abort cleanup.
  - `frontend/src/hooks/useChatExecution.ts:162` cancels the current durable run before new execution cleanup.
  - `frontend/src/hooks/useChatExecution.test.tsx:815` tests cancellation on unmount.
  - `frontend/src/hooks/useChatExecution.test.tsx:838` asserts `cancelChatRun` on unmount.
- Consequence:
  - Route remounts or component lifecycle changes can mutate server-side durable run state without explicit user intent.
- Owner phase:
  - Phase 8.
- Not in scope:
  - Do not remove user-initiated cancellation controls.
- Acceptance criteria:
  - User cancel cancels the durable run.
  - Route unmount only aborts local polling unless product explicitly chooses auto-cancel.
  - Tests cover both policies.
- Required proof:
  - `useChatExecution` hook tests.
  - Component tests for explicit cancel UI.
  - Backend chat-run cancel contract tests remain green.

## SEC-001 — Workspaces/projects are filters, not authorization boundaries

- Status: Open
- Severity: P1
- Type: Accepted limitation
- Evidence:
  - `docs/security.md:21` documents `/api/chat-runs/**` as admin-only because traces expose sensitive content.
  - `backend/src/main/java/com/example/demo/config/SecurityConfig.java:66` restricts chat runs to admin role.
  - Workspace/project filters are used throughout material/RAG flows, but no per-workspace user authorization model exists.
- Consequence:
  - The app is acceptable as an internal single-admin tool, but workspace/project keys must not be treated as multi-tenant security boundaries.
- Owner phase:
  - Phase 10.
- Not in scope:
  - Do not retrofit RBAC inside earlier refactor phases.
- Acceptance criteria:
  - Product posture is explicit: internal single-admin or multi-user/RBAC roadmap.
  - If RBAC is chosen, ownership and authorization are enforced in service layer with negative tests.
- Required proof:
  - Security/config tests.
  - Docs/security update.
  - Authorization negative tests if Phase 10 chooses RBAC.

## GOV-001 — Gates pass while preserving known debt

- Status: Open
- Severity: P0
- Type: Confirmed defect
- Evidence:
  - `python3 scripts/architecture-boundary-gate.py` passes with 61 V2 baseline violations.
  - `python3 scripts/complexity-budget-gate.py` passes with 17 over-budget baseline entries.
  - `scripts/architecture-boundary-baseline.json` requires risk id, owner phase, reason, and removal criterion for each allowed architecture/frontend boundary violation.
  - `scripts/complexity-budget-baseline.json` allows known god-files such as `ChatExecutionService.java`, `MaterialIngestionService.java`, `WorkbenchShell.tsx`, and `frontend/src/types.ts`.
- Consequence:
  - A green gate can be misread as a clean architecture unless baseline debt is tracked by risk id and owner phase.
- Owner phase:
  - Phase 1 for gate ownership; Phase 9 for complexity burn-down.
- Not in scope:
  - Do not expand baseline files or re-label new violations as existing debt.
- Acceptance criteria:
  - Every baseline entry has owner/target phase or is linked to a risk.
  - PRs reference risk ids for non-trivial work.
  - Complexity and architecture baselines shrink monotonically when owner phases close risks.
- Required proof:
  - `python3 scripts/architecture-boundary-gate.py`
  - `python3 scripts/complexity-budget-gate.py`
  - Review contract/PR template evidence.
