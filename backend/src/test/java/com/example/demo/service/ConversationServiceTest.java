package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationCreateRequest;
import com.example.demo.model.ConversationPatchRequest;
import com.example.demo.service.context.port.ConversationStickyStateRepository;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {

    private ContextProperties contextProperties;
    private ConversationRepository conversationRepository;
    private ConversationStickyStateRepository stickyStateRepository;
    private ConversationService service;

    @BeforeEach
    void setUp() {
        contextProperties = new ContextProperties();
        contextProperties.setEnabled(true);
        contextProperties.setConversationsEnabled(true);
        conversationRepository = mock(ConversationRepository.class);
        stickyStateRepository = mock(ConversationStickyStateRepository.class);
        service = new ConversationService(contextProperties, conversationRepository, stickyStateRepository);
    }

    @Test
    void disabledConversationsRejectBeforeRepositoryAccess() {
        contextProperties.setConversationsEnabled(false);

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.listConversations(null, null)
        );

        assertEquals("context.disabled", exception.getCode());
        verify(conversationRepository, never()).listConversations(any(), any(), anyInt());
    }

    @Test
    void createNormalizesTitleAndDefaultsMode() {
        String conversationId = UUID.randomUUID().toString();
        when(conversationRepository.createConversation(
            any(),
            eq("grid"),
            eq("Новая беседа"),
            eq(ChatMode.DIRECT),
            eq("qwen2.5:7b"),
            eq(null),
            any()
        )).thenReturn(conversation(conversationId, "grid", ChatMode.DIRECT, "ACTIVE", "Новая беседа"));
        when(stickyStateRepository.findByConversationId(conversationId)).thenReturn(Optional.empty());

        var detail = service.createConversation(new ConversationCreateRequest(
            "grid",
            "   ",
            null,
            "qwen2.5:7b",
            null
        ));

        assertEquals(conversationId, detail.id());
        assertEquals("Новая беседа", detail.title());
        assertEquals(ChatMode.DIRECT, detail.mode());
    }

    @Test
    void listUsesFixedLimitAndFilters() {
        String conversationId = UUID.randomUUID().toString();
        when(conversationRepository.listConversations("grid", ChatMode.RAG, 50))
            .thenReturn(List.of(conversation(conversationId, "grid", ChatMode.RAG, "ACTIVE", "Dispatch")));

        var summaries = service.listConversations(" grid ", ChatMode.RAG);

        assertEquals(1, summaries.size());
        assertEquals("Dispatch", summaries.getFirst().title());
        verify(conversationRepository).listConversations("grid", ChatMode.RAG, 50);
    }

    @Test
    void patchRejectsEmptyInvalidStatusAndInvalidId() {
        String conversationId = UUID.randomUUID().toString();
        when(conversationRepository.findConversation(conversationId))
            .thenReturn(Optional.of(conversation(conversationId, null, ChatMode.DIRECT, "ACTIVE", "Dispatch")));

        ApplicationException empty = assertThrows(
            ApplicationException.class,
            () -> service.patchConversation(conversationId, new ConversationPatchRequest(" ", null))
        );
        ApplicationException invalidStatus = assertThrows(
            ApplicationException.class,
            () -> service.patchConversation(conversationId, new ConversationPatchRequest(null, "deleted"))
        );
        ApplicationException invalidId = assertThrows(
            ApplicationException.class,
            () -> service.getConversation("not-a-uuid")
        );

        assertEquals("conversation.patch_empty", empty.getCode());
        assertEquals("conversation.invalid_status", invalidStatus.getCode());
        assertEquals("request.invalid_id", invalidId.getCode());
        verify(conversationRepository, never()).updateConversation(eq(conversationId), any(), any(), any());
    }

    @Test
    void patchAllowsTitleOrStatus() {
        String conversationId = UUID.randomUUID().toString();
        when(conversationRepository.findConversation(conversationId))
            .thenReturn(Optional.of(conversation(conversationId, null, ChatMode.DIRECT, "ACTIVE", "Dispatch")));
        when(conversationRepository.updateConversation(eq(conversationId), eq("New title"), eq("ARCHIVED"), any()))
            .thenReturn(conversation(conversationId, null, ChatMode.DIRECT, "ARCHIVED", "New title"));
        when(stickyStateRepository.findByConversationId(conversationId)).thenReturn(Optional.empty());

        var detail = service.patchConversation(conversationId, new ConversationPatchRequest(" New title ", " archived "));

        assertEquals("New title", detail.title());
        assertEquals("ARCHIVED", detail.status());
        assertNull(detail.stickyState());
    }

    private static StoredConversation conversation(
        String id,
        String workspaceKey,
        ChatMode mode,
        String status,
        String title
    ) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new StoredConversation(
            id,
            workspaceKey,
            title,
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
}
