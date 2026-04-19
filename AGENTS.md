# Global GSD Defaults

Use GSD as the default orchestration layer across Codex projects when it improves reliability.

## Silent Default
- Keep GSD mostly implicit. Do not advertise or explain the GSD workflow unless the user asks, setup is required, or the workflow choice materially affects time, risk, or cost.
- Follow this file only when it does not conflict with higher-priority system, developer, or user instructions.

## When GSD Should Be Preferred
- Brownfield codebases with more than a handful of meaningful files.
- Multi-step implementation, refactor, debug, review, or integration work spanning multiple files or subsystems.
- Higher-risk work touching deployment, infrastructure, CI/CD, secrets, migrations, data shape, auth, or security.
- Work where planning, verification, or state tracking is likely to prevent regressions.

## When GSD Should Be Skipped
- Tiny one-file edits where process overhead outweighs the benefit.
- Pure explanation, brainstorming, translation, or summarization tasks.
- Throwaway scratch folders or obviously trivial repositories.

## Default Routing Rules
- If the user identifies a project root, treat that directory as the primary workspace root.
- In a non-trivial existing repository without `.planning/`, prefer a brownfield path before major work: map the codebase first, then initialize project planning only if milestone-style work is warranted.
- For clear scoped implementation or bugfix work, prefer the quick path.
- Add lightweight research when the approach is uncertain.
- Add validation for medium-risk work.
- Use the full quality path for high-risk or cross-cutting tasks.
- If `.planning/` already exists, continue the current GSD lifecycle instead of re-initializing it.

## User Experience
- Prefer the lightest GSD path that still adds value.
- Infer process decisions from repository context when it is safe to do so.
- Summarize outcomes, not internal orchestration.
- If GSD would clearly be overkill, skip it.

## Local Service Lifecycle
- In Codex, agent, PTY, and other non-interactive runtimes, do not rely on detached helpers such as `scripts/start-ollama.sh`, `scripts/start-backend.sh`, `scripts/start-frontend.sh`, or `scripts/start-studio.sh` to keep services alive after the command exits.
- Prefer invoking `./scripts/run-local-stack.sh` directly whenever you need a long-lived local Ollama + backend + frontend stack under those runtimes.
- Treat the detached `start-*` scripts as low-level helpers for ordinary manual shell usage only.

## Context7 Policy

### Registration
- `Context7` is already configured globally in Codex for this machine.
- Do not add project-local MCP server registration files such as `.mcp.json`, `mcp.json`, `.cursor/mcp.json`, or similar duplicates in this repository.

### When To Use Context7
- Use `Context7` for questions about external dependencies, framework APIs, setup, config semantics, migration notes, and version-specific behavior.
- Prefer it when working with this repository's stack:
  - Spring Boot `3.3.4`
  - Java `21`
  - React `18.3.1`
  - Vite `5.4.10`
  - Vitest `2.1.8`
  - pgvector Java client `0.1.6`
  - Apache Tika `2.9.2`
  - PDFBox `2.0.32`
  - Testcontainers
  - PostgreSQL integration concerns

### When Not To Use Context7
- Do not use `Context7` for project-specific behavior that is already defined in local code, tests, or config.
- Prefer repository inspection first for local classes and flows such as `PromptPolicyResolver`, `MaterialRetrievalService`, repository wiring, test expectations, REST contracts, and any other behavior answerable from the codebase.

### Required Workflow
1. Inspect local manifests, code, and config first to identify the exact library and version in use.
2. Call `resolve_library_id` with the official library name.
3. Call `query_docs` with a narrow, version-aware question.
4. Map the documentation answer back to the repository and explain any local overrides or divergences.

### Query Discipline
- Use official library names in `resolve_library_id`.
- Include version and task context in the query whenever the version is known locally.
- Keep queries narrow and specific to the decision at hand.
- Do not send secrets, tokens, private prompts, credentials, proprietary document contents, or other sensitive project data to `Context7`.

### Conflict Rule
- If local code and `Context7` documentation differ, trust local code for current project behavior.
- Treat `Context7` as an external reference, not as authority over repository-specific implementation details.

### Fallback Rule
- If `Context7` returns a weak match, incomplete guidance, or no useful result, use official documentation or another approved research tool instead of forcing `Context7`.
