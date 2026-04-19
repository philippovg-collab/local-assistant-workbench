package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.HealthProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.llm.LlmClient;
import com.example.demo.llm.LlmClient.ChatRequest;
import com.example.demo.llm.LlmClient.Message;
import com.example.demo.model.OllamaModelInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeReadinessService {

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final LlmProperties llmProperties;
    private final HealthProperties healthProperties;
    private final AtomicReference<CachedRuntimeReadiness> cachedReadiness = new AtomicReference<>();
    private final AtomicReference<Instant> lastLlmSuccessfulProbeAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastDirectSuccessfulProbeAt = new AtomicReference<>();
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
        CatalogProbeResult llmReadiness = probeModelCatalog(now);
        ComponentReadiness directReadiness = probeDirectExecution(now, llmReadiness);
        ComponentReadiness embeddingReadiness = probeEmbeddings(now);
        String directStatus = directReadiness.isUp() ? "UP" : "DOWN";
        String ragStatus = directReadiness.isUp() && embeddingReadiness.isUp() ? "UP" : "DOWN";

        return new RuntimeReadiness(
            directStatus,
            ragStatus,
            directReadiness.reasonCode(),
            directReadiness.reasonMessage(),
            llmReadiness.readiness().status(),
            llmReadiness.readiness().reasonCode(),
            llmReadiness.readiness().reasonMessage(),
            embeddingReadiness.status(),
            embeddingReadiness.reasonCode(),
            embeddingReadiness.reasonMessage(),
            now.toString(),
            toTimestamp(lastDirectSuccessfulProbeAt.get()),
            toTimestamp(lastLlmSuccessfulProbeAt.get()),
            toTimestamp(lastEmbeddingSuccessfulProbeAt.get())
        );
    }

    private CatalogProbeResult probeModelCatalog(Instant now) {
        try {
            List<OllamaModelInfo> models = llmClient.listModels();
            String directProbeModel = resolveDirectProbeModel(models);
            if (!StringUtils.hasText(directProbeModel)) {
                return CatalogProbeResult.down(
                    "llm.model_unavailable",
                    "No LLM model is available from the local provider for readiness probing."
                );
            }
            if (StringUtils.hasText(llmProperties.getModel())
                && models.stream().map(OllamaModelInfo::name).noneMatch(llmProperties.getModel()::equals)) {
                return CatalogProbeResult.down(
                    "llm.model_unavailable",
                    "Configured LLM model '" + llmProperties.getModel() + "' is not available from the local provider."
                );
            }
            lastLlmSuccessfulProbeAt.set(now);
            return CatalogProbeResult.up(directProbeModel);
        } catch (ApiException exception) {
            return CatalogProbeResult.down(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return CatalogProbeResult.down("llm.healthcheck_failed", rootMessage(exception));
        }
    }

    private ComponentReadiness probeDirectExecution(Instant now, CatalogProbeResult catalogProbeResult) {
        if (!catalogProbeResult.readiness().isUp()) {
            return ComponentReadiness.down(
                catalogProbeResult.readiness().reasonCode(),
                catalogProbeResult.readiness().reasonMessage()
            );
        }

        try {
            llmClient.chat(new ChatRequest(
                catalogProbeResult.directProbeModel(),
                List.of(new Message("user", "healthcheck"))
            ));
            lastDirectSuccessfulProbeAt.set(now);
            return ComponentReadiness.up();
        } catch (ApiException exception) {
            return ComponentReadiness.down(exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return ComponentReadiness.down("direct.healthcheck_failed", rootMessage(exception));
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

    private String resolveDirectProbeModel(List<OllamaModelInfo> models) {
        String configuredModel = llmProperties.getModel();
        if (StringUtils.hasText(configuredModel)) {
            return configuredModel;
        }
        return models.isEmpty() ? null : models.getFirst().name();
    }

    public record RuntimeReadiness(
        String directStatus,
        String ragStatus,
        String directReasonCode,
        String directReasonMessage,
        String llmStatus,
        String llmReasonCode,
        String llmReasonMessage,
        String embeddingStatus,
        String embeddingReasonCode,
        String embeddingReasonMessage,
        String cachedAt,
        String directLastSuccessfulProbeAt,
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

    private record CatalogProbeResult(
        ComponentReadiness readiness,
        String directProbeModel
    ) {
        private static CatalogProbeResult up(String directProbeModel) {
            return new CatalogProbeResult(ComponentReadiness.up(), directProbeModel);
        }

        private static CatalogProbeResult down(String reasonCode, String reasonMessage) {
            return new CatalogProbeResult(ComponentReadiness.down(reasonCode, reasonMessage), null);
        }
    }

    private record CachedRuntimeReadiness(
        RuntimeReadiness readiness,
        Instant expiresAt
    ) {
    }
}
