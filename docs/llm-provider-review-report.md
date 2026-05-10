# LLM Provider Phase 6 Merge Gate Report

Date: 2026-05-10
Workspace: `/Users/gleb-imac/Documents/Тест ИИ`

## Verdict

Do not merge the current dirty worktree as one PR.

The LLM provider slice has strong targeted evidence, including unit, contract,
frontend, API type, production build, and Docker-backed persistence/health
proof. The merge gate is still blocked because the global quality gate fails on
the complexity budget, and the current worktree also carries context/conversation
manager spillover that should not be reviewed as part of the LLM decision.

Recommended cutover strategy: split before merge.

1. `LLM core`: provider storage/CRUD/secrets, runtime resolver, probe/readiness,
   health summaries, frontend provider settings, env/docs, and LLM tests.
2. `Supporting gates/contracts`: static gate, quality gate, review evidence, and
   API/client contract proof that is directly required by the LLM PR.
3. `Context manager / conversation`: separate PR or defer. Do not drag this into
   the LLM provider merge decision.

## Merge Blockers

| Blocker | Evidence | Owner decision |
| --- | --- | --- |
| Complexity gate is red | `bash scripts/quality-gates.sh fast` and `bash scripts/quality-gates.sh full` both stop at `scripts/complexity-budget-gate.py`. `RuntimeReadinessService.java` grew from baseline 511 to 541 lines. `LlmProviderSettingsPanel.tsx` grew from baseline 409 to 469 lines. | Block the PR until the two files are split or the owner explicitly accepts this as deferrable debt without increasing `scripts/complexity-budget-baseline.json`. The recommended decision is split before merge. |
| LLM scope is mixed with context/conversation work | Scope inventory lists 23 changed or untracked files that are not required for the LLM provider cutover. | Split into a separate context/conversation PR or defer. |

No LLM-provider contract, secret-redaction, provider-probe, persistence, runtime
readiness, API type, or frontend build regression was found in the targeted
Phase 6 proof.

## Scope Inventory

Commands run:

- `git status --short`: 38 tracked changed files and 9 untracked files.
- `git diff --name-status`: tracked file list captured below.
- `git ls-files --others --exclude-standard`: untracked file list captured below.
- `git diff --stat`: 38 tracked files, 1793 insertions, 72 deletions. Untracked
  files are not included in this stat.
- `git diff --name-only -- backend/src/main/resources/db/migration`: no changed
  migration files.
- `git status --short -- scripts/complexity-budget-baseline.json`: no baseline
  change.

### Bucket Summary

| Bucket | Count | Decision |
| --- | ---: | --- |
| LLM plan-aligned | 15 | Eligible for LLM PR after complexity blocker is resolved. |
| Required supporting | 9 | Include with LLM PR only where the evidence/gate/doc change supports the LLM cutover. |
| Separate PR / defer | 23 | Keep out of the LLM PR unless the owner explicitly broadens scope. |

### File Classification

| File | Bucket | Rationale |
| --- | --- | --- |
| `.env.example` | Required supporting | Documents LLM secret/env setup. |
| `.env.production.example` | Required supporting | Documents production LLM secret/env setup. |
| `README.md` | Required supporting | Operator-facing LLM setup evidence. |
| `backend/src/main/java/com/example/demo/controller/ConversationController.java` | Separate PR / defer | Conversation lifecycle scope, not required for LLM provider merge. |
| `backend/src/main/java/com/example/demo/embedding/OpenAiCompatibleEmbeddingClient.java` | LLM plan-aligned | Runtime embedding provider behavior. |
| `backend/src/main/java/com/example/demo/infrastructure/context/PostgresContextMaintenanceRepository.java` | Separate PR / defer | Context manager persistence scope. |
| `backend/src/main/java/com/example/demo/infrastructure/conversation/PostgresConversationRepository.java` | Separate PR / defer | Conversation persistence scope. |
| `backend/src/main/java/com/example/demo/llm/OpenAiCompatibleLlmClient.java` | LLM plan-aligned | Runtime chat provider behavior. |
| `backend/src/main/java/com/example/demo/llmprovider/LlmProviderProbeService.java` | LLM plan-aligned | Provider probe and sanitization behavior. |
| `backend/src/main/java/com/example/demo/service/RuntimeReadinessService.java` | LLM plan-aligned | Health/readiness provider summaries. Complexity blocker target. |
| `backend/src/test/java/com/example/demo/controller/LlmProviderControllerContractTest.java` | LLM plan-aligned | Provider REST contract coverage. |
| `backend/src/test/java/com/example/demo/llmprovider/JdbcLlmProviderRepositoryIT.java` | LLM plan-aligned | Provider persistence and V39 storage coverage. |
| `backend/src/test/java/com/example/demo/llmprovider/LlmProviderProbeServiceTest.java` | LLM plan-aligned | Probe semantics and error sanitization coverage. |
| `backend/src/test/java/com/example/demo/llmprovider/LlmProviderServiceTest.java` | LLM plan-aligned | CRUD, activation, secret, and fallback behavior coverage. |
| `backend/src/test/java/com/example/demo/service/ChatPromptAssemblyServiceContextTest.java` | Separate PR / defer | Context assembly behavior, not required for LLM provider merge. |
| `backend/src/test/java/com/example/demo/service/ChatRunSubmissionCoordinatorTest.java` | Separate PR / defer | Conversation/context run submission behavior. |
| `backend/src/test/java/com/example/demo/service/RuntimeReadinessServiceTest.java` | LLM plan-aligned | Active provider health summary coverage. |
| `backend/src/test/java/com/example/demo/service/context/HistorySelectorTest.java` | Separate PR / defer | Conversation history selection scope. |
| `docs/context-manager-roadmap.md` | Separate PR / defer | Context manager roadmap. |
| `docs/deploy-linux-compose.md` | Required supporting | Deployment docs for provider/env cutover. |
| `docs/ops-runbook.md` | Required supporting | Operator runbook for provider fallback and secrets. |
| `docs/quality-gates.md` | Required supporting | Merge gate documentation. |
| `docs/refactor-risk-register.md` | Separate PR / defer | Context/refactor release risk scope. |
| `docs/refactor-session-protocol.md` | Separate PR / defer | Refactor process scope. |
| `docs/security.md` | Required supporting | Secret handling and security notes. |
| `frontend/src/App.test.tsx` | Separate PR / defer | App shell/context feature gating coverage. |
| `frontend/src/api/client.test.ts` | LLM plan-aligned | API client contract coverage for provider calls. |
| `frontend/src/components/ContextInspectorPanel.tsx` | Separate PR / defer | Context inspector UI scope. |
| `frontend/src/components/ConversationThreadPanel.test.tsx` | Separate PR / defer | Conversation thread UI coverage. |
| `frontend/src/components/LlmProviderSettingsPanel.test.tsx` | LLM plan-aligned | Provider settings UI coverage. |
| `frontend/src/components/LlmProviderSettingsPanel.tsx` | LLM plan-aligned | Provider settings UI. Complexity blocker target. |
| `frontend/src/components/workbench/WorkbenchShell.tsx` | Separate PR / defer | Workbench shell/context navigation scope. |
| `frontend/src/hooks/useChatRunContext.ts` | Separate PR / defer | Context inspector data hook. |
| `frontend/src/hooks/useConversationChatExecution.test.tsx` | Separate PR / defer | Conversation execution behavior. |
| `frontend/src/hooks/useLlmProviders.ts` | LLM plan-aligned | Provider settings data hook. |
| `frontend/src/types.contract.test.ts` | LLM plan-aligned | Generated API contract compatibility. |
| `scripts/phase5-static-gate.py` | Required supporting | Static release guardrail. |
| `scripts/quality-gates.sh` | Required supporting | Merge quality gate wiring. |
| `backend/src/test/java/com/example/demo/controller/ConversationControllerContractTest.java` | Separate PR / defer | New conversation controller contract coverage. |
| `backend/src/test/java/com/example/demo/infrastructure/context/PostgresContextAssemblyTraceRepositoryIT.java` | Separate PR / defer | Context assembly trace persistence coverage. |
| `backend/src/test/java/com/example/demo/infrastructure/conversation/PostgresConversationRepositoryIT.java` | Separate PR / defer | Conversation persistence coverage. |
| `backend/src/test/java/com/example/demo/service/ConversationServiceTest.java` | Separate PR / defer | Conversation service behavior. |
| `backend/src/test/java/com/example/demo/service/context/ContextAssemblyServiceTest.java` | Separate PR / defer | Context assembly behavior. |
| `docs/context-manager-release-evidence.md` | Separate PR / defer | Context manager release proof. |
| `docs/llm-provider-review-report.md` | LLM plan-aligned | Phase 6 merge report. |
| `frontend/src/hooks/useConversationRuns.test.tsx` | Separate PR / defer | Conversation run hook coverage. |
| `frontend/src/hooks/useConversations.test.tsx` | Separate PR / defer | Conversation hook coverage. |

## Anti-Sprawl Gate

- Passed: `python3 scripts/review-contract.py --skip-body`
  - Result: PR body validation skipped, no new production anti-sprawl keywords
    detected, 13 high-risk changed files detected, review contract passed.
- The current complexity baseline was not changed.
- No new public APIs were added during Phase 6.
- No migration files were changed during Phase 6.

## Quality Gate Evidence

| Gate | Result | Notes |
| --- | --- | --- |
| `git diff --check` | PASS | No whitespace errors. |
| `python3 scripts/review-contract.py --skip-body` | PASS | No new production anti-sprawl keyword hits. |
| `bash scripts/quality-gates.sh fast` | FAIL | Stops at complexity budget before backend/frontend contract tests. |
| `bash scripts/quality-gates.sh full` | FAIL | Same complexity budget failure, so full proof does not proceed. |
| `npm run check:api-types` | PASS | Generated frontend API types are current. |
| `npm run build` | PASS | Vite build succeeds; main JS chunk warning remains at 620.18 kB. |
| Backend LLM unit/contract slice | PASS | 71 tests, 0 failures, 0 errors. |
| Frontend LLM/readiness slice | PASS | 54 tests, 0 failures. |
| Docker-backed LLM storage/health slice | PASS | 8 tests, 0 failures, 0 errors. Flyway validated and applied 47 migrations through V47 in Testcontainers. |

Exact targeted commands:

- `mvn -B -Dtest=LlmProviderControllerContractTest,LlmProviderServiceTest,LlmProviderCryptoServiceTest,LlmProviderProbeServiceTest,ActiveLlmProviderResolverTest,RuntimeReadinessServiceTest,PromptPolicyResolverTest,OllamaLlmClientTest,OllamaEmbeddingClientTest,OperatorAuditCoverageTest,ApiContractSchemaTest test`
- `npm run test -- src/components/LlmProviderSettingsPanel.test.tsx src/api/client.test.ts src/types.contract.test.ts src/utils/readiness.test.ts`
- `DOCKER_HOST=unix:///Users/gleb-imac/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -B -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=JdbcLlmProviderRepositoryIT,HealthControllerIT verify`

## LLM Smoke Coverage

The Phase 6 proof is automated and non-live. It covers these merge-gate smoke
scenarios through controller, service, repository, frontend, health, and
readiness tests:

- Create provider without an API key.
- Preserve, replace, clear, and avoid echoing API keys.
- Probe provider and sanitize probe failures.
- Activate chat and embedding providers.
- Fall back from DB providers to env configuration.
- Expose active provider summaries through `/api/health` without secrets.
- Validate V39 provider persistence and health integration with PostgreSQL.

Live external-provider smoke with `APP_LLM_PROVIDER_SECRET_KEY` and a real
provider endpoint was not run in Phase 6. If the owner requires live proof, run
it before merge in an environment with a disposable key and record the result in
this report or the PR body.

## Deferrable Debt

- Split `RuntimeReadinessService` into smaller collaborators for provider
  readiness calculation, probe status mapping, and health response composition.
- Split `LlmProviderSettingsPanel` into provider table, editor dialog, and action
  controls.
- Add `LlmProviderControllerIT` if a single HTTP plus database integration test
  is desired beyond the existing controller contract and JDBC repository ITs.
- Investigate production bundle size. The build is green, but the main
  minified JS chunk is above 500 kB.

## Rollback Plan

1. Deactivate DB-backed chat and embedding providers through the fallback
   activation path so runtime resolution returns to env config.
2. For embedding rollback after a model/dimension change, confirm reindex needs
   before changing the active provider again.
3. Revert the LLM PR if application behavior must return to the previous
   release.
4. Preserve V39 provider rows and encrypted secret material unless data
   corruption is proven. Keep `APP_LLM_PROVIDER_SECRET_KEY` available for any
   forward or rollback operation that needs to decrypt existing provider keys.

## PR Readiness Fields

- Risk level: high until complexity and scope slicing are resolved.
- Touched boundaries: API, data, secrets, LLM runtime, health, frontend, deploy
  docs, quality gates.
- Anti-sprawl: no new production keyword hits; complexity blocker remains.
- Complexity delta: `RuntimeReadinessService.java` 511 -> 541 lines;
  `LlmProviderSettingsPanel.tsx` 409 -> 469 lines; baseline unchanged.
- Security/data/deploy impact: provider secrets encrypted and redacted; V39
  provider data must be preserved; env fallback is the operational rollback.
- Merge decision: not ready as a single PR. Ready to slice into LLM core plus
  supporting evidence after the two complexity blockers are handled.
