package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionDetail;
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
    private final PromptPolicyResolver promptPolicyResolver;

    public ChatExecutionService(
        LlmClient llmClient,
        MaterialService materialService,
        InstructionService instructionService,
        PromptPolicyResolver promptPolicyResolver
    ) {
        this.llmClient = llmClient;
        this.materialService = materialService;
        this.instructionService = instructionService;
        this.promptPolicyResolver = promptPolicyResolver;
    }

    public ChatExecutionResponse execute(ChatExecutionRequest request) {
        List<InstructionDetail> instructions = instructionService.findInstructionsByIds(request.instructionIds());
        return execute(request, instructions);
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
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy = promptPolicyResolver.resolve(
            new ChatExecutionRequest(mode, request.model(), request.prompt(), request.systemPrompt(), request.instructionIds()),
            instructions
        );

        if (mode == ChatMode.RAG) {
            MaterialRetrievalResult retrievalResult = materialService.retrieveContext(request.prompt());
            if (retrievalResult.materialCount() == 0) {
                return emptyContextResponse(
                    mode,
                    promptPolicy,
                    request.prompt(),
                    "Сначала добавьте материалы. Без локального контекста RAG-режим не сможет ответить.",
                    List.of()
                );
            }

            if (retrievalResult.activeMaterialCount() == 0) {
                return emptyContextResponse(
                    mode,
                    promptPolicy,
                    request.prompt(),
                    "В базе знаний остались только архивные версии материалов. Добавьте новую активную версию или восстановите предыдущую.",
                    List.of()
                );
            }

            if (retrievalResult.readyMaterialCount() == 0) {
                return emptyContextResponse(
                    mode,
                    promptPolicy,
                    request.prompt(),
                    "Материалы уже приняты, но локальный индекс ещё не готов. Дождитесь завершения индексации и повторите запрос.",
                    List.of()
                );
            }

            if (retrievalResult.sources().isEmpty()) {
                return emptyContextResponse(
                    mode,
                    promptPolicy,
                    request.prompt(),
                    "Не нашёл релевантных фрагментов в загруженных материалах. Уточните запрос или обновите материалы.",
                    List.of()
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
            return new ChatExecutionResponse(
                mode,
                result.model(),
                request.prompt(),
                result.answer(),
                "ready",
                result.createdAt(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens(),
                promptPolicy.appliedInstructions(),
                retrievalResult.sources()
            );
        }

        List<LlmClient.Message> messages = buildDirectMessages(
            promptPolicy.systemPrompt(),
            request.prompt(),
            promptPolicy.userInstructions()
        );
        LlmClient.ChatResult result = llmClient.chat(new LlmClient.ChatRequest(promptPolicy.model(), messages));
        return new ChatExecutionResponse(
            mode,
            result.model(),
            request.prompt(),
            result.answer(),
            null,
            result.createdAt(),
            result.promptTokens(),
            result.completionTokens(),
            result.totalTokens(),
            promptPolicy.appliedInstructions(),
            List.of()
        );
    }

    private ChatExecutionResponse emptyContextResponse(
        ChatMode mode,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        String prompt,
        String answer,
        List<ChatSource> sources
    ) {
        return new ChatExecutionResponse(
            mode,
            promptPolicy.model(),
            prompt,
            answer,
            "no-context",
            Instant.now().toString(),
            null,
            null,
            null,
            promptPolicy.appliedInstructions(),
            sources
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
}
