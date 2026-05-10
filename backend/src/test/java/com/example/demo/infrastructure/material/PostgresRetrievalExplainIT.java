package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.pgvector.PGvector;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresRetrievalExplainIT extends PostgresIntegrationTestSupport {

    private PostgresMaterialTestRepositoryBundle repository;
    private JdbcTemplate jdbcTemplate;
    private final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        repository = new PostgresMaterialTestRepositoryBundle(database.jdbcTemplate(), database.transactionManager());
        jdbcTemplate = database.jdbcTemplate();
        seedWorkspace("north-grid", "North Grid");
        seedWorkspace("south-grid", "South Grid");
        seedProject("north-upgrade", "north-grid", "North Upgrade");
        seedProject("south-upgrade", "south-grid", "South Upgrade");
    }

    @Test
    void retrievalPlansUseHotPredicateIndexesForSelectiveFilters() throws Exception {
        seedRetrievalCorpus();

        String semanticPlan = explain(
            """
                SELECT c.material_id
                FROM material_chunks c
                JOIN materials m ON m.id = c.material_id
                WHERE m.version_state = 'ACTIVE'
                  AND m.indexing_status IN ('READY', 'PARTIAL_READY')
                  AND m.document_status = 'ACTIVE'
                  AND m.workspace_key = ?
                  AND m.project_key = ANY (?)
                  AND m.language_code = ANY (?)
                  AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> ?
                LIMIT 5
                """,
            preparedStatement -> {
                preparedStatement.setString(1, "north-grid");
                bindTextArray(preparedStatement, 2, List.of("north-upgrade"));
                bindTextArray(preparedStatement, 3, List.of("RU"));
                preparedStatement.setObject(4, new PGvector(embeddingClient.embed("phase7alpha selective token")));
            }
        );
        String lexicalPlan = explain(
            """
                WITH query_term AS (
                    SELECT plainto_tsquery('simple', ?) AS q
                )
                SELECT c.material_id
                FROM material_chunks c
                JOIN materials m ON m.id = c.material_id
                JOIN query_term qt ON TRUE
                WHERE m.version_state = 'ACTIVE'
                  AND m.indexing_status IN ('READY', 'PARTIAL_READY')
                  AND m.document_status = 'ACTIVE'
                  AND c.search_vector @@ qt.q
                  AND m.workspace_key = ?
                  AND EXISTS (
                      SELECT 1
                      FROM material_tags mt
                      WHERE mt.material_id = m.id
                        AND LOWER(mt.tag_value) = ANY (?)
                  )
                ORDER BY ts_rank_cd(c.search_vector, qt.q) DESC, c.chunk_index ASC
                LIMIT 5
                """,
            preparedStatement -> {
                preparedStatement.setString(1, "phase7alpha selective token");
                preparedStatement.setString(2, "north-grid");
                bindTextArray(preparedStatement, 3, List.of("phase7", "north"));
            }
        );
        String scopePlan = explain(
            """
                WITH scoped AS (
                    SELECT
                        m.id,
                        m.version_state,
                        m.indexing_status,
                        m.document_status,
                        m.period_start,
                        m.period_end
                    FROM materials m
                    WHERE m.workspace_key = ?
                      AND m.project_key = ANY (?)
                      AND m.language_code = ANY (?)
                      AND EXISTS (
                          SELECT 1
                          FROM material_tags mt
                          WHERE mt.material_id = m.id
                            AND LOWER(mt.tag_value) = ANY (?)
                      )
                )
                SELECT
                    (SELECT COUNT(*) FROM materials) AS material_count,
                    (SELECT COUNT(*) FROM scoped WHERE version_state = 'ACTIVE') AS scoped_active_material_count,
                    (SELECT COUNT(*) FROM scoped
                        WHERE version_state = 'ACTIVE'
                          AND indexing_status IN ('READY', 'PARTIAL_READY')
                          AND document_status = 'ACTIVE'
                    ) AS scoped_ready_material_count
                """,
            preparedStatement -> {
                preparedStatement.setString(1, "north-grid");
                bindTextArray(preparedStatement, 2, List.of("north-upgrade"));
                bindTextArray(preparedStatement, 3, List.of("RU"));
                bindTextArray(preparedStatement, 4, List.of("phase7", "north"));
            }
        );

        assertPlanUsesAny(
            semanticPlan,
            "material_chunks_embedding_hnsw_idx",
            "materials_retrieval_ready_scope_idx",
            "materials_retrieval_ready_type_status_idx"
        );
        assertPlanUsesAny(lexicalPlan, "material_chunks_search_vector_idx", "material_tags_retrieval_value_material_idx");
        assertPlanUsesAny(
            scopePlan,
            "materials_retrieval_ready_scope_idx",
            "materials_project_key_idx",
            "material_tags_retrieval_value_material_idx",
            "material_tags_value_idx"
        );
        assertFalse(hasSeqScanOnMaterialTags(lexicalPlan));
        assertFalse(hasSeqScanOnMaterialTags(scopePlan));
    }

    private void seedRetrievalCorpus() {
        Instant baseTime = Instant.parse("2026-04-17T10:00:00Z");
        for (int index = 0; index < 300; index++) {
            boolean north = index % 3 == 0;
            saveMaterial(
                index,
                "phase7alpha selective token ready material " + index,
                north ? "north-grid" : "south-grid",
                north ? "north-upgrade" : "south-upgrade",
                index % 2 == 0 ? MaterialLanguageCode.RU : MaterialLanguageCode.EN,
                MaterialIndexingStatus.READY,
                MaterialVersionState.ACTIVE,
                baseTime.plusSeconds(index)
            );
        }
        for (int index = 300; index < 330; index++) {
            saveMaterial(
                index,
                "phase7alpha selective token inactive material " + index,
                "north-grid",
                "north-upgrade",
                MaterialLanguageCode.RU,
                index % 2 == 0 ? MaterialIndexingStatus.PENDING : MaterialIndexingStatus.READY,
                index % 2 == 0 ? MaterialVersionState.ACTIVE : MaterialVersionState.SUPERSEDED,
                baseTime.plusSeconds(index)
            );
        }
    }

    private void saveMaterial(
        int index,
        String text,
        String workspaceKey,
        String projectKey,
        MaterialLanguageCode languageCode,
        MaterialIndexingStatus status,
        MaterialVersionState versionState,
        Instant timestamp
    ) {
        StoredMaterialChunk chunk = new StoredMaterialChunk(0, text, List.of(), 1, "direct-text", false);
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            "Retrieval material " + index,
            "text",
            null,
            "text/plain",
            text,
            text,
            "hash-phase7-explain-" + index,
            "phase7-explain-" + index,
            "direct-text",
            false,
            null,
            List.of(chunk),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp,
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.POLICY,
                workspaceKey,
                DocumentStatus.ACTIVE,
                projectKey,
                "DOC-" + index,
                languageCode,
                List.of("phase7", workspaceKey.startsWith("north") ? "north" : "south"),
                null,
                null,
                null,
                LocalDate.parse("2026-04-17"),
                "Dana Sarsen",
                "Grid operations",
                "v1",
                languageCode.name().toLowerCase(java.util.Locale.ROOT),
                List.of("phase7"),
                SourceTrustLevel.HIGH,
                workspaceKey.startsWith("north") ? "North Upgrade" : "South Upgrade",
                "GridBuild LLP",
                "APPROVED"
            ))
        );
        repository.catalog().save(record, List.of(chunk));
        if (status == MaterialIndexingStatus.READY || status == MaterialIndexingStatus.PARTIAL_READY) {
            repository.indexingQueue().markIndexingReady(
                record.id(),
                List.of(new StoredEmbeddedMaterialChunk(
                    0,
                    text,
                    1,
                    "direct-text",
                    false,
                    embeddingClient.embed(text)
                )),
                status,
                null,
                null,
                timestamp
            );
        }
    }

    private String explain(String sql, SqlBinder binder) throws Exception {
        StringBuilder plan = new StringBuilder();
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql
            )) {
                binder.bind(preparedStatement);
                try (java.sql.ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        plan.append(resultSet.getString(1));
                    }
                }
            }
        }
        return plan.toString();
    }

    private void assertPlanUsesAny(String plan, String... indexNames) {
        for (String indexName : indexNames) {
            if (plan.contains(indexName)) {
                return;
            }
        }
        throw new AssertionError("Expected plan to use one of " + List.of(indexNames) + " but was: " + plan);
    }

    private boolean hasSeqScanOnMaterialTags(String plan) {
        return plan.contains("\"Node Type\": \"Seq Scan\"") && plan.contains("\"Relation Name\": \"material_tags\"");
    }

    private void bindTextArray(PreparedStatement preparedStatement, int index, List<String> values) throws SQLException {
        preparedStatement.setArray(
            index,
            preparedStatement.getConnection().createArrayOf("text", values.toArray(String[]::new))
        );
    }

    private void seedWorkspace(String key, String nameRu) {
        jdbcTemplate.update(
            """
                INSERT INTO reference_workspaces (
                    key,
                    name_ru,
                    active,
                    sort_order,
                    is_default,
                    created_at,
                    updated_at
                ) VALUES (?, ?, true, 0, false, NOW(), NOW())
                ON CONFLICT (key) DO NOTHING
                """,
            key,
            nameRu
        );
    }

    private void seedProject(String key, String workspaceKey, String nameRu) {
        jdbcTemplate.update(
            """
                INSERT INTO reference_projects (
                    key,
                    workspace_key,
                    name_ru,
                    active,
                    sort_order,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, true, 0, NOW(), NOW())
                ON CONFLICT (key) DO NOTHING
                """,
            key,
            workspaceKey,
            nameRu
        );
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement preparedStatement) throws SQLException;
    }
}
