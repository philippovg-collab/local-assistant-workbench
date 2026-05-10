package com.example.demo.service;

import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationCreateRequest;
import com.example.demo.model.ConversationDetail;
import com.example.demo.model.ConversationPatchRequest;
import com.example.demo.model.ConversationRunDetail;
import com.example.demo.model.ConversationStickyState;
import com.example.demo.model.ConversationSummary;
import com.example.demo.service.context.StoredConversationStickyState;
import com.example.demo.service.context.port.ConversationStickyStateRepository;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final int DEFAULT_LIST_LIMIT = 50;

    private final ContextProperties contextProperties;
    private final ConversationRepository conversationRepository;
    private final ConversationStickyStateRepository stickyStateRepository;

    public ConversationService(
        ContextProperties contextProperties,
        ConversationRepository conversationRepository,
        ConversationStickyStateRepository stickyStateRepository
    ) {
        this.contextProperties = contextProperties;
        this.conversationRepository = conversationRepository;
        this.stickyStateRepository = stickyStateRepository;
    }

    public List<ConversationSummary> listConversations(String workspaceKey, ChatMode mode) {
        requireConversationsEnabled();
        return conversationRepository.listConversations(normalizeOptional(workspaceKey), mode, DEFAULT_LIST_LIMIT).stream()
            .map(this::toSummary)
            .toList();
    }

    public ConversationDetail getConversation(String conversationId) {
        requireConversationsEnabled();
        return conversationRepository.findConversation(requireUuid(conversationId, "conversationId"))
            .map(this::toDetail)
            .orElseThrow(() -> notFound(conversationId));
    }

    @Transactional
    public ConversationDetail createConversation(ConversationCreateRequest request) {
        requireConversationsEnabled();
        ChatMode mode = request == null || request.mode() == null ? ChatMode.DIRECT : request.mode();
        String title = normalizeTitle(request == null ? null : request.title(), "Новая беседа");
        String id = UUID.randomUUID().toString();
        StoredConversation conversation = conversationRepository.createConversation(
            id,
            normalizeOptional(request == null ? null : request.workspaceKey()),
            title,
            mode,
            normalizeOptional(request == null ? null : request.defaultModel()),
            request == null ? null : request.defaultAnswerMode(),
            Instant.now()
        );
        return toDetail(conversation);
    }

    @Transactional
    public ConversationDetail patchConversation(String conversationId, ConversationPatchRequest request) {
        requireConversationsEnabled();
        String id = requireUuid(conversationId, "conversationId");
        conversationRepository.findConversation(id).orElseThrow(() -> notFound(conversationId));
        String title = request == null ? null : normalizeOptional(request.title());
        String status = request == null ? null : normalizeStatus(request.status());
        if (title == null && status == null) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "conversation.patch_empty",
                "PATCH must include title or status"
            );
        }
        return toDetail(conversationRepository.updateConversation(id, title, status, Instant.now()));
    }

    public List<ConversationRunDetail> listRuns(String conversationId) {
        requireConversationsEnabled();
        String id = requireUuid(conversationId, "conversationId");
        conversationRepository.findConversation(id).orElseThrow(() -> notFound(conversationId));
        return conversationRepository.listRuns(id).stream()
            .map(this::toRunDetail)
            .toList();
    }

    void requireConversationsEnabled() {
        if (!contextProperties.isConversationsEnabled()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "context.disabled",
                "Conversation context is disabled"
            );
        }
    }

    static String normalizeTitle(String rawTitle, String fallback) {
        String normalized = normalizeOptional(rawTitle);
        if (normalized == null) {
            return fallback;
        }
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String normalizeWorkspaceKey(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    static String requireUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "request.invalid_id",
                "Field '" + fieldName + "' must be a valid UUID",
                exception
            );
        }
    }

    private String normalizeStatus(String rawStatus) {
        String normalized = normalizeOptional(rawStatus);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (STATUS_ACTIVE.equals(upper) || STATUS_ARCHIVED.equals(upper)) {
            return upper;
        }
        throw new ApplicationException(
            ErrorType.INVALID_REQUEST,
            "conversation.invalid_status",
            "Conversation status must be ACTIVE or ARCHIVED"
        );
    }

    private ConversationSummary toSummary(StoredConversation conversation) {
        return new ConversationSummary(
            conversation.id(),
            conversation.workspaceKey(),
            conversation.title(),
            conversation.mode(),
            conversation.status(),
            conversation.defaultModel(),
            conversation.defaultAnswerMode(),
            conversation.createdAt(),
            conversation.updatedAt(),
            conversation.lastRunAt(),
            conversation.turnCount()
        );
    }

    private ConversationStickyState toStickyState(StoredConversationStickyState stickyState) {
        return new ConversationStickyState(
            stickyState.model(),
            stickyState.answerMode(),
            stickyState.instructionWorkspaceKey(),
            stickyState.knowledgeScope(),
            stickyState.retrievalFilters(),
            stickyState.instructionIds(),
            stickyState.scenarioInstructionIds(),
            stickyState.updatedFromRunId(),
            stickyState.updatedThroughTurnNo(),
            stickyState.version(),
            stickyState.createdAt(),
            stickyState.updatedAt()
        );
    }

    private ConversationDetail toDetail(StoredConversation conversation) {
        return new ConversationDetail(
            conversation.id(),
            conversation.workspaceKey(),
            conversation.title(),
            conversation.mode(),
            conversation.status(),
            conversation.defaultModel(),
            conversation.defaultAnswerMode(),
            conversation.createdAt(),
            conversation.updatedAt(),
            conversation.lastRunAt(),
            conversation.turnCount(),
            stickyStateRepository.findByConversationId(conversation.id())
                .map(this::toStickyState)
                .orElse(null)
        );
    }

    private ConversationRunDetail toRunDetail(StoredConversationRun run) {
        return new ConversationRunDetail(
            run.conversationId(),
            run.runId(),
            run.turnNo(),
            run.parentRunId(),
            run.clientTurnId(),
            run.userPrompt(),
            run.contextAssemblyId(),
            run.contextAssemblyStatus(),
            run.createdAt(),
            run.status(),
            run.completedAt(),
            run.failedAt(),
            run.failureCode(),
            run.failureMessage(),
            "/api/chat-runs/" + run.runId() + "/status",
            "/api/chat-runs/" + run.runId() + "/trace",
            "/api/chat-runs/" + run.runId() + "/result",
            "/api/chat-runs/" + run.runId() + "/cancel"
        );
    }

    private ApplicationException notFound(String conversationId) {
        return new ApplicationException(
            ErrorType.NOT_FOUND,
            "conversation.not_found",
            "Conversation '" + conversationId + "' does not exist"
        );
    }
}
