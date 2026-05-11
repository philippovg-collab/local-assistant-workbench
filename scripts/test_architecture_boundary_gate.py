#!/usr/bin/env python3
"""Fixture tests for architecture-boundary-gate.py."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


MODULE_PATH = Path(__file__).with_name("architecture-boundary-gate.py")
SPEC = importlib.util.spec_from_file_location("architecture_boundary_gate", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {MODULE_PATH}")
gate = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gate
SPEC.loader.exec_module(gate)


class ArchitectureBoundaryGateTest(unittest.TestCase):

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temp_dir.name)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write(self, relative_path: str, source: str) -> Path:
        path = self.repo_root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")
        return path

    def collect(self) -> list:
        return gate.collect_violations(self.repo_root)

    def identifiers(self) -> set[str]:
        return {violation.identifier for violation in self.collect()}

    def test_model_to_service_import_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/model/MaterialDto.java",
            """
package com.example.demo.model;

import com.example.demo.service.material.DocumentBlockType;

record MaterialDto(DocumentBlockType blockType) {}
""",
        )

        self.assertIn(
            "model-forbidden-import|backend/src/main/java/com/example/demo/model/MaterialDto.java|"
            "com.example.demo.service.material.DocumentBlockType",
            self.identifiers(),
        )

    def test_service_to_api_import_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/MaterialService.java",
            """
package com.example.demo.service;

import com.example.demo.api.ApiException;

class MaterialService {}
""",
        )

        self.assertIn(
            "service-api-import|backend/src/main/java/com/example/demo/service/MaterialService.java|"
            "com.example.demo.api.ApiException",
            self.identifiers(),
        )

    def test_service_to_infrastructure_import_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/MaterialService.java",
            """
package com.example.demo.service;

import com.example.demo.infrastructure.material.PostgresMaterialCatalogAdapter;

class MaterialService {}
""",
        )

        self.assertIn(
            "service-infrastructure-import|backend/src/main/java/com/example/demo/service/MaterialService.java|"
            "com.example.demo.infrastructure.material.PostgresMaterialCatalogAdapter",
            self.identifiers(),
        )

    def test_infrastructure_concrete_service_import_fails_but_port_is_allowed(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/material/PostgresAdapter.java",
            """
package com.example.demo.infrastructure.material;

import com.example.demo.service.MaterialService;
import com.example.demo.service.material.port.MaterialCatalogRepository;

class PostgresAdapter {}
""",
        )

        identifiers = self.identifiers()
        self.assertIn(
            "infrastructure-service-usecase-import|backend/src/main/java/com/example/demo/infrastructure/material/"
            "PostgresAdapter.java|com.example.demo.service.MaterialService",
            identifiers,
        )
        self.assertNotIn(
            "infrastructure-service-usecase-import|backend/src/main/java/com/example/demo/infrastructure/material/"
            "PostgresAdapter.java|com.example.demo.service.material.port.MaterialCatalogRepository",
            identifiers,
        )

    def test_provider_to_api_import_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/llm/OllamaClient.java",
            """
package com.example.demo.llm;

import com.example.demo.api.ApiException;

class OllamaClient {}
""",
        )

        self.assertIn(
            "provider-api-import|backend/src/main/java/com/example/demo/llm/OllamaClient.java|"
            "com.example.demo.api.ApiException",
            self.identifiers(),
        )

    def test_production_code_importing_eval_context_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java",
            """
package com.example.demo.service;

import com.example.demo.model.eval.EvalDataset;

class MaterialRetrievalService {}
""",
        )

        self.assertIn(
            "production-eval-import|backend/src/main/java/com/example/demo/service/MaterialRetrievalService.java|"
            "com.example.demo.model.eval.EvalDataset",
            self.identifiers(),
        )

    def test_eval_context_imports_are_allowed_inside_eval_packages(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/eval/EvalCatalogService.java",
            """
package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalDataset;
import com.example.demo.service.eval.port.EvalDatasetRepository;

class EvalCatalogService {}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/eval/PostgresEvalDatasetRepository.java",
            """
package com.example.demo.infrastructure.eval;

import com.example.demo.model.eval.EvalDataset;
import com.example.demo.service.eval.port.EvalDatasetRepository;

class PostgresEvalDatasetRepository {}
""",
        )

        self.assertEqual(set(), self.identifiers())

    def test_chat_trace_repository_eval_sql_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/audit/PostgresChatRunTraceRepository.java",
            """
package com.example.demo.infrastructure.audit;

class PostgresChatRunTraceRepository {
  String sql = "SELECT COUNT(*) FROM eval_runs";
}
""",
        )

        self.assertIn(
            "chat-trace-repository-eval-sql|backend/src/main/java/com/example/demo/infrastructure/audit/"
            "PostgresChatRunTraceRepository.java|eval table access",
            self.identifiers(),
        )

    def test_frontend_component_network_call_fails(self) -> None:
        self.write(
            "frontend/src/components/MaterialPanel.tsx",
            """
import { apiClient } from "@/api/client";

export function MaterialPanel() {
  void apiClient.fetchMaterial("material-1");
  return null;
}
""",
        )

        self.assertIn(
            "frontend-api-client-network-call|frontend/src/components/MaterialPanel.tsx|apiClient.fetchMaterial",
            self.identifiers(),
        )

    def test_frontend_component_api_client_import_fails_even_without_call(self) -> None:
        self.write(
            "frontend/src/components/MaterialPanel.tsx",
            """
import { apiClient } from "@/api/client";

export function MaterialPanel() {
  return null;
}
""",
        )

        self.assertIn(
            "frontend-api-client-import|frontend/src/components/MaterialPanel.tsx|@/api/client",
            self.identifiers(),
        )

    def test_frontend_tests_hooks_api_and_pure_helpers_are_allowed(self) -> None:
        self.write(
            "frontend/src/components/MaterialPanel.test.tsx",
            'import { apiClient } from "@/api/client"; apiClient.fetchMaterial("material-1");\n',
        )
        self.write(
            "frontend/src/hooks/useMaterial.ts",
            'import { apiClient } from "@/api/client"; apiClient.fetchMaterial("material-1");\n',
        )
        self.write(
            "frontend/src/api/materials.ts",
            'import { apiClient } from "@/api/client"; apiClient.fetchMaterial("material-1");\n',
        )
        self.write(
            "frontend/src/components/CurlPreview.tsx",
            'import { buildChatRunCurlExample } from "@/api/curlExample"; buildChatRunCurlExample({});\n',
        )

        self.assertEqual(set(), self.identifiers())

    def test_reference_repository_material_methods_fail(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/service/reference/port/ReferenceDataRepository.java",
            """
package com.example.demo.service.reference.port;

interface ReferenceDataRepository {
  int countMaterialsByWorkspace(String workspaceKey);
  boolean projectHasMaterialReferences(String projectKey);
}
""",
        )

        self.assertIn(
            "reference-repository-material-method|backend/src/main/java/com/example/demo/service/reference/port/"
            "ReferenceDataRepository.java|material summary or usage method",
            self.identifiers(),
        )

    def test_postgres_reference_repository_material_sql_fails(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/reference/PostgresReferenceDataRepository.java",
            """
package com.example.demo.infrastructure.reference;

class PostgresReferenceDataRepository {
  String sql = "SELECT COUNT(*) FROM materials WHERE workspace_key = ?";
}
""",
        )

        self.assertIn(
            "reference-repository-material-sql|backend/src/main/java/com/example/demo/infrastructure/reference/"
            "PostgresReferenceDataRepository.java|materials table access",
            self.identifiers(),
        )

    def test_dedicated_rag_and_usage_material_sql_is_allowed(self) -> None:
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/rag/PostgresRagProjectReadRepository.java",
            """
package com.example.demo.infrastructure.rag;

class PostgresRagProjectReadRepository {
  String sql = "SELECT COUNT(m.id) FROM reference_workspaces rw JOIN materials m ON m.workspace_key = rw.key";
}
""",
        )
        self.write(
            "backend/src/main/java/com/example/demo/infrastructure/material/PostgresReferenceUsageRepository.java",
            """
package com.example.demo.infrastructure.material;

class PostgresReferenceUsageRepository {
  String sql = "SELECT EXISTS (SELECT 1 FROM materials WHERE project_key = ?)";
}
""",
        )

        self.assertEqual(set(), self.identifiers())

    def test_stale_baseline_entry_fails_evaluation(self) -> None:
        baseline_entry = gate.BaselineEntry(
            "service-api-import|backend/src/main/java/com/example/demo/service/Missing.java|com.example.demo.api.ApiException",
            "ERR-001-service-api-Missing-ApiException",
            "service-api-import",
            "backend/src/main/java/com/example/demo/service/Missing.java",
            "com.example.demo.api.ApiException",
        )

        evaluation = gate.evaluate([], [baseline_entry], [])

        self.assertFalse(evaluation.passed)
        self.assertEqual([baseline_entry], evaluation.stale_entries)


if __name__ == "__main__":
    unittest.main()
