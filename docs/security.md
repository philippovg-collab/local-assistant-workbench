# Security

## Security Model

Rag Studio is currently a single-admin internal deployment. It is intended to run behind a customer-controlled network or reverse proxy, with one administrative operator account.

The following concepts are product filters, not authorization boundaries:

- workspaces;
- projects;
- knowledge presets;
- material scopes.

They help organize retrieval and UI workflows, but they do not isolate data between users. Do not run this system as a multi-tenant service until full RBAC, user management, and per-workspace authorization are implemented.

All `/api/**` routes require an authenticated session except:

- `GET /api/liveness`;
- `/api/auth/**`.

`/api/chat-runs/**` is admin-only because it exposes audit traces, prompts, sources, and model execution details.

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

The Compose file refuses to start the hardened backend when the admin password is missing.

## CORS

Set `APP_CORS_ALLOWED_ORIGINS` to explicit browser origins, for example:

```dotenv
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

Origins are comma-separated and whitespace is ignored. Blank entries are ignored. Wildcard origins (`*`) are rejected because the API uses credentialed session cookies and CSRF tokens.

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

`scripts/phase5-static-gate.py` blocks weak runtime credential regressions, including:

- a fallback admin password in local scripts or runtime config;
- weak admin credential examples in runtime-facing docs;
- a prefilled admin password in production frontend code.

Run it locally with:

```bash
python3 scripts/phase5-static-gate.py
```
