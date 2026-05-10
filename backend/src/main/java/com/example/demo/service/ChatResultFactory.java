package com.example.demo.service;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChatResultFactory {

    private final AnswerModePostProcessor answerModePostProcessor;

    public ChatResultFactory(AnswerModePostProcessor answerModePostProcessor) {
        this.answerModePostProcessor = answerModePostProcessor;
    }

    public PreparedChatResult direct(
        ChatExecutionRequestParts requestParts,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        LlmClient.ChatResult result
    ) {
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.DIRECT,
            result.model(),
            requestParts.prompt(),
            result.answer(),
            null,
            result.createdAt(),
            result.promptTokens(),
            result.completionTokens(),
            result.totalTokens(),
            promptPolicy.answerMode(),
            promptPolicy.appliedInstructions(),
            requestParts.instructionTrace(),
            requestParts.knowledgeScopeResolved(),
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            null
        );
        return new PreparedChatResult(
            response,
            result.answer(),
            result.answer(),
            List.of(),
            postprocessSnapshot(promptPolicy.answerMode(), null, result.answer(), result.answer()),
            false,
            false
        );
    }

    public PreparedChatResult rag(
        ChatExecutionRequestParts requestParts,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        MaterialRetrievalResult retrievalResult,
        LlmClient.ChatResult result
    ) {
        String processedAnswer = answerModePostProcessor.apply(
            promptPolicy.answerMode(),
            result.answer(),
            retrievalResult
        );
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.RAG,
            result.model(),
            requestParts.prompt(),
            processedAnswer,
            "ready",
            result.createdAt(),
            result.promptTokens(),
            result.completionTokens(),
            result.totalTokens(),
            promptPolicy.answerMode(),
            promptPolicy.appliedInstructions(),
            requestParts.instructionTrace(),
            requestParts.knowledgeScopeResolved(),
            retrievalResult.retrievalTrace(),
            retrievalResult.retrievalDebug(),
            retrievalResult.sources(),
            null
        );
        return new PreparedChatResult(
            response,
            result.answer(),
            processedAnswer,
            retrievalResult.sources(),
            postprocessSnapshot(promptPolicy.answerMode(), "ready", result.answer(), processedAnswer),
            abstained(processedAnswer),
            strictSourcesBlocked(promptPolicy.answerMode(), result.answer(), processedAnswer)
        );
    }

    public PreparedChatResult noContext(
        ChatMode mode,
        PromptPolicyResolver.ResolvedPromptPolicy promptPolicy,
        ChatExecutionRequestParts requestParts,
        RetrievalTrace retrievalTrace,
        RetrievalDebug retrievalDebug,
        String answer,
        List<ChatSource> sources,
        String rawModelAnswer,
        boolean strictSourcesBlockedAnswer
    ) {
        ChatExecutionResponse response = new ChatExecutionResponse(
            mode,
            promptPolicy.model(),
            requestParts.prompt(),
            answer,
            "no-context",
            Instant.now().toString(),
            null,
            null,
            null,
            promptPolicy.answerMode(),
            promptPolicy.appliedInstructions(),
            requestParts.instructionTrace(),
            requestParts.knowledgeScopeResolved(),
            retrievalTrace,
            retrievalDebug,
            sources,
            null
        );
        return new PreparedChatResult(
            response,
            rawModelAnswer,
            answer,
            sources,
            postprocessSnapshot(promptPolicy.answerMode(), "no-context", rawModelAnswer, answer),
            abstained(answer),
            strictSourcesBlockedAnswer
        );
    }

    private Map<String, Object> postprocessSnapshot(
        AnswerMode answerMode,
        String contextStatus,
        String rawModelAnswer,
        String finalUserAnswer
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (answerMode != null) {
            snapshot.put("answerMode", answerMode.value());
        }
        if (contextStatus != null) {
            snapshot.put("contextStatus", contextStatus);
        }
        snapshot.put("rawChanged", rawModelAnswer != null && finalUserAnswer != null && !rawModelAnswer.equals(finalUserAnswer));
        snapshot.put("postprocessors", rawModelAnswer == null ? List.of() : List.of("AnswerModePostProcessor"));
        return snapshot;
    }

    private boolean abstained(String finalUserAnswer) {
        return "Не найдено в источниках.".equals(finalUserAnswer);
    }

    private boolean strictSourcesBlocked(AnswerMode answerMode, String rawModelAnswer, String finalUserAnswer) {
        return answerMode == AnswerMode.STRICT_SOURCES_ONLY
            && "Не найдено в источниках.".equals(finalUserAnswer)
            && (rawModelAnswer == null || !rawModelAnswer.equals(finalUserAnswer));
    }

    public record ChatExecutionRequestParts(
        String prompt,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved
    ) {
    }

    public record PreparedChatResult(
        ChatExecutionResponse response,
        String rawModelAnswer,
        String finalUserAnswer,
        List<ChatSource> sources,
        Map<String, Object> postprocess,
        boolean abstained,
        boolean strictSourcesBlockedAnswer
    ) {
        public PreparedChatResult withResponse(ChatExecutionResponse response) {
            return new PreparedChatResult(
                response,
                rawModelAnswer,
                finalUserAnswer,
                sources,
                postprocess,
                abstained,
                strictSourcesBlockedAnswer
            );
        }
    }
}
