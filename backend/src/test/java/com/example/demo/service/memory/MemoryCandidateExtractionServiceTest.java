package com.example.demo.service.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.memory.port.MemoryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemoryCandidateExtractionServiceTest {

    private final CapturingMemoryRepository repository = new CapturingMemoryRepository();
    private final MemoryCandidateExtractionService service = new MemoryCandidateExtractionService(
        repository,
        new MemorySafetyPolicy()
    );

    @Test
    void explicitUserPreferenceCreatesPendingCandidateWithPromptOnlyProvenance() {
        StoredConversationRun run = run("Запомни: предпочитаю короткие ответы");

        List<MemoryEntryResponse> candidates = service.extractCandidates(run, "workspace-a", "project-a");

        assertEquals(1, candidates.size());
        assertEquals(MemoryEntryStatus.PENDING_REVIEW, candidates.getFirst().status());
        assertEquals(MemoryEntryType.USER_PREFERENCE, candidates.getFirst().entryType());
        assertEquals("Запомни: предпочитаю короткие ответы", candidates.getFirst().sourceTextPreview());
        assertEquals("11111111-1111-1111-1111-111111111111", candidates.getFirst().sourceRunId());
        assertTrue(repository.capturedDrafts.getFirst().sourceTextHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void sourceFactsAndSecretsAreRejectedBeforePersistence() {
        assertTrue(service.extractCandidates(run("Запомни: тариф по документу равен 100"), "workspace-a", "project-a").isEmpty());
        assertTrue(service.extractCandidates(run("Remember: api key: secret-value"), "workspace-a", "project-a").isEmpty());
        assertTrue(repository.capturedDrafts.isEmpty());
    }

    @Test
    void bearerTokensAreRedactedFromCandidateContentAndPreview() {
        List<MemoryEntryResponse> candidates = service.extractCandidates(
            run("Remember: Authorization: Bearer live-token"),
            "workspace-a",
            "project-a"
        );

        assertEquals(1, candidates.size());
        assertTrue(candidates.getFirst().contentText().contains("[REDACTED]"));
        assertTrue(candidates.getFirst().sourceTextPreview().contains("[REDACTED]"));
        assertTrue(!candidates.getFirst().contentText().contains("live-token"));
        assertTrue(!candidates.getFirst().sourceTextPreview().contains("live-token"));
    }

    @Test
    void nonCompletedRunsDoNotCreateCandidates() {
        assertTrue(service.extractCandidates(
            new StoredConversationRun(
                "22222222-2222-2222-2222-222222222222",
                "11111111-1111-1111-1111-111111111111",
                1,
                null,
                null,
                "hash",
                "Запомни: отвечай кратко",
                null,
                "NONE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "FAILED",
                null,
                null,
                "error",
                "failed"
            ),
            "workspace-a",
            "project-a"
        ).isEmpty());
    }

    private static StoredConversationRun run(String prompt) {
        return new StoredConversationRun(
            "22222222-2222-2222-2222-222222222222",
            "11111111-1111-1111-1111-111111111111",
            1,
            null,
            null,
            "hash",
            prompt,
            null,
            "NONE",
            Instant.parse("2026-05-10T00:00:00Z"),
            "COMPLETED",
            Instant.parse("2026-05-10T00:00:01Z"),
            null,
            null,
            null
        );
    }

    private static final class CapturingMemoryRepository implements MemoryRepository {

        private final List<MemoryEntryDraft> capturedDrafts = new ArrayList<>();

        @Override
        public MemoryEntryResponse createCandidate(MemoryEntryDraft draft, Instant now) {
            capturedDrafts.add(draft);
            return new MemoryEntryResponse(
                UUID.randomUUID().toString(),
                MemoryEntryStatus.PENDING_REVIEW,
                draft.entryType(),
                draft.contentText(),
                draft.normalizedKey(),
                draft.workspaceKey(),
                draft.projectKey(),
                draft.pinned(),
                draft.confidence(),
                draft.provenance(),
                draft.sourceConversationId(),
                draft.sourceRunId(),
                draft.sourceTurnNo(),
                draft.sourceTextPreview(),
                draft.sourceTextHash(),
                null,
                null,
                null,
                now,
                now
            );
        }

        @Override
        public List<MemoryEntryResponse> listEntries(MemoryEntryQuery query) {
            throw unsupported();
        }

        @Override
        public Optional<MemoryEntryResponse> findEntry(String entryId) {
            throw unsupported();
        }

        @Override
        public MemoryEntryResponse createEntry(MemoryEntryDraft draft, String actor, String reason, Instant now) {
            throw unsupported();
        }

        @Override
        public Optional<MemoryEntryResponse> findActiveByKey(MemoryEntryDraft draft) {
            throw unsupported();
        }

        @Override
        public MemoryEntryResponse updateEntry(String entryId, MemoryEntryDraft draft, String actor, String reason, Instant now) {
            throw unsupported();
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
            throw unsupported();
        }

        @Override
        public MemoryEntryResponse setPinned(String entryId, boolean pinned, String actor, String reason, Instant now) {
            throw unsupported();
        }

        @Override
        public MemoryEntryResponse softDelete(String entryId, String actor, String reason, Instant now) {
            throw unsupported();
        }

        @Override
        public List<MemoryEntryResponse> selectApprovedForContext(String workspaceKey, String projectKey, int limit) {
            throw unsupported();
        }

        @Override
        public MemoryExtractionJob enqueueExtractionJob(String conversationId, String runId, int turnNo, Instant now) {
            throw unsupported();
        }

        @Override
        public Optional<MemoryExtractionLease> claimNextExtractionJob(String workerId, Instant now, Duration leaseDuration) {
            throw unsupported();
        }

        @Override
        public void resetExpiredExtractionLeases(Instant now) {
            throw unsupported();
        }

        @Override
        public void markExtractionDone(String jobId, Instant now) {
            throw unsupported();
        }

        @Override
        public void markExtractionRetry(String jobId, String failureCode, String failureMessage, Instant now, Instant nextRetryAt) {
            throw unsupported();
        }

        @Override
        public void markExtractionFailed(String jobId, String failureCode, String failureMessage, Instant now) {
            throw unsupported();
        }

        @Override
        public boolean hasPendingExtractionJobs(Instant now) {
            throw unsupported();
        }

        @Override
        public MemoryJobHealth memoryJobHealth(Instant now) {
            throw unsupported();
        }

        @Override
        public void assertMemoryStoreReadable() {
            throw unsupported();
        }

        @Override
        public int purgeRejected(Instant cutoff, int batchSize) {
            throw unsupported();
        }

        @Override
        public int purgeDeleted(Instant cutoff, int batchSize) {
            throw unsupported();
        }

        @Override
        public int purgeCompletedExtractionJobs(Instant cutoff, int batchSize) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not needed for this test");
        }
    }
}
