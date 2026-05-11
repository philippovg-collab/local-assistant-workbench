package com.example.demo.infrastructure.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.StorageException;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotItem;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.model.eval.EvalSeverity;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresEvalRepositoriesIT extends PostgresIntegrationTestSupport {

    @Test
    void migratesEvalSchemaWithExpectedTablesAndChecks() {
        TestDatabase database = resetDatabase();

        assertTrue(tableExists(database, "eval_datasets"));
        assertTrue(tableExists(database, "eval_cases"));
        assertTrue(tableExists(database, "eval_case_reviews"));
        assertTrue(tableExists(database, "eval_case_revisions"));
        assertTrue(tableExists(database, "eval_dataset_versions"));
        assertTrue(tableExists(database, "eval_case_promotions"));
        assertTrue(tableExists(database, "corpus_snapshots"));
        assertTrue(tableExists(database, "corpus_snapshot_items"));
        assertTrue(tableExists(database, "eval_runs"));
        assertTrue(tableExists(database, "eval_run_items"));
        assertTrue(tableExists(database, "eval_run_item_artifacts"));
        assertTrue(tableExists(database, "eval_compares"));

        Integer checkCount = database.jdbcTemplate().queryForObject(
            """
                SELECT COUNT(*)::int
                FROM information_schema.check_constraints
                WHERE constraint_name IN (
                    'eval_datasets_kind_check',
                    'eval_cases_case_type_check',
                    'eval_cases_review_status_check',
                    'eval_case_revisions_review_status_check',
                    'eval_runs_run_kind_check',
                    'eval_run_items_failure_code_check',
                    'eval_compares_status_check',
                    'eval_compares_compatibility_status_check'
                )
                """,
            Integer.class
        );
        assertEquals(8, checkCount);
    }

    @Test
    void persistsEvalCatalogSnapshotsRunsAndCompares() {
        TestDatabase database = resetDatabase();
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var datasetRepository = new PostgresEvalDatasetRepository(database.jdbcTemplate(), objectMapper);
        var snapshotRepository = new PostgresCorpusSnapshotRepository(database.jdbcTemplate(), objectMapper);
        var runRepository = new PostgresEvalRunRepository(database.jdbcTemplate(), objectMapper);
        var compareRepository = new PostgresEvalCompareRepository(database.jdbcTemplate(), objectMapper);

        String datasetId = UUID.randomUUID().toString();
        EvalDataset dataset = datasetRepository.saveDataset(dataset(datasetId, "golden-core", "v1"));
        assertEquals("golden-core", dataset.datasetKey());
        assertEquals(0, datasetRepository.findDatasetSummaries().getFirst().caseCount());

        assertThrows(StorageException.class, () ->
            datasetRepository.saveDataset(dataset(UUID.randomUUID().toString(), "golden-core", "v1"))
        );

        String caseId = UUID.randomUUID().toString();
        EvalCase evalCase = datasetRepository.saveCase(evalCase(datasetId, caseId, "case-1", 1));
        assertEquals("case-1", evalCase.caseKey());
        assertThrows(StorageException.class, () ->
            datasetRepository.saveCase(evalCase(datasetId, UUID.randomUUID().toString(), "case-1", 1))
        );
        EvalCaseRevision caseRevision = datasetRepository.saveCaseRevision(new EvalCaseRevision(
            UUID.randomUUID().toString(),
            evalCase,
            "case-content-hash",
            "tester",
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertEquals("case-content-hash", datasetRepository.findCaseRevision(caseId, 1).orElseThrow().contentHash());
        EvalDatasetVersion datasetVersion = datasetRepository.saveDatasetVersion(new EvalDatasetVersion(
            UUID.randomUUID().toString(),
            datasetId,
            "v1",
            "dataset-hash",
            1,
            List.of(Map.of(
                "caseId", caseId,
                "caseKey", evalCase.caseKey(),
                "revision", caseRevision.caseSnapshot().revision(),
                "reviewStatus", evalCase.reviewStatus().name(),
                "contentHash", caseRevision.contentHash()
            )),
            "tester",
            "snapshot",
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertEquals(datasetVersion.id(), datasetRepository.findDatasetVersion(datasetId, "v1").orElseThrow().id());
        assertEquals(1, datasetRepository.findCaseRevisionsByRefs(List.of(new EvalDatasetVersionCaseRef(
            caseId,
            evalCase.caseKey(),
            1,
            evalCase.reviewStatus(),
            caseRevision.contentHash()
        ))).size());

        EvalCaseReview review = datasetRepository.saveReview(new EvalCaseReview(
            UUID.randomUUID().toString(),
            caseId,
            EvalReviewStatus.APPROVED,
            "reviewer",
            "approved for skeleton test",
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        var detail = datasetRepository.findDatasetDetail(datasetId).orElseThrow();
        assertEquals(1, detail.cases().size());
        assertEquals(review.id(), detail.reviews().getFirst().id());
        assertEquals(1, datasetRepository.findDatasetSummaries().getFirst().caseCount());

        String snapshotId = UUID.randomUUID().toString();
        snapshotRepository.saveSnapshot(snapshot(snapshotId));
        String missingMaterialId = UUID.randomUUID().toString();
        CorpusSnapshotItem item = snapshotRepository.saveItem(new CorpusSnapshotItem(
            UUID.randomUUID().toString(),
            snapshotId,
            missingMaterialId,
            missingMaterialId,
            "Deleted or external material",
            "source-key",
            "ACTIVE",
            1,
            "v1",
            "READY",
            "DOC-1",
            java.time.LocalDate.parse("2026-05-10"),
            "POLICY",
            "ACTIVE",
            "general",
            "project",
            "RU",
            "content-hash",
            "metadata-hash",
            "structured-v1",
            2,
            "chunk-set-hash",
            Map.of("locator", "page-1"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertEquals(missingMaterialId, item.materialId());
        assertEquals("chunk-set-hash", item.chunkSetHash());
        assertEquals(1, snapshotRepository.findSnapshot(snapshotId).orElseThrow().items().size());

        String runId = UUID.randomUUID().toString();
        EvalRun run = runRepository.saveRun(run(runId, datasetId, snapshotId, EvalRunStatus.COMPLETED));
        assertEquals(EvalRunStatus.COMPLETED, run.status());
        EvalRunItem runItem = runRepository.saveItem(new EvalRunItem(
            UUID.randomUUID().toString(),
            runId,
            caseId,
            null,
            EvalRunItemStatus.PASSED,
            null,
            Map.of("answer", "10"),
            Map.of("score", 1),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertNull(runItem.chatRunId());
        EvalRunItemArtifact artifact = runRepository.saveArtifact(new EvalRunItemArtifact(
            runItem.id(),
            EvalRunItemArtifactType.STRUCTURED_OUTPUT,
            Map.of("answer", "10"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertEquals(EvalRunItemArtifactType.STRUCTURED_OUTPUT, artifact.artifactType());
        assertEquals("10", runRepository.findArtifactsByItemId(runItem.id()).getFirst().payload().get("answer"));

        String chatRunId = UUID.randomUUID().toString();
        database.jdbcTemplate().update(
            "INSERT INTO chat_run_headers (id, mode, status, created_at) VALUES (?, ?, ?, ?)",
            UUID.fromString(chatRunId),
            "RAG",
            "COMPLETED",
            Timestamp.from(Instant.parse("2026-05-10T00:00:00Z"))
        );
        String linkedItemId = UUID.randomUUID().toString();
        runRepository.saveItem(new EvalRunItem(
            linkedItemId,
            runId,
            caseId,
            chatRunId,
            EvalRunItemStatus.ERROR,
            EvalFailureCode.CHAT_RUN_FAILED,
            Map.of(),
            Map.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        database.jdbcTemplate().update("DELETE FROM chat_run_headers WHERE id = ?", UUID.fromString(chatRunId));
        EvalRunItem unlinkedItem = runRepository.findItemsByRunId(runId).stream()
            .filter(candidate -> linkedItemId.equals(candidate.id()))
            .findFirst()
            .orElseThrow();
        assertNull(unlinkedItem.chatRunId());

        String candidateRunId = UUID.randomUUID().toString();
        runRepository.saveRun(run(candidateRunId, datasetId, snapshotId, EvalRunStatus.COMPLETED));
        EvalRunCompare compare = compareRepository.saveCompare(new EvalRunCompare(
            UUID.randomUUID().toString(),
            runId,
            candidateRunId,
            EvalCompareStatus.COMPATIBLE,
            null,
            Map.of("delta", 0),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));
        assertEquals(EvalCompareStatus.COMPATIBLE, compare.status());
        assertEquals("COMPATIBLE", compare.compatibilityStatus().name());
        assertEquals(1, compareRepository.findCompares().size());

        assertTrue(datasetRepository.isStorageReady());
        assertTrue(snapshotRepository.isStorageReady());
        assertTrue(runRepository.isStorageReady());
        assertTrue(compareRepository.isStorageReady());
    }

    private boolean tableExists(TestDatabase database, String tableName) {
        Boolean exists = database.jdbcTemplate().queryForObject(
            "SELECT to_regclass(?) IS NOT NULL",
            Boolean.class,
            "public." + tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    private EvalDataset dataset(String id, String key, String version) {
        return new EvalDataset(
            id,
            key,
            EvalDatasetKind.GOLDEN,
            version,
            EvalLifecycleStatus.ACTIVE,
            "Golden core",
            "Core regression set",
            List.of("core"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalCase evalCase(String datasetId, String caseId, String caseKey, int revision) {
        return new EvalCase(
            caseId,
            datasetId,
            caseKey,
            revision,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalSeverity.BLOCKER,
            "What is the approved limit?",
            Map.of("workspaceKey", "general"),
            Map.of("versionLabel", "v1"),
            Map.of("facts", List.of("limit is 10")),
            Map.of("accepted", List.of("10")),
            Map.of("documents", List.of("doc-1")),
            new EvalCaseOrigin("manual", "seed", null, Map.of("reason", "regression")),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private CorpusSnapshot snapshot(String id) {
        return new CorpusSnapshot(
            id,
            "snapshot-1",
            EvalLifecycleStatus.ACTIVE,
            Instant.parse("2026-05-10T00:00:00Z"),
            "material-set-hash",
            "search-state-hash",
            "config-hash",
            Map.of("revisionPins", Map.of()),
            1,
            1,
            0,
            1,
            Map.of("embeddingModel", "test"),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalRun run(String id, String datasetId, String snapshotId, EvalRunStatus status) {
        return new EvalRun(
            id,
            datasetId,
            snapshotId,
            status,
            new EvalExecutionConfig(
                "commit",
                "v1",
                snapshotId,
                "config-hash",
                "prompt-v1",
                "judge-v1",
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.of("temperature", 0)
            ),
            "config-hash",
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z"),
            Map.of("passed", 1),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z")
        );
    }
}
