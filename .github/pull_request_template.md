## Risk / Review Focus

- Risk level: <!-- low / medium / high -->
- Risk IDs: <!-- e.g. ARCH-001, ERR-001; use N/A only for trivial changes outside the refactor ledger -->
- Touched boundaries: <!-- API, data, metadata, retrieval, queues, frontend, security, deploy, docs-only -->
- Review focus: <!-- behavior first, then tests, then architecture impact -->

## Tests / Evidence

- [ ] `bash scripts/quality-gates.sh fast`
- [ ] `mvn -B clean verify -Pcoverage`
- [ ] `mvn -B clean verify`
- [ ] `npm --prefix frontend run test:coverage`
- [ ] `npm --prefix frontend run build`
- [ ] `bash scripts/scalability-smoke.sh all`
- [ ] Eval smoke report attached or linked (`backend/target/eval-reports/smoke-report.json`)
- [ ] Eval golden report attached or linked for risk paths (`backend/target/eval-reports/golden-report.json`)
- [ ] Eval compare report attached or linked for retrieval/chunking/reranker/prompt/model/config paths (`backend/target/eval-reports/compare-report.json`)
- Evidence / skipped tests: <!-- summarize results or explain why a check is not applicable -->

## Security / Data / Deploy Impact

- Security impact: <!-- auth, CORS, secrets, audit, or N/A -->
- Data integrity impact: <!-- schema, migrations, metadata, queue durability, or N/A -->
- Migration/deploy impact: <!-- deploy scripts, compose/env, release sequencing, or N/A -->
- Docs/runbook impact: <!-- README/docs/runbook updates included, not needed, or N/A -->

## Anti-Sprawl

- [ ] No new or changed `legacy`, `fallback`, `rollout`, `bestEffort`, or compatibility path.
- [ ] This PR adds or changes one of those paths; the fields below are filled in.

- Reason:
- Owner scenario:
- Test coverage:
- Removal criterion:

## Complexity Delta

- Complexity before: <!-- line-count baseline for touched over-budget files, or N/A -->
- Complexity after: <!-- current line counts for touched files, or N/A -->
- Files removed from baseline: <!-- paths, or N/A -->
- Files added to baseline: <!-- paths and reasons, or N/A -->
- Largest file before/after: <!-- path and line count before -> after, or N/A -->

## Rollback

- Rollback plan: <!-- revert, feature flag/config rollback, deploy rollback, data rollback, or N/A -->
