# Refactor Session Protocol

This protocol keeps the phased refactor small, reviewable, and measurable. A session is complete only when its scoped risk ids have evidence or an explicit stop note.

## Session Shape

- One session has one phase goal and one primary subsystem.
- Each session names the risk ids from `docs/refactor-risk-register.md` before code changes begin.
- The default result is a small PR-sized diff. If the work no longer fits a single phase goal, stop and split it.
- Do not mix behavior changes with opportunistic cleanup, formatting churn, or unrelated refactors.

## Required Checkpoint

Before editing production code, record:

- phase number and goal;
- risk ids in scope;
- planned touched modules;
- expected behavior changes or `none`;
- rollback plan;
- tests/gates expected at the end.

For docs-only phases, the checkpoint can live in the commit/PR body. For implementation phases, keep it in the phase notes, PR body, or issue.

## Scope Guardrails

- No unrelated fixes.
- No baseline growth in architecture, complexity, coverage, or review-contract allowlists.
- No new `legacy`, `fallback`, `rollout`, `bestEffort`, or compatibility path without reason, owner scenario, test coverage, and removal criterion.
- No public API change without contract test and migration note.
- No migration, deploy, auth, or data-shape change unless that risk id is explicitly in scope.
- Do not refactor files over 500 lines without characterization tests or a documented split plan.

## Context Manager Boundary

- Imported `Downloads/PLAN*.md` files are historical drafts. The maintained source of truth is `docs/context-manager-roadmap.md`.
- Context Manager code exists through migrations `V38`-`V47`; remaining work must preserve `/api/chat-runs` as canonical execution and use `app.context.*` flags for rollout/rollback.
- Revise `docs/context-manager-roadmap.md` before putting conversation history into `systemPrompt`, using `chat_audit_runs` as runtime state, growing `MaterialRetrievalService`, or growing `PromptPolicyResolver`.

## Stop Conditions

Stop and split the work when:

- touched files exceed the planned scope;
- more than three subsystems are affected;
- a public API or persistence shape change appears unexpectedly;
- a gate failure requires unrelated cleanup;
- a risk needs a product/security decision before implementation;
- a baseline entry would need to grow.

If a stop condition triggers, leave a short handoff note with current status, findings, and the next smallest safe step.

## Definition of Done

Every phase ends with:

- updated risk statuses or a note that all scoped risks remain open;
- tests/gates run, with skipped checks explained;
- rollback plan still valid;
- no baseline growth;
- docs/runbook updates when behavior, operations, or process changed.

## Phase Evidence Defaults

| Phase | Primary evidence |
| --- | --- |
| Phase 0 | Docs/process diff only; lightweight static gates. |
| Phase 1 | Architecture/static gates and baseline ownership proof. |
| Phase 2 | Error handler tests, service/provider/storage exception tests, architecture gate. |
| Phase 3 | DTO serialization/contract tests and frontend contract proof. |
| Phase 4 | RAG project contract tests, grouped-count repository test, query-count proof. |
| Phase 5 | Chat execution/cancellation tests and durable queue integration proof. |
| Phase 6 | Material ingestion/enrichment lifecycle tests and indexing requeue proof. |
| Phase 7 | Retrieval correctness tests, query-count proof, and `EXPLAIN` notes. |
| Phase 8 | Frontend hook/component tests, API contract proof, import boundary gate. |
| Phase 9 | Characterization tests before each file split and complexity baseline reduction. |
| Phase 10 | Security posture decision, security docs, and authorization tests if RBAC is selected. |

## Risk Status Updates

Update `docs/refactor-risk-register.md` only when evidence changes the state of a risk:

- move to `In progress` when a phase starts reducing it;
- move to `Closed` only after acceptance criteria and required proof are linked;
- move to `Deferred` only with an explicit product/security decision;
- keep `Open` when a phase only documents or scopes the risk.

## Review Expectations

Each non-trivial PR must include:

- `Risk IDs`;
- risk level and touched boundaries;
- test evidence or skipped-test rationale;
- anti-sprawl declaration;
- complexity delta for tracked or large files;
- rollback plan.

Docs-only PRs may use `Risk IDs: GOV-001` or the relevant register ids when they change process rules.
