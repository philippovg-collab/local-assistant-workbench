#!/usr/bin/env python3
"""Strict static release gate for Eval reproducibility hardening.

The gate intentionally codifies known release blockers before the follow-up fix
phases address them. It reads repository files only; it does not execute Maven,
npm, application code, or write generated artifacts.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]

RETRIEVAL_RUN_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/RetrievalEvalRunService.java")
E2E_RUN_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/EvalE2ERunService.java")
CHAT_RUN_RECONCILER = Path("backend/src/main/java/com/example/demo/service/eval/EvalChatRunReconciler.java")
EXECUTION_CONFIG_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/EvalExecutionConfigService.java")
PREVIEW_REQUEST = Path("backend/src/main/java/com/example/demo/model/eval/RetrievalEvalPreviewRequest.java")
COMPARISON_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/EvalComparisonService.java")
DATASET_LIFECYCLE_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/EvalDatasetLifecycleService.java")
DATASET_VERSIONING_SERVICE = Path("backend/src/main/java/com/example/demo/service/eval/EvalDatasetVersioningService.java")
DATASET_LIFECYCLE_VALIDATOR = Path("backend/src/main/java/com/example/demo/service/eval/EvalDatasetLifecycleValidator.java")
COMPLEXITY_BASELINE = Path("scripts/complexity-budget-baseline.json")

REQUIRED_SOURCE_PATHS = (
    RETRIEVAL_RUN_SERVICE,
    E2E_RUN_SERVICE,
    CHAT_RUN_RECONCILER,
    EXECUTION_CONFIG_SERVICE,
    PREVIEW_REQUEST,
    COMPARISON_SERVICE,
    DATASET_LIFECYCLE_SERVICE,
)

PHASE8_ARTIFACTS = (
    Path("scripts/search-rollout.sh"),
    Path("scripts/search-rollback-read-alias.sh"),
    Path("backend/src/main/resources/db/migration/V54__material_lineage_operator_overrides.sql"),
)

PHASE8_TOKENS = (
    (
        "lineageOverride",
        (
            Path("backend/src/main/java/com/example/demo/model/CreateTextMaterialRequest.java"),
            Path("backend/src/main/java/com/example/demo/controller/MaterialController.java"),
            Path("frontend/src/generated/api-types.ts"),
        ),
        "Expose and persist the material lineageOverride API contract.",
    ),
    (
        "structuredV1ProofStatus",
        (
            Path("backend/src/main/java/com/example/demo/model/QualityLayerHealth.java"),
            Path("frontend/src/types/health.ts"),
            Path("frontend/src/generated/api-types.ts"),
        ),
        "Expose structuredV1ProofStatus in health/runtime state contracts.",
    ),
    (
        "mappingHash",
        (
            Path("backend/src/main/java/com/example/demo/service/eval/EvalRuntimeStateService.java"),
            Path("scripts/search-rollout.sh"),
        ),
        "Record the Elasticsearch mappingHash in rollout evidence and Eval runtime/search snapshots.",
    ),
)

FRONTEND_EVAL_COMPONENT_ROOT = Path("frontend/src/components/eval")
BACKEND_EVAL_SERVICE_ROOT = Path("backend/src/main/java/com/example/demo/service/eval")
BACKEND_EVAL_INFRA_ROOT = Path("backend/src/main/java/com/example/demo/infrastructure/eval")
FRONTEND_API_CLIENT = Path("frontend/src/api/client.ts")

FRONTEND_COMPONENT_BUDGET = 300
BACKEND_SERVICE_BUDGET = 500
ANY_FILE_BUDGET = 700


@dataclass(frozen=True)
class Violation:
    code: str
    severity: str
    path: Path
    line: int
    message: str
    fix_hint: str

    @property
    def relative_path(self) -> str:
        return self.path.as_posix()

    def as_pipe(self) -> str:
        return (
            f"{self.code}|{self.severity}|{self.relative_path}|{self.line}|"
            f"{self.message}|{self.fix_hint}"
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "code": self.code,
            "severity": self.severity,
            "path": self.relative_path,
            "line": self.line,
            "message": self.message,
            "fix_hint": self.fix_hint,
        }


class GateSourceError(RuntimeError):
    """Raised when the gate itself cannot inspect required source files."""


def read_text(repo_root: Path, relative_path: Path) -> str:
    return (repo_root / relative_path).read_text(encoding="utf-8")


def line_number(source: str, index: int) -> int:
    return source.count("\n", 0, max(index, 0)) + 1


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def check_required_sources(repo_root: Path) -> None:
    missing = [path.as_posix() for path in REQUIRED_SOURCE_PATHS if not (repo_root / path).is_file()]
    if missing:
        raise GateSourceError("Missing required source path(s): " + ", ".join(missing))


def collect_violations(repo_root: Path, require_sources: bool = True) -> list[Violation]:
    if require_sources:
        check_required_sources(repo_root)
    violations: list[Violation] = []
    violations.extend(check_pinned_dataset_versions(repo_root))
    violations.extend(check_pinned_scoring_revision(repo_root))
    violations.extend(check_execution_config_hash(repo_root))
    violations.extend(check_preview_dataset_version(repo_root))
    violations.extend(check_compare_metrics(repo_root))
    violations.extend(check_golden_smoke_approval_gate(repo_root))
    violations.extend(check_phase8_artifacts(repo_root))
    violations.extend(check_eval_complexity_budget(repo_root))
    return sorted(violations, key=lambda item: (item.relative_path, item.line, item.code))


def existing_sources(repo_root: Path, paths: Iterable[Path]) -> Iterable[tuple[Path, str]]:
    for path in paths:
        absolute = repo_root / path
        if absolute.is_file():
            yield path, absolute.read_text(encoding="utf-8")


def violation(
    code: str,
    path: Path,
    line: int,
    message: str,
    fix_hint: str,
    severity: str = "BLOCKER",
) -> Violation:
    return Violation(code, severity, path, max(line, 1), message, fix_hint)


def extract_balanced_block(source: str, open_index: int, open_char: str, close_char: str) -> str | None:
    depth = 0
    for index in range(open_index, len(source)):
        char = source[index]
        if char == open_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                return source[open_index + 1:index]
    return None


def method_body(source: str, method_pattern: str) -> tuple[str, int] | None:
    match = re.search(method_pattern, source, flags=re.MULTILINE | re.DOTALL)
    if not match:
        return None
    brace_index = source.find("{", match.end())
    if brace_index == -1:
        return None
    body = extract_balanced_block(source, brace_index, "{", "}")
    if body is None:
        return None
    return body, line_number(source, match.start())


def constructor_call(source: str, class_name: str) -> tuple[str, int] | None:
    match = re.search(r"\bnew\s+" + re.escape(class_name) + r"\s*\(", source)
    if not match:
        return None
    open_index = source.find("(", match.start())
    if open_index == -1:
        return None
    body = extract_balanced_block(source, open_index, "(", ")")
    if body is None:
        return None
    return body, line_number(source, match.start())


def record_components(source: str, record_name: str) -> tuple[str, int] | None:
    match = re.search(r"\bpublic\s+record\s+" + re.escape(record_name) + r"\s*\(", source)
    if not match:
        return None
    open_index = source.find("(", match.start())
    if open_index == -1:
        return None
    components = extract_balanced_block(source, open_index, "(", ")")
    if components is None:
        return None
    return components, line_number(source, match.start())


def check_pinned_dataset_versions(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path, source in existing_sources(repo_root, (RETRIEVAL_RUN_SERVICE, E2E_RUN_SERVICE)):
        selected = method_body(
            source,
            r"\b(?:private|protected|public)\s+(?:[\w.]+\.)?List\s*<\s*EvalCase\s*>\s+selectedCases\s*\(",
        )
        if selected is None:
            continue
        body, method_line = selected
        live_match = re.search(r"\.cases\s*\(\s*\)\s*\.stream\s*\(", body)
        if live_match:
            violations.append(violation(
                "eval-live-case-selection",
                path,
                method_line + line_number(body, live_match.start()) - 1,
                "Persistent Eval run selects cases from live EvalDatasetDetail.cases().",
                "Resolve cases through pinned dataset version caseRevisionRefs before creating run items.",
            ))
    return violations


def check_pinned_scoring_revision(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path, source in existing_sources(repo_root, (CHAT_RUN_RECONCILER,)):
        selected = method_body(source, r"\b(?:private|protected|public)\s+EvalCase\s+evalCase\s*\(")
        if selected is None:
            continue
        body, method_line = selected
        live_match = re.search(r"findDatasetDetail\s*\(|\.cases\s*\(\s*\)\s*\.stream\s*\(", body)
        if live_match:
            violations.append(violation(
                "eval-live-scoring-case",
                path,
                method_line + line_number(body, live_match.start()) - 1,
                "E2E scoring reloads the current dataset/case instead of the run item's pinned revision.",
                "Fetch the case by item.caseId() and item.caseRevision(), then score that immutable revision.",
            ))
    return violations


def check_execution_config_hash(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path, source in existing_sources(repo_root, (EXECUTION_CONFIG_SERVICE,)):
        selected = method_body(
            source,
            r"\b(?:private|protected|public)\s+(?:[\w.]+\.)?Map\s*<\s*String\s*,\s*Object\s*>\s+configPayload\s*\(",
        )
        if selected is None:
            continue
        body, method_line = selected
        signature_start = re.search(r"\bconfigPayload\s*\(", source)
        signature_end = source.find("{", signature_start.start()) if signature_start else -1
        signature = source[signature_start.start():signature_end] if signature_start and signature_end != -1 else ""
        if "gitCommitSha" in signature and not re.search(r"\.put\s*\(\s*\"gitCommitSha\"\s*,\s*gitCommitSha\s*\)", body):
            violations.append(violation(
                "eval-config-hash-missing-git-sha",
                path,
                method_line,
                "configPayload accepts gitCommitSha but omits it from the hashed payload.",
                "Add gitCommitSha to the deterministic Eval execution config hash payload.",
            ))
    return violations


def check_preview_dataset_version(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path, source in existing_sources(repo_root, (PREVIEW_REQUEST,)):
        components = record_components(source, "RetrievalEvalPreviewRequest")
        if components is None:
            continue
        component_text, record_line = components
        if not re.search(r"\bString\s+datasetVersion\b", component_text):
            violations.append(violation(
                "eval-preview-dataset-version-missing",
                path,
                record_line,
                "RetrievalEvalPreviewRequest has no datasetVersion field for pinned preview consistency.",
                "Add nullable datasetVersion and use it when validating persistent retrieval previews.",
            ))

    for path, source in existing_sources(repo_root, (RETRIEVAL_RUN_SERVICE,)):
        call = constructor_call(source, "RetrievalEvalPreviewRequest")
        if call is None:
            continue
        call_text, call_line = call
        if "executionConfigHash()" in call_text and "datasetVersion" not in call_text:
            violations.append(violation(
                "eval-persistent-preview-version-unpinned",
                path,
                call_line,
                "Persistent retrieval run calls preview with executionConfigHash but without datasetVersion.",
                "Pass the resolved datasetVersion into RetrievalEvalPreviewRequest for persistent runs.",
            ))
    return violations


def check_compare_metrics(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path, source in existing_sources(repo_root, (COMPARISON_SERVICE,)):
        empty_match = re.search(
            r"summary\s*\.\s*put\s*\(\s*\"metricSummary\"\s*,\s*(?:[\w.]+\.)?Map\s*\.\s*of\s*\(\s*\)\s*\)",
            source,
        )
        if empty_match:
            violations.append(violation(
                "eval-compare-empty-metric-summary",
                path,
                line_number(source, empty_match.start()),
                "Eval compare summary writes an empty metricSummary.",
                "Populate per-metric baseline, candidate, absolute delta, relative delta, and verdict values.",
            ))
        elif "\"metricSummary\"" not in source:
            class_match = re.search(r"\bclass\s+EvalComparisonService\b", source)
            violations.append(violation(
                "eval-compare-metric-summary-missing",
                path,
                line_number(source, class_match.start()) if class_match else 1,
                "Eval compare summary does not expose metricSummary.",
                "Return a non-empty metricSummary for comparable runs.",
            ))
    return violations


def check_golden_smoke_approval_gate(repo_root: Path) -> list[Violation]:
    lifecycle_sources = dict(existing_sources(repo_root, (DATASET_LIFECYCLE_SERVICE, DATASET_VERSIONING_SERVICE)))
    validator_source = next((source for _, source in existing_sources(repo_root, (DATASET_LIFECYCLE_VALIDATOR,))), "")

    if split_versioning_has_approval_gate(lifecycle_sources.get(DATASET_VERSIONING_SERVICE, ""), validator_source):
        return []

    for path, source in lifecycle_sources.items():
        selected = method_body(
            source,
            r"\b(?:(?:private|protected|public)\s+)?EvalDatasetVersion\s+createDatasetVersion\s*\(",
        )
        if selected is None:
            continue
        body, method_line = selected
        if source_block_has_release_approval_gate(body):
            return []
        return [violation(
            "eval-dataset-version-approval-gate-missing",
            path,
            method_line,
            "Dataset version creation does not enforce non-empty APPROVED cases for GOLDEN/SMOKE datasets.",
            "Reject GOLDEN/SMOKE version creation unless active caseRevisionRefs are non-empty and APPROVED.",
        )]
    return []


def split_versioning_has_approval_gate(versioning_source: str, validator_source: str) -> bool:
    if not versioning_source or not validator_source:
        return False
    selected = method_body(
        versioning_source,
        r"\b(?:(?:private|protected|public)\s+)?EvalDatasetVersion\s+createDatasetVersion\s*\(",
    )
    if selected is None:
        return False
    body, _ = selected
    validator = method_body(
        validator_source,
        r"\b(?:(?:private|protected|public)\s+)?void\s+ensureReleaseVersionAllowed\s*\(",
    )
    if validator is None:
        return False
    validator_body, _ = validator
    return "ensureReleaseVersionAllowed" in body and source_block_has_release_approval_gate(validator_body)


def source_block_has_release_approval_gate(body: str) -> bool:
    has_release_kinds = "EvalDatasetKind.GOLDEN" in body and "EvalDatasetKind.SMOKE" in body
    has_approved_check = "EvalReviewStatus.APPROVED" in body
    has_non_empty_check = re.search(r"\b(isEmpty|!.*isEmpty|size\s*\(\s*\)\s*[><=!])", body) is not None
    return has_release_kinds and has_approved_check and has_non_empty_check


def check_phase8_artifacts(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    for path in PHASE8_ARTIFACTS:
        if not (repo_root / path).is_file():
            violations.append(violation(
                "eval-phase8-artifact-missing",
                path,
                1,
                "Required Phase 8 rollout artifact is missing.",
                "Add the Phase 8 script/migration/proof artifact before release gating can pass.",
            ))

    for token, paths, fix_hint in PHASE8_TOKENS:
        found = False
        first_existing = paths[0]
        for path in paths:
            absolute = repo_root / path
            if not absolute.is_file():
                continue
            first_existing = path
            if token in absolute.read_text(encoding="utf-8"):
                found = True
                break
        if not found:
            violations.append(violation(
                "eval-phase8-token-missing",
                first_existing,
                1,
                f"Required Phase 8 token '{token}' is missing from expected API/proof surfaces.",
                fix_hint,
            ))
    return violations


def check_eval_complexity_budget(repo_root: Path) -> list[Violation]:
    violations: list[Violation] = []
    baseline_paths = eval_baseline_paths(repo_root)
    for path in eval_complexity_files(repo_root):
        relative = path.relative_to(repo_root)
        lines = len(path.read_text(encoding="utf-8").splitlines())
        budgets = triggered_eval_budgets(relative, lines)
        if not budgets:
            continue
        budget_text = ", ".join(budgets)
        if relative.as_posix() in baseline_paths:
            message = f"Eval file exceeds complexity budget ({budget_text}) and is allowlisted in the baseline."
            fix_hint = "Split/reduce the Eval file; do not baseline new Eval god-files."
        else:
            message = f"Eval file exceeds complexity budget ({budget_text})."
            fix_hint = "Split/reduce the Eval file before release; do not add a new complexity baseline entry."
        violations.append(violation(
            "eval-complexity-budget",
            relative,
            1,
            message,
            fix_hint,
        ))
    return violations


def eval_baseline_paths(repo_root: Path) -> set[str]:
    baseline = repo_root / COMPLEXITY_BASELINE
    if not baseline.is_file():
        return set()
    try:
        raw = json.loads(baseline.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return set()
    files = raw.get("files") if isinstance(raw, dict) else None
    if not isinstance(files, dict):
        return set()
    return {path for path in files if is_eval_complexity_relative_path(Path(path))}


def eval_complexity_files(repo_root: Path) -> list[Path]:
    roots = (
        FRONTEND_EVAL_COMPONENT_ROOT,
        BACKEND_EVAL_SERVICE_ROOT,
        BACKEND_EVAL_INFRA_ROOT,
    )
    files: list[Path] = []
    for root in roots:
        absolute_root = repo_root / root
        if not absolute_root.exists():
            continue
        for path in sorted(absolute_root.rglob("*")):
            if path.is_file() and path.suffix in {".java", ".ts", ".tsx"} and ".test." not in path.name:
                files.append(path)
    api_client = repo_root / FRONTEND_API_CLIENT
    if api_client.is_file() and re.search(r"\bEval[A-Za-z]*|fetchEval|createEval", api_client.read_text(encoding="utf-8")):
        files.append(api_client)
    return sorted(set(files))


def is_eval_complexity_relative_path(relative: Path) -> bool:
    return (
        is_relative_to(relative, FRONTEND_EVAL_COMPONENT_ROOT)
        or is_relative_to(relative, BACKEND_EVAL_SERVICE_ROOT)
        or is_relative_to(relative, BACKEND_EVAL_INFRA_ROOT)
        or relative == FRONTEND_API_CLIENT
    )


def triggered_eval_budgets(relative: Path, line_count: int) -> list[str]:
    budgets: list[str] = []
    if relative.suffix == ".tsx" and is_relative_to(relative, FRONTEND_EVAL_COMPONENT_ROOT) and line_count > FRONTEND_COMPONENT_BUDGET:
        budgets.append(f"frontend-component>{FRONTEND_COMPONENT_BUDGET} lines ({line_count})")
    if relative.suffix == ".java" and is_relative_to(relative, BACKEND_EVAL_SERVICE_ROOT) and line_count > BACKEND_SERVICE_BUDGET:
        budgets.append(f"backend-service>{BACKEND_SERVICE_BUDGET} lines ({line_count})")
    if line_count > ANY_FILE_BUDGET:
        budgets.append(f"any-file>{ANY_FILE_BUDGET} lines ({line_count})")
    return budgets


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Strict static release gate for Eval reproducibility blockers.")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT)
    parser.add_argument("--json", action="store_true", help="Print violations as stable JSON.")
    args = parser.parse_args(argv)

    repo_root = args.repo_root.resolve()
    try:
        violations = collect_violations(repo_root, require_sources=True)
    except (OSError, UnicodeDecodeError, GateSourceError) as exc:
        if args.json:
            print(json.dumps({"error": str(exc)}, ensure_ascii=False, indent=2))
        else:
            print(f"Eval release gate error: {exc}", file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps([violation.as_dict() for violation in violations], ensure_ascii=False, indent=2))
    elif violations:
        for item in violations:
            print(item.as_pipe())
    else:
        print("Eval release gate passed.")
    return 1 if violations else 0


if __name__ == "__main__":
    raise SystemExit(main())
