# Eval Final Review

Date: 2026-05-11
Scope: phases 0-8
Source plans: `/Users/gleb-imac/Downloads/PLAN-7.md` and `/Users/gleb-imac/Downloads/PLAN-.md`
Evidence artifact: `docs/eval-release-evidence.md`

## Release Decision

**LOCAL_READY_CI_PENDING**

No open source-code Blocker or Should fix remains from the Phase 7 local review. Local acceptance gates pass after the fixes listed below. Final release acceptance is pending GitHub Actions on this exact diff and an operator search-rollout smoke in an environment where Elasticsearch/backend are reachable from the shell.

## Findings

### Blocker

None open.

### Should fix

None open.

Resolved during Phase 7:

- `scripts/search-rollout.sh` failed under bash before any environment check. The rollout scripts now run under bash and write evidence.
- `MaterialControllerUnexpectedErrorTest` mocked the pre-lineage upload signature and no longer exercised the expected error path. The test now targets the current four-argument call.
- `PostgresRagProjectReadRepository` had two constructors after clock injection and no selected Spring autowire constructor. The clock-aware constructor is now explicit.

### Cleanup note

| Note | Owner | Follow-up |
| --- | --- | --- |
| Local host-shell search rollout smoke did not pass because `127.0.0.1:9200` is not published from the local Elasticsearch container. Evidence files were still written under `backend/target/search-rollout/`. | Release/operator | Run in the production-like operator environment before rollout. |
| Current-worktree CI status is pending because the diff is local and dirty. | Release owner | Push/open PR and require `ci.yml`, Eval workflows, and review-contract workflow to pass. |

## Phase Coverage

| Phase | Review status | Evidence |
| --- | --- | --- |
| Phase 0: strict review gate catches old blockers and is green now | PASS | `scripts/eval-release-gate.py` passed; `quality-gates.sh fast` passed. |
| Phase 1: dataset versions use pinned case revisions | PASS | `EvalDatasetVersionCaseResolverTest`, `RetrievalEvalRunServiceTest`, and Docker-backed `PostgresEvalRepositoriesIT` passed. |
| Phase 2: scoring/preview/config hash deterministic | PASS | `EvalChatRunReconcilerTest`, `RetrievalEvalPreviewServiceTest`, and `EvalExecutionConfigServiceTest` passed. |
| Phase 3: compare compatibility and metric deltas release-grade | PASS | `EvalComparisonServiceTest`, `eval-gate.py --mode compare`, and compare report summary passed with non-empty `metricSummary`. |
| Phase 4: complexity/architecture closure | PASS | Architecture boundary and complexity budget gates passed with existing baselines only. |
| Phase 5: search rollout, mapping hash, structured-v1 proof, lineage override | PASS_WITH_ENV_NOTE | Static gates and ES strict mapping IT passed; host-shell rollout smoke is environment-blocked locally. |
| Phase 6: Eval UI and CI workflows | PASS | `quality-gates.sh fast` passed backend Eval tests and 148 frontend tests including Eval tab/hooks/API type checks. |
| Phase 7: release proof reproducible in CI/local | LOCAL_PASS_CI_PENDING | Local gates pass; CI must still run on the pushed diff. |
| Phase 8: production hardening from plan covered | PASS_WITH_ENV_NOTE | Rollout scripts/docs/proof surfaces exist; strict mapping and Docker-backed proofs pass; operator smoke remains a release cleanup note. |

## Acceptance Check

| Criterion | Result |
| --- | --- |
| `bash scripts/quality-gates.sh fast` passes | PASS |
| Eval smoke/golden/compare reports generated and gated | PASS |
| Docker-backed Eval persistence proof | PASS with Colima Testcontainers env |
| ES strict mapping proof | PASS with Colima Testcontainers env |
| `docs/eval-release-evidence.md` records required proof | PASS |
| Final review records phase coverage 0-8 | PASS |
| No Blocker or Should fix remains | PASS |
| CI main workflow passes | PENDING external CI |
| Eval workflows pass | PENDING external CI |
