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
    frontend_password_prefill = re.compile(
        r"\b(password|setPassword)\b[^\n]*useState\(\s*[\"'`]admin[\"'`]\s*\)",
        re.IGNORECASE,
    )

    violations: list[Violation] = []
    for path in sorted(set(files)):
        if path == REPO_ROOT / "scripts/phase5-static-gate.py" or is_test_fixture(path):
            continue
        source = read_text(path)
        for line_no, line in enumerate(source.splitlines(), start=1):
            for regex, pattern, fix in line_patterns:
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
