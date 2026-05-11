#!/usr/bin/env python3
"""Fixture tests for eval-release-gate.py."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name("eval-release-gate.py")
SPEC = importlib.util.spec_from_file_location("eval_release_gate", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {MODULE_PATH}")
gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)


class EvalReleaseGateTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temp_dir.name)
        self.write_safe_repo()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write(self, relative_path: str, source: str) -> Path:
        path = self.repo_root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source.strip() + "\n", encoding="utf-8")
        return path

    def unlink(self, relative_path: str) -> None:
        (self.repo_root / relative_path).unlink()

    def collect(self) -> list:
        return gate.collect_violations(self.repo_root, require_sources=True)

    def codes(self) -> set[str]:
        return {violation.code for violation in self.collect()}

    def write_safe_repo(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/RetrievalEvalRunService.java",
            """
package com.example.demo.service.eval;

class RetrievalEvalRunService {
  private final EvalDatasetVersionCaseResolver resolver = null;
  void createRun(CreateRetrievalEvalRunRequest request) {
    resolver.resolve(request.datasetId(), request.datasetVersion(), request.caseIds(), request.limit());
    new RetrievalEvalPreviewRequest(null, request.datasetId(), request.datasetVersion(), "case-1", 1, null,
      request.executionConfigHash(), null, null, java.util.List.of(), request.referenceInstant(), null, true, false);
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalE2ERunService.java",
            """
package com.example.demo.service.eval;

class EvalE2ERunService {
  private final EvalDatasetVersionCaseResolver resolver = null;
  void createRun(CreateE2EEvalRunRequest request) {
    resolver.resolve(request.datasetId(), request.datasetVersion(), request.caseIds(), request.limit());
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalChatRunReconciler.java",
            """
package com.example.demo.service.eval;

class EvalChatRunReconciler {
  private EvalCase evalCase(EvalRunItem item) {
    return repository.findCaseRevision(item.caseId(), item.caseRevision()).orElseThrow();
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalExecutionConfigService.java",
            """
package com.example.demo.service.eval;

class EvalExecutionConfigService {
  private java.util.Map<String, Object> configPayload(String gitCommitSha, String datasetId) {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("gitCommitSha", gitCommitSha);
    payload.put("datasetId", datasetId);
    return payload;
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/model/eval/RetrievalEvalPreviewRequest.java",
            """
package com.example.demo.model.eval;

public record RetrievalEvalPreviewRequest(
  String query,
  String datasetId,
  String datasetVersion,
  String caseId,
  Integer caseRevision,
  String corpusSnapshotId,
  String executionConfigHash,
  Object knowledgeScope,
  Object retrievalFilters,
  java.util.List<String> dismissedRetrievalHintKeys,
  java.time.Instant referenceInstant,
  Integer limit,
  Boolean includeCandidates,
  Boolean includeNeighborChunks
) {}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalComparisonService.java",
            """
package com.example.demo.service.eval;

class EvalComparisonService {
  Object compatibility() {
    java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
    summary.put("metricSummary", metricSummary());
    return summary;
  }
  private java.util.Map<String, Object> metricSummary() {
    return java.util.Map.of("answer_correctness", java.util.Map.of("verdict", "PASS"));
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalDatasetLifecycleService.java",
            """
package com.example.demo.service.eval;

class EvalDatasetLifecycleService {
  public EvalDatasetVersion createDatasetVersion(String datasetId, CreateEvalDatasetVersionRequest request) {
    if ((dataset.kind() == EvalDatasetKind.GOLDEN || dataset.kind() == EvalDatasetKind.SMOKE)
        && approvedRefs.stream().noneMatch(ref -> ref.reviewStatus() == EvalReviewStatus.APPROVED)) {
      throw new IllegalStateException();
    }
    if (approvedRefs.isEmpty()) {
      throw new IllegalStateException();
    }
    return repository.saveDatasetVersion(version);
  }
}
""",
        )
        self.write("scripts/search-rollout.sh", "mappingHash=abc")
        self.write("scripts/search-rollback-read-alias.sh", "echo rollback")
        self.write(
            "backend/src/main/resources/db/migration/V54__material_lineage_operator_overrides.sql",
            "CREATE TABLE material_lineage_operator_overrides(id text);",
        )
        self.write(
            "backend/src/main/java/com/example/demo/model/CreateTextMaterialRequest.java",
            "record CreateTextMaterialRequest(Object lineageOverride) {}",
        )
        self.write(
            "backend/src/main/java/com/example/demo/model/QualityLayerHealth.java",
            "record QualityLayerHealth(Object structuredV1ProofStatus) {}",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalRuntimeStateService.java",
            "class EvalRuntimeStateService { String mappingHash; }",
        )
        self.write("frontend/src/generated/api-types.ts", "export type T = 'lineageOverride' | 'structuredV1ProofStatus';")
        self.write("frontend/src/types/health.ts", "export type H = { structuredV1ProofStatus?: unknown };")
        self.write(
            "scripts/complexity-budget-baseline.json",
            '{"version":1,"files":{}}',
        )

    def test_positive_fixture_passes(self) -> None:
        self.assertEqual([], self.collect())

    def test_split_lifecycle_approval_gate_passes(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalDatasetLifecycleService.java",
            """
package com.example.demo.service.eval;

class EvalDatasetLifecycleService {
  public EvalDatasetVersion createDatasetVersion(String datasetId, CreateEvalDatasetVersionRequest request) {
    return versioningService.createDatasetVersion(datasetId, request);
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalDatasetVersioningService.java",
            """
package com.example.demo.service.eval;

class EvalDatasetVersioningService {
  EvalDatasetVersion createDatasetVersion(String datasetId, CreateEvalDatasetVersionRequest request) {
    validator.ensureReleaseVersionAllowed(dataset, activeCases);
    return repository.saveDatasetVersion(version);
  }
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalDatasetLifecycleValidator.java",
            """
package com.example.demo.service.eval;

class EvalDatasetLifecycleValidator {
  void ensureReleaseVersionAllowed(EvalDataset dataset, java.util.List<EvalCase> activeCases) {
    if (dataset.kind() != EvalDatasetKind.GOLDEN && dataset.kind() != EvalDatasetKind.SMOKE) {
      return;
    }
    if (activeCases.isEmpty() || activeCases.stream().anyMatch(evalCase -> evalCase.reviewStatus() != EvalReviewStatus.APPROVED)) {
      throw new IllegalStateException();
    }
  }
}
""",
        )

        self.assertNotIn("eval-dataset-version-approval-gate-missing", self.codes())

    def test_live_dataset_case_selection_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/RetrievalEvalRunService.java",
            """
package com.example.demo.service.eval;

class RetrievalEvalRunService {
  private java.util.List<EvalCase> selectedCases(EvalDatasetDetail dataset, java.util.List<String> ids, int limit) {
    return dataset.cases().stream().filter(EvalCase::active).limit(limit).toList();
  }
}
""",
        )

        self.assertIn("eval-live-case-selection", self.codes())

    def test_e2e_live_dataset_case_selection_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalE2ERunService.java",
            """
package com.example.demo.service.eval;

class EvalE2ERunService {
  private java.util.List<EvalCase> selectedCases(EvalDatasetDetail dataset, java.util.List<String> ids, int limit) {
    return dataset.cases().stream().filter(EvalCase::active).limit(limit).toList();
  }
}
""",
        )

        self.assertIn("eval-live-case-selection", self.codes())

    def test_reconciler_live_scoring_case_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalChatRunReconciler.java",
            """
package com.example.demo.service.eval;

class EvalChatRunReconciler {
  private EvalCase evalCase(EvalRun run, EvalRunItem item) {
    EvalDatasetDetail dataset = datasetRepository.findDatasetDetail(run.datasetId()).orElseThrow();
    return dataset.cases().stream().filter(candidate -> candidate.id().equals(item.caseId())).findFirst().orElseThrow();
  }
}
""",
        )

        self.assertIn("eval-live-scoring-case", self.codes())

    def test_empty_metric_summary_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalComparisonService.java",
            """
package com.example.demo.service.eval;

class EvalComparisonService {
  Object compatibility() {
    java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
    summary.put("metricSummary", java.util.Map.of());
    return summary;
  }
}
""",
        )

        self.assertIn("eval-compare-empty-metric-summary", self.codes())

    def test_missing_phase8_artifact_fails(self) -> None:
        self.unlink("scripts/search-rollout.sh")

        self.assertIn("eval-phase8-artifact-missing", self.codes())

    def test_missing_phase8_token_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/model/CreateTextMaterialRequest.java",
            "record CreateTextMaterialRequest(String title) {}",
        )
        self.write("frontend/src/generated/api-types.ts", "export type T = 'structuredV1ProofStatus';")

        self.assertIn("eval-phase8-token-missing", self.codes())

    def test_json_output_contains_stable_fields(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalComparisonService.java",
            """
package com.example.demo.service.eval;

class EvalComparisonService {
  Object compatibility() {
    java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
    summary.put("metricSummary", java.util.Map.of());
    return summary;
  }
}
""",
        )

        payload = [violation.as_dict() for violation in self.collect()]

        self.assertTrue(payload)
        self.assertEqual(
            {"code", "severity", "path", "line", "message", "fix_hint"},
            set(payload[0]),
        )

    def test_preview_request_without_dataset_version_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/model/eval/RetrievalEvalPreviewRequest.java",
            """
package com.example.demo.model.eval;

public record RetrievalEvalPreviewRequest(
  String query,
  String datasetId,
  String caseId,
  Integer caseRevision,
  String executionConfigHash
) {}
""",
        )

        self.assertIn("eval-preview-dataset-version-missing", self.codes())

    def test_config_payload_missing_git_commit_sha_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalExecutionConfigService.java",
            """
package com.example.demo.service.eval;

class EvalExecutionConfigService {
  private java.util.Map<String, Object> configPayload(String gitCommitSha, String datasetId) {
    java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
    payload.put("datasetId", datasetId);
    return payload;
  }
}
""",
        )

        self.assertIn("eval-config-hash-missing-git-sha", self.codes())


if __name__ == "__main__":
    unittest.main()
