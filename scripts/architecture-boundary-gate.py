#!/usr/bin/env python3
"""Architecture boundary ratchet gate."""

from __future__ import annotations

import argparse
from collections.abc import Callable
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO_ROOT / "scripts/architecture-boundary-baseline.json"
BACKEND_PACKAGE_ROOT = Path("backend/src/main/java/com/example/demo")
FRONTEND_ROOT = Path("frontend/src")

API_PREFIX = "com.example.demo.api."
CONFIG_PREFIX = "com.example.demo.config."
CONTROLLER_PREFIX = "com.example.demo.controller."
INFRASTRUCTURE_PREFIX = "com.example.demo.infrastructure."
SERVICE_PREFIX = "com.example.demo.service."
SPRING_HTTP_STATUS = "org.springframework.http.HttpStatus"
EVAL_IMPORT_PREFIXES = (
    "com.example.demo.controller.eval.",
    "com.example.demo.service.eval.",
    "com.example.demo.infrastructure.eval.",
    "com.example.demo.model.eval.",
)

IMPORT_PATTERN = re.compile(r"^\s*import\s+(?:static\s+)?(?P<imported>[^;]+);\s*$")
API_CLIENT_CALL_PATTERN = re.compile(r"\bapiClient\.(?P<method>[A-Za-z_$][\w$]*)\s*\(")
FRONTEND_API_CLIENT_IMPORT_PATTERN = re.compile(r"\bfrom\s+[\"'](?P<module>(?:@|\.{1,2})/api/client)[\"']")
FRONTEND_HELPER_METHODS: set[str] = set()


@dataclass(frozen=True)
class ImportRecord:
    imported: str
    line: int


@dataclass(frozen=True)
class BoundaryRule:
    rule: str
    source_roots: tuple[str, ...]
    matcher: Callable[[str], bool]
    fix: str


@dataclass(frozen=True)
class SourceBoundaryRule:
    rule: str
    relative_path: Path
    pattern: re.Pattern[str]
    imported: str
    fix: str


@dataclass(frozen=True)
class Violation:
    rule: str
    path: Path
    imported: str
    line: int
    fix: str
    repo_root: Path

    @property
    def relative_path(self) -> str:
        return self.path.relative_to(self.repo_root).as_posix()

    @property
    def identifier(self) -> str:
        return f"{self.rule}|{self.relative_path}|{self.imported}"

    def format(self) -> str:
        return (
            f"{self.relative_path}:{self.line}: [{self.rule}] {self.imported}\n"
            f"      Fix: {self.fix}"
        )


@dataclass(frozen=True)
class BaselineEntry:
    identifier: str
    entry_id: str
    rule: str
    path: str
    imported: str


@dataclass(frozen=True)
class GateEvaluation:
    actual_count: int
    new_violations: list[Violation]
    stale_entries: list[BaselineEntry]
    baseline_errors: list[str]

    @property
    def passed(self) -> bool:
        return not self.new_violations and not self.stale_entries and not self.baseline_errors


def java_files(root: Path) -> list[Path]:
    if not root.exists():
        return []
    return sorted(path for path in root.rglob("*.java") if path.is_file())


def text_files(root: Path, suffixes: tuple[str, ...]) -> list[Path]:
    if not root.exists():
        return []
    return sorted(path for path in root.rglob("*") if path.is_file() and path.suffix in suffixes)


def imports_in(path: Path) -> list[ImportRecord]:
    imports: list[ImportRecord] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = IMPORT_PATTERN.match(line)
        if match:
            imports.append(ImportRecord(match.group("imported"), line_number))
    return imports


def line_number(source: str, index: int) -> int:
    return source.count("\n", 0, index) + 1


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def backend_root(repo_root: Path) -> Path:
    return repo_root / BACKEND_PACKAGE_ROOT


def frontend_root(repo_root: Path) -> Path:
    return repo_root / FRONTEND_ROOT


def backend_area_root(repo_root: Path, area: str) -> Path:
    return backend_root(repo_root) / area


def is_under_any_area(path: Path, repo_root: Path, areas: tuple[str, ...]) -> bool:
    return any(is_relative_to(path, backend_area_root(repo_root, area)) for area in areas)


def is_model_forbidden_import(imported: str) -> bool:
    return imported.startswith((
        API_PREFIX,
        CONFIG_PREFIX,
        CONTROLLER_PREFIX,
        INFRASTRUCTURE_PREFIX,
        SERVICE_PREFIX,
    ))


def is_api_import(imported: str) -> bool:
    return imported.startswith(API_PREFIX)


def is_service_infrastructure_import(imported: str) -> bool:
    return imported.startswith(INFRASTRUCTURE_PREFIX)


def is_service_import(imported: str) -> bool:
    return imported.startswith(SERVICE_PREFIX)


def is_spring_http_status_import(imported: str) -> bool:
    return imported == SPRING_HTTP_STATUS


def is_infrastructure_service_usecase_import(imported: str) -> bool:
    if not imported.startswith(SERVICE_PREFIX):
        return False
    if ".port." in imported:
        return False
    service_relative = imported.removeprefix(SERVICE_PREFIX)
    class_name = service_relative.rsplit(".", 1)[-1]
    return "." not in service_relative or class_name.endswith(("Service", "UseCase"))


def is_eval_import(imported: str) -> bool:
    return imported.startswith(EVAL_IMPORT_PREFIXES)


def is_eval_source_path(path: Path, repo_root: Path) -> bool:
    try:
        relative = path.relative_to(backend_root(repo_root))
    except ValueError:
        return False
    return "eval" in relative.parts


BACKEND_RULES: tuple[BoundaryRule, ...] = (
    BoundaryRule(
        "model-forbidden-import",
        ("model",),
        is_model_forbidden_import,
        "Keep public model/DTO types independent of api, config, controller, infrastructure, and service packages.",
    ),
    BoundaryRule(
        "service-api-import",
        ("service",),
        is_api_import,
        "Use application/domain exceptions or limits in service code; map them to API responses only in the api/controller layer.",
    ),
    BoundaryRule(
        "service-infrastructure-import",
        ("service",),
        is_service_infrastructure_import,
        "Depend on service-level ports/domain types; keep infrastructure adapters behind port interfaces.",
    ),
    BoundaryRule(
        "infrastructure-api-import",
        ("infrastructure",),
        is_api_import,
        "Use storage/provider/application exceptions in infrastructure; keep HTTP/API exceptions in the api layer.",
    ),
    BoundaryRule(
        "infrastructure-service-usecase-import",
        ("infrastructure",),
        is_infrastructure_service_usecase_import,
        "Infrastructure may implement ports and use stored value types, but must not call concrete service/use-case classes.",
    ),
    BoundaryRule(
        "provider-api-import",
        ("llm", "embedding"),
        is_api_import,
        "Provider clients should raise provider/application failures; keep ApiException out of llm and embedding packages.",
    ),
    BoundaryRule(
        "lower-layer-http-status-import",
        ("service", "infrastructure", "llm", "embedding"),
        is_spring_http_status_import,
        "Use neutral ErrorType values below the api/controller layer; map them to HttpStatus only in ApiExceptionHandler.",
    ),
    BoundaryRule(
        "config-service-import",
        ("config",),
        is_service_import,
        "Configuration properties should own config enums/types or depend on neutral model types, not service-layer classes.",
    ),
)

REFERENCE_DATA_REPOSITORY_PATH = BACKEND_PACKAGE_ROOT / "service/reference/port/ReferenceDataRepository.java"
POSTGRES_REFERENCE_DATA_REPOSITORY_PATH = (
    BACKEND_PACKAGE_ROOT / "infrastructure/reference/PostgresReferenceDataRepository.java"
)
POSTGRES_CHAT_RUN_TRACE_REPOSITORY_PATH = (
    BACKEND_PACKAGE_ROOT / "infrastructure/audit/PostgresChatRunTraceRepository.java"
)

SOURCE_RULES: tuple[SourceBoundaryRule, ...] = (
    SourceBoundaryRule(
        "reference-repository-material-method",
        REFERENCE_DATA_REPOSITORY_PATH,
        re.compile(r"\b(countMaterialsByWorkspace|countReadyMaterialsByWorkspace|projectHasMaterialReferences)\b"),
        "material summary or usage method",
        "Keep reference data repository focused on reference tables; use RagProjectReadRepository for counts and ReferenceUsageRepository for material usage checks.",
    ),
    SourceBoundaryRule(
        "reference-repository-material-sql",
        POSTGRES_REFERENCE_DATA_REPOSITORY_PATH,
        re.compile(r"\b(?:FROM|JOIN)\s+materials\b", re.IGNORECASE),
        "materials table access",
        "PostgresReferenceDataRepository must not read materials; use rag/material infrastructure adapters behind dedicated ports.",
    ),
    SourceBoundaryRule(
        "chat-trace-repository-eval-sql",
        POSTGRES_CHAT_RUN_TRACE_REPOSITORY_PATH,
        re.compile(r"\beval_[a-z0-9_]+\b", re.IGNORECASE),
        "eval table access",
        "Keep eval persistence in infrastructure.eval repositories; PostgresChatRunTraceRepository must stay focused on chat-run trace tables.",
    ),
)


def collect_backend_violations(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    root = backend_root(repo_root)
    for path in java_files(root):
        imports = imports_in(path)
        for rule in BACKEND_RULES:
            if not is_under_any_area(path, repo_root, rule.source_roots):
                continue
            for import_record in imports:
                if rule.matcher(import_record.imported):
                    violations.append(Violation(
                        rule.rule,
                        path,
                        import_record.imported,
                        import_record.line,
                        rule.fix,
                        repo_root,
                    ))
        if not is_eval_source_path(path, repo_root):
            for import_record in imports:
                if is_eval_import(import_record.imported):
                    violations.append(Violation(
                        "production-eval-import",
                        path,
                        import_record.imported,
                        import_record.line,
                        "Keep eval dependencies inside *.eval packages; production chat/search/audit code must not depend on eval bounded context types.",
                        repo_root,
                    ))
    return violations


def is_frontend_test_file(path: Path, repo_root: Path) -> bool:
    relative = path.relative_to(repo_root).as_posix()
    return (
        ".test." in path.name
        or ".spec." in path.name
        or relative.startswith("frontend/src/test/")
        or relative == "frontend/src/testBuilders.ts"
    )


def is_frontend_allowed_api_client_layer(path: Path, repo_root: Path) -> bool:
    relative = path.relative_to(repo_root).as_posix()
    return relative.startswith("frontend/src/api/") or relative.startswith("frontend/src/hooks/")


def collect_frontend_violations(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path in text_files(frontend_root(repo_root), (".ts", ".tsx")):
        if is_frontend_test_file(path, repo_root) or is_frontend_allowed_api_client_layer(path, repo_root):
            continue
        source = path.read_text(encoding="utf-8")
        for match in FRONTEND_API_CLIENT_IMPORT_PATTERN.finditer(source):
            violations.append(Violation(
                "frontend-api-client-import",
                path,
                match.group("module"),
                line_number(source, match.start()),
                "Import apiClient only from hooks or frontend/src/api; components/App must receive data through hooks, props, or pure helpers.",
                repo_root,
            ))
        for match in API_CLIENT_CALL_PATTERN.finditer(source):
            method = match.group("method")
            if method in FRONTEND_HELPER_METHODS:
                continue
            violations.append(Violation(
                "frontend-api-client-network-call",
                path,
                f"apiClient.{method}",
                line_number(source, match.start()),
                "Move network reads/mutations into frontend/src/hooks or frontend/src/api; components may only use non-network helpers.",
                repo_root,
            ))
    return violations


def collect_source_violations(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for rule in SOURCE_RULES:
        path = repo_root / rule.relative_path
        if not path.exists():
            continue
        source = path.read_text(encoding="utf-8")
        for match in rule.pattern.finditer(source):
            violations.append(Violation(
                rule.rule,
                path,
                rule.imported,
                line_number(source, match.start()),
                rule.fix,
                repo_root,
            ))
    return violations


def collect_violations(repo_root: Path = REPO_ROOT) -> list[Violation]:
    violations = (
        collect_backend_violations(repo_root)
        + collect_frontend_violations(repo_root)
        + collect_source_violations(repo_root)
    )
    return sorted(violations, key=lambda violation: violation.identifier)


def validate_baseline_entry(raw_entry: Any, index: int) -> tuple[BaselineEntry | None, list[str]]:
    errors: list[str] = []
    if not isinstance(raw_entry, dict):
        return None, [f"allowed[{index}] must be an object."]

    required_fields = ("id", "rule", "path", "imported", "riskId", "ownerPhase", "reason", "removalCriterion")
    for field in required_fields:
        value = raw_entry.get(field)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"allowed[{index}] missing non-empty `{field}`.")

    if errors:
        return None, errors

    rule = raw_entry["rule"]
    path = raw_entry["path"]
    imported = raw_entry["imported"]
    return BaselineEntry(
        f"{rule}|{path}|{imported}",
        raw_entry["id"],
        rule,
        path,
        imported,
    ), []


def load_baseline(path: Path = BASELINE_PATH, repo_root: Path = REPO_ROOT) -> tuple[list[BaselineEntry], list[str]]:
    if not path.exists():
        return [], [f"Architecture boundary baseline not found: {path.relative_to(repo_root)}"]

    try:
        data: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [], [f"Architecture boundary baseline is not valid JSON: {exc}"]

    errors: list[str] = []
    if data.get("version") != 2:
        errors.append("Architecture boundary baseline must use schema `version: 2`.")

    entries = data.get("allowed")
    if not isinstance(entries, list):
        return [], errors + ["Architecture boundary baseline must contain an `allowed` list."]

    baseline_entries: list[BaselineEntry] = []
    seen_ids: set[str] = set()
    seen_identifiers: set[str] = set()
    for index, raw_entry in enumerate(entries):
        entry, entry_errors = validate_baseline_entry(raw_entry, index)
        errors.extend(entry_errors)
        if entry is None:
            continue
        if entry.entry_id in seen_ids:
            errors.append(f"Duplicate baseline id `{entry.entry_id}`.")
        if entry.identifier in seen_identifiers:
            errors.append(f"Duplicate baseline identifier `{entry.identifier}`.")
        seen_ids.add(entry.entry_id)
        seen_identifiers.add(entry.identifier)
        baseline_entries.append(entry)

    return baseline_entries, errors


def evaluate(violations: list[Violation], baseline_entries: list[BaselineEntry], baseline_errors: list[str]) -> GateEvaluation:
    actual_by_identifier = {violation.identifier: violation for violation in violations}
    baseline_by_identifier = {entry.identifier: entry for entry in baseline_entries}
    new_identifiers = sorted(set(actual_by_identifier) - set(baseline_by_identifier))
    stale_identifiers = sorted(set(baseline_by_identifier) - set(actual_by_identifier))
    return GateEvaluation(
        len(actual_by_identifier),
        [actual_by_identifier[identifier] for identifier in new_identifiers],
        [baseline_by_identifier[identifier] for identifier in stale_identifiers],
        baseline_errors,
    )


def run_gate(repo_root: Path = REPO_ROOT, baseline_path: Path = BASELINE_PATH) -> GateEvaluation:
    violations = collect_violations(repo_root)
    baseline_entries, baseline_errors = load_baseline(baseline_path, repo_root)
    return evaluate(violations, baseline_entries, baseline_errors)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help="Repository root to scan.")
    parser.add_argument("--baseline", type=Path, default=BASELINE_PATH, help="Architecture baseline JSON path.")
    args = parser.parse_args(argv)

    repo_root = args.repo_root.resolve()
    baseline_path = args.baseline.resolve()
    evaluation = run_gate(repo_root, baseline_path)

    if not evaluation.passed:
        print("Architecture boundary gate failed:", file=sys.stderr)
        if evaluation.baseline_errors:
            print("  Baseline schema error(s):", file=sys.stderr)
            for error in evaluation.baseline_errors:
                print(f"    {error}", file=sys.stderr)
        if evaluation.new_violations:
            print("  New violation(s):", file=sys.stderr)
            for violation in evaluation.new_violations:
                print(f"    {violation.format()}", file=sys.stderr)
        if evaluation.stale_entries:
            print("  Stale baseline entry(s):", file=sys.stderr)
            for entry in evaluation.stale_entries:
                print(f"    {entry.entry_id}: {entry.identifier}", file=sys.stderr)
        return 1

    print(f"Architecture boundary gate passed ({evaluation.actual_count} baseline violation(s)).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
