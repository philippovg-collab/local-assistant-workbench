package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.support.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresReferenceUsageRepositoryIT extends PostgresIntegrationTestSupport {

    @Test
    void detectsReferenceProjectsUsedByMaterials() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        PostgresReferenceUsageRepository repository = new PostgresReferenceUsageRepository(jdbcTemplate);

        jdbcTemplate.update(
            """
                INSERT INTO reference_workspaces (
                    key,
                    name_ru,
                    description,
                    active,
                    sort_order,
                    is_default,
                    created_at,
                    updated_at
                ) VALUES ('north-rag', 'Северный RAG', 'Корпус северного проекта', true, 10, false, now(), now())
                """
        );
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
                ) VALUES ('north-grid', 'north-rag', 'Северная сеть', true, 10, now(), now())
                """
        );

        assertFalse(repository.projectHasMaterialReferences("north-grid"));
        insertMaterial(jdbcTemplate, "north-rag", "north-grid");
        assertTrue(repository.projectHasMaterialReferences("north-grid"));
    }

    private void insertMaterial(JdbcTemplate jdbcTemplate, String workspaceKey, String projectKey) {
        UUID id = UUID.randomUUID();
        String unique = id.toString();
        jdbcTemplate.update(
            """
                INSERT INTO materials (
                    id,
                    title,
                    source_type,
                    content,
                    normalized_content,
                    content_hash,
                    source_key,
                    extractor,
                    created_at,
                    updated_at,
                    lineage_version,
                    workspace_key,
                    project_key,
                    document_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, ?, ?)
                """,
            id,
            "Reference usage material",
            "text",
            "content " + unique,
            "content " + unique,
            "hash-" + unique,
            "source-" + unique,
            "direct-text",
            1,
            workspaceKey,
            projectKey,
            "ACTIVE"
        );
    }
}
