package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionDetail;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalTrace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatExecutionService {

    private final LlmClient llmClient;
    private final MaterialService materialService;
    private final InstructionService instructionService;
    private final KnowledgePresetService knowledgePresetService;
    private final ChatAuditService chatAuditService;
    private final PromptPolicyResolver promptPolicyResolver;
    private final AnswerModePostProcessor answerModePostProcessor;

    public ChatExecutionService(
        LlmClient llmClient,
        MaterialService materialService,
        InstructionService instructionService,
        KnowledgePresetService knowledgePresetService,
        ChatAuditService chatAuditService,
        PromptPolicyResolver promptPolicyResolver,
        AnswerModePostProcessor answerModePostProcessor
    ) {
        this.llmClient = llmClient;
        this.materialService = materialService;
        this.instructionService = instructionService;
        this.knowledgePresetService = knowledgePresetService;
        this.chatAuditService = chatAuditService;
        this.promptPolicyResolver = promptPolicyResolver;
        this.answerModePostProcessor = answerModePostProcessor;
    }

    public ChatExecutionResponse execute(ChatExecutionRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_request",
                "Field 'prompt' is required"
            );
        }

        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        KnowledgePresetService.ResolvedKnowledgeScopeContext scopeContext = knowledgePresetService.resolveScope(request.knowledgeScope());
        ChatExecutionRequest requestWithResolvedScope = withKnowledgeScope(request, scopeContext.effectiveScope());
        InstructionService.ResolvedInstructionContext instructionContext =
            instructionService.resolveRuntimeInstructions(requestWithResolvedScope);
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy = promptPolicyResolver.resolve(
            normalizedRequest(mode, requestWithResolvedScope),
            instructionContext.instructions(),
            instructionContext.temporaryInstruction()
        );

        if (mode == ChatMode.RAG) {
            return executeRag(requestWithResolvedScope, promptPolicy, instructionContext.trace(), scopeContext);
        }

        return executeDirect(requestWithResolvedScope, promptPolicy, instructionContext.trace(), scopeContext.resolvedScope());
    }

    public ChatExecutionResponse execute(ChatExecutionRequest request, List<InstructionDetail> instructions) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_request",
                "Field 'prompt' is required"
            );
        }

        ChatMode mode = request.mode() == null ? ChatMode.DIRECT : request.mode();
        String temporaryInstruction = firstNonBlank(request.temporaryInstruction(), request.systemPrompt());
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy = promptPolicyResolver.resolve(
            normalizedRequest(mode, request),
            instructions == null ? List.of() : instructions,
            temporaryInstruction
        );
        List<InstructionTraceEntry> trace = instructions == null
            ? List.of()
            : instructions.stream()
                .map(instruction -> new InstructionTraceEntry(
                    instruction.id(),
                    instruction.title(),
                    instruction.category(),
                    instruction.scopeLevel(),
                    instruction.scopeTargetId(),
                    instruction.revision(),
                    instruction.active(),
                    false,
                    clip(instruction.content(), 240)
                ))
                .toList();

        if (mode == ChatMode.RAG) {
            return executeRag(
                request,
                promptPolicy,
                trace,
                knowledgePresetService.resolveScope(request.knowledgeScope())
            );
        }

        return executeDirect(
            request,
            promptPolicy,
            trace,
            knowledgePresetService.resolveScope(request.knowledgeScope()).resolvedScope()
        );
    }

    private ChatExecutionResponse executeRag(
        ChatExecutionRequest request,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgePresetService.ResolvedKnowledgeScopeContext scopeContext
    ) {
        MaterialRetrievalResult retrievalResult = materialService.retrieveContext(
            request.prompt(),
            scopeContext.effectiveScope(),
            request.retrievalFilters()
        );
        AnswerMode answerMode = promptPolicy.answerMode();

        if (retrievalResult.materialCount() == 0) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                "Сначала добавьте материалы. Без локального контекста RAG-режим не сможет ответить.",
                List.of()
            );
        }

        if (retrievalResult.activeMaterialCount() == 0) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                "В базе знаний остались только архивные версии материалов. Добавьте новую активную версию или восстановите предыдущую.",
                List.of()
            );
        }

        if (retrievalResult.scopedMaterialCount() == 0) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                "В выбранном наборе знаний нет материалов. Измени пресет или загрузи документы в этот корпус.",
                List.of()
            );
        }

        if (retrievalResult.scopedReadyMaterialCount() == 0) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                "В выбранном корпусе есть материалы, но индекс ещё не готов. Дождитесь завершения индексации и повторите запрос.",
                List.of()
            );
        }

        if (retrievalResult.sources().isEmpty()) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                answerMode == AnswerMode.STRICT_SOURCES_ONLY
                    ? "Не найдено в источниках."
                    : "Не нашёл релевантных фрагментов в загруженных материалах. Уточните запрос или обновите материалы.",
                List.of()
            );
        }

        if (answerMode == AnswerMode.STRICT_SOURCES_ONLY
            && !"sufficient".equalsIgnoreCase(retrievalResult.retrievalTrace().supportVerdict())) {
            return emptyContextResponse(
                ChatMode.RAG,
                promptPolicy,
                instructionTrace,
                scopeContext.resolvedScope(),
                retrievalResult.retrievalTrace(),
                retrievalResult.retrievalDebug(),
                request.prompt(),
                "Не найдено в источниках.",
                retrievalResult.sources()
            );
        }

        List<LlmClient.Message> messages = buildRagMessages(
            promptPolicy.systemPrompt(),
            request.prompt(),
            retrievalResult.matches(),
            promptPolicy.contextInstructions(),
            promptPolicy.userInstructions()
        );
        LlmClient.ChatResult result = llmClient.chat(new LlmClient.ChatRequest(promptPolicy.model(), messages));
        String processedAnswer = answerModePostProcessor.apply(answerMode, result.answer(), retrievalResult);

        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.RAG,
            result.model(),
            request.prompt(),
            processedAnswer,
            "ready",
            result.createdAt(),
            result.promptTokens(),
            result.completionTokens(),
            result.totalTokens(),
            answerMode,
            promptPolicy.appliedInstructions(),
            instructionTrace,
            scopeContext.resolvedScope(),
            retrievalResult.retrievalTrace(),
            retrievalResult.retrievalDebug(),
            retrievalResult.sources(),
            null
        );
        return withAudit(response);
    }

    private ChatExecutionResponse executeDirect(
        ChatExecutionRequest request,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved
    ) {
        List<LlmClient.Message> messages = buildDirectMessages(
            promptPolicy.systemPrompt(),
            request.prompt(),
            promptPolicy.userInstructions()
        );
        LlmClient.ChatResult result = llmClient.chat(new LlmClient.ChatRequest(promptPolicy.model(), messages));
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.DIRECT,
            result.model(),
            request.prompt(),
            result.answer(),
            null,
            result.createdAt(),
            result.promptTokens(),
            result.completionTokens(),
            result.totalTokens(),
            promptPolicy.answerMode(),
            promptPolicy.appliedInstructions(),
            instructionTrace,
            knowledgeScopeResolved == null ? KnowledgeScopeResolved.empty() : knowledgeScopeResolved,
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            null
        );
        return withAudit(response);
    }

    private ChatExecutionResponse emptyContextResponse(
        ChatMode mode,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved,
        RetrievalTrace retrievalTrace,
        com.example.demo.model.RetrievalDebug retrievalDebug,
        String prompt,
        String answer,
        List<ChatSource> sources
    ) {
        ChatExecutionResponse response = new ChatExecutionResponse(
            mode,
            promptPolicy.model(),
            prompt,
            answer,
            "no-context",
            Instant.now().toString(),
            null,
            null,
            null,
            promptPolicy.answerMode(),
            promptPolicy.appliedInstructions(),
            instructionTrace,
            knowledgeScopeResolved,
            retrievalTrace,
            retrievalDebug,
            sources,
            null
        );
        return withAudit(response);
    }

    private ChatExecutionRequest normalizedRequest(ChatMode mode, ChatExecutionRequest request) {
        return new ChatExecutionRequest(
            mode,
            request.model(),
            request.prompt(),
            request.systemPrompt(),
            request.instructionIds(),
            request.answerMode(),
            request.knowledgeScope(),
            request.retrievalFilters(),
            request.scenarioInstructionIds(),
            request.temporaryInstruction()
        );
    }

    private ChatExecutionRequest withKnowledgeScope(ChatExecutionRequest request, com.example.demo.model.KnowledgeScope knowledgeScope) {
        return new ChatExecutionRequest(
            request.mode(),
            request.model(),
            request.prompt(),
            request.systemPrompt(),
            request.instructionIds(),
            request.answerMode(),
            knowledgeScope,
            request.retrievalFilters(),
            request.scenarioInstructionIds(),
            request.temporaryInstruction()
        );
    }

    private List<LlmClient.Message> buildDirectMessages(
        String systemPrompt,
        String prompt,
        String userInstructions
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new LlmClient.Message("system", systemPrompt));
        }

        messages.add(new LlmClient.Message("user", composeUserMessage(userInstructions, prompt)));
        return messages;
    }

    private List<LlmClient.Message> buildRagMessages(
        String systemPrompt,
        String prompt,
        List<RetrievedMaterialChunk> matches,
        String contextInstructions,
        String userInstructions
    ) {
        List<LlmClient.Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new LlmClient.Message("system", systemPrompt));
        }

        StringBuilder userMessage = new StringBuilder();
        if (StringUtils.hasText(contextInstructions)) {
            userMessage.append(contextInstructions.trim()).append("\n\n");
        }
        userMessage.append("Retrieved context:\n");
        for (RetrievedMaterialChunk match : matches) {
            ChatSource source = match.source();
            userMessage.append("Source: ")
                .append(source.title())
                .append(source.page() == null ? "" : " (page " + source.page() + ")")
                .append('\n')
                .append(match.contextText())
                .append("\n\n");
        }
        if (StringUtils.hasText(userInstructions)) {
            userMessage.append(userInstructions.trim()).append("\n\n");
        }
        userMessage.append("User request:\n").append(prompt.trim());

        messages.add(new LlmClient.Message("user", userMessage.toString().trim()));
        return messages;
    }

    private String composeUserMessage(String userInstructions, String prompt) {
        if (!StringUtils.hasText(userInstructions)) {
            return prompt.trim();
        }
        return userInstructions.trim() + "\n\nUser request:\n" + prompt.trim();
    }

    private ChatExecutionResponse withAudit(ChatExecutionResponse response) {
        String auditRunId = chatAuditService.record(response);
        return new ChatExecutionResponse(
            response.mode(),
            response.model(),
            response.prompt(),
            response.answer(),
            response.contextStatus(),
            response.createdAt(),
            response.promptTokens(),
            response.completionTokens(),
            response.totalTokens(),
            response.answerModeApplied(),
            response.appliedInstructions(),
            response.instructionTrace(),
            response.knowledgeScopeResolved(),
            response.retrievalTrace(),
            response.retrievalDebug(),
            response.sources(),
            auditRunId
        );
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    private String clip(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
