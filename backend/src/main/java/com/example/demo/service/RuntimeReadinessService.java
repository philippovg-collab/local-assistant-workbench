package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.HealthProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.llm.LlmClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class RuntimeReadinessService {

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final LlmProperties llmProperties;
    private final HealthProperties healthProperties;
    private final AtomicReference<CachedRuntimeReadiness> cachedReadiness = new AtomicReference<>();
    private final AtomicReference<Instant> lastLlmSuccessfulProbeAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastEmbeddingSuccessfulProbeAt = new AtomicReference<>();

    public RuntimeReadinessService(
        LlmClient llmClient,
        EmbeddingClient embeddingClient,
        LlmProperties llmProperties,
        HealthProperties healthProperties
    ) {
        this.llmClient = llmClient;
        this.embeddingClient = embeddingClient;
        this.llmProperties = llmProperties;
        this.healthProperties = healthProperties;
    }

    public RuntimeReadiness currentReadiness() {
        Instant now = Instant.now();
        CachedRuntimeReadiness cached = cachedReadiness.get();
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.readiness();
        }

        RuntimeReadiness computed = computeReadiness(now);
        cachedReadiness.set(new CachedRuntimeReadiness(
            computed,
            now.plus(Duration.ofSeconds(Math.max(1, healthProperties.getReadinessCacheSeconds())))
        ));
        return computed;
    }

    private RuntimeReadiness computeReadiness(Instant now) {
        ComponentReadiness llmReadiness = probeDirectChat(now);
        ComponentReadiness embeddingReadiness = probeEmbeddings(now);
        String directStatus = llmReadiness.isUp() ? "UP" : "DOWN";
        String ragStatus = llmReadiness.isUp() && embeddingReadiness.isUp() ? "UP" : "DOWN";

        return new RuntimeReadiness(
            directStatus,
            ragStatus,
                llmReadiness.status(),
                llmReadiness.reasonCode(),
                llmReadiness.reasonMessage(),
                embeddingReadiness.status(),
                embeddingReadiness.reasonCode(),
                embeddingReadiness.reasonMessage(),
                now.toString(),
                toTimestamp(lastLlmSuccessfulProbeAt.get()),
                toTimestamp(lastEmbeddingSuccessfulProbeAt.get())
        );
    }

    private ComponentReadiness probeDirectChat(Instant now) {
        try {
            llmClient.chat(new LlmClient.ChatRequest(
                llmProperties.getModel(),
                List.of(new LlmClient.Message("user", "healthcheck"))
            ));
            lastLlmSuccessfulProbeAt.set(now);
            return ComponentReadiness.up();
        } catch (ApiException exception) {
            return ComponentReadiness.down(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ComponentReadiness.down("llm.healthcheck_failed", rootMessage(exception));
        }
    }

    private ComponentReadiness probeEmbeddings(Instant now) {
        try {
            float[] embedding = embeddingClient.embed("healthcheck");
            if (embedding == null || embedding.length == 0) {
                return ComponentReadiness.down(
                    "embedding.provider_empty_embedding",
                    "Embedding provider returned an empty vector during readiness probing."
                );
            }
            lastEmbeddingSuccessfulProbeAt.set(now);
            return ComponentReadiness.up();
        } catch (ApiException exception) {
            return ComponentReadiness.down(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ComponentReadiness.down("embedding.healthcheck_failed", rootMessage(exception));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String toTimestamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public record RuntimeReadiness(
        String directStatus,
        String ragStatus,
        String llmStatus,
        String llmReasonCode,
        String llmReasonMessage,
        String embeddingStatus,
        String embeddingReasonCode,
        String embeddingReasonMessage,
        String cachedAt,
        String llmLastSuccessfulProbeAt,
        String embeddingLastSuccessfulProbeAt
    ) {
    }

    private record ComponentReadiness(
        String status,
        String reasonCode,
        String reasonMessage
    ) {
        private static ComponentReadiness up() {
            return new ComponentReadiness("UP", null, null);
        }

        private static ComponentReadiness down(String reasonCode, String reasonMessage) {
            return new ComponentReadiness("DOWN", reasonCode, reasonMessage);
        }

        private boolean isUp() {
            return "UP".equals(status);
        }
    }
    private record CachedRuntimeReadiness(
        RuntimeReadiness readiness,
        Instant expiresAt
    ) {
    }
}
