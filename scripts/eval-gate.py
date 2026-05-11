#!/usr/bin/env python3
"""CI regression gate for backend eval reports."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = REPO_ROOT / "config/eval-gates.yml"
REQUIRED_FIELDS = (
    "gateStatus",
    "runId",
    "runKind",
    "runStatus",
    "datasetKind",
    "datasetVersion",
    "snapshotId",
    "configHash",
    "generatedAt",
    "metrics",
    "slices",
    "failures",
    "worstRegressions",
    "compatibility",
    "overallVerdict",
    "metricSummary",
    "itemCounts",
    "artifacts",
)


@dataclass(frozen=True)
class GateFailure:
    code: str
    message: str


@dataclass(frozen=True)
class GateEvaluation:
    mode: str
    failures: list[GateFailure]

    @property
    def passed(self) -> bool:
        return not self.failures


def load_json(path: Path) -> dict[str, Any]:
    try:
        with path.open(encoding="utf-8") as handle:
            value = json.load(handle)
    except FileNotFoundError as exception:
        raise ValueError(f"Report file does not exist: {path}") from exception
    except json.JSONDecodeError as exception:
        raise ValueError(f"Report is not valid JSON: {exception}") from exception
    if not isinstance(value, dict):
        raise ValueError("Report root must be a JSON object")
    return value


def load_config(path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    return load_json(path)


def mode_config(config: dict[str, Any], mode: str) -> dict[str, Any]:
    modes = config.get("modes")
    if not isinstance(modes, dict) or mode not in modes or not isinstance(modes[mode], dict):
        raise ValueError(f"Gate config does not define mode '{mode}'")
    return modes[mode]


def evaluate_report(
    report: dict[str, Any],
    config: dict[str, Any],
    mode: str,
    report_path: Path | None = None,
) -> GateEvaluation:
    failures: list[GateFailure] = []
    required_mode_config = mode_config(config, mode)
    validate_contract(report, failures)
    if failures:
        return GateEvaluation(mode, failures)

    if report.get("mode") not in (None, mode):
        failures.append(GateFailure("mode_mismatch", f"Report mode {report.get('mode')!r} does not match --mode {mode!r}"))

    gate_status = str(report.get("gateStatus", "")).upper()
    if gate_status == "FAIL":
        failures.append(GateFailure("report_gate_failed", "Backend report gateStatus is FAIL"))
    elif gate_status not in {"PASS", "WARN", "WARNING"}:
        failures.append(GateFailure("invalid_gate_status", f"Unsupported gateStatus {report.get('gateStatus')!r}"))

    evaluate_terminal_run(report, failures)
    expected_kind = required_mode_config.get("requiredDatasetKind")
    if expected_kind and report.get("datasetKind") != expected_kind:
        failures.append(GateFailure(
            "dataset_kind_mismatch",
            f"Expected datasetKind {expected_kind}, got {report.get('datasetKind')}",
        ))

    evaluate_compatibility(report, required_mode_config, failures)
    evaluate_compare_summary(report, required_mode_config, mode, failures)
    evaluate_required_metrics(report, required_mode_config, failures)
    evaluate_max_metrics(report, required_mode_config, failures)
    evaluate_required_slices(report, required_mode_config, failures)
    evaluate_report_failures(report, required_mode_config, failures)
    evaluate_regressions(report, required_mode_config, failures)
    evaluate_artifacts(report, report_path, failures)
    return GateEvaluation(mode, failures)


def validate_contract(report: dict[str, Any], failures: list[GateFailure]) -> None:
    for field in REQUIRED_FIELDS:
        if field not in report:
            failures.append(GateFailure("malformed_report", f"Missing required field '{field}'"))
    if "metrics" in report and not isinstance(report["metrics"], dict):
        failures.append(GateFailure("malformed_report", "Field 'metrics' must be an object"))
    if "slices" in report and not isinstance(report["slices"], list):
        failures.append(GateFailure("malformed_report", "Field 'slices' must be an array"))
    if "failures" in report and not isinstance(report["failures"], list):
        failures.append(GateFailure("malformed_report", "Field 'failures' must be an array"))
    if "worstRegressions" in report and not isinstance(report["worstRegressions"], list):
        failures.append(GateFailure("malformed_report", "Field 'worstRegressions' must be an array"))
    if "compatibility" in report and not isinstance(report["compatibility"], dict):
        failures.append(GateFailure("malformed_report", "Field 'compatibility' must be an object"))
    if "metricSummary" in report and not isinstance(report["metricSummary"], dict):
        failures.append(GateFailure("malformed_report", "Field 'metricSummary' must be an object"))
    if "itemCounts" in report and not isinstance(report["itemCounts"], dict):
        failures.append(GateFailure("malformed_report", "Field 'itemCounts' must be an object"))
    if "artifacts" in report and not isinstance(report["artifacts"], list):
        failures.append(GateFailure("malformed_report", "Field 'artifacts' must be an array"))


def evaluate_terminal_run(report: dict[str, Any], failures: list[GateFailure]) -> None:
    status = str(report.get("runStatus", "")).upper()
    if status in {"QUEUED", "RUNNING", "PENDING", ""}:
        failures.append(GateFailure("non_terminal_run", f"Eval run is not terminal: {status or 'missing'}"))
    elif status != "COMPLETED":
        failures.append(GateFailure("run_not_successful", f"Eval run did not complete successfully: {status}"))


def evaluate_compatibility(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    compatibility = report.get("compatibility", {})
    status = str(compatibility.get("status", "")).upper()
    allowed = {str(item).upper() for item in config.get("allowedCompatibilityStatuses", [])}
    if allowed and status not in allowed:
        reason_codes = []
        reasons = compatibility.get("reasons", [])
        if isinstance(reasons, list):
            reason_codes = [str(reason.get("code")) for reason in reasons if isinstance(reason, dict) and reason.get("code")]
        suffix = f": {', '.join(reason_codes)}" if reason_codes else ""
        failures.append(GateFailure("incompatible_compare", f"Compatibility status {status} is not allowed{suffix}"))


def evaluate_compare_summary(
    report: dict[str, Any],
    config: dict[str, Any],
    mode: str,
    failures: list[GateFailure],
) -> None:
    required_verdict = config.get("requiredOverallVerdict")
    if required_verdict and report.get("overallVerdict") != required_verdict:
        failures.append(GateFailure(
            "overall_verdict_failed",
            f"Expected overallVerdict {required_verdict}, got {report.get('overallVerdict')}",
        ))

    metric_summary = report.get("metricSummary", {})
    requires_metric_summary = bool(config.get("requireMetricSummary")) or mode == "compare"
    compatibility = report.get("compatibility", {})
    compatibility_status = str(compatibility.get("status", "")).upper() if isinstance(compatibility, dict) else ""
    if requires_metric_summary and (not isinstance(metric_summary, dict) or not metric_summary):
        failures.append(GateFailure("metric_summary_missing", "Compare report must include non-empty metricSummary"))
    elif mode == "compare" and compatibility_status == "COMPATIBLE" and not metric_summary:
        failures.append(GateFailure("metric_summary_missing", "Compatible compare cannot have empty metricSummary"))


def evaluate_required_metrics(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    metrics = report.get("metrics", {})
    for name, rule in config.get("requiredMetrics", {}).items():
        value = metric_value(metrics, name)
        threshold = numeric_rule(rule, "min")
        if value is None:
            failures.append(GateFailure("metric_missing", f"Required metric '{name}' is missing"))
        elif threshold is not None and value < threshold:
            failures.append(GateFailure(
                "metric_threshold_failed",
                f"Metric '{name}' value {value:.4f} is below threshold {threshold:.4f}",
            ))


def evaluate_max_metrics(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    metrics = report.get("metrics", {})
    for name, rule in config.get("maxMetrics", {}).items():
        value = metric_value(metrics, name)
        threshold = numeric_rule(rule, "max")
        if value is None:
            failures.append(GateFailure("metric_missing", f"Required max metric '{name}' is missing"))
        elif threshold is not None and value > threshold:
            failures.append(GateFailure(
                "metric_threshold_failed",
                f"Metric '{name}' value {value:.4f} is above maximum {threshold:.4f}",
            ))


def evaluate_required_slices(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    slices = {item.get("name"): item for item in report.get("slices", []) if isinstance(item, dict)}
    for name, slice_rule in config.get("requiredSlices", {}).items():
        report_slice = slices.get(name)
        if not isinstance(report_slice, dict):
            failures.append(GateFailure("slice_missing", f"Required slice '{name}' is missing"))
            continue
        slice_metrics = report_slice.get("metrics", {})
        if not isinstance(slice_metrics, dict):
            failures.append(GateFailure("malformed_report", f"Slice '{name}' metrics must be an object"))
            continue
        for metric_name, metric_rule in slice_rule.get("metrics", {}).items():
            value = metric_value(slice_metrics, metric_name)
            threshold = numeric_rule(metric_rule, "min")
            if value is None:
                failures.append(GateFailure("slice_metric_missing", f"Slice '{name}' metric '{metric_name}' is missing"))
            elif threshold is not None and value < threshold:
                failures.append(GateFailure(
                    "slice_metric_threshold_failed",
                    f"Slice '{name}' metric '{metric_name}' value {value:.4f} is below threshold {threshold:.4f}",
                ))


def evaluate_report_failures(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    disallowed_codes = {str(code).upper() for code in config.get("disallowedFailureCodes", [])}
    blocked_severities = {str(severity).upper() for severity in config.get("blockedRegressionSeverities", [])}
    for item in report.get("failures", []):
        if not isinstance(item, dict):
            failures.append(GateFailure("malformed_report", "Each failure entry must be an object"))
            continue
        code = str(item.get("code", "")).upper()
        severity = str(item.get("severity", "")).upper()
        case_id = item.get("caseId", "-")
        if code in disallowed_codes:
            failures.append(GateFailure("disallowed_failure_code", f"Failure code {code} is disallowed for case {case_id}"))
        if severity in blocked_severities:
            failures.append(GateFailure("blocker_failure", f"BLOCKER failure {code or '-'} for case {case_id}"))


def evaluate_regressions(
    report: dict[str, Any],
    config: dict[str, Any],
    failures: list[GateFailure],
) -> None:
    blocked_severities = {str(severity).upper() for severity in config.get("blockedRegressionSeverities", [])}
    no_drop_metrics = {str(metric) for metric in config.get("noDropMetrics", [])}
    no_rise_metrics = {str(metric) for metric in config.get("noRiseMetrics", [])}
    for item in report.get("worstRegressions", []):
        if not isinstance(item, dict):
            failures.append(GateFailure("malformed_report", "Each worstRegressions entry must be an object"))
            continue
        metric = str(item.get("metric", ""))
        severity = str(item.get("severity", "")).upper()
        delta = number(item.get("delta"))
        case_id = item.get("caseId", "-")
        if severity in blocked_severities:
            failures.append(GateFailure("blocker_regression", f"BLOCKER regression for metric {metric} case {case_id}"))
        if metric in no_drop_metrics and delta is not None and delta < 0.0:
            failures.append(GateFailure("metric_regression", f"Metric {metric} dropped by {delta:.4f} for case {case_id}"))
        if metric in no_rise_metrics and delta is not None and delta > 0.0:
            failures.append(GateFailure("metric_regression", f"Metric {metric} rose by {delta:.4f} for case {case_id}"))


def evaluate_artifacts(
    report: dict[str, Any],
    report_path: Path | None,
    failures: list[GateFailure],
) -> None:
    artifacts = report.get("artifacts", [])
    if not isinstance(artifacts, list):
        return
    for index, artifact in enumerate(artifacts):
        if not isinstance(artifact, dict):
            failures.append(GateFailure("malformed_report", f"Artifact entry #{index + 1} must be an object"))
            continue
        path_value = artifact.get("path")
        if not isinstance(path_value, str) or not path_value.strip():
            failures.append(GateFailure("artifact_path_missing", f"Artifact entry #{index + 1} has no path"))
            continue
        required = bool(artifact.get("required", True))
        if required and report_path is not None and not artifact_exists(path_value, report_path):
            failures.append(GateFailure("artifact_path_missing", f"Required artifact path is missing: {path_value}"))


def artifact_exists(path_value: str, report_path: Path) -> bool:
    candidate = Path(path_value)
    if candidate.is_absolute():
        return candidate.is_file()
    return (report_path.parent / candidate).is_file() or (REPO_ROOT / candidate).is_file()


def metric_value(metrics: dict[str, Any], name: str) -> float | None:
    if name not in metrics:
        return None
    raw = metrics[name]
    if isinstance(raw, (int, float)) and not isinstance(raw, bool):
        return float(raw)
    if isinstance(raw, dict):
        for key in ("value", "average"):
            value = number(raw.get(key))
            if value is not None:
                return value
    return None


def numeric_rule(rule: Any, key: str) -> float | None:
    if not isinstance(rule, dict):
        return None
    return number(rule.get(key))


def number(value: Any) -> float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def print_evaluation(evaluation: GateEvaluation) -> None:
    if evaluation.passed:
        print(f"Eval {evaluation.mode} gate passed")
        return
    print(f"Eval {evaluation.mode} gate failed:", file=sys.stderr)
    for failure in evaluation.failures:
        print(f"- [{failure.code}] {failure.message}", file=sys.stderr)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="Path to eval JSON report")
    parser.add_argument("--mode", required=True, choices=("smoke", "golden", "compare"))
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        report = load_json(args.report)
        config = load_config(args.config)
        evaluation = evaluate_report(report, config, args.mode, args.report)
    except ValueError as exception:
        print(f"Eval gate failed: {exception}", file=sys.stderr)
        return 1
    print_evaluation(evaluation)
    return 0 if evaluation.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
