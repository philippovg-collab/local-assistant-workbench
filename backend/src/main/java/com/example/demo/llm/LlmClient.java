package com.example.demo.llm;

import com.example.demo.model.OllamaModelInfo;
import java.util.List;

public interface LlmClient {

    List<OllamaModelInfo> listModels();

    ChatResult chat(ChatRequest request);

    record ChatRequest(
        String model,
        List<Message> messages
    ) {
    }

    record Message(
        String role,
        String content
    ) {
    }

    record ChatResult(
        String model,
        String answer,
        String createdAt,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        String rawResponse,
        String finishReason,
        Long latencyMs
    ) {
        public ChatResult(
            String model,
            String answer,
            String createdAt,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
        ) {
            this(model, answer, createdAt, promptTokens, completionTokens, totalTokens, null, null, null);
        }

        public ChatResult withLatencyMs(Long fallbackLatencyMs) {
            if (latencyMs != null || fallbackLatencyMs == null) {
                return this;
            }
            return new ChatResult(
                model,
                answer,
                createdAt,
                promptTokens,
                completionTokens,
                totalTokens,
                rawResponse,
                finishReason,
                fallbackLatencyMs
            );
        }
    }
}
