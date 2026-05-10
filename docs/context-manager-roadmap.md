# Context Manager Roadmap

This document is the maintained source of truth for Context Manager release readiness. The imported `Downloads/PLAN*.md` files are historical drafts only; keep them as appendix/reference material and do not treat their migration numbers or phase gates as executable.

## Phase 0 Rebaseline

Recorded on 2026-05-10, Asia/Almaty.

Phase 0 for Context Manager is now a source-of-truth and evidence rebaseline only. It is not a pre-implementation phase and must not add production code, migrations, API/codegen output, or frontend behavior changes.

The historical `Downloads/PLAN-0.md` body is stale after its banner. In particular, its old claims that the last migration is `V36`, that conversation/context APIs are absent, and that new context DTOs are forbidden as future work are no longer valid for this worktree. Context Manager artifacts already exist through `V37`-`V48`; future work must start from this roadmap, `docs/refactor-risk-register.md`, `docs/refactor-session-protocol.md`, and `docs/context-manager-release-evidence.md`.

## Phase 1 Conversation Backbone Rebaseline

Recorded on 2026-05-10, Asia/Almaty.

Phase 1 source of truth is `V38__chat_conversation_backbone.sql`. The historical Phase 1 draft that referred to `V37` as the conversation backbone is stale: `V37__operator_audit_events.sql` is operator audit, and `V47__context_assembly_status.sql` later adds the context assembly availability status to conversation runs.

Phase 1 is not a new execution surface. `POST /api/chat-runs` remains the only submit endpoint, and there must be no `/api/conversations/{id}/messages` execution endpoint. Conversation endpoints are administrative/thread state APIs gated by effective `app.context.enabled && app.context.conversations-enabled`; disabled conversational submit returns `context.disabled` before enqueue. Stateless chat stays stateless: no `chat_conversations` or `chat_conversation_runs` writes, no `persistConversation`, `conversationId`, or `clientTurnId` in frontend submit payloads when `contextFeatures.conversations=false`.

Phase 1 proof is tracked by `CTX-006` in `docs/refactor-risk-register.md` and evidence entries `CM-E09` through `CM-E12` in `docs/context-manager-release-evidence.md`.

## Phase 2 Context Assembly Trace Rebaseline

Recorded on 2026-05-10, Asia/Almaty.

Phase 2 source of truth is `V40__context_assembly_snapshots.sql` plus the `V47__context_assembly_status.sql` availability column on `chat_conversation_runs`. Historical draft references to `V38` as the snapshot migration are stale; `V38` is the Phase 1 conversation backbone only.

`GET /api/chat-runs/{id}/context` remains the only context inspector endpoint. It uses stable payloads for known conversation runs:

- known run with an available snapshot returns `AVAILABLE` or `DEGRADED`;
- known run with `context_assembly_status=NONE` returns `NOT_AVAILABLE`;
- known run whose snapshot was removed by retention returns `EXPIRED`;
- known run while context is disabled returns `DISABLED`;
- unknown run remains an API `404`, and the frontend renders `NOT_FOUND` rather than `EXPIRED`.

`ChatExecutionResponse.contextAssemblyId`, `ChatExecutionResponse.contextSummary`, and `ConversationRunDetail.contextAssemblyStatus` are part of the Phase 2+ contract proof. Phase 2 does not add sticky, resolver, summary, or memory behavior; later-phase code can exist, but this phase proves bounded recent history, snapshot trace, and stateless safety.

Phase 2 proof is tracked by `CTX-007` in `docs/refactor-risk-register.md` and evidence entries `CM-E13` through `CM-E15` in `docs/context-manager-release-evidence.md`.

## Current State

Recorded on 2026-05-10, Asia/Almaty.

The repository already contains Context Manager runtime artifacts. The remaining work is release hardening, evidence, and default-on decision support rather than implementing the old phase set from scratch.

Actual migration order in this worktree:

| Migration | Scope |
| --- | --- |
| `V37__operator_audit_events.sql` | Operator audit baseline. |
| `V38__chat_conversation_backbone.sql` | Conversation and run binding backbone. |
| `V39__llm_provider_configs.sql` | LLM provider configuration store. |
| `V40__context_assembly_snapshots.sql` | Context assembly snapshots. |
| `V41__conversation_working_memory.sql` | Sticky conversation state. |
| `V42__retrieval_query_resolution_snapshots.sql` | Retrieval follow-up resolution snapshots. |
| `V43__conversation_summary_compaction.sql` | Conversation summaries and refresh jobs. |
| `V44__context_retention_cleanup_indexes.sql` | Retention cleanup indexes. |
| `V45__long_term_memory_review.sql` | Long-term memory review store. |
| `V46__context_release_hardening_constraints.sql` | Release hardening constraints. |
| `V47__context_assembly_status.sql` | Snapshot availability state on conversation runs. |
| `V48__chat_conversation_terminal_statuses.sql` | Internal terminal conversation statuses for retention purge. |

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

The release evidence manifest is `docs/context-manager-release-evidence.md`.

As of the Phase 0 rebaseline:

- `CTX-002` through `CTX-005` have focused proof and are closed in `docs/refactor-risk-register.md`.
- `CTX-006` has Phase 1 conversation backbone proof and is closed in `docs/refactor-risk-register.md`.
- `CTX-007` has Phase 2 context assembly trace and recent-history proof and is closed in `docs/refactor-risk-register.md`.
- Phase 0 durable chat and frontend/API compatibility proof is green in evidence entries `CM-E16` and `CM-E17`.
- `CTX-001` remains open because `bash scripts/quality-gates.sh fast` is blocked by dirty allowlisted-file growth in `PostgresMemoryRepository.java`, `RuntimeReadinessService.java`, `LlmProviderSettingsPanel.tsx`, and `MemoryReviewPanel.tsx`, and `bash scripts/quality-gates.sh full` or exact CI-equivalent evidence is still required before default-on.
- `Downloads/PLAN-0.md` remains a historical draft; do not expand its old phase list into an executable backlog.
