package com.example.demo.infrastructure.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.support.PostgresIntegrationTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

class PostgresRagProjectReadRepositoryIT extends PostgresIntegrationTestSupport {

    @Test
    void readsProjectSummariesWithGroupedMaterialCounts() {
        TestDatabase database = resetDatabase();
        seedProjectsAndMaterials(database.jdbcTemplate());
        PostgresRagProjectReadRepository repository = new PostgresRagProjectReadRepository(database.jdbcTemplate());

        var north = repository.findSummaryByKey("north-rag").orElseThrow();
        var archive = repository.findSummaryByKey("archive-rag").orElseThrow();
        var activeProjects = repository.listSummariesWithCounts(true);

        assertEquals("Северный RAG", north.name());
        assertEquals("Корпус северного проекта", north.description());
        assertEquals(7, north.materialCount());
        assertEquals(2, north.readyMaterialCount());
        assertEquals(1, archive.materialCount());
        assertEquals(1, archive.readyMaterialCount());
        assertTrue(activeProjects.stream().anyMatch(project -> "north-rag".equals(project.key())));
        assertFalse(activeProjects.stream().anyMatch(project -> "archive-rag".equals(project.key())));
    }

    @Test
    void listsProjectSummariesWithOnePreparedStatement() {
        TestDatabase database = resetDatabase();
        seedProjectsAndMaterials(database.jdbcTemplate());
        CountingDataSource countingDataSource = new CountingDataSource(database.dataSource());
        PostgresRagProjectReadRepository repository = new PostgresRagProjectReadRepository(
            new JdbcTemplate(countingDataSource)
        );

        countingDataSource.reset();
        var summaries = repository.listSummariesWithCounts(false);

        assertEquals(3, summaries.size());
        assertEquals(1, countingDataSource.preparedStatementCount());
        Map<String, Long> countsByKey = summaries.stream()
            .collect(java.util.stream.Collectors.toMap(summary -> summary.key(), summary -> summary.materialCount()));
        assertEquals(7L, countsByKey.get("north-rag"));
        assertEquals(1L, countsByKey.get("archive-rag"));
    }

    private void seedProjectsAndMaterials(JdbcTemplate jdbcTemplate) {
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
                ) VALUES
                    ('north-rag', 'Северный RAG', 'Корпус северного проекта', true, 10, false, now(), now()),
                    ('archive-rag', 'Архивный RAG', 'Архивный корпус', false, 20, false, now(), now())
                """
        );

        insertMaterial(jdbcTemplate, "north-rag", "north-ready", "READY", "ACTIVE", "ACTIVE");
        insertMaterial(jdbcTemplate, "north-rag", "north-partial", "PARTIAL_READY", "ACTIVE", "ACTIVE");
        insertMaterial(jdbcTemplate, "north-rag", "north-pending", "PENDING", "ACTIVE", "ACTIVE");
        insertMaterial(jdbcTemplate, "north-rag", "north-superseded", "READY", "SUPERSEDED", "ACTIVE");
        insertMaterial(jdbcTemplate, "north-rag", "north-archived", "READY", "ACTIVE", "ARCHIVED");
        UUID future = insertMaterial(jdbcTemplate, "north-rag", "north-future", "READY", "ACTIVE", "ACTIVE");
        UUID expired = insertMaterial(jdbcTemplate, "north-rag", "north-expired", "READY", "ACTIVE", "ACTIVE");
        insertMaterial(jdbcTemplate, "archive-rag", "archive-ready", "READY", "ACTIVE", "ACTIVE");

        jdbcTemplate.update("UPDATE materials SET period_start = CURRENT_DATE + 1 WHERE id = ?", future);
        jdbcTemplate.update("UPDATE materials SET period_end = CURRENT_DATE - 1 WHERE id = ?", expired);
    }

    private UUID insertMaterial(
        JdbcTemplate jdbcTemplate,
        String workspaceKey,
        String title,
        String indexingStatus,
        String versionState,
        String documentStatus
    ) {
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
                    document_status,
                    indexing_status,
                    version_state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now(), ?, ?, ?, ?, ?)
                """,
            id,
            title,
            "text",
            "content " + unique,
            "content " + unique,
            "hash-" + unique,
            "source-" + unique,
            "direct-text",
            1,
            workspaceKey,
            documentStatus,
            indexingStatus,
            versionState
        );
        return id;
    }

    private static final class CountingDataSource extends AbstractDataSource {

        private final DataSource delegate;
        private final AtomicInteger preparedStatementCount = new AtomicInteger();

        private CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return wrap(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return wrap(delegate.getConnection(username, password));
        }

        void reset() {
            preparedStatementCount.set(0);
        }

        int preparedStatementCount() {
            return preparedStatementCount.get();
        }

        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if (method.getName().startsWith("prepareStatement")) {
                        preparedStatementCount.incrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getTargetException();
                    }
                }
            );
        }
    }
}
