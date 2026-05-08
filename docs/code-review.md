# Code Review Guardrails

Use code review as a quality gate for behavior, boundaries, tests, and removal of temporary paths. Style comments are secondary unless they hide a real defect.

## Required Flow

1. Run the local review loop before requesting review:

```bash
bash scripts/quality-gates.sh fast
```

2. For medium/high-risk backend work, include the integration proof:

```bash
bash scripts/quality-gates.sh full
```

3. Fill the PR template. Every PR must include risk, touched boundaries, tests/evidence, security/data/deploy impact, anti-sprawl declaration, complexity delta, and rollback plan.

4. Review in this order:

- behavior and contracts
- data and migration safety
- retrieval correctness and grounding
- frontend async/error states
- test proof
- architecture impact and removal of temporary paths

## Anti-Sprawl Rules

- A new `legacy`, `fallback`, `rollout`, `bestEffort`, or compatibility path must document its reason, owner scenario, test coverage, and removal criterion.
- A new behavior branch needs at least one unit, integration, hook, or component test.
- Do not grow `Material*`, `Chat*`, `KnowledgePreset*`, or `Elasticsearch*` services with a new independent use case unless the change also introduces an appropriate decomposition.
- PostgreSQL remains the source of truth for materials, lineage, readiness, and embeddings.
- Elasticsearch remains a lexical/search plane, not a second source of truth.
- Frontend code may normalize and present backend payloads, but should not recreate backend semantics.
- New operator-facing config/env flags need a default and documentation in `.env.example` or README.

## Review Severity

- `Blocker`: breaks API contracts, data safety, security, migrations, retrieval correctness, or CI quality gates.
- `Should fix`: adds duplication, untested behavior, unclear compatibility paths, or API changes without evidence.
- `Cleanup note`: useful follow-up that does not change the safety of the current PR.

## Automation

- `.github/workflows/ci.yml` runs static gates, fast quality gates, backend coverage ratchets, frontend coverage/build, Docker-backed integration checks, and SonarCloud Quality Gate.
- `.github/workflows/review-contract.yml` validates that the PR body contains review focus, test evidence, high-risk path proof, anti-sprawl details, complexity delta, and rollback plan.
- `scripts/review-contract.py` flags new production-code sprawl keywords unless the PR declares the path and fills the required anti-sprawl details.
- `scripts/quality-gates.sh fast|full|ci` is the primary local/CI entrypoint for Phase 7 gates.
