# Eval Release Evidence

Date: 2026-05-11
Run timestamp: 2026-05-11T16:28:51Z
Scope: Eval and rollout closure for phases 0-8
Branch: `main`
HEAD commit: `c2f87cfd24dfbe95854d7b8e01ce17df7a6d40a9`
Worktree: dirty; this evidence covers HEAD plus the local phase 0-8 worktree changes.

## Release Status

Local release proof: **PASS with environment note**.

Current release acceptance is still **pending external CI** for the current worktree after the changes are pushed or opened as a PR. CI is the release authority; this file records local proof only.

## Environment

- macOS host with Colima-backed Docker.
- Docker client context: `colima`.
- Docker server: 29.2.1, Ubuntu 24.04.4, aarch64, 9.69 GiB memory.
- Java for release proof: OpenJDK 21.0.10 via `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Maven default Java on this machine is Java 25.0.2; release proof commands were run with Java 21 to match CI policy.
- Testcontainers with Colima required:
  - `DOCKER_HOST=unix:///Users/gleb-imac/.colima/default/docker.sock`
  - `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`
- A local Elasticsearch container exists, but port 9200 is not normally published to the host. A temporary ES bridge was used for the remaining local rollout proof without restarting the stack.
- The running local backend is configured with `APP_ROLLOUT_SEARCH_API_V1=false`, `APP_SEARCH_SYNC_ENABLED=false`, and `APP_RAG_LEXICAL_PROVIDER=postgres`; authenticated rollout smoke can reach health but cannot complete sample `/api/search` on this stack.

## Command Results

| Command | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PASS | No whitespace errors. |
| `python3 scripts/phase5-static-gate.py` | PASS | 15 checks passed. |
| `python3 scripts/frontend-boundary-gate.py` | PASS | Frontend boundary gate passed. |
| `python3 scripts/architecture-boundary-gate.py` | PASS | Passed with 5 baseline violations. |
| `python3 scripts/test_architecture_boundary_gate.py` | PASS | 15 tests passed. |
| `python3 scripts/complexity-budget-gate.py` | PASS | 21 over-budget baseline entries tracked. |
| `python3 scripts/review-contract.py --skip-body` | PASS | PR body skipped; diff risk sections detected; contract passed. |
| `python3 scripts/eval-release-gate.py` | PASS | Strict Eval release gate passed. |
| `python3 scripts/test_eval_release_gate.py` | PASS | 11 tests passed. |
| `python3 scripts/test_eval_gate.py` | PASS | 8 tests passed. |
| `node scripts/generate-frontend-types.mjs --check` | PASS | Generated frontend types are current. |
| `bash scripts/eval-run.sh smoke` | PASS | Regenerated smoke report at 2026-05-11T22:17:57+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/smoke-report.json --mode smoke` | PASS | Eval smoke gate passed. |
| `bash scripts/eval-run.sh golden` | PASS | Regenerated golden report at 2026-05-11T22:18:13+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/golden-report.json --mode golden` | PASS | Eval golden gate passed. |
| `bash scripts/eval-run.sh compare` | PASS | Regenerated compare report at 2026-05-11T22:18:25+05:00. |
| `python3 scripts/eval-gate.py backend/target/eval-reports/compare-report.json --mode compare` | PASS | Eval compare gate passed. |
| `python3 scripts/eval-report-summary.py ... smoke/golden/compare` | PASS | Summaries rendered. |
| `APP_SECURITY_ADMIN_PASSWORD=adminadmin bash scripts/search-rollout.sh status` | PASS | Temporary ES bridge exposed the running compose Elasticsearch to host shell; aliases and cluster health passed. |
| `APP_SECURITY_ADMIN_PASSWORD=adminadmin bash scripts/search-rollout.sh smoke phase8-local-smoke` | CONFIG_BLOCK | Authenticated backend health passed; sample `/api/search` returned 409 because the running backend has search API/sync rollout flags disabled. |
| `mvn -B -Dit.test=PostgresEvalRepositoriesIT,PostgresChatRunTraceRepositoryIT verify` with Java 21 and Colima Testcontainers env | PASS | 669 unit tests passed before Failsafe; 14 Docker-backed IT tests passed. |
| `mvn -B -Dtest=SearchableChunkDocumentTest -Dit.test=ElasticsearchIndexAdminIT verify` with Java 21 and Colima Testcontainers env | PASS | 3 strict mapping unit tests and 3 Elasticsearch IT tests passed. |
| `bash scripts/quality-gates.sh fast` with Java 21 | PASS | Static gates, backend contract tests, backend Eval tests, frontend API type check, and 148 frontend tests passed. |

## Eval Reports

- Smoke JSON: `backend/target/eval-reports/smoke-report.json`
- Smoke Markdown: `backend/target/eval-reports/smoke-report.md`
- Golden JSON: `backend/target/eval-reports/golden-report.json`
- Golden Markdown: `backend/target/eval-reports/golden-report.md`
- Compare JSON: `backend/target/eval-reports/compare-report.json`
- Compare Markdown: `backend/target/eval-reports/compare-report.md`

Report proof summary:

- Smoke: `gateStatus=PASS`, `overallVerdict=PASS`, `runStatus=COMPLETED`, 4 total items, 4 passed, 0 failed.
- Golden: `gateStatus=PASS`, `overallVerdict=PASS`, `runStatus=COMPLETED`, 4 total items, 4 passed, 0 failed.
- Compare: `gateStatus=PASS`, `overallVerdict=PASS`, `runStatus=COMPLETED`, compatibility accepted by gate, non-empty `metricSummary`.
- Blocker/high failed cases: none reported by gate summaries.

The generated reports contain no artifact references; therefore there are no missing referenced report artifacts in this run.

## Search Rollout Evidence

Evidence directory: `backend/target/search-rollout/`

- `backend/target/search-rollout/2026-05-11T171721Z-status-na.json`
- `backend/target/search-rollout/2026-05-11T171640Z-smoke-phase8-local-smoke.json`

Both evidence files include command, timestamp, Elasticsearch URL, before/after alias targets, mapping hash, result, and error. The mapping hash recorded in both files is:

`c4037fabecf322869b1b057d3811d1dfeb804565edb57e953847ecd25eea67b8`

The status result is `success`. The smoke result is classified as a local config block because the current backend container intentionally has `searchApiV1=false` and search sync disabled; after the script authenticated successfully, `/api/health` reported `searchStatus=DISABLED` and sample `/api/search` returned 409.

## CI Evidence

Required workflows are present:

- `.github/workflows/ci.yml`
- `.github/workflows/eval-smoke.yml`
- `.github/workflows/eval-golden.yml`
- `.github/workflows/eval-compare.yml`
- `.github/workflows/review-contract.yml`

Current-worktree GitHub Actions status was not available locally because these changes are not committed and pushed. Release acceptance remains pending until CI runs against this exact diff.

## Fixes Applied During Phase 7

- Made `scripts/search-rollout.sh`, `scripts/search-rollout-common.sh`, and `scripts/search-rollback-read-alias.sh` bash-compatible so the plan-required `bash scripts/search-rollout.sh ...` commands start correctly.
- Added optional authenticated backend smoke support to `scripts/search-rollout.sh` via `APP_SECURITY_ADMIN_PASSWORD`.
- Updated `MaterialControllerUnexpectedErrorTest` to mock the current four-argument `saveUpload(..., lineageOverride)` call.
- Marked the `PostgresRagProjectReadRepository(JdbcTemplate, Clock)` constructor as the Spring autowire constructor so integration contexts can create the repository after adding clock-based date behavior.

## Remaining Cleanup Notes

| Note | Owner | Follow-up |
| --- | --- | --- |
| Run full `search-rollout.sh smoke` in an operator environment with `APP_ROLLOUT_SEARCH_API_V1=true`, search sync enabled, and an indexed sample query. | Release/operator | Before production rollout. |
| Push the current diff and require `ci.yml`, `eval-smoke.yml`, `eval-golden.yml`, `eval-compare.yml`, and `review-contract.yml` to pass. | Release owner | Before merge/release. |
