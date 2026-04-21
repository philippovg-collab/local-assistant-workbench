package com.example.demo.infrastructure.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ReferenceProjectRequest;
import com.example.demo.model.ReferenceWorkspaceRequest;
import com.example.demo.service.ReferenceDataService;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresReferenceDataRepositoryIT extends PostgresIntegrationTestSupport {

    @Test
    void migratesSeedAndPersistsReferenceData() {
        TestDatabase database = resetDatabase();
        PostgresReferenceDataRepository repository = new PostgresReferenceDataRepository(database.jdbcTemplate());
        ReferenceDataService service = new ReferenceDataService(repository);

        var seededWorkspaces = service.listWorkspaces(false);
        assertTrue(seededWorkspaces.stream().anyMatch(workspace ->
            "general".equals(workspace.key())
                && "Общая".equals(workspace.nameRu())
                && workspace.isDefault()
        ));

        service.createWorkspace(new ReferenceWorkspaceRequest(
            "repo-north",
            "Северная рабочая область",
            true,
            10,
            true
        ));

        Integer defaultCount = database.jdbcTemplate().queryForObject(
            "SELECT COUNT(*) FROM reference_workspaces WHERE is_default = true",
            Integer.class
        );
        assertEquals(1, defaultCount);
        assertEquals("repo-north", service.listWorkspaces(false).stream()
            .filter(workspace -> workspace.isDefault())
            .findFirst()
            .orElseThrow()
            .key());

        service.createProject(new ReferenceProjectRequest(
            "repo-north-grid",
            "repo-north",
            "Северная сеть",
            true,
            5
        ));

        var projects = service.listProjects(false, "repo-north");
        assertEquals(1, projects.size());
        assertEquals("repo-north-grid", projects.getFirst().key());
        assertEquals("repo-north", projects.getFirst().workspaceKey());

        assertFalse(repository.projectHasMaterialReferences("repo-north-grid"));
        database.jdbcTemplate().update(
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
            UUID.randomUUID(),
            "Reference usage material",
            "text",
            "Reference usage content",
            "reference usage content",
            "hash-reference-usage",
            "source-reference-usage",
            "direct-text",
            1,
            "repo-north",
            "repo-north-grid",
            "ACTIVE"
        );

        assertTrue(repository.projectHasMaterialReferences("repo-north-grid"));
    }
}
