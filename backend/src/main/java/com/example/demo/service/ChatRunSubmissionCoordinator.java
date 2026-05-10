package com.example.demo.service;

import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.example.demo.validation.InputLimits;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class ChatRunSubmissionCoordinator {

    private final ChatExecutionProperties chatExecutionProperties;
    private final ContextProperties contextProperties;
    private final ChatRunQueueRepository queueRepository;
    private final ConversationRepository conversationRepository;
    private final ChatRunExecutionService chatRunExecutionService;
    private final ChatRunQueryService chatRunQueryService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper hashMapper;

    public ChatRunSubmissionCoordinator(
        ChatExecutionProperties chatExecutionProperties,
        ContextProperties contextProperties,
        ChatRunQueueRepository queueRepository,
        ConversationRepository conversationRepository,
        ChatRunExecutionService chatRunExecutionService,
        ChatRunQueryService chatRunQueryService,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        this.chatExecutionProperties = chatExecutionProperties;
        this.contextProperties = contextProperties;
        this.queueRepository = queueRepository;
        this.conversationRepository = conversationRepository;
        this.chatRunExecutionService = chatRunExecutionService;
        this.chatRunQueryService = chatRunQueryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.hashMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public ChatRunSubmissionResponse submit(ChatExecutionRequest request) {
        InputLimits.validateChatRequest(request);
        if (!isConversationalSubmit(request)) {
            ChatRunSubmissionResponse response = enqueueStateless(request);
            chatRunExecutionService.requestProcessing();
            return response;
        }
        if (!contextProperties.isConversationsEnabled()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "context.disabled",
                "Conversation context is disabled"
            );
        }

        SubmissionOutcome outcome = transactionTemplate.execute(status -> submitConversationally(request));
        if (outcome == null) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "chat_run.submit_failed",
                "Chat run submission did not return metadata"
            );
        }
        if (outcome.enqueued()) {
            chatRunExecutionService.requestProcessing();
        }
        return outcome.response();
    }

    public ChatExecutionResponse submitAndWait(ChatExecutionRequest request, Duration timeout) {
        ChatRunSubmissionResponse submittedRun = submit(request);
        Instant deadline = Instant.now().plus(effectiveWaitTimeout(timeout));
        while (true) {
            ChatRunStatusResponse status = chatRunQueryService.getStatus(submittedRun.id());
            if ("COMPLETED".equals(status.status())) {
                return chatRunQueryService.getResult(submittedRun.id());
            }
            if ("FAILED".equals(status.status())) {
                throw failedRunException(status);
            }
            if ("CANCELLED".equals(status.status())) {
                throw cancelledRunException(status);
            }
            Instant now = Instant.now();
            if (!now.isBefore(deadline)) {
                throw stillProcessingException(submittedRun);
            }
            sleepUntilNextPoll(now, deadline, submittedRun);
        }
    }

    private ChatRunSubmissionResponse enqueueStateless(ChatExecutionRequest request) {
        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        EnqueuedChatRun run = queueRepository.enqueue(request, mode, Instant.now());
        return responseFor(run.runId(), "RECEIVED", run.createdAt(), null, null);
    }

    private SubmissionOutcome submitConversationally(ChatExecutionRequest request) {
        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        String requestHash = requestHash(request);
        String conversationId = normalizeOptional(request.conversationId());
        boolean autoCreate = !StringUtils.hasText(conversationId) && Boolean.TRUE.equals(request.persistConversation());
        if (!autoCreate && !StringUtils.hasText(conversationId)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "conversation.required",
                "conversationId is required unless persistConversation is true"
            );
        }

        StoredConversation conversation = autoCreate
            ? createConversationForRequest(request, mode)
            : loadConversationForUpdate(conversationId);
        validateConversationForRequest(conversation, request, mode);

        String clientTurnId = normalizeOptional(request.clientTurnId());
        if (clientTurnId != null) {
            StoredConversationRun existingRun = conversationRepository
                .findRunByClientTurnId(conversation.id(), clientTurnId)
                .orElse(null);
            if (existingRun != null) {
                if (requestHash.equals(existingRun.requestHash())) {
                    return new SubmissionOutcome(
                        responseFor(existingRun.runId(), existingRun.status(), existingRun.createdAt(), existingRun.conversationId(), existingRun.turnNo()),
                        false
                    );
                }
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "conversation.client_turn_conflict",
                    "clientTurnId already exists in this conversation with different request content"
                );
            }
        }

        String parentRunId = normalizeOptional(request.parentRunId());
        if (parentRunId != null) {
            parentRunId = ConversationService.requireUuid(parentRunId, "parentRunId");
            if (!conversationRepository.runBelongsToConversation(conversation.id(), parentRunId)) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "conversation.parent_run_mismatch",
                    "parentRunId must belong to the same conversation"
                );
            }
        }

        Instant createdAt = Instant.now();
        int turnNo = conversationRepository.nextTurnNo(conversation.id());
        EnqueuedChatRun run = queueRepository.enqueue(request, mode, createdAt);
        conversationRepository.insertRun(
            conversation.id(),
            run.runId(),
            turnNo,
            parentRunId,
            clientTurnId,
            requestHash,
            request.prompt(),
            null,
            run.createdAt()
        );
        conversationRepository.touchConversation(conversation.id(), run.createdAt());
        return new SubmissionOutcome(
            responseFor(run.runId(), "RECEIVED", run.createdAt(), conversation.id(), turnNo),
            true
        );
    }

    private StoredConversation createConversationForRequest(ChatExecutionRequest request, ChatMode mode) {
        String title = ConversationService.normalizeTitle(request.prompt(), "Новая беседа");
        String workspaceKey = mode == ChatMode.RAG ? requestWorkspaceKey(request) : null;
        return conversationRepository.createConversation(
            UUID.randomUUID().toString(),
            workspaceKey,
            title,
            mode,
            ConversationService.normalizeOptional(request.model()),
            request.answerMode(),
            Instant.now()
        );
    }

    private StoredConversation loadConversationForUpdate(String conversationId) {
        String id = ConversationService.requireUuid(conversationId, "conversationId");
        return conversationRepository.findConversationForUpdate(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "conversation.not_found",
                "Conversation '" + conversationId + "' does not exist"
            ));
    }

    private void validateConversationForRequest(
        StoredConversation conversation,
        ChatExecutionRequest request,
        ChatMode mode
    ) {
        if (!ConversationService.STATUS_ACTIVE.equals(conversation.status())) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "conversation.archived",
                "Conversation is archived"
            );
        }
        if (conversation.mode() != mode) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "conversation.mode_mismatch",
                "Conversation mode does not match chat request mode"
            );
        }
        String conversationWorkspace = ConversationService.normalizeWorkspaceKey(conversation.workspaceKey());
        String requestWorkspace = mode == ChatMode.RAG ? requestWorkspaceKey(request) : null;
        if (conversationWorkspace != null && requestWorkspace != null && !conversationWorkspace.equals(requestWorkspace)) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "conversation.workspace_mismatch",
                "Conversation workspace does not match chat request workspace"
            );
        }
    }

    private String requestWorkspaceKey(ChatExecutionRequest request) {
        KnowledgeScope scope = request.knowledgeScope();
        String workspaceKey = scope == null ? null : scope.workspaceKey();
        if (!StringUtils.hasText(workspaceKey)) {
            workspaceKey = request.instructionWorkspaceKey();
        }
        return ConversationService.normalizeWorkspaceKey(workspaceKey);
    }

    private ChatRunSubmissionResponse responseFor(
        String runId,
        String status,
        Instant createdAt,
        String conversationId,
        Integer turnNo
    ) {
        return new ChatRunSubmissionResponse(
            runId,
            status,
            createdAt,
            "/api/chat-runs/" + runId + "/status",
            "/api/chat-runs/" + runId + "/trace",
            "/api/chat-runs/" + runId + "/result",
            conversationId,
            turnNo,
            "/api/chat-runs/" + runId + "/cancel"
        );
    }

    private boolean isConversationalSubmit(ChatExecutionRequest request) {
        return StringUtils.hasText(request.conversationId())
            || StringUtils.hasText(request.parentRunId())
            || StringUtils.hasText(request.clientTurnId())
            || Boolean.TRUE.equals(request.persistConversation());
    }

    private String requestHash(ChatExecutionRequest request) {
        try {
            byte[] payload = hashMapper.writeValueAsBytes(hashableRequest(request));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "conversation.request_hash_failed",
                "Unable to hash chat request for conversation idempotency",
                exception
            );
        }
    }

    private ChatExecutionRequest hashableRequest(ChatExecutionRequest request) {
        return new ChatExecutionRequest(
            request.mode(),
            request.model(),
            request.prompt(),
            request.systemPrompt(),
            request.instructionIds(),
            request.answerMode(),
            request.knowledgeScope(),
            request.instructionWorkspaceKey(),
            request.retrievalFilters(),
            request.dismissedRetrievalHintKeys(),
            request.scenarioInstructionIds(),
            request.temporaryInstruction(),
            null,
            request.parentRunId(),
            null,
            null,
            request.contextDebug(),
            request.contextOptions()
        );
    }

    private Duration effectiveWaitTimeout(Duration requestedTimeout) {
        Duration fallback = Duration.ofSeconds(chatExecutionProperties.getCompatibilityWaitTimeoutSeconds());
        if (requestedTimeout == null || requestedTimeout.isNegative() || requestedTimeout.isZero()) {
            return fallback;
        }
        return requestedTimeout.compareTo(Duration.ofSeconds(120)) > 0 ? Duration.ofSeconds(120) : requestedTimeout;
    }

    private void sleepUntilNextPoll(
        Instant now,
        Instant deadline,
        ChatRunSubmissionResponse submittedRun
    ) {
        long remainingMillis = Math.max(0L, Duration.between(now, deadline).toMillis());
        long pollMillis = Math.min(remainingMillis, boundedPollIntervalMillis());
        if (pollMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(pollMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApplicationException(
                ErrorType.PROVIDER_UNAVAILABLE,
                "chat_run.wait_interrupted",
                "Chat run '" + submittedRun.id() + "' was submitted, but waiting for the result was interrupted. Poll "
                    + submittedRun.statusUrl() + " or fetch " + submittedRun.resultUrl() + " when it completes.",
                exception
            );
        }
    }

    private long boundedPollIntervalMillis() {
        return Math.min(5000L, Math.max(100L, chatExecutionProperties.getPollIntervalMillis()));
    }

    private ApplicationException stillProcessingException(ChatRunSubmissionResponse submittedRun) {
        return new ApplicationException(
            ErrorType.REQUEST_TIMEOUT,
            "chat.run_still_processing",
            "Chat run '" + submittedRun.id() + "' is still processing. Poll "
                + submittedRun.statusUrl() + " or fetch " + submittedRun.resultUrl() + " when it completes."
        );
    }

    private ApplicationException failedRunException(ChatRunStatusResponse status) {
        return new ApplicationException(
            ErrorType.CONFLICT,
            StringUtils.hasText(status.failureCode()) ? status.failureCode() : "chat_run.failed",
            StringUtils.hasText(status.failureMessage()) ? status.failureMessage() : "Chat run failed"
        );
    }

    private ApplicationException cancelledRunException(ChatRunStatusResponse status) {
        return new ApplicationException(
            ErrorType.CONFLICT,
            "chat_run.cancelled",
            StringUtils.hasText(status.failureMessage()) ? status.failureMessage() : "Chat run was cancelled"
        );
    }

    private String normalizeOptional(String value) {
        return ConversationService.normalizeOptional(value);
    }

    private record SubmissionOutcome(ChatRunSubmissionResponse response, boolean enqueued) {
    }
}
