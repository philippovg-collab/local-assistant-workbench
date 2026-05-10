package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextOptions;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.memory.MemoryEntryDraft;
import com.example.demo.service.memory.MemoryEntryQuery;
import com.example.demo.service.memory.MemoryExtractionJob;
import com.example.demo.service.memory.MemoryExtractionLease;
import com.example.demo.service.memory.MemorySelection;
import com.example.demo.service.memory.port.MemoryRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemorySelectorTest {

    @Test
    void disabledGlobalFlagDoesNotSelectApprovedMemoryEvenWhenRequested() {
        ContextProperties properties = properties(false);
        CapturingMemoryRepository repository = new CapturingMemoryRepository(List.of(memory("memory-1", true)));
        MemorySelector selector = new MemorySelector(properties, repository, new ContextTokenBudgeter(properties));

        MemorySelection selection = selector.select(request(true), run(), new ContextTokenBudgeter.Budget(6, 1000), 0);

        assertEquals("disabled", selection.status());
        assertEquals("memory_flag_disabled", selection.degradedReason());
        assertTrue(selection.selectedMemory().isEmpty());
        assertEquals(0, repository.selectCalls);
    }

    @Test
    void requestFlagMustBeEnabledBeforeSelectingApprovedMemory() {
        ContextProperties properties = properties(true);
        CapturingMemoryRepository repository = new CapturingMemoryRepository(List.of(memory("memory-1", true)));
        MemorySelector selector = new MemorySelector(properties, repository, new ContextTokenBudgeter(properties));

        MemorySelection selection = selector.select(request(false), run(), new ContextTokenBudgeter.Budget(6, 1000), 0);

        assertEquals("disabled", selection.status());
        assertEquals("request_disabled", selection.degradedReason());
        assertTrue(selection.selectedMemory().isEmpty());
        assertEquals(0, repository.selectCalls);
    }

    @Test
    void selectsApprovedMemoryWithinRemainingBudgetAndDropsOverflow() {
        ContextProperties properties = properties(true);
        properties.setMemorySelectionLimit(8);
        CapturingMemoryRepository repository = new CapturingMemoryRepository(List.of(
            memory("pinned", true),
            memory("large", false, "x".repeat(200))
        ));
        MemorySelector selector = new MemorySelector(properties, repository, new ContextTokenBudgeter(properties));

        MemorySelection selection = selector.select(request(true), run(), new ContextTokenBudgeter.Budget(6, 40), 20);

        assertEquals("ready", selection.status());
        assertEquals(List.of("pinned"), selection.selectedMemory().stream().map(item -> item.id()).toList());
        assertEquals(List.of("large"), selection.droppedMemory().stream().map(item -> item.id()).toList());
        assertEquals("workspace-a", repository.lastWorkspaceKey);
        assertEquals("project-a", repository.lastProjectKey);
    }

    private static ContextProperties properties(boolean longTermMemoryEnabled) {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setLongTermMemoryEnabled(longTermMemoryEnabled);
        properties.setMaxHistoryTokens(1000);
        return properties;
    }

    private static ChatExecutionRequest request(boolean useLongTermMemory) {
        return new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "prompt",
            null,
            List.of(),
            null,
            new KnowledgeScope(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("project-a"),
                null,
                List.of(),
                List.of(),
                "workspace-a",
                null,
                null,
                null,
                null,
                false
            ),
            null,
            null,
            null,
            null,
            null,
            "22222222-2222-2222-2222-222222222222",
            null,
            "client-1",
            true,
            false,
            new ContextOptions(null, null, null, null, null, useLongTermMemory, null, null)
        );
    }

    private static StoredConversationRun run() {
        return new StoredConversationRun(
            "22222222-2222-2222-2222-222222222222",
            "11111111-1111-1111-1111-111111111111",
            2,
            null,
            null,
            "hash",
            "prompt",
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

    private static MemoryEntryResponse memory(String id, boolean pinned) {
        return memory(id, pinned, "Пользователь предпочитает короткие ответы.");
    }

    private static MemoryEntryResponse memory(String id, boolean pinned, String content) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new MemoryEntryResponse(
            id,
            MemoryEntryStatus.APPROVED,
            MemoryEntryType.USER_PREFERENCE,
            content,
            id,
            "workspace-a",
            "project-a",
            pinned,
            BigDecimal.ONE,
            Map.of(),
            null,
            null,
            null,
            null,
            null,
            now,
            null,
            null,
            now,
            now
        );
    }

    private static final class CapturingMemoryRepository implements MemoryRepository {

        private final List<MemoryEntryResponse> approved;
        private int selectCalls;
        private String lastWorkspaceKey;
        private String lastProjectKey;

        private CapturingMemoryRepository(List<MemoryEntryResponse> approved) {
            this.approved = approved;
        }

        @Override
        public List<MemoryEntryResponse> selectApprovedForContext(String workspaceKey, String projectKey, int limit) {
            selectCalls++;
            lastWorkspaceKey = workspaceKey;
            lastProjectKey = projectKey;
            return approved.stream().limit(limit).toList();
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
        public MemoryEntryResponse createCandidate(MemoryEntryDraft draft, Instant now) {
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
