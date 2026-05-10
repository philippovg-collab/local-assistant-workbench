# LLM Phase 0 Scope Inventory

Date: 2026-05-10

Phase 0 gate status: compile/type blockers only. Do not add LLM behavior, API surface, migrations, UI flows, or architecture refactors in this phase.

## Compile Gate Fixes

- Backend contract test now uses `ChatRunContextDetail` for `/api/chat-runs/{id}/context` instead of the storage snapshot DTO.
- Frontend context inspector link rendering keeps only links with a concrete `string` href.
- Conversation thread context test uses `ChatRunContextDetail` and asserts the UI currently rendered by `ContextInspectorPanel`.

## LLM Plan-Aligned

These areas appear directly tied to the LLM provider plan and should be reviewed in Phases 1-4:

- Backend provider API and DTOs: `LlmProviderController`, `LlmProviderInput`, `LlmProviderConfigResponse`, `LlmProviderProbeResult`, `LlmProviderActivateRequest`, provider enums/status models.
- Provider storage and runtime resolution: `backend/src/main/java/com/example/demo/llmprovider/`, `V39__llm_provider_configs.sql`, active provider resolver, crypto service, repository, probe service.
- Runtime clients and selection: `OpenAiCompatibleLlmClient`, `OpenAiCompatibleEmbeddingClient`, `OllamaLlmClient`, `OllamaEmbeddingClient`, `ModelCatalogService`, `PromptPolicyResolver`, chat and embedding call sites.
- Readiness/health exposure for active providers: `RuntimeReadinessService`, `HealthStatusService`, `HealthResponse`.
- Frontend settings surface and API contract: `LlmProviderSettingsPanel`, `useLlmProviders`, `frontend/src/api/client.ts`, generated API types, provider-focused tests.
- Environment and deployment docs for provider configuration and secret key setup.

## Required Supporting

These changes may be needed to make the LLM provider work reviewable, but should stay subordinate to the provider scope:

- API contract generation and frontend type checks: `backend/src/main/java/com/example/demo/contract/`, `backend/src/main/resources/api-contract/`, `frontend/src/generated/`, `scripts/generate-frontend-types.mjs`.
- Error and validation plumbing for API responses: `ApiExceptionHandler`, `ApplicationException`, `CodedException`, `ProviderException`, `StorageException`, `ErrorReasonResolver`.
- Secret-safe diagnostics and audit support, only where provider keys or provider failures can leak: `AuditRedactionService`, operator audit events, logback configuration.
- Request metadata and correlation, only where needed for operator audit and safe troubleshooting: `RequestContext`, `RequestCorrelationFilter`, `SecurityActorCaptureFilter`.
- Phase 0 contract/type alignment: `ChatRunQueryControllerContractTest`, `ContextInspectorPanel`, `ConversationThreadPanel.test`.

## Unrelated Or High-Risk Spillover

These areas are outside the core LLM provider plan or broad enough to require separate review justification:

- Context and conversation subsystem: conversation controllers, context assembly, summary compaction, sticky state, long-term memory, memory review, migrations `V38`, `V40`-`V46`.
- Chat execution decomposition: new pipeline/coordinator/result/recorder/retrieval step classes and large changes to `ChatExecutionService`.
- Material ingestion and search refactors: material ingestion workflow/coordinator, auto-tagging lifecycle/worker, search sync, retrieval SQL, reference usage, material metadata and indexing changes.
- Security and auth changes beyond provider secrets: `SecurityConfig`, `AuthController`, security filters, audit coverage, request limits.
- CI/deploy and operational scripts: GitHub workflows, compose files, local diagnostics, quality gates, architecture/complexity gates.
- Frontend shell/navigation changes not required for provider settings: workbench shell/sidebar/mobile header/navigation, memory tab, broader chat and reference panels.
- Documentation changes not specific to provider setup or provider rollout behavior.

## Review Boundary For Phase 1

- Start Phase 1 from provider storage, CRUD, validation, and secret handling only.
- Treat every spillover item above as out of scope unless a later phase explicitly depends on it.
- Do not use green compile gates as evidence that provider behavior, activation safety, secret hygiene, or reindex semantics are correct; those remain Phase 1-5 review work.
