# Eval Phase 8 Review

Date: 2026-05-11
Scope: Phase 8 review/proof pass over phases 0-8
Source plans: `/Users/gleb-imac/Downloads/PLAN-8.md` and `/Users/gleb-imac/Downloads/PLAN-.md`

## Verdict

**LOCAL GO, CI PENDING**

No open Blocker or Should-fix remains after the Phase 8 review. The first full release gate exposed three release-blocking issues; all three were fixed during this pass and the full gate was rerun successfully.

Release acceptance is still pending GitHub Actions on this exact diff and an operator search-rollout smoke against a backend started with the search API rollout enabled.

## Findings

### Open Blocker

None.

### Open Should-fix

None.

### Resolved During Phase 8

| ID | Severity | Finding | Fix | Evidence |
| --- | --- | --- | --- | --- |
| P8-B1 | Blocker | `RetrievalFilters.isEmpty()` was serialized as JSON property `empty`, then rejected during sticky-state deserialization. | Added `@JsonIgnore` to `RetrievalFilters.isEmpty()`. | `PostgresConversationStickyStateRepositoryIT` passed in targeted and full gates. |
| P8-B2 | Blocker | Concurrent conversation submissions could allocate duplicate `turn_no` values. | Split conversation row locking and `MAX(turn_no)+1` into separate statements so the second read observes the committed predecessor while the transaction holds the lock. | `PostgresConversationRepositoryIT.allocatesUniqueSequentialTurnsUnderParallelSubmits` passed in targeted and full gates. |
| P8-B3 | Blocker | `MaterialControllerIT` enabled structured-v1 but used the production proof service without a seeded compatible Eval compare, causing 409 responses. | Overrode `StructuredV1ProofService` with the test allow-all implementation inside `MaterialControllerIT`. | `MaterialControllerIT` passed in targeted and full gates. |
| P8-C1 | Cleanup | `search-rollout.sh smoke` could not authenticate to a security-enabled backend even when the operator had admin credentials. | Added optional admin session login using `APP_SECURITY_ADMIN_PASSWORD`; documented the required backend auth environment. | `APP_SECURITY_ADMIN_PASSWORD=adminadmin bash scripts/search-rollout.sh smoke phase8-local-smoke` reached authenticated `/api/health` before stopping on the current stack's disabled search API. |

## Phase Coverage Matrix

| Phase | Coverage result | Evidence |
| --- | --- | --- |
| Phase 0: strict review gate | PASS | `scripts/eval-release-gate.py`, `test_eval_release_gate.py`, and review contract passed. |
| Phase 1: dataset/version reproducibility | PASS | Eval repository and dataset-version tests passed through targeted and full gates. |
| Phase 2: deterministic scoring/config/preview | PASS | Eval service release-gate tests passed; smoke/golden/compare reports gated. |
| Phase 3: compare compatibility/release deltas | PASS | `eval-gate.py --mode compare` passed with non-empty metric summary. |
| Phase 4: architecture/complexity closure | PASS | Architecture and complexity gates passed with tracked baselines only. |
| Phase 5: search rollout/proof/lineage | PASS_WITH_CONFIG_NOTE | Strict mapping IT passed; rollout status passed through a temporary ES bridge; smoke authenticated successfully and is now blocked only by the current local backend's disabled search API rollout. |
| Phase 6: Eval UI and CI workflows | PASS | Frontend API type check, Eval UI tests, and workflow presence checks passed. |
| Phase 7: release proof | PASS_WITH_CI_PENDING | `quality-gates.sh fast` and full local gate passed; external CI still pending. |
| Phase 8: final review/proof | PASS_WITH_CI_PENDING | This report records findings, commands, CI evidence, and residual risk. |

## Commands Run

| Command | Result | Notes |
| --- | --- | --- |
| `git diff --check` | PASS | No whitespace errors. |
| `python3 scripts/eval-release-gate.py` | PASS | Strict Eval release gate passed. |
| `python3 scripts/test_eval_release_gate.py` | PASS | 11 tests passed. |
| `python3 scripts/test_eval_gate.py` | PASS | 8 tests passed. |
| `python3 scripts/architecture-boundary-gate.py` | PASS | Passed with 5 baseline violations. |
| `python3 scripts/test_architecture_boundary_gate.py` | PASS | 15 tests passed. |
| `python3 scripts/review-contract.py --skip-body` | PASS | Contract passed; local diff still contains risk-path changes from prior phases. |
| `node scripts/generate-frontend-types.mjs --check` | PASS | Frontend API types current. |
| `bash scripts/eval-run.sh smoke` | PASS | Smoke report regenerated at 2026-05-11T22:17:57+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/smoke-report.json --mode smoke` | PASS | Smoke gate passed. |
| `bash scripts/eval-run.sh golden` | PASS | Golden report regenerated at 2026-05-11T22:18:13+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/golden-report.json --mode golden` | PASS | Golden gate passed. |
| `bash scripts/eval-run.sh compare` | PASS | Compare report regenerated at 2026-05-11T22:18:25+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/compare-report.json --mode compare` | PASS | Compare gate passed. |
| `APP_SECURITY_ADMIN_PASSWORD=adminadmin bash scripts/search-rollout.sh status` | PASS | Temporary ES bridge exposed the running compose Elasticsearch to host shell; evidence `backend/target/search-rollout/2026-05-11T171721Z-status-na.json`. |
| `APP_SECURITY_ADMIN_PASSWORD=adminadmin bash scripts/search-rollout.sh smoke phase8-local-smoke` | CONFIG_BLOCK | Authenticated backend health passed; sample `/api/search` stopped with 409 because the current backend container is running with `APP_ROLLOUT_SEARCH_API_V1=false` and `APP_SEARCH_SYNC_ENABLED=false`. Evidence `backend/target/search-rollout/2026-05-11T171640Z-smoke-phase8-local-smoke.json`. |
| `mvn -B -Dit.test=PostgresEvalRepositoriesIT,PostgresChatRunTraceRepositoryIT verify` | PASS | 669 unit tests and 14 Docker-backed IT tests passed with Java 21/Colima. |
| `mvn -B -Dtest=SearchableChunkDocumentTest -Dit.test=ElasticsearchIndexAdminIT verify` | PASS | 3 unit tests and 3 Elasticsearch IT tests passed with Java 21/Colima. |
| `VITE_API_URL= VITE_BACKEND_ORIGIN=http://127.0.0.1:8080 npm run build` | PASS | Production build passed; Vite emitted the existing chunk-size warning. |
| `bash scripts/quality-gates.sh fast` | PASS | Static, backend contract, backend Eval, frontend type/test gates passed. |
| `bash scripts/quality-gates.sh full` | FAIL_THEN_FIXED | First run exposed P8-B1, P8-B2, P8-B3. |
| `mvn -B -Dit.test=MaterialControllerIT,PostgresConversationStickyStateRepositoryIT,PostgresConversationRepositoryIT verify` | PASS | Targeted regression proof after fixes: 669 unit tests and 12 IT tests passed. |
| `bash scripts/quality-gates.sh full` | PASS | Final proof: backend contract 279 tests, backend Eval 51 tests, frontend contract 148 tests, backend full 669 unit + 135 IT, backend coverage, frontend coverage 232 tests, frontend build. |

## CI Evidence

Required workflows are present:

| Workflow | Status |
| --- | --- |
| `.github/workflows/ci.yml` | Present; pending external run on current diff. |
| `.github/workflows/eval-smoke.yml` | Present; pending external run on current diff. |
| `.github/workflows/eval-golden.yml` | Present; pending external run on current diff. |
| `.github/workflows/eval-compare.yml` | Present; pending external run on current diff. |
| `.github/workflows/review-contract.yml` | Present; pending external run on current diff. |

Current-worktree GitHub Actions results are unavailable locally because the worktree is dirty and not pushed as this exact diff.

## Residual Risk

| Risk | Severity | Status |
| --- | --- | --- |
| Full search rollout smoke is still blocked on the current local compose stack because search API/sync rollout flags are disabled. | Cleanup | Run `search-rollout.sh smoke` against an operator stack started with `APP_ROLLOUT_SEARCH_API_V1=true`, search sync enabled, and an indexed sample query before production rollout. |
| Scheduled background jobs log JDBC errors after Testcontainers databases are already torn down. | Cleanup | Does not fail gates, but should be quieted by disabling schedulers or isolating app contexts in Docker-backed tests. |
| Current diff has no external CI result yet. | Release gate | Push/open PR and require all listed workflows to pass. |
| Frontend build emits a chunk-size warning. | Cleanup | Consider code-splitting after release proof; current production build passes. |
