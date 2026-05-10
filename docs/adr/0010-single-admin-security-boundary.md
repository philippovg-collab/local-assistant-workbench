# ADR 0010: Single-Admin Security Boundary

## Status

Accepted.

## Decision

Rag Studio remains an internal single-admin deployment. Production and production-like deployments have one administrative operator account, and every business API under `/api/**` is admin-only except:

- `GET /api/liveness`;
- `/api/auth/**`.

Workspaces, RAG projects, knowledge presets, scopes, `workspaceKey`, and `projectKey` are product organization filters. They are not authorization, tenant isolation, ownership, or privacy boundaries.

## Consequences

- Do not add RBAC, role management, user management, per-workspace access, ownership columns, or tenant semantics in small incremental changes.
- A future multi-user version requires a separate roadmap covering users, roles, ownership, service-layer authorization, data migration, audit access policy, and negative authorization tests.
- UI and docs must not describe workspaces, projects, presets, or scopes as private user areas or tenant isolation.
- Static guardrails fail if `/api/**` falls back to `authenticated()`, if security tests allow `ROLE_USER` through business APIs, or if production code starts introducing multi-user/RBAC artifacts without a dedicated security decision.
