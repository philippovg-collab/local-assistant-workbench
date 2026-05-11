package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialLineageOperatorOverride;
import com.example.demo.service.material.port.MaterialLineageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialLineageAdapter extends PostgresMaterialJdbcSupport implements MaterialLineageRepository {

    private static final RowMapper<MaterialLineageOperatorOverride> OVERRIDE_ROW_MAPPER = (resultSet, rowNum) ->
        new MaterialLineageOperatorOverride(
            resultSet.getString("id"),
            resultSet.getString("lineage_key"),
            resultSet.getString("source_key"),
            resultSet.getString("reason"),
            resultSet.getString("created_by"),
            resultSet.getBoolean("active"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("deactivated_at") == null ? null : resultSet.getTimestamp("deactivated_at").toInstant()
        );

    PostgresMaterialLineageAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public String resolveSourceKey(MaterialLineageIdentity identity) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    INSERT INTO material_lineage_identities (
                        source_key,
                        source_type,
                        identity_kind,
                        identity_key,
                        explicit_title_norm,
                        original_file_name_norm,
                        file_stem_norm,
                        content_anchor,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_type, identity_kind, identity_key)
                    DO UPDATE
                    SET explicit_title_norm = EXCLUDED.explicit_title_norm,
                        original_file_name_norm = EXCLUDED.original_file_name_norm,
                        file_stem_norm = EXCLUDED.file_stem_norm,
                        content_anchor = EXCLUDED.content_anchor
                    RETURNING source_key
                    """,
                String.class,
                identity.sourceKey(),
                identity.sourceType(),
                identity.identityKind().name(),
                identity.identityKey(),
                identity.explicitTitleNorm(),
                identity.originalFileNameNorm(),
                identity.fileStemNorm(),
                identity.contentAnchor(),
                Timestamp.from(Instant.now())
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to resolve material lineage identity in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<MaterialLineageOperatorOverride> findActiveOverride(String lineageKey) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id, lineage_key, source_key, reason, created_by, active, created_at, deactivated_at
                    FROM material_lineage_operator_overrides
                    WHERE lineage_key = ?
                      AND active = true
                    LIMIT 1
                    """,
                OVERRIDE_ROW_MAPPER,
                lineageKey
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.lineage_override_read_failed",
                "Unable to load material lineage override from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<MaterialLineageOperatorOverride> findActiveOverrideBySourceKey(String sourceKey) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id, lineage_key, source_key, reason, created_by, active, created_at, deactivated_at
                    FROM material_lineage_operator_overrides
                    WHERE source_key = ?
                      AND active = true
                    ORDER BY created_at DESC, lineage_key ASC
                    LIMIT 1
                    """,
                OVERRIDE_ROW_MAPPER,
                sourceKey
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.lineage_override_read_failed",
                "Unable to load material lineage override from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public MaterialLineageOperatorOverride saveOverride(
        String lineageKey,
        String sourceKey,
        String reason,
        String author,
        Instant now
    ) {
        Instant timestamp = now == null ? Instant.now() : now;
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO material_lineage_operator_overrides (
                        lineage_key,
                        id,
                        source_key,
                        reason,
                        created_by,
                        active,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, true, ?)
                    ON CONFLICT (lineage_key) WHERE active = true DO UPDATE
                    SET source_key = EXCLUDED.source_key,
                        reason = EXCLUDED.reason,
                        created_by = EXCLUDED.created_by,
                        active = true
                    """,
                lineageKey,
                UUID.randomUUID(),
                sourceKey,
                reason,
                author,
                Timestamp.from(timestamp)
            );
            return findActiveOverride(lineageKey).orElseThrow(() -> new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.lineage_override_read_failed",
                "Material lineage override was written but could not be reloaded"
            ));
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.lineage_override_write_failed",
                "Unable to persist material lineage override in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void lockLineage(String sourceKey) {
        try {
            jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?, hashtext(?))",
                resultSet -> {
                },
                71_201,
                sourceKey
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to lock material lineage in PostgreSQL",
                exception
            );
        }
    }
}
