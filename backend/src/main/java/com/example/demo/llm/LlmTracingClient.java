package com.example.demo.llm;

import com.example.demo.service.cancellation.ChatCancellationToken;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class LlmTracingClient {

    private final LlmClient delegate;

    public LlmTracingClient(LlmClient delegate) {
        this.delegate = delegate;
    }

    public LlmClient.ChatResult chat(LlmClient.ChatRequest request) {
        return chat(request, ChatCancellationToken.none());
    }

    public LlmClient.ChatResult chat(LlmClient.ChatRequest request, ChatCancellationToken cancellationToken) {
        Instant startedAt = Instant.now();
        LlmClient.ChatResult result = delegate.chat(request, cancellationToken);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        return result == null ? null : result.withLatencyMs(latencyMs);
    }
}
