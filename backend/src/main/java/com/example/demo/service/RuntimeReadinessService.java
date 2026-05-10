package com.example.demo.service;

import com.example.demo.error.CodedException;
import com.example.demo.config.HealthProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.embedding.OpenAiCompatibleEmbeddingClient;
import com.example.demo.llm.LlmClient;
import com.example.demo.llm.LlmClient.ChatRequest;
import com.example.demo.llm.LlmClient.Message;
import com.example.demo.llm.OpenAiCompatibleLlmClient;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.llmprovider.LlmProviderActivationChangedEvent;
import com.example.demo.llmprovider.LlmProviderErrorSanitizer;
import com.example.demo.llmprovider.LlmProviderService;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.OllamaModelInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RuntimeReadinessService {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeReadinessService.class);

    private final LlmClient llmClient;
    private final EmbeddingClient embeddingClient;
    private final LlmProperties llmProperties;
    private final ActiveLlmProviderResolver activeProviderResolver;
    private final LlmProviderService llmProviderService;
    private final LlmProviderErrorSanitizer errorSanitizer;
    private final HealthProperties healthProperties;
    private final Object readinessRefreshMonitor = new Object();
    private final AtomicReference<CachedRuntimeReadiness> cachedReadiness = new AtomicReference<>();
    private final AtomicReference<Instant> lastLlmSuccessfulProbeAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastDirectSuccessfulProbeAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastEmbeddingSuccessfulProbeAt = new AtomicReference<>();

    @Autowired
    public RuntimeReadinessService(
        LlmClient llmClient,
        EmbeddingClient embeddingClient,
        LlmProperties llmProperties,
        ActiveLlmProviderResolver activeProviderResolver,
        LlmProviderService llmProviderService,
        LlmProviderErrorSanitizer errorSanitizer,
        HealthProperties healthProperties
    ) {
        this.llmClient = llmClient;
        this.embeddingClient = embeddingClient;
        this.llmProperties = llmProperties;
        this.activeProviderResolver = activeProviderResolver;
        this.llmProviderService = llmProviderService;
        this.errorSanitizer = errorSanitizer;
        this.healthProperties = healthProperties;
    }

    public RuntimeReadinessService(
        LlmClient llmClient,
        EmbeddingClient embeddingClient,
        LlmProperties llmProperties,
        HealthProperties healthProperties
    ) {
        this(llmClient, embeddingClient, llmProperties, null, null, new LlmProviderErrorSanitizer(), healthProperties);
    }

    public RuntimeReadiness snapshot() {
        CachedRuntimeReadiness cached = cachedReadiness.get();
        if (cached != null) {
            return cached.readiness();
        }
        return RuntimeReadiness.unknown(Instant.now());
    }

    public RuntimeReadiness currentReadiness() {
        return refreshReadiness(false);
    }

    @Scheduled(
        initialDelayString = "${app.health.readiness-initial-delay-millis:1000}",
        fixedDelayString = "${app.health.readiness-probe-interval-millis:30000}"
    )
    public void refreshReadinessInBackground() {
        refreshReadiness(true);
    }

    @EventListener
    public void invalidateReadinessCache(LlmProviderActivationChangedEvent event) {
        cachedReadiness.set(null);
    }

    public RuntimeReadiness refreshReadiness() {
        return refreshReadiness(true);
    }

    private RuntimeReadiness refreshReadiness(boolean force) {
        Instant now = Instant.now();
        CachedRuntimeReadiness cached = cachedReadiness.get();
        if (!force && cached != null && now.isBefore(cached.expiresAt())) {
            return cached.readiness();
        }

        synchronized (readinessRefreshMonitor) {
            now = Instant.now();
            cached = cachedReadiness.get();
            if (!force && cached != null && now.isBefore(cached.expiresAt())) {
                return cached.readiness();
            }

            RuntimeReadiness computed = computeReadiness(now);
            cachedReadiness.set(new CachedRuntimeReadiness(
                computed,
                now.plus(Duration.ofSeconds(Math.max(1, healthProperties.getReadinessCacheSeconds())))
            ));
            return computed;
        }
    }

    private RuntimeReadiness computeReadiness(Instant now) {
        ProviderSnapshot providers = activeProviderSnapshot();
        CatalogProbeResult llmReadiness = probeModelCatalog(now, providers.chatProvider());
        ComponentReadiness directReadiness = probeDirectExecution(now, llmReadiness, providers.chatProvider());
        ComponentReadiness embeddingReadiness = probeEmbeddings(now, providers.embeddingProvider());
        String directStatus = directReadiness.isUp() ? "UP" : "DOWN";
        String ragStatus = directReadiness.isUp() && embeddingReadiness.isUp() ? "UP" : "DOWN";

        RuntimeReadiness readiness = new RuntimeReadiness(
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
        persistActiveProviderStatuses(readiness, now, providers);
        return readiness;
    }

    private ProviderSnapshot activeProviderSnapshot() {
        if (activeProviderResolver == null) {
            return new ProviderSnapshot(null, null);
        }
        return new ProviderSnapshot(
            activeProviderResolver.resolveChatProvider(),
            activeProviderResolver.resolveEmbeddingProvider()
        );
    }

    private void persistActiveProviderStatuses(
        RuntimeReadiness readiness,
        Instant checkedAt,
        ProviderSnapshot providers
    ) {
        if (llmProviderService == null || activeProviderResolver == null || providers == null) {
            return;
        }
        ActiveLlmProvider chatProvider = providers.chatProvider();
        ActiveLlmProvider embeddingProvider = providers.embeddingProvider();
        if (chatProvider != null
            && embeddingProvider != null
            && !chatProvider.fallback()
            && !embeddingProvider.fallback()
            && chatProvider.id().equals(embeddingProvider.id())) {
            if (!activeProviderStillCurrent(chatProvider, LlmProviderPurpose.CHAT)
                || !activeProviderStillCurrent(embeddingProvider, LlmProviderPurpose.EMBEDDING)) {
                return;
            }
            persistProviderStatus(
                chatProvider,
                combinedStatus(llmProviderStatus(readiness.llmStatus()), llmProviderStatus(readiness.embeddingStatus())),
                checkedAt,
                firstFailureCode(readiness),
                firstFailureMessage(readiness),
                LlmProviderPurpose.CHAT
            );
            return;
        }
        persistProviderStatus(
            chatProvider,
            llmProviderStatus(readiness.llmStatus()),
            checkedAt,
            readiness.llmReasonCode(),
            readiness.llmReasonMessage(),
            LlmProviderPurpose.CHAT
        );
        persistProviderStatus(
            embeddingProvider,
            llmProviderStatus(readiness.embeddingStatus()),
            checkedAt,
            readiness.embeddingReasonCode(),
            readiness.embeddingReasonMessage(),
            LlmProviderPurpose.EMBEDDING
        );
    }

    private void persistProviderStatus(
        ActiveLlmProvider provider,
        LlmProviderStatus status,
        Instant checkedAt,
        String errorCode,
        String errorMessage,
        LlmProviderPurpose purpose
    ) {
        if (provider == null || provider.fallback()) {
            return;
        }
        if (!activeProviderStillCurrent(provider, purpose)) {
            return;
        }
        try {
            llmProviderService.persistProbeResult(
                UUID.fromString(provider.id()),
                status,
                checkedAt,
                status == LlmProviderStatus.UP ? null : errorCode,
                status == LlmProviderStatus.UP ? null : sanitize(errorMessage)
            );
        } catch (RuntimeException exception) {
            logger.warn(
                "Unable to persist active LLM provider readiness status providerId={} status={}",
                provider.id(),
                status,
                exception
            );
        }
    }

    private boolean activeProviderStillCurrent(ActiveLlmProvider provider, LlmProviderPurpose purpose) {
        if (provider == null || provider.fallback() || activeProviderResolver == null) {
            return false;
        }
        ActiveLlmProvider current = purpose == LlmProviderPurpose.CHAT
            ? activeProviderResolver.resolveChatProvider()
            : activeProviderResolver.resolveEmbeddingProvider();
        return current != null && !current.fallback() && provider.id().equals(current.id());
    }

    private LlmProviderStatus llmProviderStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return LlmProviderStatus.UNKNOWN;
        }
        try {
            return LlmProviderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return LlmProviderStatus.UNKNOWN;
        }
    }

    private LlmProviderStatus combinedStatus(LlmProviderStatus first, LlmProviderStatus second) {
        if (first == LlmProviderStatus.DOWN || second == LlmProviderStatus.DOWN) {
            return LlmProviderStatus.DOWN;
        }
        if (first == LlmProviderStatus.DEGRADED || second == LlmProviderStatus.DEGRADED) {
            return LlmProviderStatus.DEGRADED;
        }
        if (first == LlmProviderStatus.UP && second == LlmProviderStatus.UP) {
            return LlmProviderStatus.UP;
        }
        return LlmProviderStatus.UNKNOWN;
    }

    private String firstFailureCode(RuntimeReadiness readiness) {
        return "UP".equals(readiness.llmStatus())
            ? readiness.embeddingReasonCode()
            : readiness.llmReasonCode();
    }

    private String firstFailureMessage(RuntimeReadiness readiness) {
        return "UP".equals(readiness.llmStatus())
            ? readiness.embeddingReasonMessage()
            : readiness.llmReasonMessage();
    }

    private CatalogProbeResult probeModelCatalog(Instant now, ActiveLlmProvider chatProvider) {
        try {
            OpenAiCompatibleLlmClient.ModelListResult modelResult = listModelsForReadiness(chatProvider);
            List<OllamaModelInfo> models = modelResult.models();
            String directProbeModel = resolveDirectProbeModel(models, chatProvider);
            if (!StringUtils.hasText(directProbeModel)) {
                return CatalogProbeResult.down(
                    "llm.model_unavailable",
                    "No LLM model is available from the local provider for readiness probing."
                );
            }
            String configuredModel = configuredChatModel(chatProvider);
            if (StringUtils.hasText(configuredModel)
                && models.stream().map(OllamaModelInfo::name).noneMatch(configuredModel::equals)) {
                return CatalogProbeResult.down(
                    "llm.model_unavailable",
                    "Configured LLM model '" + configuredModel + "' is not available from the active provider."
                );
            }
            lastLlmSuccessfulProbeAt.set(now);
            if (!modelResult.modelsAvailable()) {
                return CatalogProbeResult.degraded(
                    directProbeModel,
                    modelResult.errorCode(),
                    sanitize(modelResult.errorMessage())
                );
            }
            return CatalogProbeResult.up(directProbeModel);
        } catch (CodedException exception) {
            return CatalogProbeResult.down(exception.getCode(), sanitize(exception.getMessage()));
        } catch (RuntimeException exception) {
            return CatalogProbeResult.down("llm.healthcheck_failed", rootMessage(exception));
        }
    }

    private OpenAiCompatibleLlmClient.ModelListResult listModelsForReadiness(ActiveLlmProvider chatProvider) {
        if (chatProvider != null && llmClient instanceof OpenAiCompatibleLlmClient openAiClient) {
            return openAiClient.listModelsForReadiness(chatProvider);
        }
        return new OpenAiCompatibleLlmClient.ModelListResult(llmClient.listModels(), true, null, null);
    }

    private ComponentReadiness probeDirectExecution(
        Instant now,
        CatalogProbeResult catalogProbeResult,
        ActiveLlmProvider chatProvider
    ) {
        if (!catalogProbeResult.readiness().isAvailable()) {
            return ComponentReadiness.down(
                catalogProbeResult.readiness().reasonCode(),
                catalogProbeResult.readiness().reasonMessage()
            );
        }

        try {
            ChatRequest request = new ChatRequest(
                catalogProbeResult.directProbeModel(),
                List.of(new Message("user", "healthcheck"))
            );
            if (chatProvider != null && llmClient instanceof OpenAiCompatibleLlmClient openAiClient) {
                openAiClient.chat(chatProvider, request, com.example.demo.service.cancellation.ChatCancellationToken.none());
            } else {
                llmClient.chat(request);
            }
            lastDirectSuccessfulProbeAt.set(now);
            return ComponentReadiness.up();
        } catch (CodedException exception) {
            return ComponentReadiness.down(exception.getCode(), sanitize(exception.getMessage()));
        } catch (RuntimeException exception) {
            return ComponentReadiness.down("direct.healthcheck_failed", rootMessage(exception));
        }
    }

    private ComponentReadiness probeEmbeddings(Instant now, ActiveLlmProvider embeddingProvider) {
        try {
            float[] embedding = embeddingProvider != null && embeddingClient instanceof OpenAiCompatibleEmbeddingClient openAiClient
                ? openAiClient.embed(embeddingProvider, "healthcheck")
                : embeddingClient.embed("healthcheck");
            if (embedding == null || embedding.length == 0) {
                return ComponentReadiness.down(
                    "embedding.provider_empty_embedding",
                    "Embedding provider returned an empty vector during readiness probing."
                );
            }
            lastEmbeddingSuccessfulProbeAt.set(now);
            return ComponentReadiness.up();
        } catch (CodedException exception) {
            return ComponentReadiness.down(exception.getCode(), sanitize(exception.getMessage()));
        } catch (RuntimeException exception) {
            return ComponentReadiness.down("embedding.healthcheck_failed", rootMessage(exception));
        }
    }

    private String rootMessage(Throwable throwable) {
        return errorSanitizer == null ? null : errorSanitizer.rootMessage(throwable);
    }

    private String toTimestamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String resolveDirectProbeModel(List<OllamaModelInfo> models, ActiveLlmProvider chatProvider) {
        String configuredModel = configuredChatModel(chatProvider);
        if (StringUtils.hasText(configuredModel)) {
            return configuredModel;
        }
        return models.isEmpty() ? null : models.getFirst().name();
    }

    private String configuredChatModel(ActiveLlmProvider chatProvider) {
        if (chatProvider != null) {
            return chatProvider.defaultModel();
        }
        return activeProviderResolver == null
            ? llmProperties.getModel()
            : activeProviderResolver.defaultChatModel();
    }

    private String sanitize(String message) {
        return errorSanitizer == null ? message : errorSanitizer.sanitize(message);
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
        private static RuntimeReadiness unknown(Instant now) {
            String timestamp = now == null ? Instant.now().toString() : now.toString();
            return new RuntimeReadiness(
                "UNKNOWN",
                "DOWN",
                "runtime.readiness_not_probed",
                "Runtime readiness probe has not completed yet.",
                "UNKNOWN",
                "runtime.readiness_not_probed",
                "Runtime readiness probe has not completed yet.",
                "UNKNOWN",
                "runtime.readiness_not_probed",
                "Runtime readiness probe has not completed yet.",
                timestamp,
                null,
                null,
                null
            );
        }
    }

    private record ComponentReadiness(
        String status,
        String reasonCode,
        String reasonMessage
    ) {
        private static ComponentReadiness up() {
            return new ComponentReadiness("UP", null, null);
        }

        private static ComponentReadiness degraded(String reasonCode, String reasonMessage) {
            return new ComponentReadiness("DEGRADED", reasonCode, reasonMessage);
        }

        private static ComponentReadiness down(String reasonCode, String reasonMessage) {
            return new ComponentReadiness("DOWN", reasonCode, reasonMessage);
        }

        private boolean isUp() {
            return "UP".equals(status);
        }

        private boolean isAvailable() {
            return "UP".equals(status) || "DEGRADED".equals(status);
        }
    }

    private record CatalogProbeResult(
        ComponentReadiness readiness,
        String directProbeModel
    ) {
        private static CatalogProbeResult up(String directProbeModel) {
            return new CatalogProbeResult(ComponentReadiness.up(), directProbeModel);
        }

        private static CatalogProbeResult degraded(
            String directProbeModel,
            String reasonCode,
            String reasonMessage
        ) {
            return new CatalogProbeResult(ComponentReadiness.degraded(reasonCode, reasonMessage), directProbeModel);
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

    private record ProviderSnapshot(
        ActiveLlmProvider chatProvider,
        ActiveLlmProvider embeddingProvider
    ) {
    }
}
