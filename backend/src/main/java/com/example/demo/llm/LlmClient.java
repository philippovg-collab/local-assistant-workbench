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
        Integer totalTokens
    ) {
    }
}
