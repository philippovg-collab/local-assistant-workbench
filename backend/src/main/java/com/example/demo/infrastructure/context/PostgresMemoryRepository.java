package com.example.demo.infrastructure.context;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.memory.MemoryEntryDraft;
import com.example.demo.service.memory.MemoryEntryQuery;
import com.example.demo.service.memory.MemoryExtractionJob;
import com.example.demo.service.memory.MemoryExtractionLease;
import com.example.demo.service.memory.port.MemoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Repository
public class PostgresMemoryRepository implements MemoryRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PostgresMemoryRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MemoryEntryResponse> listEntries(MemoryEntryQuery query) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM memory_entries WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (query != null && query.status() != null) {
                sql.append(" AND status = ?");
                params.add(query.status().name());
            }
            if (query != null && query.entryType() != null) {
                sql.append(" AND entry_type = ?");
                params.add(query.entryType().name());
            }
            if (query != null && StringUtils.hasText(query.workspaceKey())) {
                sql.append(" AND workspace_key = ?");
                params.add(query.workspaceKey().trim());
            }
            if (query != null && StringUtils.hasText(query.projectKey())) {
                sql.append(" AND project_key = ?");
                params.add(query.projectKey().trim());
            }
            sql.append(" ORDER BY status ASC, pinned DESC, updated_at DESC, created_at DESC LIMIT 500");
            return jdbcTemplate.query(sql.toString(), entryRowMapper(), params.toArray());
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_read_failed", "Unable to list memory entries", exception);
        }
    }

    @Override
    public Optional<MemoryEntryResponse> findEntry(String entryId) {
        try {
            return jdbcTemplate.query(
                "SELECT * FROM memory_entries WHERE id = ?",
                entryRowMapper(),
                UUID.fromString(entryId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_read_failed", "Unable to load memory entry", exception);
        }
    }

    @Override
    public MemoryEntryResponse createEntry(MemoryEntryDraft draft, String actor, String reason, Instant now) {
        return insertEntry(draft, MemoryEntryStatus.PENDING_REVIEW, actor, reason, now, true, null);
    }

    @Override
    public MemoryEntryResponse createCandidate(MemoryEntryDraft draft, Instant now) {
        return createCandidate(draft, now, null);
    }

    @Override
    public MemoryEntryResponse createCandidate(MemoryEntryDraft draft, Instant now, MemoryExtractionLease lease) {
        return insertEntry(draft, MemoryEntryStatus.PENDING_REVIEW, "memory-extractor", "candidate_extraction", now, false, lease);
    }

    @Override
    public Optional<MemoryEntryResponse> findActiveByKey(MemoryEntryDraft draft) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT *
                    FROM memory_entries
                    WHERE status IN ('APPROVED', 'PENDING_REVIEW')
                      AND entry_type = ?
                      AND normalized_key = ?
                      AND COALESCE(workspace_key, '') = COALESCE(?, '')
                      AND COALESCE(project_key, '') = COALESCE(?, '')
                    ORDER BY updated_at DESC
                    LIMIT 1
                    """,
                entryRowMapper(),
                draft.entryType().name(),
                draft.normalizedKey(),
                draft.workspaceKey(),
                draft.projectKey()
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_read_failed", "Unable to load duplicate memory entry", exception);
        }
    }

    @Override
    public MemoryEntryResponse updateEntry(
        String entryId,
        MemoryEntryDraft draft,
        String actor,
        String reason,
        Instant now
    ) {
        try {
            return transactionTemplate.execute(status -> {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE memory_entries
                        SET entry_type = ?,
                            content_text = ?,
                            normalized_key = ?,
                            workspace_key = ?,
                            project_key = ?,
                            confidence = ?,
                            provenance_jsonb = ?::jsonb,
                            updated_at = ?
                        WHERE id = ?
                          AND status IN ('PENDING_REVIEW', 'APPROVED')
                        """,
                    draft.entryType().name(),
                    draft.contentText(),
                    draft.normalizedKey(),
                    draft.workspaceKey(),
                    draft.projectKey(),
                    draft.confidence(),
                    writeJson(draft.provenance()),
                    Timestamp.from(now),
                    UUID.fromString(entryId)
                );
                if (updated == 0) {
                    throw notFoundOrInvalidState("memory.update_invalid_state", "Only pending or approved memories can be edited");
                }
                recordAction(entryId, MemoryEntryAction.EDIT, actor, reason, now);
                return findEntry(entryId).orElseThrow();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_write_failed", "Unable to update memory entry", exception);
        }
    }

    @Override
    public MemoryEntryResponse changeStatus(
        String entryId,
        MemoryEntryStatus expectedStatus,
        MemoryEntryStatus nextStatus,
        MemoryEntryAction action,
        String actor,
        String reason,
        Instant now
    ) {
        try {
            return transactionTemplate.execute(status -> {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE memory_entries
                        SET status = ?,
                            pinned = CASE WHEN ? IN ('REJECTED', 'DELETED') THEN false ELSE pinned END,
                            approved_at = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_at END,
                            rejected_at = CASE WHEN ? = 'REJECTED' THEN ? ELSE rejected_at END,
                            deleted_at = CASE WHEN ? = 'DELETED' THEN ? ELSE deleted_at END,
                            updated_at = ?
                        WHERE id = ?
                          AND status = ?
                          AND content_text IS NOT NULL
                        """,
                    nextStatus.name(),
                    nextStatus.name(),
                    nextStatus.name(),
                    Timestamp.from(now),
                    nextStatus.name(),
                    Timestamp.from(now),
                    nextStatus.name(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    UUID.fromString(entryId),
                    expectedStatus.name()
                );
                if (updated == 0) {
                    throw notFoundOrInvalidState("memory.transition_invalid_state", "Memory entry cannot transition from its current state");
                }
                recordAction(entryId, action, actor, reason, now);
                return findEntry(entryId).orElseThrow();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_write_failed", "Unable to change memory entry status", exception);
        }
    }

    @Override
    public MemoryEntryResponse setPinned(String entryId, boolean pinned, String actor, String reason, Instant now) {
        try {
            return transactionTemplate.execute(status -> {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE memory_entries
                        SET pinned = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status = 'APPROVED'
                          AND content_text IS NOT NULL
                        """,
                    pinned,
                    Timestamp.from(now),
                    UUID.fromString(entryId)
                );
                if (updated == 0) {
                    throw notFoundOrInvalidState("memory.pin_invalid_state", "Only approved memories can be pinned");
                }
                recordAction(entryId, pinned ? MemoryEntryAction.PIN : MemoryEntryAction.UNPIN, actor, reason, now);
                return findEntry(entryId).orElseThrow();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_write_failed", "Unable to update memory pin state", exception);
        }
    }

    @Override
    public MemoryEntryResponse softDelete(String entryId, String actor, String reason, Instant now) {
        try {
            return transactionTemplate.execute(status -> {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE memory_entries
                        SET status = 'DELETED',
                            content_text = NULL,
                            source_text_preview = NULL,
                            pinned = false,
                            deleted_at = ?,
                            updated_at = ?
                        WHERE id = ?
                          AND status <> 'DELETED'
                        """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    UUID.fromString(entryId)
                );
                if (updated == 0) {
                    throw notFoundOrInvalidState("memory.delete_invalid_state", "Memory entry is already deleted or does not exist");
                }
                redactDeletedMemoryFromSnapshots(entryId);
                recordAction(entryId, MemoryEntryAction.DELETE, actor, reason, now);
                return findEntry(entryId).orElseThrow();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_write_failed", "Unable to delete memory entry", exception);
        }
    }

    @Override
    public List<MemoryEntryResponse> selectApprovedForContext(String workspaceKey, String projectKey, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                """
                    SELECT *
                    FROM memory_entries
                    WHERE status = 'APPROVED'
                      AND content_text IS NOT NULL
                      AND (workspace_key IS NULL OR workspace_key = ?)
                      AND (project_key IS NULL OR project_key = ?)
                    ORDER BY pinned DESC,
                             CASE WHEN project_key = ? THEN 0 WHEN project_key IS NULL THEN 1 ELSE 2 END,
                             CASE WHEN workspace_key = ? THEN 0 WHEN workspace_key IS NULL THEN 1 ELSE 2 END,
                             updated_at DESC
                    LIMIT ?
                    """,
                entryRowMapper(),
                workspaceKey,
                projectKey,
                projectKey,
                workspaceKey,
                limit
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_read_failed", "Unable to select approved memories", exception);
        }
    }

    @Override
    public MemoryExtractionJob enqueueExtractionJob(String conversationId, String runId, int turnNo, Instant now) {
        try {
            List<MemoryExtractionJob> jobs = jdbcTemplate.query(
                """
                    WITH inserted AS (
                        INSERT INTO memory_extraction_jobs (
                            id,
                            conversation_id,
                            run_id,
                            turn_no,
                            status,
                            attempt_count,
                            next_retry_at,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                        ON CONFLICT (run_id) DO NOTHING
                        RETURNING *
                    )
                    SELECT * FROM inserted
                    UNION ALL
                    SELECT *
                    FROM memory_extraction_jobs
                    WHERE run_id = ?
                      AND NOT EXISTS (SELECT 1 FROM inserted)
                    LIMIT 1
                    """,
                jobRowMapper(),
                UUID.randomUUID(),
                UUID.fromString(conversationId),
                UUID.fromString(runId),
                turnNo,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                UUID.fromString(runId)
            );
            return jobs.getFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_enqueue_failed", "Unable to enqueue memory extraction job", exception);
        }
    }

    @Override
    public Optional<MemoryExtractionLease> claimNextExtractionJob(String workerId, Instant now, Duration leaseDuration) {
        try {
            return transactionTemplate.execute(status -> {
                List<MemoryExtractionJob> candidates = jdbcTemplate.query(
                    """
                        SELECT *
                        FROM memory_extraction_jobs
                        WHERE status = 'PENDING'
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                        ORDER BY COALESCE(next_retry_at, created_at) ASC, created_at ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """,
                    jobRowMapper(),
                    Timestamp.from(now)
                );
                if (candidates.isEmpty()) {
                    return Optional.empty();
                }
                MemoryExtractionJob candidate = candidates.getFirst();
                jdbcTemplate.update(
                    """
                        UPDATE memory_extraction_jobs
                        SET status = 'RUNNING',
                            attempt_count = attempt_count + 1,
                            lease_owner = ?,
                            lease_expires_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                    workerId,
                    Timestamp.from(now.plus(leaseDuration)),
                    Timestamp.from(now),
                    UUID.fromString(candidate.id())
                );
                MemoryExtractionJob claimed = findJob(candidate.id()).orElseThrow();
                return Optional.of(new MemoryExtractionLease(claimed, claimed.attemptCount()));
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_claim_failed", "Unable to claim memory extraction job", exception);
        }
    }

    @Override
    public void resetExpiredExtractionLeases(Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE memory_extraction_jobs
                    SET status = 'PENDING',
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE status = 'RUNNING'
                      AND lease_expires_at IS NOT NULL
                      AND lease_expires_at <= ?
                    """,
                Timestamp.from(now),
                Timestamp.from(now)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_recovery_failed", "Unable to recover expired memory extraction jobs", exception);
        }
    }

    @Override
    public void markExtractionDone(String jobId, Instant now) {
        updateJobTerminal(jobId, "DONE", null, null, now);
    }

    @Override
    public boolean markExtractionDone(MemoryExtractionLease lease, Instant now) {
        return updateJobTerminal(lease, "DONE", null, null, now);
    }

    @Override
    public void markExtractionRetry(
        String jobId,
        String failureCode,
        String failureMessage,
        Instant now,
        Instant nextRetryAt
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE memory_extraction_jobs
                    SET status = 'PENDING',
                        next_retry_at = ?,
                        last_error_code = ?,
                        last_error_message = ?,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE id = ?
                    """,
                Timestamp.from(nextRetryAt),
                failureCode,
                failureMessage,
                Timestamp.from(now),
                UUID.fromString(jobId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_retry_failed", "Unable to reschedule memory extraction job", exception);
        }
    }

    @Override
    public boolean markExtractionRetry(
        MemoryExtractionLease lease,
        String failureCode,
        String failureMessage,
        Instant now,
        Instant nextRetryAt
    ) {
        if (lease == null || lease.job() == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                """
                    UPDATE memory_extraction_jobs
                    SET status = 'PENDING',
                        next_retry_at = ?,
                        last_error_code = ?,
                        last_error_message = ?,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?
                    WHERE id = ?
                      AND status = 'RUNNING'
                      AND lease_owner = ?
                      AND attempt_count = ?
                    """,
                Timestamp.from(nextRetryAt),
                failureCode,
                failureMessage,
                Timestamp.from(now),
                UUID.fromString(lease.job().id()),
                lease.job().leaseOwner(),
                lease.attemptNumber()
            ) > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_retry_failed", "Unable to reschedule memory extraction job", exception);
        }
    }

    @Override
    public void markExtractionFailed(String jobId, String failureCode, String failureMessage, Instant now) {
        updateJobTerminal(jobId, "FAILED", failureCode, failureMessage, now);
    }

    @Override
    public boolean markExtractionFailed(
        MemoryExtractionLease lease,
        String failureCode,
        String failureMessage,
        Instant now
    ) {
        return updateJobTerminal(lease, "FAILED", failureCode, failureMessage, now);
    }

    @Override
    public boolean hasPendingExtractionJobs(Instant now) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM memory_extraction_jobs
                    WHERE status = 'PENDING'
                      AND (next_retry_at IS NULL OR next_retry_at <= ?)
                    """,
                Integer.class,
                Timestamp.from(now)
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_read_failed", "Unable to inspect memory extraction queue", exception);
        }
    }

    @Override
    public MemoryJobHealth memoryJobHealth(Instant now) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_count,
                        COUNT(*) FILTER (
                            WHERE status = 'RUNNING'
                              AND lease_expires_at IS NOT NULL
                              AND lease_expires_at <= ?
                        ) AS stuck_count
                    FROM memory_extraction_jobs
                    """,
                (resultSet, rowNum) -> new MemoryJobHealth(
                    resultSet.getInt("failed_count"),
                    resultSet.getInt("stuck_count")
                ),
                Timestamp.from(now)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_read_failed", "Unable to inspect memory extraction jobs", exception);
        }
    }

    @Override
    public void assertMemoryStoreReadable() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory_entries", Integer.class);
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM memory_extraction_jobs", Integer.class);
        } catch (DataAccessException exception) {
            throw storageFailure("memory.health_storage_read_failed", "Unable to read memory stores", exception);
        }
    }

    @Override
    public int purgeRejected(Instant cutoff, int batchSize) {
        return purgeEntries("REJECTED", cutoff, batchSize);
    }

    @Override
    public int purgeDeleted(Instant cutoff, int batchSize) {
        return purgeEntries("DELETED", cutoff, batchSize);
    }

    @Override
    public int purgeCompletedExtractionJobs(Instant cutoff, int batchSize) {
        try {
            return jdbcTemplate.update(
                """
                    WITH candidates AS (
                        SELECT id
                        FROM memory_extraction_jobs
                        WHERE status IN ('DONE', 'FAILED')
                          AND updated_at < ?
                        ORDER BY updated_at ASC, id ASC
                        LIMIT ?
                    )
                    DELETE FROM memory_extraction_jobs job
                    USING candidates
                    WHERE job.id = candidates.id
                    """,
                Timestamp.from(cutoff),
                batchSize
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.retention_failed", "Unable to purge memory extraction jobs", exception);
        }
    }

    private MemoryEntryResponse insertEntry(
        MemoryEntryDraft draft,
        MemoryEntryStatus status,
        String actor,
        String reason,
        Instant now,
        boolean failOnDuplicate,
        MemoryExtractionLease lease
    ) {
        try {
            return transactionTemplate.execute(transactionStatus -> {
                if (!currentExtractionLeaseOwned(lease)) {
                    throw storageFailure("memory.job_lease_lost", "Memory extraction job lease is no longer current", null);
                }
                UUID id = UUID.randomUUID();
                List<MemoryEntryResponse> inserted = jdbcTemplate.query(
                    """
                        INSERT INTO memory_entries (
                            id,
                            status,
                            entry_type,
                            content_text,
                            normalized_key,
                            workspace_key,
                            project_key,
                            pinned,
                            confidence,
                            provenance_jsonb,
                            source_conversation_id,
                            source_run_id,
                            source_turn_no,
                            source_text_preview,
                            source_text_hash,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        RETURNING *
                        """,
                    entryRowMapper(),
                    id,
                    status.name(),
                    draft.entryType().name(),
                    draft.contentText(),
                    draft.normalizedKey(),
                    draft.workspaceKey(),
                    draft.projectKey(),
                    draft.pinned(),
                    draft.confidence(),
                    writeJson(draft.provenance()),
                    uuidOrNull(draft.sourceConversationId()),
                    uuidOrNull(draft.sourceRunId()),
                    draft.sourceTurnNo(),
                    draft.sourceTextPreview(),
                    draft.sourceTextHash(),
                    Timestamp.from(now),
                    Timestamp.from(now)
                );
                if (inserted.isEmpty()) {
                    Optional<MemoryEntryResponse> existing = findActiveByKey(draft);
                    if (existing.isPresent() && !failOnDuplicate) {
                        return existing.get();
                    }
                    throw new ApplicationException(
                        ErrorType.CONFLICT,
                        "memory.duplicate_active_key",
                        "An active memory entry with the same normalized key already exists"
                    );
                }
                recordAction(inserted.getFirst().id(), MemoryEntryAction.CREATE, actor, reason, now);
                return inserted.getFirst();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("memory.storage_write_failed", "Unable to create memory entry", exception);
        }
    }

    private Optional<MemoryExtractionJob> findJob(String jobId) {
        return jdbcTemplate.query(
            "SELECT * FROM memory_extraction_jobs WHERE id = ?",
            jobRowMapper(),
            UUID.fromString(jobId)
        ).stream().findFirst();
    }

    private void updateJobTerminal(String jobId, String status, String failureCode, String failureMessage, Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE memory_extraction_jobs
                    SET status = ?,
                        next_retry_at = NULL,
                        last_error_code = ?,
                        last_error_message = ?,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?,
                        completed_at = ?
                    WHERE id = ?
                    """,
                status,
                failureCode,
                failureMessage,
                Timestamp.from(now),
                Timestamp.from(now),
                UUID.fromString(jobId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_update_failed", "Unable to update memory extraction job", exception);
        }
    }

    private boolean updateJobTerminal(
        MemoryExtractionLease lease,
        String status,
        String failureCode,
        String failureMessage,
        Instant now
    ) {
        if (lease == null || lease.job() == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                """
                    UPDATE memory_extraction_jobs
                    SET status = ?,
                        next_retry_at = NULL,
                        last_error_code = ?,
                        last_error_message = ?,
                        lease_owner = NULL,
                        lease_expires_at = NULL,
                        updated_at = ?,
                        completed_at = ?
                    WHERE id = ?
                      AND status = 'RUNNING'
                      AND lease_owner = ?
                      AND attempt_count = ?
                    """,
                status,
                failureCode,
                failureMessage,
                Timestamp.from(now),
                Timestamp.from(now),
                UUID.fromString(lease.job().id()),
                lease.job().leaseOwner(),
                lease.attemptNumber()
            ) > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("memory.job_update_failed", "Unable to update memory extraction job", exception);
        }
    }

    private boolean currentExtractionLeaseOwned(MemoryExtractionLease lease) {
        if (lease == null) {
            return true;
        }
        if (lease.job() == null || lease.job().id() == null || lease.job().leaseOwner() == null) {
            return false;
        }
        List<String> rows = jdbcTemplate.queryForList(
            """
                SELECT id::text
                FROM memory_extraction_jobs
                WHERE id = ?
                  AND status = 'RUNNING'
                  AND lease_owner = ?
                  AND attempt_count = ?
                FOR UPDATE
                """,
            String.class,
            UUID.fromString(lease.job().id()),
            lease.job().leaseOwner(),
            lease.attemptNumber()
        );
        return !rows.isEmpty();
    }

    private int purgeEntries(String status, Instant cutoff, int batchSize) {
        try {
            return jdbcTemplate.update(
                """
                    WITH candidates AS (
                        SELECT id
                        FROM memory_entries
                        WHERE status = ?
                          AND updated_at < ?
                        ORDER BY updated_at ASC, id ASC
                        LIMIT ?
                    )
                    DELETE FROM memory_entries entry
                    USING candidates
                    WHERE entry.id = candidates.id
                    """,
                status,
                Timestamp.from(cutoff),
                batchSize
            );
        } catch (DataAccessException exception) {
            throw storageFailure("memory.retention_failed", "Unable to purge memory entries", exception);
        }
    }

    private void redactDeletedMemoryFromSnapshots(String entryId) {
        jdbcTemplate.update(
            """
                UPDATE context_assembly_snapshots snapshot
                SET selected_memory_jsonb = (
                    SELECT COALESCE(
                        jsonb_agg(
                            CASE
                                WHEN item.value ->> 'id' = ? THEN item.value - 'contentText'
                                ELSE item.value
                            END
                            ORDER BY item.ordinality
                        ),
                        '[]'::jsonb
                    )
                    FROM jsonb_array_elements(snapshot.selected_memory_jsonb) WITH ORDINALITY AS item(value, ordinality)
                )
                WHERE snapshot.selected_memory_jsonb @> jsonb_build_array(jsonb_build_object('id', ?))
                """,
            entryId,
            entryId
        );
    }

    private void recordAction(String entryId, MemoryEntryAction action, String actor, String reason, Instant now) {
        jdbcTemplate.update(
            """
                INSERT INTO memory_review_actions (id, entry_id, action, actor, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            UUID.randomUUID(),
            UUID.fromString(entryId),
            action.name(),
            StringUtils.hasText(actor) ? actor.trim() : "anonymous",
            trimToNull(reason),
            Timestamp.from(now)
        );
    }

    private RowMapper<MemoryEntryResponse> entryRowMapper() {
        return (resultSet, rowNum) -> new MemoryEntryResponse(
            resultSet.getObject("id").toString(),
            MemoryEntryStatus.valueOf(resultSet.getString("status")),
            MemoryEntryType.valueOf(resultSet.getString("entry_type")),
            resultSet.getString("content_text"),
            resultSet.getString("normalized_key"),
            resultSet.getString("workspace_key"),
            resultSet.getString("project_key"),
            resultSet.getBoolean("pinned"),
            resultSet.getBigDecimal("confidence"),
            readMap(resultSet.getString("provenance_jsonb")),
            objectToString(resultSet.getObject("source_conversation_id")),
            objectToString(resultSet.getObject("source_run_id")),
            resultSet.getObject("source_turn_no", Integer.class),
            resultSet.getString("source_text_preview"),
            resultSet.getString("source_text_hash"),
            toInstantOrNull(resultSet.getTimestamp("approved_at")),
            toInstantOrNull(resultSet.getTimestamp("rejected_at")),
            toInstantOrNull(resultSet.getTimestamp("deleted_at")),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private RowMapper<MemoryExtractionJob> jobRowMapper() {
        return (resultSet, rowNum) -> new MemoryExtractionJob(
            resultSet.getObject("id").toString(),
            resultSet.getObject("conversation_id").toString(),
            resultSet.getObject("run_id").toString(),
            resultSet.getInt("turn_no"),
            resultSet.getString("status"),
            resultSet.getInt("attempt_count"),
            toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
            resultSet.getString("last_error_code"),
            resultSet.getString("last_error_message"),
            resultSet.getString("lease_owner"),
            toInstantOrNull(resultSet.getTimestamp("lease_expires_at")),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at")),
            toInstantOrNull(resultSet.getTimestamp("completed_at"))
        );
    }

    private Map<String, Object> readMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "memory.json_read_failed",
                "Unable to deserialize memory provenance",
                exception
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "memory.json_write_failed",
                "Unable to serialize memory payload",
                exception
            );
        }
    }

    private UUID uuidOrNull(String value) {
        return StringUtils.hasText(value) ? UUID.fromString(value) : null;
    }

    private static String objectToString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static ApplicationException notFoundOrInvalidState(String code, String message) {
        return new ApplicationException(ErrorType.INVALID_REQUEST, code, message);
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
