# Security

## Security Model

Rag Studio is currently a single-admin internal deployment. It is intended to run behind a customer-controlled network or reverse proxy, with one administrative operator account.

The security boundary decision is recorded in [ADR 0010](adr/0010-single-admin-security-boundary.md). RBAC, user management, tenant isolation, and per-workspace authorization are out of scope until a separate roadmap defines ownership, service-layer authorization, migration, and audit policy.

The following concepts are product filters, not authorization boundaries:

- workspaces;
- projects;
- knowledge presets;
- material scopes.

They help organize retrieval and UI workflows, but they do not isolate data between users. `workspaceKey`, `projectKey`, scopes, and presets are not security boundaries. Do not run this system as a multi-tenant service until full RBAC, user management, and per-workspace authorization are implemented.

All `/api/**` routes require an admin session except:

- `GET /api/liveness`;
- `/api/auth/**`.

Business APIs such as `/api/health`, `/api/materials`, `/api/search`, `/api/models`, `/api/instructions`, `/api/reference/**`, `/api/rag-projects`, `/api/chat`, and `/api/chat-runs/**` are admin-only. `/api/chat-runs/**` is explicitly covered because it exposes audit traces, prompts, sources, and model execution details.

`app.security.enabled=false` is only for local, dev, or test runtimes. Production-like Compose runs must keep security enabled and provide an explicit admin password.

## Admin Credentials

The application is currently a single-admin internal deployment. The username defaults to `admin`, but the password must not have a weak default.

For local scripts:

- If `APP_SECURITY_ADMIN_PASSWORD` is set, scripts use it.
- If it is not set, `scripts/run-local-stack.zsh` and `scripts/start-backend.sh` generate a random password with `openssl`.
- If `openssl` is unavailable, startup fails fast and asks for `APP_SECURITY_ADMIN_PASSWORD`.

The generated password is valid only for the current local process environment. Set an explicit password when you need repeatable diagnostics or manual login across restarts:

```bash
export APP_SECURITY_ADMIN_PASSWORD=<strong-local-password>
./scripts/run-local-stack.sh
```

`scripts/local-runtime-diagnostics.zsh` never invents an admin password. Without `APP_SECURITY_ADMIN_PASSWORD`, it skips authenticated checks and prints a warning.

## Docker Compose

Production-like Compose startup requires:

```dotenv
APP_SECURITY_ADMIN_PASSWORD=<strong-admin-password>
POSTGRES_PASSWORD=<strong-postgres-password>
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

The production Compose file refuses to start when the admin or PostgreSQL password is missing. The preferred deployment mode is same-origin browser access through the frontend nginx container; backend, PostgreSQL, and Elasticsearch stay unpublished.

## LLM Provider Secrets

UI-managed LLM provider API keys are encrypted before storage with `APP_LLM_PROVIDER_SECRET_KEY`. Set this value to a long random secret before allowing operators to save API keys through `Settings -> LLM подключения`.

The secret key may be empty only when operators use env fallback or save keyless providers. In that mode, requests containing `apiKey` fail closed instead of storing plaintext.

Provider APIs and diagnostics expose only non-secret metadata:

- provider REST responses include `hasApiKey`, never `apiKey` or `apiKeyCiphertext`;
- `/api/health` active provider summaries omit API keys and ciphertext;
- probe and model errors are sanitized before being returned or persisted;
- operator/audit payloads must not include provider API keys, authorization headers, or ciphertext.

Rotating `APP_LLM_PROVIDER_SECRET_KEY` requires a planned re-entry of saved provider API keys unless a migration/re-encryption procedure is added first.

## CORS

Set `APP_CORS_ALLOWED_ORIGINS` to explicit browser origins, for example:

```dotenv
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

Origins are comma-separated and whitespace is ignored. Blank entries are ignored. Wildcard origins (`*`) are rejected because the API uses credentialed session cookies and CSRF tokens.

For the primary same-origin nginx deployment, keep this value aligned with the public HTTPS origin. If direct backend browser access is ever exposed for diagnostics, it must also use explicit non-wildcard origins; do not publish the backend with permissive CORS.

## Chat Audit Redaction

Chat audit traces are retained for operator diagnostics and may include redacted prompt, request, model-call, and output content. Redaction is best-effort protection, not an authorization boundary.

Defaults:

```dotenv
APP_AUDIT_REDACTION_ENABLED=true
APP_AUDIT_STORE_RAW_LLM_RESPONSE=false
APP_AUDIT_STORE_REQUEST_MESSAGES=true
APP_AUDIT_MAX_STORED_TEXT_CHARS=12000
```

With the default `APP_AUDIT_STORE_RAW_LLM_RESPONSE=false`, raw LLM response fields are not stored. The final user-facing answer is still stored in audit detail after redaction/truncation so operators can inspect completed runs.

`app.audit.retention-days` defaults to `30`. Set it to `0` or a negative value only when retention deletion must be explicitly disabled.

## Frontend Login

The login screen may prefill the username for operator convenience, but the password field must start empty.

## Static Guardrails

`scripts/phase5-static-gate.py` blocks weak runtime credential and single-admin posture regressions, including:

- a fallback admin password in local scripts or runtime config;
- weak admin credential examples in runtime-facing docs;
- a prefilled admin password in production frontend code.
- `/api/**` falling back to authenticated non-admin access;
- security tests that allow `ROLE_USER` through business APIs;
- new multi-user/RBAC production artifacts without a dedicated security decision.

Run it locally with:

```bash
python3 scripts/phase5-static-gate.py
```
