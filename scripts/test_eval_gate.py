#!/usr/bin/env python3
"""Fixture tests for eval-gate.py."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


MODULE_PATH = Path(__file__).with_name("eval-gate.py")
SPEC = importlib.util.spec_from_file_location("eval_gate", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {MODULE_PATH}")
gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)


class EvalGateTest(unittest.TestCase):

    def test_passes_valid_smoke_report(self) -> None:
        evaluation = gate.evaluate_report(report(), config(), "smoke")

        self.assertTrue(evaluation.passed)

    def test_fails_metric_regression(self) -> None:
        payload = report()
        payload["metrics"]["answer_correctness"]["value"] = 0.5

        evaluation = gate.evaluate_report(payload, config(), "smoke")

        self.assertFalse(evaluation.passed)
        self.assertIn("metric_threshold_failed", failure_codes(evaluation))

    def test_fails_blocker_regression(self) -> None:
        payload = report()
        payload["worstRegressions"] = [{
            "metric": "doc_recall@k",
            "severity": "BLOCKER",
            "delta": 0.0,
            "caseId": "case-1",
        }]

        evaluation = gate.evaluate_report(payload, config(), "smoke")

        self.assertFalse(evaluation.passed)
        self.assertIn("blocker_regression", failure_codes(evaluation))

    def test_fails_incompatible_compare(self) -> None:
        payload = report()
        payload["compatibility"] = {
            "status": "BLOCKED",
            "reasons": [{"code": "snapshot_mismatch"}],
        }

        evaluation = gate.evaluate_report(payload, config(), "smoke")

        self.assertFalse(evaluation.passed)
        self.assertIn("incompatible_compare", failure_codes(evaluation))

    def test_fails_malformed_report(self) -> None:
        payload = report()
        del payload["metrics"]

        evaluation = gate.evaluate_report(payload, config(), "smoke")

        self.assertFalse(evaluation.passed)
        self.assertIn("malformed_report", failure_codes(evaluation))

    def test_fails_non_terminal_run(self) -> None:
        payload = report()
        payload["runStatus"] = "RUNNING"

        evaluation = gate.evaluate_report(payload, config(), "smoke")

        self.assertFalse(evaluation.passed)
        self.assertIn("non_terminal_run", failure_codes(evaluation))

    def test_compare_requires_metric_summary(self) -> None:
        payload = report(mode="compare", dataset_kind="GOLDEN")
        payload["metricSummary"] = {}

        evaluation = gate.evaluate_report(payload, config(), "compare")

        self.assertFalse(evaluation.passed)
        self.assertIn("metric_summary_missing", failure_codes(evaluation))

    def test_compare_requires_pass_overall_verdict(self) -> None:
        payload = report(mode="compare", dataset_kind="GOLDEN")
        payload["overallVerdict"] = "FAIL"

        evaluation = gate.evaluate_report(payload, config(), "compare")

        self.assertFalse(evaluation.passed)
        self.assertIn("overall_verdict_failed", failure_codes(evaluation))


def config() -> dict:
    return gate.load_config()


def report(mode: str = "smoke", dataset_kind: str = "SMOKE") -> dict:
    return {
        "schemaVersion": "eval-ci-report/v1",
        "mode": mode,
        "gateStatus": "PASS",
        "runId": "run-1",
        "runKind": "E2E",
        "runStatus": "COMPLETED",
        "datasetKind": dataset_kind,
        "datasetVersion": "v1",
        "snapshotId": "snapshot-1",
        "configHash": "config-1",
        "generatedAt": "2026-05-11T00:00:00Z",
        "metrics": {
            "answer_correctness": {"value": 1.0, "count": 1},
            "groundedness": {"value": 1.0, "count": 1},
            "citation_precision": {"value": 1.0, "count": 1},
            "instruction_adherence": {"value": 1.0, "count": 1},
            "output_format_validity": {"value": 1.0, "count": 1},
            "doc_recall@k": {"value": 1.0, "count": 1},
            "filter_adherence_retrieval": {"value": 1.0, "count": 1},
            "forbidden_doc_rate": {"value": 0.0, "count": 1},
        },
        "slices": [
            {
                "name": "NO_ANSWER",
                "severity": "BLOCKER",
                "metrics": {"abstention_recall": {"value": 1.0, "count": 1}},
                "failures": [],
            },
            {
                "name": "AMBIGUOUS_QUERY",
                "severity": "BLOCKER",
                "metrics": {"clarification_recall": {"value": 1.0, "count": 1}},
                "failures": [],
            },
        ],
        "failures": [],
        "worstRegressions": [],
        "compatibility": {"status": "COMPATIBLE", "reasons": []},
        "overallVerdict": "PASS",
        "metricSummary": {
            "answer_correctness": {
                "metric": "answer_correctness",
                "baselineValue": 1.0,
                "candidateValue": 1.0,
                "delta": 0.0,
                "relativeDelta": 0.0,
                "direction": "HIGHER_IS_BETTER",
                "verdict": "UNCHANGED",
                "sampleSize": 1,
                "nonScorableCount": 0,
            },
        },
        "itemCounts": {"total": 1, "passed": 1, "failed": 0, "error": 0, "open": 0},
        "artifacts": [],
    }


def failure_codes(evaluation: gate.GateEvaluation) -> set[str]:
    return {failure.code for failure in evaluation.failures}


if __name__ == "__main__":
    unittest.main()
