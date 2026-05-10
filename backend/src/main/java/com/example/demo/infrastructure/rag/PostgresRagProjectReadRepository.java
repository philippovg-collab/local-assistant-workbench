package com.example.demo.infrastructure.rag;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.rag.StoredRagProjectSummary;
import com.example.demo.service.rag.port.RagProjectReadRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresRagProjectReadRepository implements RagProjectReadRepository {

    private static final String SUMMARY_SELECT = """
        SELECT
            rw.key,
            rw.name_ru,
            rw.description,
            rw.active,
            rw.is_default,
            rw.sort_order,
            rw.updated_at,
            COUNT(m.id) AS material_count,
            COUNT(m.id) FILTER (
                WHERE m.version_state = 'ACTIVE'
                  AND m.indexing_status IN ('READY', 'PARTIAL_READY')
                  AND m.document_status = 'ACTIVE'
                  AND (m.period_start IS NULL OR m.period_start <= CURRENT_DATE)
                  AND (m.period_end IS NULL OR m.period_end >= CURRENT_DATE)
            ) AS ready_material_count
        FROM reference_workspaces rw
        LEFT JOIN materials m ON m.workspace_key = rw.key
        """;

    private static final String SUMMARY_GROUPING = """
        GROUP BY rw.key, rw.name_ru, rw.description, rw.active, rw.is_default, rw.sort_order, rw.updated_at
        """;

    private static final RowMapper<StoredRagProjectSummary> SUMMARY_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredRagProjectSummary(
            resultSet.getString("key"),
            resultSet.getString("name_ru"),
            resultSet.getString("description"),
            resultSet.getBoolean("active"),
            resultSet.getBoolean("is_default"),
            resultSet.getInt("sort_order"),
            resultSet.getLong("material_count"),
            resultSet.getLong("ready_material_count"),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresRagProjectReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredRagProjectSummary> listSummariesWithCounts(boolean activeOnly) {
        try {
            return jdbcTemplate.query(
                SUMMARY_SELECT
                    + """
                    WHERE (? = false OR rw.active = true)
                    """
                    + SUMMARY_GROUPING
                    + """
                    ORDER BY rw.sort_order ASC, rw.name_ru ASC
                    """,
                SUMMARY_ROW_MAPPER,
                activeOnly
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "rag_project.storage_read_failed",
                "Unable to read RAG project summaries from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<StoredRagProjectSummary> findSummaryByKey(String key) {
        try {
            List<StoredRagProjectSummary> records = jdbcTemplate.query(
                SUMMARY_SELECT
                    + """
                    WHERE rw.key = ?
                    """
                    + SUMMARY_GROUPING
                    + """
                    LIMIT 1
                    """,
                SUMMARY_ROW_MAPPER,
                key
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "rag_project.storage_read_failed",
                "Unable to load RAG project summary from PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
