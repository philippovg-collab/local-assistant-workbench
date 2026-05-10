package com.example.demo.service;

import com.example.demo.llm.LlmClient;
import com.example.demo.llm.LlmTracingClient;
import com.example.demo.service.cancellation.ChatCancellationToken;
import org.springframework.stereotype.Service;

@Service
public class ChatLlmExecutionStep {

    private final LlmClient llmClient;
    private final LlmTracingClient llmTracingClient;

    public ChatLlmExecutionStep(LlmClient llmClient, LlmTracingClient llmTracingClient) {
        this.llmClient = llmClient;
        this.llmTracingClient = llmTracingClient;
    }

    public LlmClient.ChatResult execute(
        LlmClient.ChatRequest request,
        ChatCancellationToken cancellationToken
    ) {
        return llmTracingClient == null
            ? llmClient.chat(request, cancellationToken)
            : llmTracingClient.chat(request, cancellationToken);
    }
}
