#!/usr/bin/env python3
"""Print a compact GitHub Step Summary for eval CI reports."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any


SEVERITY_RANK = {
    "BLOCKER": 0,
    "HIGH": 1,
    "CRITICAL": 1,
    "MEDIUM": 2,
    "MAJOR": 2,
    "LOW": 3,
    "MINOR": 3,
}


def load_report(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("Report root must be an object")
    return value


def render(report: dict[str, Any]) -> str:
    lines = [
        f"# Eval {report.get('mode', '-')} report",
        "",
        f"- Gate status: {report.get('gateStatus', '-')}",
        f"- Overall verdict: {report.get('overallVerdict', '-')}",
        f"- Run: {report.get('runId', '-')}",
        f"- Run status: {report.get('runStatus', '-')}",
        f"- Dataset: {report.get('datasetKind', '-')} {report.get('datasetVersion', '-')}",
        f"- Snapshot: {report.get('snapshotId', '-')}",
        f"- Config hash: {report.get('configHash', '-')}",
        f"- Mapping hash: {report.get('mappingHash', '-')}",
        f"- Case refs: {report.get('caseRevisionRefHash', '-')}",
        item_counts_line(report),
        "",
    ]
    lines.extend(regression_section(report))
    lines.extend(metric_section(report))
    lines.extend(failure_section(report))
    lines.extend(artifact_section(report))
    return "\n".join(lines).rstrip() + "\n"


def item_counts_line(report: dict[str, Any]) -> str:
    counts = report.get("itemCounts", {})
    if not isinstance(counts, dict):
        return "- Items: -"
    return (
        f"- Items: total {counts.get('total', '-')} / passed {counts.get('passed', '-')}"
        f" / failed {counts.get('failed', '-')} / error {counts.get('error', '-')}"
    )


def regression_section(report: dict[str, Any]) -> list[str]:
    regressions = sorted(
        (item for item in report.get("worstRegressions", []) if isinstance(item, dict)),
        key=lambda item: (
            SEVERITY_RANK.get(str(item.get("severity", "")).upper(), 9),
            float(item.get("delta", 0.0) or 0.0),
        ),
    )
    lines = ["## Worst Regressions", ""]
    if not regressions:
        return lines + ["- None", ""]
    lines.extend([
        "| Severity | Metric | Delta | Baseline | Candidate | Case |",
        "| --- | --- | ---: | ---: | ---: | --- |",
    ])
    for regression in regressions[:10]:
        lines.append(
            "| {severity} | {metric} | {delta} | {baseline} | {candidate} | {case} |".format(
                severity=regression.get("severity", "-"),
                metric=regression.get("metric", "-"),
                delta=format_number(regression.get("delta")),
                baseline=format_number(regression.get("baseline")),
                candidate=format_number(regression.get("candidate")),
                case=regression.get("caseId") or "-",
            )
        )
    lines.append("")
    return lines


def metric_section(report: dict[str, Any]) -> list[str]:
    metrics = report.get("metrics", {})
    lines = ["## Gate Metrics", ""]
    if not isinstance(metrics, dict) or not metrics:
        return lines + ["- None", ""]
    lines.extend(["| Metric | Value | Count |", "| --- | ---: | ---: |"])
    for name in sorted(metrics):
        metric = metrics[name]
        value = metric.get("value") if isinstance(metric, dict) else metric
        count = metric.get("count", "-") if isinstance(metric, dict) else "-"
        lines.append(f"| {name} | {format_number(value)} | {count} |")
    lines.append("")
    return lines


def failure_section(report: dict[str, Any]) -> list[str]:
    failures = [item for item in report.get("failures", []) if isinstance(item, dict)]
    lines = ["## Failures", ""]
    if not failures:
        return lines + ["- None", ""]
    for failure in failures[:20]:
        lines.append(
            f"- [{failure.get('severity', '-')}] {failure.get('code', '-')}"
            f" case={failure.get('caseId') or '-'}: {failure.get('message') or '-'}"
        )
    lines.append("")
    return lines


def artifact_section(report: dict[str, Any]) -> list[str]:
    artifacts = [item for item in report.get("artifacts", []) if isinstance(item, dict)]
    lines = ["## Artifacts", ""]
    if not artifacts:
        return lines + ["- None", ""]
    for artifact in artifacts:
        label = artifact.get("label") or artifact.get("type") or "artifact"
        path = artifact.get("path")
        if isinstance(path, str) and path:
            lines.append(f"- [{label}]({path})")
        else:
            lines.append(f"- {label}")
    lines.append("")
    return lines


def format_number(value: Any) -> str:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return f"{value:.4f}"
    return "-"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--github-step-summary", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output = render(load_report(args.report))
    print(output, end="")
    if args.github_step_summary and os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as handle:
            handle.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
