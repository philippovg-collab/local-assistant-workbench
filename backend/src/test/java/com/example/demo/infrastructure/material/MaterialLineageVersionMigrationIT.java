package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.config.MaterialProperties;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.example.demo.service.MaterialContentSupport;

class MaterialLineageVersionMigrationIT extends PostgresIntegrationTestSupport {

    private final MaterialContentSupport contentSupport = new MaterialContentSupport(new MaterialProperties());

    @Test
    void migratesLegacyLineagesToImmutableVersionOrderingAndSingleActiveInvariant() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway upToV11 = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .target(MigrationVersion.fromVersion("11"))
            .load();
        upToV11.clean();
        upToV11.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String rollbackActiveId = UUID.randomUUID().toString();
        String rollbackNewerId = UUID.randomUUID().toString();
        insertLegacyMaterialV11(
            jdbcTemplate,
            rollbackActiveId,
            "rollback-lineage",
            "hash-rollback-active",
            "Rollback active version",
            Instant.parse("2026-04-17T10:00:00Z"),
            Instant.parse("2026-04-17T10:05:00Z"),
            "ACTIVE"
        );
        insertLegacyMaterialV11(
            jdbcTemplate,
            rollbackNewerId,
            "rollback-lineage",
            "hash-rollback-newer",
            "Rollback newer version",
            Instant.parse("2026-04-17T10:10:00Z"),
            Instant.parse("2026-04-17T10:10:00Z"),
            "SUPERSEDED"
        );

        String dirtyOlderActiveId = UUID.randomUUID().toString();
        String dirtyNewerActiveId = UUID.randomUUID().toString();
        insertLegacyMaterialV11(
            jdbcTemplate,
            dirtyOlderActiveId,
            "dirty-lineage",
            "hash-dirty-first",
            "Dirty active one",
            Instant.parse("2026-04-17T11:00:00Z"),
            Instant.parse("2026-04-17T11:00:00Z"),
            "ACTIVE"
        );
        insertLegacyMaterialV11(
            jdbcTemplate,
            dirtyNewerActiveId,
            "dirty-lineage",
            "hash-dirty-second",
            "Dirty active two",
            Instant.parse("2026-04-17T11:10:00Z"),
            Instant.parse("2026-04-17T11:10:00Z"),
            "ACTIVE"
        );

        String noActiveOlderId = UUID.randomUUID().toString();
        String noActiveNewerId = UUID.randomUUID().toString();
        insertLegacyMaterialV11(
            jdbcTemplate,
            noActiveOlderId,
            "no-active-lineage",
            "hash-no-active-first",
            "Historical version one",
            Instant.parse("2026-04-17T12:00:00Z"),
            Instant.parse("2026-04-17T12:00:00Z"),
            "SUPERSEDED"
        );
        insertLegacyMaterialV11(
            jdbcTemplate,
            noActiveNewerId,
            "no-active-lineage",
            "hash-no-active-second",
            "Historical version two",
            Instant.parse("2026-04-17T12:10:00Z"),
            Instant.parse("2026-04-17T12:10:00Z"),
            "SUPERSEDED"
        );

        Flyway head = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        head.migrate();

        assertEquals("ACTIVE", versionStateOf(jdbcTemplate, rollbackActiveId));
        assertEquals("SUPERSEDED", versionStateOf(jdbcTemplate, rollbackNewerId));
        assertEquals(1, lineageVersionOf(jdbcTemplate, rollbackActiveId));
        assertEquals(2, lineageVersionOf(jdbcTemplate, rollbackNewerId));

        assertEquals("SUPERSEDED", versionStateOf(jdbcTemplate, dirtyOlderActiveId));
        assertEquals("ACTIVE", versionStateOf(jdbcTemplate, dirtyNewerActiveId));
        assertEquals(dirtyNewerActiveId, supersededByOf(jdbcTemplate, dirtyOlderActiveId));
        assertEquals("material.migration_resolved_multiple_active_versions", supersedeReasonOf(jdbcTemplate, dirtyOlderActiveId));

        assertEquals("SUPERSEDED", versionStateOf(jdbcTemplate, noActiveOlderId));
        assertEquals("ACTIVE", versionStateOf(jdbcTemplate, noActiveNewerId));
        assertEquals(1, countActiveRowsForSourceKey(jdbcTemplate, "rollback-lineage"));
        assertEquals(1, countActiveRowsForSourceKey(jdbcTemplate, "dirty-lineage"));
        assertEquals(1, countActiveRowsForSourceKey(jdbcTemplate, "no-active-lineage"));
    }

    @Test
    void v12AllowsDuplicateContentAcrossLineagesButStillEnforcesOneActivePerLineage() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway flyway = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        flyway.clean();
        flyway.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertMaterialV12(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "source-a",
            "shared-hash",
            "Identical content",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T13:00:00Z")
        );
        insertMaterialV12(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "source-b",
            "shared-hash",
            "Identical content",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T13:05:00Z")
        );

        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE content_hash = ?",
            Integer.class,
            "shared-hash"
        ));

        assertThrows(
            DataAccessException.class,
            () -> insertMaterialV12(
                jdbcTemplate,
                UUID.randomUUID().toString(),
                "source-a",
                "second-active-hash",
                "Another active version in same lineage",
                2,
                "ACTIVE",
                Instant.parse("2026-04-17T13:10:00Z")
            )
        );
    }

    @Test
    void v15BackfillsCanonicalLineageIdentityRows() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway upToV14 = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .target(MigrationVersion.fromVersion("14"))
            .load();
        upToV14.clean();
        upToV14.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "text-explicit-lineage",
            "text",
            "Pricing FAQ",
            null,
            "hash-text-explicit",
            "Старая редакция тарифа 9000 тенге.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T14:00:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "text-anchor-lineage",
            "text",
            "Text material",
            null,
            "hash-text-anchor",
            "Политика закупки оборудования и бюджетирования.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T14:05:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "file-explicit-lineage",
            "file",
            "Ремонтный план 2026",
            "brief.txt",
            "hash-file-explicit",
            "План ремонта северной подстанции на май и июнь.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T14:10:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "file-stem-lineage",
            "file",
            "brief.txt",
            "brief.txt",
            "hash-file-stem",
            "План ремонта северной подстанции май июнь июль август сентябрь октябрь ноябрь декабрь версия один.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T14:15:00Z")
        );

        Flyway head = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        head.migrate();

        assertEquals(4, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_lineage_identities", Integer.class));
        assertEquals(
            "EXPLICIT_TITLE",
            jdbcTemplate.queryForObject(
                "SELECT identity_kind FROM material_lineage_identities WHERE source_key = ?",
                String.class,
                "text-explicit-lineage"
            )
        );
        assertEquals(
            contentSupport.buildLineageIdentity("text", "Pricing FAQ", null, "Старая редакция тарифа 9000 тенге.").identityKey(),
            jdbcTemplate.queryForObject(
                "SELECT identity_key FROM material_lineage_identities WHERE source_key = ?",
                String.class,
                "text-explicit-lineage"
            )
        );
        assertEquals(
            "CONTENT_ANCHOR",
            jdbcTemplate.queryForObject(
                "SELECT identity_kind FROM material_lineage_identities WHERE source_key = ?",
                String.class,
                "text-anchor-lineage"
            )
        );
        assertEquals(
            "EXPLICIT_TITLE",
            jdbcTemplate.queryForObject(
                "SELECT identity_kind FROM material_lineage_identities WHERE source_key = ?",
                String.class,
                "file-explicit-lineage"
            )
        );
        assertEquals(
            "FILE_STEM_AND_CONTENT_ANCHOR",
            jdbcTemplate.queryForObject(
                "SELECT identity_kind FROM material_lineage_identities WHERE source_key = ?",
                String.class,
                "file-stem-lineage"
            )
        );
    }

    @Test
    void v15FailsOnCanonicalIdentityCollisionAcrossExistingSourceKeys() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway upToV14 = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .target(MigrationVersion.fromVersion("14"))
            .load();
        upToV14.clean();
        upToV14.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "collision-a",
            "text",
            "Pricing FAQ",
            null,
            "hash-collision-a",
            "Первая редакция тарифа.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T15:00:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "collision-b",
            "text",
            "Pricing FAQ",
            null,
            "hash-collision-b",
            "Вторая редакция тарифа.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T15:05:00Z")
        );

        Flyway head = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();

        assertThrows(Exception.class, head::migrate);
    }

    @Test
    void v15FailsWhenFileLineageHasNeitherExplicitTitleNorFileStem() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway upToV14 = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .target(MigrationVersion.fromVersion("14"))
            .load();
        upToV14.clean();
        upToV14.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        insertMaterialV14(
            jdbcTemplate,
            UUID.randomUUID().toString(),
            "broken-file-lineage",
            "file",
            "",
            null,
            "hash-broken-file",
            "Содержимое файла без явной lineage identity.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T15:10:00Z")
        );

        Flyway head = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();

        assertThrows(Exception.class, head::migrate);
    }

    @Test
    void v16MigratesLegacySearchSyncEventsIntoCollapsedQueueEntries() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            postgresJdbcUrl(),
            postgresUsername(),
            postgresPassword()
        );
        Flyway upToV15 = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .target(MigrationVersion.fromVersion("15"))
            .load();
        upToV15.clean();
        upToV15.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String deliveredOnlyMaterialId = UUID.randomUUID().toString();
        String failedMaterialId = UUID.randomUUID().toString();
        String pendingMaterialId = UUID.randomUUID().toString();
        insertMaterialV14(
            jdbcTemplate,
            deliveredOnlyMaterialId,
            "delivered-only-lineage",
            "text",
            "Delivered only",
            null,
            "hash-delivered-only",
            "Delivered-only material content.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T16:00:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            failedMaterialId,
            "failed-lineage",
            "text",
            "Failed material",
            null,
            "hash-failed-material",
            "Failed material content.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T16:01:00Z")
        );
        insertMaterialV14(
            jdbcTemplate,
            pendingMaterialId,
            "pending-lineage",
            "text",
            "Pending material",
            null,
            "hash-pending-material",
            "Pending material content.",
            1,
            "ACTIVE",
            Instant.parse("2026-04-17T16:02:00Z")
        );

        insertSearchSyncEventV15(
            jdbcTemplate,
            deliveredOnlyMaterialId,
            "UPSERT_SEARCHABLE",
            "DELIVERED",
            1,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-17T16:05:00Z"),
            Instant.parse("2026-04-17T16:05:05Z")
        );
        insertSearchSyncEventV15(
            jdbcTemplate,
            failedMaterialId,
            "UPSERT_SEARCHABLE",
            "FAILED",
            3,
            null,
            Instant.parse("2026-04-17T16:10:05Z"),
            "search.sync_failed",
            "Terminal failure",
            Instant.parse("2026-04-17T16:10:00Z"),
            Instant.parse("2026-04-17T16:10:05Z")
        );
        insertSearchSyncEventV15(
            jdbcTemplate,
            pendingMaterialId,
            "UPSERT_SEARCHABLE",
            "IN_PROGRESS",
            2,
            null,
            Instant.parse("2026-04-17T16:20:10Z"),
            null,
            null,
            Instant.parse("2026-04-17T16:20:00Z"),
            Instant.parse("2026-04-17T16:20:10Z")
        );
        insertSearchSyncEventV15(
            jdbcTemplate,
            pendingMaterialId,
            "REMOVE_SEARCHABLE",
            "PENDING",
            1,
            Instant.parse("2026-04-17T16:26:00Z"),
            null,
            null,
            null,
            Instant.parse("2026-04-17T16:25:00Z"),
            Instant.parse("2026-04-17T16:25:05Z")
        );

        Flyway head = Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load();
        head.migrate();

        assertNull(jdbcTemplate.queryForObject("SELECT to_regclass('public.material_search_sync_events')", String.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_search_sync_queue", Integer.class));

        assertEquals(
            "FAILED",
            jdbcTemplate.queryForObject(
                "SELECT delivery_state FROM material_search_sync_queue WHERE material_id = ?",
                String.class,
                UUID.fromString(failedMaterialId)
            )
        );
        assertEquals(
            3,
            jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM material_search_sync_queue WHERE material_id = ?",
                Integer.class,
                UUID.fromString(failedMaterialId)
            )
        );
        assertEquals(
            "search.sync_failed",
            jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM material_search_sync_queue WHERE material_id = ?",
                String.class,
                UUID.fromString(failedMaterialId)
            )
        );

        assertEquals(
            "PENDING",
            jdbcTemplate.queryForObject(
                "SELECT delivery_state FROM material_search_sync_queue WHERE material_id = ?",
                String.class,
                UUID.fromString(pendingMaterialId)
            )
        );
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM material_search_sync_queue WHERE material_id = ?",
                Integer.class,
                UUID.fromString(pendingMaterialId)
            )
        );
        assertEquals(
            Timestamp.from(Instant.parse("2026-04-17T16:26:00Z")),
            jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM material_search_sync_queue WHERE material_id = ?",
                Timestamp.class,
                UUID.fromString(pendingMaterialId)
            )
        );
        assertEquals(
            Timestamp.from(Instant.parse("2026-04-17T16:25:00Z")),
            jdbcTemplate.queryForObject(
                "SELECT requested_at FROM material_search_sync_queue WHERE material_id = ?",
                Timestamp.class,
                UUID.fromString(pendingMaterialId)
            )
        );
        assertEquals(
            Timestamp.from(Instant.parse("2026-04-17T16:20:00Z")),
            jdbcTemplate.queryForObject(
                "SELECT created_at FROM material_search_sync_queue WHERE material_id = ?",
                Timestamp.class,
                UUID.fromString(pendingMaterialId)
            )
        );
        assertNull(jdbcTemplate.queryForObject(
            "SELECT claimed_at FROM material_search_sync_queue WHERE material_id = ?",
            Timestamp.class,
            UUID.fromString(pendingMaterialId)
        ));
    }

    private void insertLegacyMaterialV11(
        JdbcTemplate jdbcTemplate,
        String id,
        String sourceKey,
        String contentHash,
        String content,
        Instant createdAt,
        Instant updatedAt,
        String versionState
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO materials (
                    id,
                    title,
                    source_type,
                    media_type,
                    content,
                    normalized_content,
                    content_hash,
                    source_key,
                    extractor,
                    created_at,
                    updated_at,
                    indexing_status,
                    version_state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(id),
            sourceKey + " title",
            "text",
            "text/plain",
            content,
            content,
            contentHash,
            sourceKey,
            "direct-text",
            Timestamp.from(createdAt),
            Timestamp.from(updatedAt),
            "READY",
            versionState
        );
    }

    private void insertMaterialV12(
        JdbcTemplate jdbcTemplate,
        String id,
        String sourceKey,
        String contentHash,
        String content,
        int lineageVersion,
        String versionState,
        Instant timestamp
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO materials (
                    id,
                    title,
                    source_type,
                    media_type,
                    content,
                    normalized_content,
                    content_hash,
                    source_key,
                    extractor,
                    lineage_version,
                    created_at,
                    updated_at,
                    indexing_status,
                    version_state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(id),
            sourceKey + " title",
            "text",
            "text/plain",
            content,
            content,
            contentHash,
            sourceKey,
            "direct-text",
            lineageVersion,
            Timestamp.from(timestamp),
            Timestamp.from(timestamp),
            "READY",
            versionState
        );
    }

    private void insertMaterialV14(
        JdbcTemplate jdbcTemplate,
        String id,
        String sourceKey,
        String sourceType,
        String title,
        String originalFileName,
        String contentHash,
        String content,
        int lineageVersion,
        String versionState,
        Instant timestamp
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO materials (
                    id,
                    title,
                    source_type,
                    original_file_name,
                    media_type,
                    content,
                    normalized_content,
                    content_hash,
                    source_key,
                    extractor,
                    lineage_version,
                    created_at,
                    updated_at,
                    indexing_status,
                    version_state
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(id),
            title,
            sourceType,
            originalFileName,
            "text/plain",
            content,
            content,
            contentHash,
            sourceKey,
            "direct-text",
            lineageVersion,
            Timestamp.from(timestamp),
            Timestamp.from(timestamp),
            "READY",
            versionState
        );
    }

    private void insertSearchSyncEventV15(
        JdbcTemplate jdbcTemplate,
        String materialId,
        String eventType,
        String deliveryState,
        int attemptCount,
        Instant nextAttemptAt,
        Instant claimedAt,
        String lastErrorCode,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
    ) {
        jdbcTemplate.update(
            """
                INSERT INTO material_search_sync_events (
                    id,
                    material_id,
                    event_type,
                    delivery_state,
                    attempt_count,
                    next_attempt_at,
                    claimed_at,
                    last_error_code,
                    last_error_message,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            UUID.fromString(materialId),
            eventType,
            deliveryState,
            attemptCount,
            nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
            claimedAt == null ? null : Timestamp.from(claimedAt),
            lastErrorCode,
            lastErrorMessage,
            Timestamp.from(createdAt),
            Timestamp.from(updatedAt)
        );
    }

    private String versionStateOf(JdbcTemplate jdbcTemplate, String id) {
        return jdbcTemplate.queryForObject(
            "SELECT version_state FROM materials WHERE id = ?",
            String.class,
            UUID.fromString(id)
        );
    }

    private Integer lineageVersionOf(JdbcTemplate jdbcTemplate, String id) {
        return jdbcTemplate.queryForObject(
            "SELECT lineage_version FROM materials WHERE id = ?",
            Integer.class,
            UUID.fromString(id)
        );
    }

    private String supersededByOf(JdbcTemplate jdbcTemplate, String id) {
        return jdbcTemplate.queryForObject(
            "SELECT superseded_by_material_id::text FROM materials WHERE id = ?",
            String.class,
            UUID.fromString(id)
        );
    }

    private String supersedeReasonOf(JdbcTemplate jdbcTemplate, String id) {
        return jdbcTemplate.queryForObject(
            "SELECT supersede_reason FROM materials WHERE id = ?",
            String.class,
            UUID.fromString(id)
        );
    }

    private Integer countActiveRowsForSourceKey(JdbcTemplate jdbcTemplate, String sourceKey) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE source_key = ? AND version_state = 'ACTIVE'",
            Integer.class,
            sourceKey
        );
    }
}
