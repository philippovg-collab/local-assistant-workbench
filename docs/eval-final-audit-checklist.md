# Eval Final Audit Checklist

Дата аудита: 2026-05-11
Цель: финальная проверка готовности Eval-направления по фазам 0-8 перед релизным решением.
Источник критериев: фазовый план `PLAN-9.md` и общий план `PLAN.md` из `Downloads`.
Проверяемая база: `c2f87cfd24dfbe95854d7b8e01ce17df7a6d40a9` + текущий dirty worktree фаз 0-8.

## Release Decision

**NOT_READY**

Релиз блокируется не отсутствием основной реализации, а двумя P1-разрывами в доказательности:

- E2E Eval пока не закрывает полный product-grade набор метрик и judge-output, заявленный для фазы.
- CI Eval gates сейчас проверяют fixture-отчеты, а не гарантированно исполняют реальные Eval runs/datasets как merge-proof.

Для всех P1 ниже указан обязательный follow-up: **Phase 10 - Eval release readiness closure**. До закрытия этих пунктов Eval можно считать feature-complete в значительной части, но не release-ready.

## Status Summary

| Phase | Status | Highest finding | Итог |
| --- | --- | --- | --- |
| 0. Time/version-aware retrieval hardening | PARTIAL | P2 | Основной путь получил reference time/version filters, но остался runtime fallback на `LocalDate.now`. |
| 1. Eval bounded context | PASS | - | Bounded context, миграции, контракты и boundary gate присутствуют. |
| 2. Snapshot reproducibility | PASS | P3 | Snapshot/config compatibility реализованы; стоит уточнить включение `gitCommitSha` в hash payload. |
| 3. Retrieval-only Eval | PASS | - | Preview/run path, deterministic scorer и retrieval metrics реализованы. |
| 4. E2E Eval | PARTIAL | P1 | Durable E2E path есть, но метрики/judge-output не соответствуют полной релизной планке. |
| 5. Dataset lifecycle | PASS | - | SMOKE/GOLDEN/CANDIDATE, review, versioning и promotion flow реализованы. |
| 6. Eval UI | PASS | P2 | UI/workbench flow есть; дальнейший рост требует split для anti-sprawl. |
| 7. CI gates | PARTIAL | P1 | Workflows/scripts есть, но CI proof пока fixture-driven. |
| 8. Search rollout hardening | PASS | P2 | Scripts/runbooks/proofs есть; runtime rollout evidence не генерировался в этом аудите. |

## Findings

### P1-EVAL-001 - E2E Eval scoring не закрывает полный релизный набор метрик

**Phase:** 4
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure

Evidence:

- `backend/src/main/java/com/example/demo/service/eval/EvalE2EScoringService.java`
- `backend/src/main/java/com/example/demo/service/eval/EvalE2ERunService.java`
- `backend/src/test/java/com/example/demo/service/eval/EvalE2EScoringServiceTest.java`

Observed:

- E2E execution path durable: run items, linked chat run, artifacts, parser output, citation resolver output, score summary and failure codes are persisted.
- Implemented metric names include `accepted_answer_match`, `required_gold_fact_coverage`, `citation_locator_match`, `forbidden_doc_violation`, `abstain_recall`, `abstain_precision`, `clarification_recall`, `clarification_precision`.
- Plan-level product metrics such as correctness, groundedness, completeness, usefulness and instruction adherence are not implemented as separate first-class scorer outputs.
- LLM judge output is effectively skipped when no judge is configured.

Risk:

- A release gate can pass without proving answer quality across the intended product dimensions.
- Future compare reports may look complete while measuring a narrower substitute metric set.

Required closure:

- Add explicit E2E metrics for the planned product dimensions.
- Persist judge/scorer output in run artifacts with clear skipped/configured states.
- Add tests that fail when required metric families are absent from E2E summaries and compare output.

### P1-CI-001 - CI Eval gates are fixture-driven, not real-run merge proof

**Phase:** 7
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure

Evidence:

- `.github/workflows/eval-smoke.yml`
- `.github/workflows/eval-golden.yml`
- `.github/workflows/eval-compare.yml`
- `scripts/eval-run.sh`
- `backend/src/test/java/com/example/demo/service/eval/EvalCiReportGenerationTest.java`
- `backend/src/test/java/com/example/demo/service/eval/EvalCiReportFixtures.java`
- `config/eval-gates.yml`
- `scripts/eval-gate.py`

Observed:

- Eval workflows, threshold config, report summary script and gate script are present.
- `scripts/eval-run.sh` delegates to `EvalCiReportGenerationTest`.
- `EvalCiReportGenerationTest` writes deterministic reports from `EvalCiReportFixtures`, not from real backend Eval runs over persisted datasets/snapshots.

Risk:

- CI can prove report parsing and threshold enforcement, but not that application changes preserved real retrieval/E2E behavior.
- Merge gate can be green while live Eval execution is broken or bypassed.

Required closure:

- Add a CI path that executes real SMOKE/GOLDEN eval datasets through backend services or API.
- Keep fixture tests as unit coverage, but separate them from release proof.
- Persist CI run artifacts with dataset version, snapshot/config hashes and compare IDs.

### P2-TIME-001 - Retrieval SQL builder retains runtime date fallback

**Phase:** 0
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure

Evidence:

- `backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java`
- `backend/src/main/java/com/example/demo/model/RetrievalFilters.java`
- `backend/src/main/java/com/example/demo/model/RetrievalQueryHints.java`
- `backend/src/main/java/com/example/demo/repository/PostgresMaterialFilterSqlBuilder.java`

Observed:

- Main retrieval execution carries `referenceInstant`, injected clock behavior and trace metadata.
- Retrieval filters include version label/effective date/version mode fields.
- `PostgresMaterialFilterSqlBuilder.defaultEffectiveDate()` still uses `LocalDate.now(ZoneOffset.UTC)` as fallback.

Risk:

- A future caller that omits pinned effective date can reintroduce non-reproducible date-dependent retrieval.

Required closure:

- Replace runtime fallback with explicit reference date propagation or a fail-closed contract for Eval/persistent paths.
- Add a test that persistent Eval retrieval cannot depend on wall-clock date.

### P2-OPS-001 - Search rollout evidence was not produced in this audit environment

**Phase:** 8
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure or operator UAT

Evidence:

- `docs/search-rollout.md`
- `docs/ops-runbook.md`
- `scripts/search-rollout.sh`
- `scripts/search-rollback-read-alias.sh`
- `backend/src/main/resources/elasticsearch/searchable-chunks-index.json`
- `backend/src/test/java/com/example/demo/search/SearchableChunkDocumentTest.java`

Observed:

- Rollout, rebuild, smoke, promote, rollback and status scripts exist.
- Strict mapping contract and mapping hash checks exist.
- Audit did not run Elasticsearch/alias-changing commands, so no fresh `backend/target/search-rollout/` evidence bundle was generated.

Risk:

- Implementation is present, but current operator evidence for this exact worktree is missing.

Required closure:

- Run rollout smoke in an environment with Elasticsearch.
- Attach generated alias/mapping evidence to the release record.

### P2-SPRAWL-001 - Eval UI/API files are near god-file territory

**Phase:** 6
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure or next UI refactor phase

Evidence:

- `frontend/src/components/eval/EvalTab.tsx`
- `frontend/src/api/client.ts`
- `frontend/src/hooks/useEvalRuns.ts`
- `frontend/src/hooks/useEvalDatasets.ts`
- `frontend/src/hooks/useEvalCompare.ts`

Observed:

- UI flow is functional and routed through hooks/API layer.
- `EvalTab.tsx` still owns several subviews/dialog/detail concerns.
- `frontend/src/api/client.ts` remains large and contains eval API surface together with the broader API client.

Risk:

- Further feature work can push Eval UI/API past maintainability gates even if behavior remains correct.

Required closure:

- Split Eval tab into dataset, runs, compare, start-dialog and run-detail components before adding more UI scope.
- Move eval API calls to a dedicated eval client module if the current API client grows further.

### P3-CONFIG-001 - `gitCommitSha` storage and config hash intent should be clarified

**Phase:** 2
**Status:** Open
**Follow-up:** Phase 10 - Eval release readiness closure

Evidence:

- `backend/src/main/java/com/example/demo/service/eval/EvalExecutionConfigService.java`
- `backend/src/main/java/com/example/demo/model/eval/EvalExecutionConfig.java`

Observed:

- `gitCommitSha` is stored on the execution config object.
- The audited config hash payload focuses on corpus snapshot, reference instant, material/search/runtime state, prompt version and revision pins.

Risk:

- If release policy expects config hash to vary by commit, two runs from different commits could share a config hash.

Required closure:

- Decide whether `gitCommitSha` is part of reproducibility identity or only metadata.
- If identity, include it in the hash payload and add a regression test.

## Phase Checklist

### Phase 0 - Time/version-aware retrieval hardening

Status: PARTIAL

Implemented evidence:

- `RetrievalFilters` carries version label, effective date, version mode, version state and upload time bounds.
- `RetrievalQueryHints` propagates version label into retrieval filters.
- `MaterialRetrievalService` uses execution request reference instant and records retrieval debug metadata.
- `TimeConfig` provides injectable clock behavior.

Remaining gap:

- `PostgresMaterialFilterSqlBuilder.defaultEffectiveDate()` still has a wall-clock fallback.

### Phase 1 - Eval bounded context

Status: PASS

Implemented evidence:

- Eval packages exist under `controller/eval`, `service/eval`, `infrastructure/eval`, `model/eval` and `service/eval/port`.
- Migrations `V49` to `V54` are present for Eval and related rollout state.
- `EvalController` exposes `/api/evals` endpoints.
- `ApiContractRegistry` includes Eval contracts and generated frontend types exist.
- `python3 scripts/architecture-boundary-gate.py` passed.

### Phase 2 - Snapshot reproducibility

Status: PASS

Implemented evidence:

- Snapshot/config services capture dataset version, case revision references, corpus/material/search/runtime state and reference instant.
- Compare blocks incompatible dataset/snapshot/material/search/config/prompt/judge combinations.
- Run artifacts include snapshot/config data used for reproducibility and compare.

Watch item:

- Clarify `gitCommitSha` hash identity expectation.

### Phase 3 - Retrieval-only Eval

Status: PASS

Implemented evidence:

- Retrieval preview/run endpoints exist under `/api/evals`.
- Persistent retrieval run uses snapshots/config/reference data.
- `RetrievalScoringService` includes document/evidence/table hit and recall metrics, MRR, NDCG, filter adherence, forbidden doc rate and retrieval sufficiency.
- `/api/search` remains outside Eval controller ownership.

### Phase 4 - E2E Eval

Status: PARTIAL

Implemented evidence:

- `EvalE2ERunService` executes durable E2E runs via chat-run coordination.
- Run items retain linked chat run, artifacts, parser result, citation resolution, score summary and failure codes.
- Structured-output parsing and E2E scoring tests exist.

Remaining gap:

- Product-grade metric families and judge-output are not fully represented as first-class required outputs.

### Phase 5 - Dataset lifecycle

Status: PASS

Implemented evidence:

- Dataset kinds include SMOKE, GOLDEN and CANDIDATE.
- Case schema validation, review workflow, candidate creation from chat run and promotion flow are implemented.
- Dataset versioning/revision tables and services exist.
- Promotion requires approved active candidate cases before moving into release datasets.

### Phase 6 - Eval UI

Status: PASS

Implemented evidence:

- Workbench exposes Eval tab.
- `EvalTab` covers datasets, runs, compare, start-run and run-detail flows.
- Hooks cover datasets, runs, compare, run detail and candidate promotion.
- Chat audit panel exposes candidate creation entrypoint.
- Frontend tests for Eval tab/hooks/API payloads pass.

Watch item:

- Split UI/API modules before adding substantial new Eval UI scope.

### Phase 7 - CI gates

Status: PARTIAL

Implemented evidence:

- Eval smoke/golden/compare workflows exist.
- Gate config and gate scripts exist.
- Report summary and threshold behavior are covered by tests.

Remaining gap:

- Release CI proof is currently fixture-driven instead of executing real Eval runs/datasets.

### Phase 8 - Search rollout hardening

Status: PASS

Implemented evidence:

- Search rollout and rollback scripts exist.
- Search rollout and ops runbook docs exist.
- Strict Elasticsearch mapping file uses `dynamic: "strict"`.
- Mapping hash/contract tests exist.
- Structured-v1 proof guard exists.
- Material lineage override migration, model, service behavior and runbook section exist.

Watch item:

- Run operator smoke to generate fresh rollout evidence before production release.

## Cross-Phase Anti-Sprawl And Boundary Check

Observed boundary result:

- `python3 scripts/architecture-boundary-gate.py` passed with existing baseline violations only.

Risky-size observations:

- `backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java` remains large.
- `backend/src/main/java/com/example/demo/repository/PostgresChatRunTraceRepository.java` remains very large.
- `frontend/src/components/ChatAuditPanel.tsx` remains large.
- `frontend/src/api/client.ts` remains very large.

Boundary-specific observations:

- No direct Eval imports were observed in core retrieval/chat services checked during audit.
- No direct `eval_` table access was observed in `PostgresChatRunTraceRepository`.
- Frontend Eval API access is routed through API/hook layers, not scattered across arbitrary UI files.

## Verification Run

| Command | Result | Notes |
| --- | --- | --- |
| `python3 scripts/architecture-boundary-gate.py` | PASS | Passed with existing baseline violations only. |
| `python3 scripts/test_eval_gate.py` | PASS | 5 tests passed. |
| `mvn -q -Dtest=EvalControllerContractTest,EvalDatasetLifecycleServiceTest,EvalCaseValidationServiceTest,CorpusSnapshotServiceTest,EvalComparisonServiceTest,RetrievalScoringServiceTest,EvalE2EScoringServiceTest,EvalStructuredOutputParserTest,EvalCiReportServiceTest,StructuredV1ProofServiceTest,SearchableChunkDocumentTest test` | PASS | Targeted backend Eval unit/controller/contract tests passed. |
| `npm --prefix frontend run test -- EvalTab useEvalRuns useEvalCandidatePromotion client` | PASS | 5 files, 30 tests passed. |
| `npm --prefix frontend run check:api-types` | PASS | Generated API type check passed. |
| `mvn -q -Dtest=ApiContractSchemaTest,DtoSerializationCompatibilityTest,RetrievalFiltersTest,MaterialRetrievalServiceTest,PostgresMaterialFilterSqlBuilderTest,PostgresMaterialRetrievalScopeSqlBuilderTest,EvalCiReportGenerationTest test` | PASS | Contract/retrieval/CI report tests passed. |
| `mvn -q -Dtest=EvalControllerContractTest,PostgresEvalRepositoriesIT,EvalDatasetLifecycleServiceTest,EvalCaseValidationServiceTest,CorpusSnapshotServiceTest,EvalComparisonServiceTest,RetrievalScoringServiceTest,EvalE2EScoringServiceTest,EvalStructuredOutputParserTest,EvalCiReportServiceTest,StructuredV1ProofServiceTest,SearchableChunkDocumentTest test` | BLOCKED | `PostgresEvalRepositoriesIT` requires Docker/Testcontainers; local Docker socket `/var/run/docker.sock` was unavailable. |

## Required Follow-Up Before Release

Phase 10 - Eval release readiness closure:

1. Close `P1-EVAL-001`: implement and gate full E2E product metric families plus judge/scorer artifact persistence.
2. Close `P1-CI-001`: replace release CI fixture proof with real Eval SMOKE/GOLDEN runs and compare artifacts.
3. Close or explicitly accept `P2-TIME-001`: eliminate wall-clock fallback from persistent Eval retrieval paths.
4. Generate Phase 8 operator evidence in an Elasticsearch-enabled environment.
5. Decide `gitCommitSha` hash semantics and add/adjust regression tests.

## Acceptance Criteria Check

| Criterion | Result |
| --- | --- |
| All phase statuses filled | PASS |
| No unfilled or unknown statuses | PASS |
| Release decision is one of allowed values | PASS: `NOT_READY` |
| No `BLOCKER`/`P1` without follow-up phase | PASS: both P1 findings point to Phase 10 |
| Final artifact written to `docs/eval-final-audit-checklist.md` | PASS |
