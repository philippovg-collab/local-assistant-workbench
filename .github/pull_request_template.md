## Risk / Review Focus

- Risk level: <!-- low / medium / high -->
- Touched boundaries: <!-- API, data, retrieval, frontend, security, deploy, docs-only -->
- Review focus: <!-- behavior first, then tests, then architecture impact -->

## Tests / Evidence

- [ ] `mvn -B -Pcoverage test`
- [ ] `npm run test:coverage`
- [ ] `npm run build`
- [ ] `mvn -B verify` for medium/high-risk backend changes
- Evidence / skipped tests: <!-- summarize results or explain why a check is not applicable -->

## Anti-Sprawl

- [ ] No new or changed `legacy`, `fallback`, `rollout`, `bestEffort`, or compatibility path.
- [ ] This PR adds or changes one of those paths; the fields below are filled in.

- Reason:
- Owner scenario:
- Test coverage:
- Removal criterion:
