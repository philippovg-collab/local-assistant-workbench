#!/usr/bin/env python3
"""Phase 6 complexity budget gate.

The gate tracks production file line counts as a ratchet. New files over the
budget fail immediately, and known large files may not grow past the checked-in
baseline.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import sys
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO_ROOT / "scripts/complexity-budget-baseline.json"

FRONTEND_ROOT = REPO_ROOT / "frontend/src"
FRONTEND_COMPONENT_ROOT = FRONTEND_ROOT / "components"
BACKEND_MAIN_ROOT = REPO_ROOT / "backend/src/main/java"
BACKEND_SERVICE_ROOT = BACKEND_MAIN_ROOT / "com/example/demo/service"

BUDGETS = {
    "frontend-component": 300,
    "backend-service": 500,
    "any-file": 700,
}


@dataclass(frozen=True)
class FileBudget:
    path: Path
    relative_path: str
    line_count: int
    budgets: tuple[str, ...]


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def is_frontend_test_file(path: Path) -> bool:
    relative = path.relative_to(REPO_ROOT).as_posix()
    return (
        ".test." in path.name
        or ".spec." in path.name
        or relative.startswith("frontend/src/test/")
        or relative == "frontend/src/testBuilders.ts"
    )


def production_files() -> list[Path]:
    files: list[Path] = []
    if FRONTEND_ROOT.exists():
        for path in sorted(FRONTEND_ROOT.rglob("*")):
            if path.is_file() and path.suffix in {".ts", ".tsx"} and not is_frontend_test_file(path):
                files.append(path)
    if BACKEND_MAIN_ROOT.exists():
        files.extend(sorted(path for path in BACKEND_MAIN_ROOT.rglob("*.java") if path.is_file()))
    return files


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def triggered_budgets(path: Path, count: int) -> tuple[str, ...]:
    budgets: list[str] = []
    if (
        path.suffix == ".tsx"
        and is_relative_to(path, FRONTEND_COMPONENT_ROOT)
        and count > BUDGETS["frontend-component"]
    ):
        budgets.append("frontend-component")
    if (
        path.suffix == ".java"
        and is_relative_to(path, BACKEND_SERVICE_ROOT)
        and count > BUDGETS["backend-service"]
    ):
        budgets.append("backend-service")
    if count > BUDGETS["any-file"]:
        budgets.append("any-file")
    return tuple(budgets)


def current_over_budget_files() -> dict[str, FileBudget]:
    current: dict[str, FileBudget] = {}
    for path in production_files():
        count = line_count(path)
        budgets = triggered_budgets(path, count)
        if budgets:
            relative = path.relative_to(REPO_ROOT).as_posix()
            current[relative] = FileBudget(path, relative, count, budgets)
    return current


def load_baseline() -> dict[str, Any]:
    try:
        raw = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"Complexity baseline missing: {BASELINE_PATH.relative_to(REPO_ROOT)}", file=sys.stderr)
        return {}
    except json.JSONDecodeError as exc:
        print(f"Complexity baseline is not valid JSON: {exc}", file=sys.stderr)
        return {}
    if not isinstance(raw, dict):
        print("Complexity baseline root must be an object.", file=sys.stderr)
        return {}
    return raw


def baseline_files(raw: dict[str, Any]) -> dict[str, Any]:
    files = raw.get("files")
    if not isinstance(files, dict):
        print("Complexity baseline must contain an object field named `files`.", file=sys.stderr)
        return {}
    return files


def validate_baseline_entry(relative_path: str, entry: Any) -> list[str]:
    errors: list[str] = []
    if not isinstance(entry, dict):
        return [f"{relative_path}: baseline entry must be an object."]
    line_count_value = entry.get("lineCount")
    if not isinstance(line_count_value, int) or line_count_value <= 0:
        errors.append(f"{relative_path}: baseline `lineCount` must be a positive integer.")
    budgets_value = entry.get("budgets")
    if (
        not isinstance(budgets_value, list)
        or not budgets_value
        or any(not isinstance(value, str) or value not in BUDGETS for value in budgets_value)
    ):
        errors.append(f"{relative_path}: baseline `budgets` must list known budget names.")
    reason = entry.get("reason")
    if not isinstance(reason, str) or not reason.strip():
        errors.append(f"{relative_path}: baseline `reason` is required.")
    return errors


def format_budget_names(names: tuple[str, ...] | list[str]) -> str:
    return ", ".join(f"{name}>{BUDGETS[name]}" for name in names)


def main() -> int:
    baseline = load_baseline()
    if not baseline:
        return 1
    baseline_entries = baseline_files(baseline)
    if not baseline_entries:
        return 1

    violations: list[str] = []
    current = current_over_budget_files()

    for relative_path, entry in sorted(baseline_entries.items()):
        violations.extend(validate_baseline_entry(relative_path, entry))
        if not isinstance(entry, dict):
            continue
        expected_count = entry.get("lineCount")
        expected_budgets = entry.get("budgets")
        actual = current.get(relative_path)
        path = REPO_ROOT / relative_path
        if actual is None:
            if path.exists():
                count = line_count(path)
                violations.append(
                    f"{relative_path}: now {count} lines and within budget; remove it from "
                    "scripts/complexity-budget-baseline.json."
                )
            else:
                violations.append(
                    f"{relative_path}: file no longer exists; remove it from "
                    "scripts/complexity-budget-baseline.json."
                )
            continue
        if isinstance(expected_count, int):
            if actual.line_count > expected_count:
                violations.append(
                    f"{relative_path}: grew from baseline {expected_count} to {actual.line_count} lines "
                    f"({format_budget_names(actual.budgets)}). Split the file or update the baseline only "
                    "if this is an intentional pre-existing allowlist."
                )
            elif actual.line_count < expected_count:
                violations.append(
                    f"{relative_path}: shrank from baseline {expected_count} to {actual.line_count} lines; "
                    "lower the baseline so future changes cannot grow it back."
                )
        if isinstance(expected_budgets, list):
            actual_budget_set = set(actual.budgets)
            expected_budget_set = set(expected_budgets)
            if actual_budget_set != expected_budget_set:
                violations.append(
                    f"{relative_path}: budget categories changed from "
                    f"{format_budget_names(sorted(expected_budget_set))} to "
                    f"{format_budget_names(actual.budgets)}; update the baseline entry."
                )

    for relative_path, actual in sorted(current.items()):
        if relative_path not in baseline_entries:
            violations.append(
                f"{relative_path}: {actual.line_count} lines exceeds {format_budget_names(actual.budgets)} "
                "and is not in the baseline. Split it before merging or add an explicit baseline entry "
                "for a known existing god-file."
            )

    if violations:
        print("Complexity budget gate failed:", file=sys.stderr)
        for violation in violations:
            print(f"  {violation}", file=sys.stderr)
        return 1

    print(f"Complexity budget gate passed ({len(current)} over-budget baseline entries tracked).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
