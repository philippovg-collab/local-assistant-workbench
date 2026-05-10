package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class ChatRunSubmissionCoordinatorTest {

    private final ChatRunQueueRepository queueRepository = mock(ChatRunQueueRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatRunExecutionService executionService = mock(ChatRunExecutionService.class);
    private final ChatRunQueryService queryService = mock(ChatRunQueryService.class);
    private final ContextProperties contextProperties = new ContextProperties();
    private final ChatRunSubmissionCoordinator coordinator = new ChatRunSubmissionCoordinator(
        new ChatExecutionProperties(),
        contextProperties,
        queueRepository,
        conversationRepository,
        executionService,
        queryService,
        new NoopTransactionManager(),
        JsonMapper.builder().findAndAddModules().build()
    );

    @Test
    void statelessSubmitDoesNotRequireConversations() {
        ChatExecutionRequest request = request(null, null, null, null);
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-05-10T00:00:00Z");
        when(queueRepository.enqueue(eq(request), eq(ChatMode.RAG), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = coordinator.submit(request);

        assertEquals(runId, response.id());
        assertEquals("/api/chat-runs/" + runId + "/cancel", response.cancelUrl());
        verify(conversationRepository, never()).createConversation(anyString(), any(), anyString(), any(), any(), any(), any());
        verify(conversationRepository, never()).insertRun(anyString(), anyString(), anyInt(), any(), any(), any(), any(), any(), any());
        verify(executionService).requestProcessing();
    }

    @Test
    void disabledConversationsRejectConversationalSubmitBeforeEnqueue() {
        ChatExecutionRequest request = request(UUID.randomUUID().toString(), null, "turn-1", true);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> coordinator.submit(request));

        assertEquals("context.disabled", exception.getCode());
        verify(queueRepository, never()).enqueue(any(), any(), any());
    }

    @Test
    void autoCreatesConversationAndBindsEnqueuedRun() {
        enableConversations();
        ChatExecutionRequest request = request(null, null, "turn-1", true);
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-05-10T00:00:00Z");
        when(conversationRepository.createConversation(
            anyString(),
            eq("grid"),
            eq("Prompt"),
            eq(ChatMode.RAG),
            eq("qwen2.5:7b"),
            eq(null),
            any(Instant.class)
        )).thenReturn(conversation(conversationId, "grid", ChatMode.RAG, "ACTIVE"));
        when(conversationRepository.findRunByClientTurnId(conversationId, "turn-1")).thenReturn(Optional.empty());
        when(conversationRepository.nextTurnNo(conversationId)).thenReturn(1);
        when(queueRepository.enqueue(eq(request), eq(ChatMode.RAG), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = coordinator.submit(request);

        assertEquals(conversationId, response.conversationId());
        assertEquals(1, response.turnNo());
        verify(conversationRepository).insertRun(
            eq(conversationId),
            eq(runId),
            eq(1),
            eq(null),
            eq("turn-1"),
            anyString(),
            eq("Prompt"),
            eq(null),
            eq(createdAt)
        );
        verify(conversationRepository).touchConversation(conversationId, createdAt);
        verify(executionService).requestProcessing();
    }

    @Test
    void existingConversationBindHonorsParentRunAndSequentialTurn() {
        enableConversations();
        String conversationId = UUID.randomUUID().toString();
        String parentRunId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        ChatExecutionRequest request = request(conversationId, parentRunId, "turn-2", false);
        Instant createdAt = Instant.parse("2026-05-10T00:00:00Z");
        when(conversationRepository.findConversationForUpdate(conversationId))
            .thenReturn(Optional.of(conversation(conversationId, "grid", ChatMode.RAG, "ACTIVE")));
        when(conversationRepository.findRunByClientTurnId(conversationId, "turn-2")).thenReturn(Optional.empty());
        when(conversationRepository.runBelongsToConversation(conversationId, parentRunId)).thenReturn(true);
        when(conversationRepository.nextTurnNo(conversationId)).thenReturn(2);
        when(queueRepository.enqueue(eq(request), eq(ChatMode.RAG), any(Instant.class)))
            .thenReturn(new EnqueuedChatRun(runId, createdAt));

        ChatRunSubmissionResponse response = coordinator.submit(request);

        assertEquals(conversationId, response.conversationId());
        assertEquals(2, response.turnNo());
        verify(conversationRepository).insertRun(
            eq(conversationId),
            eq(runId),
            eq(2),
            eq(parentRunId),
            eq("turn-2"),
            anyString(),
            eq("Prompt"),
            eq(null),
            eq(createdAt)
        );
    }

    @Test
    void duplicateClientTurnWithSameHashReturnsExistingRunWithoutEnqueue() {
        enableConversations();
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        ChatExecutionRequest request = request(conversationId, null, "turn-1", false);
        when(conversationRepository.findConversationForUpdate(conversationId))
            .thenReturn(Optional.of(conversation(conversationId, "grid", ChatMode.RAG, "ACTIVE")));
        when(conversationRepository.findRunByClientTurnId(conversationId, "turn-1"))
            .thenReturn(Optional.of(new StoredConversationRun(
                conversationId,
                runId,
                3,
                null,
                "turn-1",
                hashFor(request),
                "Prompt",
                null,
                "NONE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "RECEIVED",
                null,
                null,
                null,
                null
            )));

        ChatRunSubmissionResponse response = coordinator.submit(request);

        assertEquals(runId, response.id());
        assertEquals(conversationId, response.conversationId());
        assertEquals(3, response.turnNo());
        verify(queueRepository, never()).enqueue(any(), any(), any());
        verify(executionService, never()).requestProcessing();
    }

    @Test
    void duplicateClientTurnWithDifferentHashConflicts() {
        enableConversations();
        String conversationId = UUID.randomUUID().toString();
        ChatExecutionRequest request = request(conversationId, null, "turn-1", false);
        when(conversationRepository.findConversationForUpdate(conversationId))
            .thenReturn(Optional.of(conversation(conversationId, null, ChatMode.RAG, "ACTIVE")));
        when(conversationRepository.findRunByClientTurnId(conversationId, "turn-1"))
            .thenReturn(Optional.of(new StoredConversationRun(
                conversationId,
                UUID.randomUUID().toString(),
                1,
                null,
                "turn-1",
                "different",
                "Prompt",
                null,
                "NONE",
                Instant.parse("2026-05-10T00:00:00Z"),
                "RECEIVED",
                null,
                null,
                null,
                null
            )));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> coordinator.submit(request));

        assertEquals("conversation.client_turn_conflict", exception.getCode());
        verify(queueRepository, never()).enqueue(any(), any(), any());
    }

    @Test
    void archivedModeWorkspaceAndParentMismatchesConflictBeforeEnqueue() {
        enableConversations();
        String archivedId = UUID.randomUUID().toString();
        String modeMismatchId = UUID.randomUUID().toString();
        String workspaceMismatchId = UUID.randomUUID().toString();
        String parentMismatchId = UUID.randomUUID().toString();
        String parentRunId = UUID.randomUUID().toString();
        when(conversationRepository.findConversationForUpdate(archivedId))
            .thenReturn(Optional.of(conversation(archivedId, "grid", ChatMode.RAG, "ARCHIVED")));
        when(conversationRepository.findConversationForUpdate(modeMismatchId))
            .thenReturn(Optional.of(conversation(modeMismatchId, null, ChatMode.DIRECT, "ACTIVE")));
        when(conversationRepository.findConversationForUpdate(workspaceMismatchId))
            .thenReturn(Optional.of(conversation(workspaceMismatchId, "dispatch", ChatMode.RAG, "ACTIVE")));
        when(conversationRepository.findConversationForUpdate(parentMismatchId))
            .thenReturn(Optional.of(conversation(parentMismatchId, "grid", ChatMode.RAG, "ACTIVE")));
        when(conversationRepository.runBelongsToConversation(parentMismatchId, parentRunId)).thenReturn(false);

        assertEquals("conversation.archived", conflictCode(request(archivedId, null, "turn-1", false)));
        assertEquals("conversation.mode_mismatch", conflictCode(new ChatExecutionRequest(
            ChatMode.RAG,
            "qwen2.5:7b",
            "Prompt",
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            modeMismatchId,
            null,
            "turn-1",
            false,
            null,
            null
        )));
        assertEquals("conversation.workspace_mismatch", conflictCode(request(workspaceMismatchId, null, "turn-1", false)));
        assertEquals("conversation.parent_run_mismatch", conflictCode(request(parentMismatchId, parentRunId, "turn-1", false)));
        verify(queueRepository, never()).enqueue(any(), any(), any());
    }

    private void enableConversations() {
        contextProperties.setEnabled(true);
        contextProperties.setConversationsEnabled(true);
    }

    private String conflictCode(ChatExecutionRequest request) {
        return assertThrows(ApplicationException.class, () -> coordinator.submit(request)).getCode();
    }

    private String hashFor(ChatExecutionRequest request) {
        try {
            var method = ChatRunSubmissionCoordinator.class.getDeclaredMethod("requestHash", ChatExecutionRequest.class);
            method.setAccessible(true);
            return (String) method.invoke(coordinator, request);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to compute request hash", exception);
        }
    }

    private ChatExecutionRequest request(
        String conversationId,
        String parentRunId,
        String clientTurnId,
        Boolean persistConversation
    ) {
        return new ChatExecutionRequest(
            ChatMode.RAG,
            "qwen2.5:7b",
            "Prompt",
            null,
            List.of(),
            null,
            new KnowledgeScope(List.of(), List.of(), List.of(), "grid", false),
            "grid",
            null,
            null,
            null,
            null,
            conversationId,
            parentRunId,
            clientTurnId,
            persistConversation,
            null,
            null
        );
    }

    private StoredConversation conversation(
        String conversationId,
        String workspaceKey,
        ChatMode mode,
        String status
    ) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new StoredConversation(
            conversationId,
            workspaceKey,
            "Prompt",
            mode,
            status,
            "qwen2.5:7b",
            null,
            now,
            now,
            null,
            0
        );
    }

    private static final class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
