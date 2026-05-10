package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.port.MaterialLineageRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialLineageAdapter extends PostgresMaterialJdbcSupport implements MaterialLineageRepository {

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
