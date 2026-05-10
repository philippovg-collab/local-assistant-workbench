#!/usr/bin/env python3
"""Phase 5 static anti-regression gate.

This script keeps release-hardening invariants close to the repo and produces
actionable failures instead of broad grep output.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import sys


REPO_ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Violation:
    gate: str
    path: Path
    line: int
    pattern: str
    fix: str

    def format(self) -> str:
        relative_path = self.path.relative_to(REPO_ROOT)
        return (
            f"{relative_path}:{self.line}: [{self.gate}] forbidden pattern `{self.pattern}`\n"
            f"  Fix: {self.fix}"
        )


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def java_files(root: Path) -> list[Path]:
    if not root.exists():
        return []
    return sorted(path for path in root.rglob("*.java") if path.is_file())


def text_files(root: Path, suffixes: tuple[str, ...]) -> list[Path]:
    if not root.exists():
        return []
    return sorted(path for path in root.rglob("*") if path.is_file() and path.suffix in suffixes)


def is_test_fixture(path: Path) -> bool:
    relative = path.relative_to(REPO_ROOT).as_posix()
    return (
        "/src/test/" in f"/{relative}"
        or "/test/" in f"/{relative}"
        or ".test." in path.name
        or ".spec." in path.name
        or relative.startswith("frontend/src/test")
        or path.name == "testBuilders.ts"
    )


def line_number(source: str, index: int) -> int:
    return source.count("\n", 0, index) + 1


def check_service_layer_imports() -> list[Violation]:
    service_root = REPO_ROOT / "backend/src/main/java/com/example/demo/service"
    violations: list[Violation] = []
    for path in java_files(service_root):
        for line_no, line in enumerate(read_text(path).splitlines(), start=1):
            if line.strip().startswith("import com.example.demo.infrastructure."):
                violations.append(Violation(
                    "service-boundary",
                    path,
                    line_no,
                    "import com.example.demo.infrastructure.*",
                    "Depend on a service-level domain type or port; keep infrastructure adapters behind port interfaces.",
                ))
    return violations


def check_legacy_material_repository_absent() -> list[Violation]:
    main_root = REPO_ROOT / "backend/src/main/java"
    violations: list[Violation] = []
    for path in sorted(main_root.rglob("PostgresMaterialRepository.java")):
        violations.append(Violation(
            "material-topology",
            path,
            1,
            "PostgresMaterialRepository.java",
            "Use the dedicated Postgres material adapters/DAOs instead of restoring the aggregate repository.",
        ))
    return violations


def check_service_layer_instanceof_ports() -> list[Violation]:
    service_root = REPO_ROOT / "backend/src/main/java/com/example/demo/service"
    patterns = (
        "instanceof MaterialChunkingRepository",
        "instanceof SemanticSearchRepository",
    )
    violations: list[Violation] = []
    for path in java_files(service_root):
        source = read_text(path)
        for pattern in patterns:
            for match in re.finditer(re.escape(pattern), source):
                violations.append(Violation(
                    "service-boundary",
                    path,
                    line_number(source, match.start()),
                    pattern,
                    "Do not branch on concrete port capabilities in services; move behavior behind the port contract.",
                ))
    return violations


def check_scoped_ready_material_ids_absent() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java"
    if not path.exists():
        return []
    source = read_text(path)
    violations: list[Violation] = []
    for match in re.finditer(r"\bscopedReadyMaterialIds\b", source):
        violations.append(Violation(
            "retrieval-scope",
            path,
            line_number(source, match.start()),
            "scopedReadyMaterialIds",
            "Pass MaterialSearchScope into search repositories; do not pre-query corpus UUIDs in the normal retrieval path.",
        ))
    return violations


def method_body(source: str, method_name: str) -> tuple[int, int, int] | None:
    match = re.search(r"\b(?:public|private|protected)\s+[^{;=]+?\s+" + re.escape(method_name) + r"\s*\(", source)
    if not match:
        return None
    brace_start = source.find("{", match.end())
    if brace_start == -1:
        return None

    depth = 0
    for index in range(brace_start, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return brace_start, index + 1, line_number(source, match.start())
    return None


def check_trace_snapshots_do_not_mutate_status() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/service/ChatRunTraceService.java"
    if not path.exists():
        return []
    source = read_text(path)
    snapshot_methods = (
        "saveRequestSnapshot",
        "savePromptSnapshot",
        "saveRetrievalSummary",
    )
    forbidden_calls = (
        "stateMachine.complete",
        "stateMachine.completeWithResult",
        "stateMachine.fail",
        "stateMachine.cancel",
        "stateMachine.transitionStage",
        "repository.completeRun",
        "repository.completeRunWithResult",
        "repository.failRun",
        "repository.cancelRun",
        "repository.transitionStage",
        "repository.updateHeaderStatus",
    )
    violations: list[Violation] = []
    for method_name in snapshot_methods:
        span = method_body(source, method_name)
        if span is None:
            violations.append(Violation(
                "trace-snapshot-status",
                path,
                1,
                method_name,
                f"Keep `{method_name}` present and limited to snapshot/event writes.",
            ))
            continue
        start, end, method_line = span
        body = source[start:end]
        for call in forbidden_calls:
            offset = body.find(call)
            if offset != -1:
                violations.append(Violation(
                    "trace-snapshot-status",
                    path,
                    line_number(source, start + offset),
                    call,
                    f"Move status changes out of `{method_name}`; snapshot writes must not implicitly mutate run status.",
                ))
        if method_name == "saveRetrievalSummary" and "insertEventIfRunMutable" not in body:
            violations.append(Violation(
                "trace-snapshot-status",
                path,
                method_line,
                method_name,
                "Keep retrieval snapshot events guarded with insertEventIfRunMutable so terminal runs are not mutated.",
            ))
    return violations


def check_metadata_alias_overwrites_absent() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/model/MaterialMetadataSnapshot.java"
    if not path.exists():
        return []
    source = read_text(path)
    patterns = (
        (r"\bproject\s*=\s*projectKey\b", "project = projectKey", "Keep legacy `project` and canonical `projectKey` as separate fields."),
        (
            r"\bbusinessStatus\s*=\s*documentStatus\s*\.\s*name\s*\(\s*\)",
            "businessStatus = documentStatus.name()",
            "Keep legacy `businessStatus` and canonical `documentStatus` as separate fields.",
        ),
    )
    violations: list[Violation] = []
    for regex, pattern, fix in patterns:
        for match in re.finditer(regex, source):
            violations.append(Violation(
                "metadata-aliases",
                path,
                line_number(source, match.start()),
                pattern,
                fix,
            ))
    return violations


def check_weak_runtime_credentials_absent() -> list[Violation]:
    roots = (
        REPO_ROOT / "scripts",
        REPO_ROOT / "frontend/src",
        REPO_ROOT / "backend/src/main/java",
        REPO_ROOT / "backend/src/main/resources",
        REPO_ROOT / "docs",
    )
    files: list[Path] = []
    for root in roots:
        files.extend(text_files(root, (".java", ".js", ".jsx", ".ts", ".tsx", ".sh", ".zsh", ".yml", ".yaml", ".properties", ".py", ".md")))
    for path in (REPO_ROOT / "README.md", REPO_ROOT / ".env.example", REPO_ROOT / "docker-compose.yml"):
        if path.exists():
            files.append(path)
    for path in (
        REPO_ROOT / ".env.production.defaults",
        REPO_ROOT / ".env.production.example",
        REPO_ROOT / "docker-compose.prod.yml",
        REPO_ROOT / ".github/workflows/deploy.yml",
    ):
        if path.exists():
            files.append(path)

    line_patterns = (
        (
            re.compile(r"APP_SECURITY_ADMIN_PASSWORD[^\n]*:-admin\b"),
            "APP_SECURITY_ADMIN_PASSWORD:-admin",
            "Do not default the runtime admin password to `admin`; generate a local password or require an explicit value.",
        ),
        (
            re.compile(r"APP_SECURITY_ADMIN_PASSWORD\s*=\s*admin\b"),
            "APP_SECURITY_ADMIN_PASSWORD=admin",
            "Do not ship `admin` as the runtime admin password.",
        ),
        (
            re.compile(r"APP_SECURITY_ADMIN_PASSWORD[^\n]*\$\{APP_SECURITY_ADMIN_PASSWORD:-?admin\}"),
            "${APP_SECURITY_ADMIN_PASSWORD:admin}",
            "Do not ship `admin` as the runtime admin password fallback.",
        ),
        (
            re.compile(r"\badmin\s*/\s*admin\b"),
            "admin/admin",
            "Do not document or ship `admin/admin` as a non-test runtime credential.",
        ),
    )
    production_line_patterns = (
        (
            re.compile(r"APP_SECURITY_ADMIN_PASSWORD\s*=\s*(change-me-admin|change-me|ragstudio|replace-with-[^\s#]+)\b", re.IGNORECASE),
            "placeholder APP_SECURITY_ADMIN_PASSWORD",
            "Production-facing admin passwords must come from secrets and must not use placeholder values.",
        ),
        (
            re.compile(r"POSTGRES_PASSWORD\s*=\s*(change-me|ragstudio|replace-with-[^\s#]+)\b", re.IGNORECASE),
            "placeholder POSTGRES_PASSWORD",
            "Production-facing database passwords must come from secrets and must not use placeholder values.",
        ),
        (
            re.compile(r"POSTGRES_PASSWORD[^\n]*:-ragstudio\b"),
            "POSTGRES_PASSWORD:-ragstudio",
            "Do not ship weak PostgreSQL password fallbacks in production compose.",
        ),
    )
    frontend_password_prefill = re.compile(
        r"\b(password|setPassword)\b[^\n]*useState\(\s*[\"'`]admin[\"'`]\s*\)",
        re.IGNORECASE,
    )

    violations: list[Violation] = []
    for path in sorted(set(files)):
        if path == REPO_ROOT / "scripts/phase5-static-gate.py" or is_test_fixture(path):
            continue
        relative = path.relative_to(REPO_ROOT).as_posix()
        production_facing = (
            relative.startswith(".env.production")
            or relative == "docker-compose.prod.yml"
            or relative == ".github/workflows/deploy.yml"
            or relative == "scripts/linux/preflight-compose.sh"
        )
        source = read_text(path)
        for line_no, line in enumerate(source.splitlines(), start=1):
            if line.strip().startswith("#"):
                continue
            for regex, pattern, fix in line_patterns:
                if regex.search(line):
                    violations.append(Violation(
                        "weak-runtime-credentials",
                        path,
                        line_no,
                        pattern,
                        fix,
                    ))
            if production_facing:
                for regex, pattern, fix in production_line_patterns:
                    if regex.search(line):
                        violations.append(Violation(
                            "weak-runtime-credentials",
                            path,
                            line_no,
                            pattern,
                            fix,
                        ))
            if path.as_posix().startswith((REPO_ROOT / "frontend/src").as_posix()) and frontend_password_prefill.search(line):
                violations.append(Violation(
                    "weak-runtime-credentials",
                    path,
                    line_no,
                    "prefilled admin password",
                    "Keep production login password fields empty; never prefill the admin password in the frontend.",
                ))
    return violations


def check_controllers_do_not_use_direct_chat_executor() -> list[Violation]:
    controller_root = REPO_ROOT / "backend/src/main/java/com/example/demo/controller"
    violations: list[Violation] = []
    for path in java_files(controller_root):
        source = read_text(path)
        for pattern in ("ChatExecutionService", ".execute("):
            for match in re.finditer(re.escape(pattern), source):
                if pattern == ".execute(" and "chatExecutionService" not in source[max(0, match.start() - 80):match.start()]:
                    continue
                violations.append(Violation(
                    "chat-execution-lifecycle",
                    path,
                    line_number(source, match.start()),
                    pattern,
                    "HTTP controllers must submit durable chat runs through ChatRunExecutionService; keep ChatExecutionService as the internal worker executor.",
                ))
    return violations


def check_frontend_production_uses_durable_chat_runs() -> list[Violation]:
    frontend_root = REPO_ROOT / "frontend/src"
    violations: list[Violation] = []
    for path in text_files(frontend_root, (".ts", ".tsx")):
        if is_test_fixture(path):
            continue
        source = read_text(path)
        for match in re.finditer(r"\bapiClient\.executeChat\s*\(", source):
            violations.append(Violation(
                "chat-execution-lifecycle",
                path,
                line_number(source, match.start()),
                "apiClient.executeChat(",
                "Submit through apiClient.submitChatRun and poll status/result; /api/chat is compatibility-only.",
            ))
    return violations


def check_chat_execution_service_is_orchestrator() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/service/ChatExecutionService.java"
    if not path.exists():
        return []
    source = read_text(path)
    violations: list[Violation] = []
    forbidden_methods = (
        "buildRagMessages",
        "buildDirectMessages",
        "writeRetrievedContextJson",
    )
    for method_name in forbidden_methods:
        for match in re.finditer(r"\b" + re.escape(method_name) + r"\b", source):
            violations.append(Violation(
                "chat-execution-lifecycle",
                path,
                line_number(source, match.start()),
                method_name,
                "Keep prompt/message assembly in ChatPromptAssemblyService; ChatExecutionService should only orchestrate lifecycle steps.",
            ))
    line_count = len(source.splitlines())
    if line_count > 350:
        violations.append(Violation(
            "chat-execution-lifecycle",
            path,
            1,
            f"{line_count} lines",
            "Keep ChatExecutionService below 350 lines after the phase 5 split.",
        ))
    return violations


def check_legacy_chat_audit_write_path_absent() -> list[Violation]:
    paths = (
        REPO_ROOT / "backend/src/main/java/com/example/demo/service/audit/port/ChatAuditRepository.java",
        REPO_ROOT / "backend/src/main/java/com/example/demo/service/ChatAuditService.java",
        REPO_ROOT / "backend/src/main/java/com/example/demo/infrastructure/audit/PostgresChatAuditRepository.java",
    )
    violations: list[Violation] = []
    for path in paths:
        if not path.exists():
            continue
        source = read_text(path)
        for pattern in ("void save(", "public String record(", "repository.save("):
            for match in re.finditer(re.escape(pattern), source):
                violations.append(Violation(
                    "chat-legacy-audit",
                    path,
                    line_number(source, match.start()),
                    pattern,
                    "New executions must write only to chat_run_* tables; keep legacy audit support read/delete-only.",
                ))
    return violations


def check_unguarded_chat_queue_deletion_absent() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/service/audit/port/ChatRunQueueRepository.java"
    if not path.exists():
        return []
    source = read_text(path)
    violations: list[Violation] = []
    for match in re.finditer(r"\bdeleteQueueEntry\s*\(\s*String\s+runId\s*\)", source):
        violations.append(Violation(
            "chat-queue-ownership",
            path,
            line_number(source, match.start()),
            "deleteQueueEntry(String runId)",
            "Use deletePendingQueueEntry(runId) or deleteQueueEntryIfOwned(lease) so cleanup preserves worker ownership.",
        ))
    return violations


def check_material_auto_tagging_not_hidden_in_ingestion() -> list[Violation]:
    path = REPO_ROOT / "backend/src/main/java/com/example/demo/service/MaterialIngestionService.java"
    if not path.exists():
        return []
    source = read_text(path)
    violations: list[Violation] = []
    forbidden_patterns = (
        (
            "applyAutoTagsBestEffort",
            "Enqueue durable material auto-tagging tasks and let MaterialAutoTaggingWorkerService own enrichment.",
        ),
        (
            "autoTaggingExecutor",
            "Material ingestion must not submit hidden executor side effects; wake the durable worker after enqueue.",
        ),
    )
    for pattern, fix in forbidden_patterns:
        for match in re.finditer(re.escape(pattern), source):
            violations.append(Violation(
                "material-auto-tagging-lifecycle",
                path,
                line_number(source, match.start()),
                pattern,
                fix,
            ))
    return violations


def check_retrieval_hot_paths_use_bulk_chunk_loading() -> list[Violation]:
    paths = (
        REPO_ROOT / "backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java",
        REPO_ROOT / "backend/src/main/java/com/example/demo/service/RetrievalResultMapper.java",
    )
    forbidden_patterns = (
        "chunkingRepository::findChunks",
        "chunkingRepository.findChunks(",
    )
    violations: list[Violation] = []
    for path in paths:
        if not path.exists():
            continue
        source = read_text(path)
        for pattern in forbidden_patterns:
            for match in re.finditer(re.escape(pattern), source):
                violations.append(Violation(
                    "retrieval-bulk-chunks",
                    path,
                    line_number(source, match.start()),
                    pattern,
                    "Bulk-load chunks with findChunksByMaterialIds in retrieval rerank/search-hit hot paths.",
                ))
    return violations


def check_single_admin_security_posture() -> list[Violation]:
    violations: list[Violation] = []

    security_config = REPO_ROOT / "backend/src/main/java/com/example/demo/config/SecurityConfig.java"
    if security_config.exists():
        source = read_text(security_config)
        for match in re.finditer(r'\.requestMatchers\s*\(\s*"/api/\*\*"\s*\)\s*\.authenticated\s*\(', source):
            violations.append(Violation(
                "single-admin-security-boundary",
                security_config,
                line_number(source, match.start()),
                '.requestMatchers("/api/**").authenticated()',
                "Keep the internal single-admin posture explicit: use hasRole(\"ADMIN\") for /api/**.",
            ))

    test_root = REPO_ROOT / "backend/src/test/java"
    for path in java_files(test_root):
        source = read_text(path)
        for test_chunk in re.split(r"(?=@Test\b)", source):
            if 'roles("USER")' not in test_chunk:
                continue
            if "status().isOk()" in test_chunk:
                violations.append(Violation(
                    "single-admin-security-boundary",
                    path,
                    line_number(source, source.find(test_chunk)),
                    'roles("USER") with status().isOk()',
                    "ROLE_USER must not be accepted by business API security tests while the product is single-admin only.",
                ))

    production_roots = (
        REPO_ROOT / "backend/src/main/java",
        REPO_ROOT / "frontend/src",
    )
    artifact_patterns = (
        (
            re.compile(r"\b(?:tenantId|ownerUserId)\b"),
            "tenant/owner authorization field",
        ),
        (
            re.compile(r"\bRBAC\b"),
            "RBAC production artifact",
        ),
        (
            re.compile(r"\b(?:class|record|interface|enum|type)\s+\w*(?:User|Role)\b"),
            "User/Role production artifact",
        ),
    )
    for root in production_roots:
        for path in text_files(root, (".java", ".ts", ".tsx")):
            source = read_text(path)
            for regex, pattern in artifact_patterns:
                for match in regex.finditer(source):
                    violations.append(Violation(
                        "single-admin-security-boundary",
                        path,
                        line_number(source, match.start()),
                        pattern,
                        "Do not introduce multi-user/RBAC production artifacts without a separate security ADR and roadmap.",
                    ))
    return violations


def main() -> int:
    checks = (
        check_service_layer_imports,
        check_legacy_material_repository_absent,
        check_service_layer_instanceof_ports,
        check_scoped_ready_material_ids_absent,
        check_trace_snapshots_do_not_mutate_status,
        check_metadata_alias_overwrites_absent,
        check_weak_runtime_credentials_absent,
        check_controllers_do_not_use_direct_chat_executor,
        check_frontend_production_uses_durable_chat_runs,
        check_chat_execution_service_is_orchestrator,
        check_legacy_chat_audit_write_path_absent,
        check_unguarded_chat_queue_deletion_absent,
        check_material_auto_tagging_not_hidden_in_ingestion,
        check_retrieval_hot_paths_use_bulk_chunk_loading,
        check_single_admin_security_posture,
    )
    violations: list[Violation] = []
    for check in checks:
        violations.extend(check())

    if violations:
        print(f"Phase 5 static gate failed with {len(violations)} violation(s):", file=sys.stderr)
        for violation in violations:
            print(violation.format(), file=sys.stderr)
        return 1

    print(f"Phase 5 static gate passed ({len(checks)} checks).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
