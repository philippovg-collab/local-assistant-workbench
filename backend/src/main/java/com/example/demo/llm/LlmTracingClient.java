package com.example.demo.llm;

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
        Instant startedAt = Instant.now();
        LlmClient.ChatResult result = delegate.chat(request);
        long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
        return result == null ? null : result.withLatencyMs(latencyMs);
    }
}
