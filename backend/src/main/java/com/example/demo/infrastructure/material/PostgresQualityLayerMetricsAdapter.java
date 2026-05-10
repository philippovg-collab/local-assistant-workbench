package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.QualityLayerCoverageSnapshot;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresQualityLayerMetricsAdapter extends PostgresMaterialJdbcSupport implements QualityLayerMetricsRepository {

    PostgresQualityLayerMetricsAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public QualityLayerCoverageSnapshot qualityLayerCoverageSnapshot() {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE m.version_state = 'ACTIVE') AS active_total,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND NULLIF(BTRIM(COALESCE(m.workspace_key, '')), '') IS NOT NULL
                              AND m.document_type <> 'OTHER'
                              AND NULLIF(BTRIM(COALESCE(m.document_status, '')), '') IS NOT NULL
                        ) AS active_with_effective_metadata,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND NULLIF(BTRIM(COALESCE(m.workspace_key, '')), '') IS NOT NULL
                        ) AS workspace_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.document_type <> 'OTHER'
                        ) AS document_type_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND NULLIF(BTRIM(COALESCE(m.document_status, '')), '') IS NOT NULL
                        ) AS document_status_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.chunk_profile = 'structured-v1'
                        ) AS structured_profile_active,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.indexing_status = 'PARTIAL_READY'
                        ) AS partial_ready_active
                    FROM materials m
                    """,
                (resultSet, rowNum) -> new QualityLayerCoverageSnapshot(
                    resultSet.getInt("active_total"),
                    resultSet.getInt("active_with_effective_metadata"),
                    resultSet.getInt("workspace_covered"),
                    resultSet.getInt("document_type_covered"),
                    resultSet.getInt("document_status_covered"),
                    resultSet.getInt("structured_profile_active"),
                    resultSet.getInt("partial_ready_active")
                )
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load quality-layer coverage metrics from PostgreSQL",
                exception
            );
        }
    }
}
