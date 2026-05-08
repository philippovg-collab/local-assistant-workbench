#!/usr/bin/env python3
"""Check backend and frontend coverage against the Phase 7 ratchet baseline."""

from __future__ import annotations

import argparse
import csv
import fnmatch
import json
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
import sys
from typing import Callable


REPO_ROOT = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO_ROOT / "scripts/coverage-baseline.json"


@dataclass(frozen=True)
class CoverageRatio:
    covered: int
    total: int

    @property
    def ratio(self) -> float:
        if self.total <= 0:
            return 1.0
        return self.covered / self.total

    @property
    def percent(self) -> float:
        return self.ratio * 100


def load_baseline() -> dict:
    try:
        return json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"Coverage baseline missing: {BASELINE_PATH.relative_to(REPO_ROOT)}", file=sys.stderr)
        raise SystemExit(1)
    except json.JSONDecodeError as exc:
        print(f"Coverage baseline is invalid JSON: {exc}", file=sys.stderr)
        raise SystemExit(1)


def require_report(path_value: str) -> Path:
    report_path = REPO_ROOT / path_value
    if not report_path.exists():
        print(f"Coverage report missing: {report_path.relative_to(REPO_ROOT)}", file=sys.stderr)
        raise SystemExit(1)
    return report_path


def check_ratio(label: str, actual: CoverageRatio, minimum: float, failures: list[str]) -> None:
    if actual.ratio + 1e-9 < minimum:
        failures.append(
            f"{label}: {actual.percent:.2f}% is below ratchet minimum {minimum * 100:.2f}% "
            f"({actual.covered}/{actual.total})"
        )
    else:
        print(f"{label}: {actual.percent:.2f}% >= {minimum * 100:.2f}% ({actual.covered}/{actual.total})")


def read_backend_packages(report_path: Path) -> dict[str, dict[str, CoverageRatio]]:
    package_counts: dict[str, dict[str, list[int]]] = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    with report_path.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            package_name = row["PACKAGE"]
            package_counts[package_name]["line"][0] += int(row["LINE_COVERED"])
            package_counts[package_name]["line"][1] += int(row["LINE_MISSED"]) + int(row["LINE_COVERED"])
            package_counts[package_name]["branch"][0] += int(row["BRANCH_COVERED"])
            package_counts[package_name]["branch"][1] += int(row["BRANCH_MISSED"]) + int(row["BRANCH_COVERED"])
            package_counts[package_name]["method"][0] += int(row["METHOD_COVERED"])
            package_counts[package_name]["method"][1] += int(row["METHOD_MISSED"]) + int(row["METHOD_COVERED"])

    return {
        package_name: {
            metric: CoverageRatio(covered=count[0], total=count[1])
            for metric, count in metrics.items()
        }
        for package_name, metrics in package_counts.items()
    }


def merge_ratios(packages: dict[str, dict[str, CoverageRatio]], metric: str) -> CoverageRatio:
    covered = sum(metrics[metric].covered for metrics in packages.values())
    total = sum(metrics[metric].total for metrics in packages.values())
    return CoverageRatio(covered=covered, total=total)


def check_backend(baseline: dict) -> int:
    backend = baseline.get("backend", {})
    report_path = require_report(backend.get("report", "backend/target/site/jacoco/jacoco.csv"))
    packages = read_backend_packages(report_path)
    failures: list[str] = []

    for metric, minimum in backend.get("global", {}).items():
        check_ratio(f"backend global {metric}", merge_ratios(packages, metric), float(minimum), failures)

    for package_name, thresholds in backend.get("packages", {}).items():
        if package_name not in packages:
            failures.append(f"backend package {package_name}: package not present in JaCoCo report")
            continue
        for metric, minimum in thresholds.items():
            check_ratio(
                f"backend {package_name} {metric}",
                packages[package_name][metric],
                float(minimum),
                failures,
            )

    if failures:
        print("Backend coverage ratchet failed:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1

    print("Backend coverage ratchet passed.")
    return 0


def read_frontend_records(report_path: Path) -> list[dict[str, int | str]]:
    records: list[dict[str, int | str]] = []
    current: dict[str, int | str] | None = None
    for line in report_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("SF:"):
            current = {
                "file": line[3:],
                "lines_found": 0,
                "lines_hit": 0,
                "functions_found": 0,
                "functions_hit": 0,
                "branches_found": 0,
                "branches_hit": 0,
            }
            continue
        if current is None:
            continue
        if line.startswith("LF:"):
            current["lines_found"] = int(line[3:])
        elif line.startswith("LH:"):
            current["lines_hit"] = int(line[3:])
        elif line.startswith("FNF:"):
            current["functions_found"] = int(line[4:])
        elif line.startswith("FNH:"):
            current["functions_hit"] = int(line[4:])
        elif line.startswith("BRF:"):
            current["branches_found"] = int(line[4:])
        elif line.startswith("BRH:"):
            current["branches_hit"] = int(line[4:])
        elif line == "end_of_record":
            records.append(current)
            current = None
    return records


def frontend_ratio(records: list[dict[str, int | str]], metric: str, predicate: Callable[[str], bool]) -> CoverageRatio:
    found_key = f"{metric}_found"
    hit_key = f"{metric}_hit"
    found = 0
    hit = 0
    for record in records:
        file_name = str(record["file"])
        if predicate(file_name):
            found += int(record[found_key])
            hit += int(record[hit_key])
    return CoverageRatio(covered=hit, total=found)


def check_frontend(baseline: dict) -> int:
    frontend = baseline.get("frontend", {})
    report_path = require_report(frontend.get("report", "frontend/coverage/lcov.info"))
    records = read_frontend_records(report_path)
    failures: list[str] = []

    for metric, minimum in frontend.get("global", {}).items():
        actual = frontend_ratio(records, metric, lambda _path: True)
        check_ratio(f"frontend global {metric}", actual, float(minimum) / 100, failures)

    for pattern, thresholds in frontend.get("groups", {}).items():
        for metric, minimum in thresholds.items():
            actual = frontend_ratio(records, metric, lambda path, glob=pattern: fnmatch.fnmatch(path, glob))
            check_ratio(f"frontend {pattern} {metric}", actual, float(minimum) / 100, failures)

    if failures:
        print("Frontend coverage ratchet failed:", file=sys.stderr)
        for failure in failures:
            print(f"  {failure}", file=sys.stderr)
        return 1

    print("Frontend coverage ratchet passed.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "target",
        nargs="?",
        default="all",
        choices=("backend", "frontend", "all"),
        help="Coverage report set to validate.",
    )
    args = parser.parse_args()

    baseline = load_baseline()
    if args.target == "backend":
        return check_backend(baseline)
    if args.target == "frontend":
        return check_frontend(baseline)

    backend_status = check_backend(baseline)
    frontend_status = check_frontend(baseline)
    return 1 if backend_status or frontend_status else 0


if __name__ == "__main__":
    raise SystemExit(main())
