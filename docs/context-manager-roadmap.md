# Context Manager Roadmap

This document is the maintained source of truth for Context Manager release readiness. The imported `Downloads/PLAN*.md` files are historical drafts only; keep them as appendix/reference material and do not treat their migration numbers or phase gates as executable.

## Current State

Recorded on 2026-05-10, Asia/Almaty.

The repository already contains Context Manager runtime artifacts. The remaining work is release hardening, evidence, and default-on decision support rather than implementing the old phase set from scratch.

Actual migration order in this worktree:

| Migration | Scope |
| --- | --- |
| `V37__operator_audit_events.sql` | Operator audit baseline. |
| `V38__chat_conversation_backbone.sql` | Conversation and run binding backbone. |
| `V40__context_assembly_snapshots.sql` | Context assembly snapshots. |
| `V41__conversation_working_memory.sql` | Sticky conversation state. |
| `V42__retrieval_query_resolution_snapshots.sql` | Retrieval follow-up resolution snapshots. |
| `V43__conversation_summary_compaction.sql` | Conversation summaries and refresh jobs. |
| `V44__context_retention_cleanup_indexes.sql` | Retention cleanup indexes. |
| `V45__long_term_memory_review.sql` | Long-term memory review store. |
| `V46__context_release_hardening_constraints.sql` | Release hardening constraints. |
| `V47__context_assembly_status.sql` | Snapshot availability state on conversation runs. |

## Release Contract

- `POST /api/chat-runs` remains the canonical execution API.
- `POST /api/chat` remains compatibility-only; it must not become the Context Manager execution path.
- `chat_run_headers` remains the durable lifecycle source of truth.
- `chat_conversation_runs` binds conversation turns to durable runs and records context assembly availability; it does not own execution state.
- Context snapshots explain selected history, sticky state, summary, memory, retrieval rewrite, token budget, and dropped items.
- Conversation summary is continuity context, not prior RAG history for follow-up resolution.
- Long-term memory stays separate from material KB tables and is review-gated.
- Rollback is config-based; do not add destructive down migrations.

## Rollout Surface

Spring uses flat properties under `app.context.*`; environment variables use Spring relaxed binding:

| Env var | Spring property | Default | Notes |
| --- | --- | --- | --- |
| `APP_CONTEXT_ENABLED` | `app.context.enabled` | `false` | Master gate. |
| `APP_CONTEXT_CONVERSATIONS_ENABLED` | `app.context.conversations-enabled` | `false` | Conversation binding and UI thread actions. |
| `APP_CONTEXT_HISTORY_ENABLED` | `app.context.history-enabled` | `false` | Prior turn selection. |
| `APP_CONTEXT_STICKY_STATE_ENABLED` | `app.context.sticky-state-enabled` | `false` | Sticky model/scope/instruction state. |
| `APP_CONTEXT_RETRIEVAL_QUERY_RESOLUTION_ENABLED` | `app.context.retrieval-query-resolution-enabled` | `false` | Follow-up RAG rewrite. |
| `APP_CONTEXT_SUMMARY_ENABLED` | `app.context.summary-enabled` | `false` | Summary compaction and prompt inclusion. |
| `APP_CONTEXT_LONG_TERM_MEMORY_ENABLED` | `app.context.long-term-memory-enabled` | `false` | Memory extraction, review API, and prompt selection. |

`/api/health` publishes the effective feature state as `contextFeatures`. Frontend controls must use that response: hide memory review unless `contextFeatures.longTermMemory=true`, hide thread actions unless `contextFeatures.conversations=true`, and keep chat submission stateless when conversations are disabled.

## Remaining Release Plan

| Workstream | Required outcome |
| --- | --- |
| Source-of-truth docs | Roadmap, risk register, env examples, and runbook describe the implemented state and current rollout flags. |
| API and UI gates | `HealthResponse.contextFeatures`, `ConversationRunDetail.contextAssemblyStatus`, memory review gating, and stateless chat behavior are contract-tested. |
| Snapshot semantics | Runs distinguish `NONE`, `AVAILABLE`, and `EXPIRED`; inspector statuses distinguish `AVAILABLE`, `DEGRADED`, `EXPIRED`, `DISABLED`, and `NOT_AVAILABLE`. |
| Summary separation | Retrieval follow-up resolution only uses actual prior RAG turns, not synthetic summary context. |
| Release proof | Fast quality gate, targeted backend/frontend tests, Docker-backed context ITs, and full gate or documented CI-equivalent proof are attached before default-on. |

## Evidence

Focused checks from the hardening pass:

- Backend targeted context/memory/health tests: `mvn -q -Dtest=HealthControllerTest,MemoryEntryServiceTest,ContextAssemblyQueryServiceTest,RetrievalQueryResolutionServiceTest,ChatRunSubmissionCoordinatorTest,ConversationStickyStateServiceTest,MemoryCandidateExtractionServiceTest,MemorySelectorTest,HistorySelectorTest test`
- Backend API contract regeneration/check: `mvn -q -Dtest=ApiContractSchemaTest -Dapi.contract.write=true test`, then `mvn -q -Dtest=ApiContractSchemaTest test`
- Frontend contract and conversation tests: `npm test -- --run src/types.contract.test.ts src/api/client.test.ts src/hooks/useConversationChatExecution.test.tsx src/components/ConversationThreadPanel.test.tsx`
- Frontend shell/memory gating proof: `npm test -- --run src/hooks/useConversationChatExecution.test.tsx src/components/ConversationThreadPanel.test.tsx src/App.test.tsx`

Release still requires Docker-backed proof for `PostgresContextHardeningRepositoryIT` and `PostgresContextMaintenanceRepositoryIT`, plus `bash scripts/quality-gates.sh full` or exact CI-equivalent evidence.
