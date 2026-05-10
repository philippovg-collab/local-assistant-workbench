package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.example.demo.service.context.port.ConversationStickyStateRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationStickyStateService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationStickyStateService.class);

    private final ContextProperties contextProperties;
    private final ConversationRepository conversationRepository;
    private final ConversationStickyStateRepository stickyStateRepository;
    private final ChatRunTraceService chatRunTraceService;

    public ConversationStickyStateService(
        ContextProperties contextProperties,
        ConversationRepository conversationRepository,
        ConversationStickyStateRepository stickyStateRepository,
        ChatRunTraceService chatRunTraceService
    ) {
        this.contextProperties = contextProperties;
        this.conversationRepository = conversationRepository;
        this.stickyStateRepository = stickyStateRepository;
        this.chatRunTraceService = chatRunTraceService;
    }

    public StickyResolution resolve(
        ChatExecutionRequest request,
        ChatMode mode,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        if (request == null) {
            return StickyResolution.inactive(null, "missing_request");
        }
        StoredConversationRun currentRun = currentRun(traceContext);
        if (currentRun == null) {
            return StickyResolution.inactive(request, "missing_conversation_binding");
        }
        if (!contextProperties.isStickyStateEnabled()) {
            return StickyResolution.inactive(request, "sticky_state_disabled");
        }
        if (!useStickyState(request)) {
            return StickyResolution.disabledForRun(request, currentRun);
        }

        StoredConversation conversation = conversationRepository.findConversation(currentRun.conversationId()).orElse(null);
        if (conversation == null) {
            return StickyResolution.inactive(request, "missing_conversation");
        }
        StoredConversationStickyState stickyState = stickyStateRepository
            .findByConversationId(currentRun.conversationId())
            .orElse(null);

        Map<String, String> sources = new LinkedHashMap<>();
        String model = firstText(
            sources,
            "model",
            explicitText(request.model()),
            "request",
            stickyState == null ? null : explicitText(stickyState.model()),
            "sticky",
            explicitText(conversation.defaultModel()),
            "conversation_default"
        );
        AnswerMode answerMode = firstValue(
            sources,
            "answerMode",
            request.answerMode(),
            "request",
            stickyState == null ? null : stickyState.answerMode(),
            "sticky",
            conversation.defaultAnswerMode(),
            "conversation_default"
        );
        KnowledgeScope knowledgeScope = firstValue(
            sources,
            "knowledgeScope",
            request.knowledgeScope(),
            "request",
            stickyState == null ? null : stickyState.knowledgeScope(),
            "sticky",
            null,
            "conversation_default"
        );
        RetrievalFilters retrievalFilters = firstValue(
            sources,
            "retrievalFilters",
            request.retrievalFilters(),
            "request",
            stickyState == null ? null : stickyState.retrievalFilters(),
            "sticky",
            null,
            "conversation_default"
        );
        List<String> instructionIds = firstValue(
            sources,
            "instructionIds",
            request.instructionIds(),
            "request",
            stickyState == null ? null : stickyState.instructionIds(),
            "sticky",
            null,
            "conversation_default"
        );
        List<String> scenarioInstructionIds = firstValue(
            sources,
            "scenarioInstructionIds",
            request.scenarioInstructionIds(),
            "request",
            stickyState == null ? null : stickyState.scenarioInstructionIds(),
            "sticky",
            null,
            "conversation_default"
        );
        String instructionWorkspaceKey = firstText(
            sources,
            "instructionWorkspaceKey",
            explicitText(request.instructionWorkspaceKey()),
            "request",
            stickyState == null ? null : explicitText(stickyState.instructionWorkspaceKey()),
            "sticky",
            null,
            "conversation_default"
        );
        boolean derivedInstructionWorkspaceKey = false;
        if (!StringUtils.hasText(instructionWorkspaceKey) && knowledgeScope != null && StringUtils.hasText(knowledgeScope.workspaceKey())) {
            instructionWorkspaceKey = knowledgeScope.workspaceKey().trim();
            sources.put("instructionWorkspaceKey", "derived_knowledge_scope");
            derivedInstructionWorkspaceKey = true;
        }

        ChatExecutionRequest mergedRequest = new ChatExecutionRequest(
            mode,
            model,
            request.prompt(),
            request.systemPrompt(),
            instructionIds,
            answerMode,
            knowledgeScope,
            instructionWorkspaceKey,
            retrievalFilters,
            request.dismissedRetrievalHintKeys(),
            scenarioInstructionIds,
            request.temporaryInstruction(),
            request.conversationId(),
            request.parentRunId(),
            request.clientTurnId(),
            request.persistConversation(),
            request.contextDebug(),
            request.contextOptions()
        );
        return new StickyResolution(
            true,
            false,
            null,
            mergedRequest,
            currentRun,
            stickyState,
            Map.copyOf(sources),
            derivedInstructionWorkspaceKey
        );
    }

    public void updateAfterCompleted(
        StickyResolution resolution,
        ChatExecutionRequest normalizedRequest,
        ChatExecutionResponse response,
        ChatRunTraceService.RunTraceContext traceContext
    ) {
        if (resolution == null || !resolution.active() || resolution.disabledForRun()) {
            return;
        }
        StoredConversationRun run = resolution.currentRun();
        if (run == null || normalizedRequest == null) {
            return;
        }

        String instructionWorkspaceKey = resolution.derivedInstructionWorkspaceKey()
            ? null
            : explicitText(normalizedRequest.instructionWorkspaceKey());
        try {
            stickyStateRepository.upsertCompletedTurn(
                run.conversationId(),
                firstNonBlank(response == null ? null : response.model(), normalizedRequest.model()),
                response == null || response.answerModeApplied() == null
                    ? normalizedRequest.answerMode()
                    : response.answerModeApplied(),
                instructionWorkspaceKey,
                normalizedRequest.knowledgeScope(),
                normalizedRequest.retrievalFilters(),
                normalizedRequest.instructionIds(),
                normalizedRequest.scenarioInstructionIds(),
                run.runId(),
                run.turnNo(),
                Instant.now()
            );
        } catch (RuntimeException exception) {
            recordDegraded(traceContext, exception);
            logger.warn("Conversation sticky state update failed; completed run is kept", exception);
        }
    }

    private StoredConversationRun currentRun(ChatRunTraceService.RunTraceContext traceContext) {
        if (traceContext == null || !StringUtils.hasText(traceContext.id())) {
            return null;
        }
        return conversationRepository.findRunByRunId(traceContext.id()).orElse(null);
    }

    private boolean useStickyState(ChatExecutionRequest request) {
        return request.contextOptions() == null
            || request.contextOptions().useStickyState() == null
            || Boolean.TRUE.equals(request.contextOptions().useStickyState());
    }

    private void recordDegraded(ChatRunTraceService.RunTraceContext traceContext, RuntimeException exception) {
        try {
            chatRunTraceService.insertEvent(traceContext, "CONVERSATION_STICKY_STATE_DEGRADED", Map.of(
                "reason",
                exception.getClass().getSimpleName()
            ));
        } catch (RuntimeException eventException) {
            logger.warn("Unable to record sticky state degraded event", eventException);
        }
    }

    private String firstText(
        Map<String, String> sources,
        String field,
        String requestValue,
        String requestSource,
        String stickyValue,
        String stickySource,
        String defaultValue,
        String defaultSource
    ) {
        if (StringUtils.hasText(requestValue)) {
            sources.put(field, requestSource);
            return requestValue.trim();
        }
        if (StringUtils.hasText(stickyValue)) {
            sources.put(field, stickySource);
            return stickyValue.trim();
        }
        if (StringUtils.hasText(defaultValue)) {
            sources.put(field, defaultSource);
            return defaultValue.trim();
        }
        sources.put(field, "app_default");
        return null;
    }

    private <T> T firstValue(
        Map<String, String> sources,
        String field,
        T requestValue,
        String requestSource,
        T stickyValue,
        String stickySource,
        T defaultValue,
        String defaultSource
    ) {
        if (requestValue != null) {
            sources.put(field, requestSource);
            return requestValue;
        }
        if (stickyValue != null) {
            sources.put(field, stickySource);
            return stickyValue;
        }
        if (defaultValue != null) {
            sources.put(field, defaultSource);
            return defaultValue;
        }
        sources.put(field, "app_default");
        return null;
    }

    private String explicitText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : explicitText(second);
    }

    public record StickyResolution(
        boolean active,
        boolean disabledForRun,
        String disabledReason,
        ChatExecutionRequest request,
        StoredConversationRun currentRun,
        StoredConversationStickyState stickyState,
        Map<String, String> fieldSources,
        boolean derivedInstructionWorkspaceKey
    ) {
        public StickyResolution {
            fieldSources = fieldSources == null ? Map.of() : Map.copyOf(fieldSources);
        }

        public static StickyResolution inactive(ChatExecutionRequest request, String reason) {
            return new StickyResolution(false, false, reason, request, null, null, Map.of(), false);
        }

        public static StickyResolution disabledForRun(ChatExecutionRequest request, StoredConversationRun currentRun) {
            return new StickyResolution(false, true, "disabled_by_request", request, currentRun, null, Map.of(), false);
        }

        public Map<String, Object> metadata() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("active", active);
            payload.put("disabledForRun", disabledForRun);
            if (disabledReason != null) {
                payload.put("disabledReason", disabledReason);
            }
            if (currentRun != null) {
                payload.put("conversationId", currentRun.conversationId());
                payload.put("turnNo", currentRun.turnNo());
            }
            if (stickyState != null) {
                payload.put("stickyVersion", stickyState.version());
                payload.put("stickyUpdatedThroughTurnNo", stickyState.updatedThroughTurnNo());
            }
            Map<String, Object> resolvedState = resolvedState();
            if (!resolvedState.isEmpty()) {
                payload.put("resolvedState", resolvedState);
            }
            payload.put("fieldSources", fieldSources);
            return payload;
        }

        private Map<String, Object> resolvedState() {
            if (request == null) {
                return Map.of();
            }
            Map<String, Object> state = new LinkedHashMap<>();
            putText(state, "model", request.model());
            if (request.answerMode() != null) {
                state.put("answerMode", request.answerMode().value());
            }
            putText(state, "instructionWorkspaceKey", request.instructionWorkspaceKey());
            if (request.knowledgeScope() != null) {
                state.put("knowledgeScope", request.knowledgeScope());
            }
            if (request.retrievalFilters() != null) {
                state.put("retrievalFilters", request.retrievalFilters());
            }
            if (request.instructionIds() != null) {
                state.put("instructionIds", request.instructionIds());
            }
            if (request.scenarioInstructionIds() != null) {
                state.put("scenarioInstructionIds", request.scenarioInstructionIds());
            }
            return state;
        }

        private static void putText(Map<String, Object> state, String key, String value) {
            if (StringUtils.hasText(value)) {
                state.put(key, value.trim());
            }
        }
    }
}
