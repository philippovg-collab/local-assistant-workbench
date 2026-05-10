package com.example.demo.infrastructure.context;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.context.ContextRetentionCleanupResult;
import com.example.demo.service.context.port.ContextMaintenanceRepository;
import com.example.demo.service.memory.port.MemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresContextMaintenanceRepository implements ContextMaintenanceRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MemoryRepository memoryRepository;

    public PostgresContextMaintenanceRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this(
            jdbcTemplate,
            transactionManager,
            new PostgresMemoryRepository(jdbcTemplate, transactionManager, new ObjectMapper())
        );
    }

    @Autowired
    public PostgresContextMaintenanceRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        MemoryRepository memoryRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.memoryRepository = memoryRepository;
    }

    @Override
    public void assertConversationStoreReadable() {
        assertReadable("chat_conversations");
    }

    @Override
    public void assertContextSnapshotStoreReadable() {
        assertReadable("context_assembly_snapshots");
    }

    @Override
    public void assertStickyStateStoreReadable() {
        assertReadable("conversation_working_memory");
    }

    @Override
    public void assertRetrievalResolutionStoreReadable() {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(retrieval_query_resolution_jsonb) FROM context_assembly_snapshots",
                Integer.class
            );
        } catch (DataAccessException exception) {
            throw storageFailure("context.health_storage_read_failed", "Unable to read retrieval query resolution snapshots", exception);
        }
    }

    @Override
    public void assertMemoryStoreReadable() {
        memoryRepository.assertMemoryStoreReadable();
    }

    @Override
    public SummaryJobHealth summaryJobHealth(Instant now) {
        try {
            Instant effectiveNow = now == null ? Instant.now() : now;
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_count,
                        COUNT(*) FILTER (
                            WHERE status = 'RUNNING'
                              AND lease_expires_at IS NOT NULL
                              AND lease_expires_at <= ?
                        ) AS stuck_count
                    FROM conversation_summary_refresh_jobs
                    """,
                (resultSet, rowNum) -> new SummaryJobHealth(
                    resultSet.getInt("failed_count"),
                    resultSet.getInt("stuck_count")
                ),
                Timestamp.from(effectiveNow)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("context.health_storage_read_failed", "Unable to inspect conversation summary jobs", exception);
        }
    }

    @Override
    public MemoryJobHealth memoryJobHealth(Instant now) {
        MemoryRepository.MemoryJobHealth health = memoryRepository.memoryJobHealth(now == null ? Instant.now() : now);
        return new MemoryJobHealth(health.failedCount(), health.stuckCount());
    }

    @Override
    public ContextRetentionCleanupResult deleteExpiredContextArtifacts(
        Instant snapshotCutoff,
        Instant summaryJobCutoff,
        Instant memoryRejectedCutoff,
        Instant memoryDeletedCutoff,
        Instant memoryJobCutoff,
        Instant deletedConversationCutoff,
        Instant emptyConversationCutoff,
        int keepLatestSnapshotsPerConversation,
        int batchSize
    ) {
        try {
            return transactionTemplate.execute(status -> {
                int deletedSnapshots = deleteExpiredSnapshots(snapshotCutoff, keepLatestSnapshotsPerConversation, batchSize);
                int deletedSummaryJobs = deleteExpiredSummaryJobs(summaryJobCutoff, batchSize);
                int purgedRejectedMemory = memoryRepository.purgeRejected(memoryRejectedCutoff, batchSize);
                int purgedDeletedMemory = memoryRepository.purgeDeleted(memoryDeletedCutoff, batchSize);
                int deletedMemoryJobs = memoryRepository.purgeCompletedExtractionJobs(memoryJobCutoff, batchSize);
                int purgedSoftDeletedConversations = purgeSoftDeletedConversations(deletedConversationCutoff, batchSize);
                int purgedEmptyConversations = purgeEmptyConversations(emptyConversationCutoff, batchSize);
                return new ContextRetentionCleanupResult(
                    deletedSnapshots,
                    deletedSummaryJobs,
                    purgedRejectedMemory,
                    purgedDeletedMemory,
                    deletedMemoryJobs,
                    purgedSoftDeletedConversations,
                    purgedEmptyConversations
                );
            });
        } catch (DataAccessException exception) {
            throw storageFailure("context.retention_storage_failed", "Unable to clean up context artifacts", exception);
        }
    }

    public ContextRetentionCleanupResult deleteExpiredContextArtifacts(
        Instant snapshotCutoff,
        Instant summaryJobCutoff,
        Instant deletedConversationCutoff,
        Instant emptyConversationCutoff,
        int keepLatestSnapshotsPerConversation,
        int batchSize
    ) {
        return deleteExpiredContextArtifacts(
            snapshotCutoff,
            summaryJobCutoff,
            summaryJobCutoff,
            summaryJobCutoff,
            summaryJobCutoff,
            deletedConversationCutoff,
            emptyConversationCutoff,
            keepLatestSnapshotsPerConversation,
            batchSize
        );
    }

    private void assertReadable(String tableName) {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        } catch (DataAccessException exception) {
            throw storageFailure("context.health_storage_read_failed", "Unable to read " + tableName, exception);
        }
    }

    private int deleteExpiredSnapshots(Instant cutoff, int keepLatestSnapshotsPerConversation, int batchSize) {
        return jdbcTemplate.update(
            """
                WITH ranked AS (
                    SELECT
                        id,
                        created_at,
                        ROW_NUMBER() OVER (
                            PARTITION BY conversation_id
                            ORDER BY created_at DESC, turn_no DESC, id DESC
                        ) AS snapshot_rank
                    FROM context_assembly_snapshots
                ),
                candidates AS (
                    SELECT id
                    FROM ranked
                    WHERE created_at < ?
                      AND snapshot_rank > ?
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                ),
                marked_runs AS (
                    UPDATE chat_conversation_runs run
                    SET context_assembly_status = 'EXPIRED'
                    FROM candidates
                    WHERE run.context_assembly_id = candidates.id
                    RETURNING run.run_id
                )
                DELETE FROM context_assembly_snapshots snapshot
                USING candidates
                WHERE snapshot.id = candidates.id
                """,
            Timestamp.from(cutoff),
            keepLatestSnapshotsPerConversation,
            batchSize
        );
    }

    private int deleteExpiredSummaryJobs(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
            """
                WITH candidates AS (
                    SELECT job.conversation_id
                    FROM conversation_summary_refresh_jobs job
                    LEFT JOIN conversation_working_memory memory
                           ON memory.conversation_id = job.conversation_id
                    WHERE (
                            job.status = 'FAILED'
                            AND job.updated_at < ?
                        )
                       OR (
                            memory.summary_through_turn_no >= job.requested_through_turn_no
                            AND job.updated_at < ?
                        )
                    ORDER BY job.updated_at ASC, job.conversation_id ASC
                    LIMIT ?
                )
                DELETE FROM conversation_summary_refresh_jobs job
                USING candidates
                WHERE job.conversation_id = candidates.conversation_id
                """,
            Timestamp.from(cutoff),
            Timestamp.from(cutoff),
            batchSize
        );
    }

    private int purgeSoftDeletedConversations(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
            """
                WITH candidates AS (
                    SELECT id
                    FROM chat_conversations
                    WHERE status IN ('DELETED', 'SOFT_DELETED')
                      AND updated_at < ?
                    ORDER BY updated_at ASC, id ASC
                    LIMIT ?
                )
                DELETE FROM chat_conversations conversation
                USING candidates
                WHERE conversation.id = candidates.id
                """,
            Timestamp.from(cutoff),
            batchSize
        );
    }

    private int purgeEmptyConversations(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
            """
                WITH candidates AS (
                    SELECT conversation.id
                    FROM chat_conversations conversation
                    WHERE conversation.updated_at < ?
                      AND NOT EXISTS (
                          SELECT 1
                          FROM chat_conversation_runs run
                          WHERE run.conversation_id = conversation.id
                      )
                    ORDER BY conversation.updated_at ASC, conversation.id ASC
                    LIMIT ?
                )
                DELETE FROM chat_conversations conversation
                USING candidates
                WHERE conversation.id = candidates.id
                """,
            Timestamp.from(cutoff),
            batchSize
        );
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
