package com.example.demo.llmprovider;

import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.llm.ProviderHttpStatus;
import com.example.demo.model.LlmProviderModelInfo;
import com.example.demo.model.LlmProviderProbeResult;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LlmProviderProbeService {

    private final LlmProviderService providerService;
    private final ActiveLlmProviderResolver activeProviderResolver;
    private final OllamaApiTransport transport;
    private final LlmProviderErrorSanitizer errorSanitizer;

    @Autowired
    public LlmProviderProbeService(
        LlmProviderService providerService,
        ActiveLlmProviderResolver activeProviderResolver,
        OllamaApiTransport transport,
        LlmProviderErrorSanitizer errorSanitizer
    ) {
        this.providerService = providerService;
        this.activeProviderResolver = activeProviderResolver;
        this.transport = transport;
        this.errorSanitizer = errorSanitizer;
    }

    public LlmProviderProbeService(
        LlmProviderService providerService,
        ActiveLlmProviderResolver activeProviderResolver,
        OllamaApiTransport transport
    ) {
        this(providerService, activeProviderResolver, transport, new LlmProviderErrorSanitizer());
    }

    public List<LlmProviderModelInfo> listModels(UUID providerId) {
        LlmProviderConfig provider = providerService.getProvider(providerId);
        ActiveLlmProvider runtimeProvider = activeProviderResolver.fromStoredChatProvider(provider);
        List<LlmProviderModelInfo> models = listModelsStrict(runtimeProvider);
        if (models.isEmpty() && StringUtils.hasText(provider.defaultModel())) {
            return List.of(new LlmProviderModelInfo(provider.defaultModel()));
        }
        return models;
    }

    public LlmProviderProbeResult probe(UUID providerId) {
        LlmProviderConfig provider = providerService.getProvider(providerId);
        ActiveLlmProvider runtimeProvider = activeProviderResolver.fromStoredChatProvider(provider);
        Instant checkedAt = Instant.now();
        Instant startedAt = checkedAt;
        boolean modelsAvailable = false;
        boolean chatAvailable = false;
        boolean embeddingAvailable = false;
        String errorCode = null;
        String errorMessage = null;
        List<LlmProviderModelInfo> models = List.of();

        try {
            ModelProbeOutcome modelProbe = probeModels(runtimeProvider);
            models = modelProbe.models();
            modelsAvailable = modelProbe.modelsAvailable();
            errorCode = modelProbe.errorCode();
            errorMessage = modelProbe.errorMessage();

            if (provider.canServeChat()) {
                String model = resolveProbeChatModel(provider, models);
                if (!StringUtils.hasText(model)) {
                    throw new ProviderException(
                        ErrorType.PROVIDER_BAD_RESPONSE,
                        "llm_provider.model_unavailable",
                        "No chat model is available for provider probing"
                    );
                }
                probeChat(runtimeProvider, model);
                chatAvailable = true;
            }

            if (provider.canServeEmbedding()) {
                probeEmbedding(runtimeProvider, provider.expectedEmbeddingDimension());
                embeddingAvailable = true;
            }

            LlmProviderStatus status = modelsAvailable ? LlmProviderStatus.UP : LlmProviderStatus.DEGRADED;
            LlmProviderProbeResult result = new LlmProviderProbeResult(
                provider.id().toString(),
                status,
                checkedAt.toString(),
                Duration.between(startedAt, Instant.now()).toMillis(),
                modelsAvailable,
                chatAvailable,
                embeddingAvailable,
                status == LlmProviderStatus.DEGRADED ? errorCode : null,
                status == LlmProviderStatus.DEGRADED ? errorMessage : null
            );
            providerService.persistProbeResult(
                provider.id(),
                status,
                checkedAt,
                result.errorCode(),
                result.errorMessage()
            );
            return result;
        } catch (CodedException exception) {
            return persistDown(provider, checkedAt, startedAt, modelsAvailable, chatAvailable, embeddingAvailable, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return persistDown(provider, checkedAt, startedAt, modelsAvailable, chatAvailable, embeddingAvailable, "llm_provider.probe_failed", rootMessage(exception));
        }
    }

    private ModelProbeOutcome probeModels(ActiveLlmProvider provider) {
        try {
            return ModelProbeOutcome.available(fetchModels(provider));
        } catch (CodedException exception) {
            String code = isUnsupportedModelsEndpoint(exception)
                ? "llm_provider.models_unsupported"
                : exception.getCode();
            String message = isUnsupportedModelsEndpoint(exception)
                ? "Provider models endpoint is unavailable or unsupported"
                : sanitize(exception.getMessage());
            return ModelProbeOutcome.unavailable(code, message);
        }
    }

    private List<LlmProviderModelInfo> listModelsStrict(ActiveLlmProvider provider) {
        try {
            return fetchModels(provider);
        } catch (ProviderException exception) {
            if (isUnsupportedModelsEndpoint(exception)) {
                return List.of();
            }
            throw exception;
        }
    }

    private List<LlmProviderModelInfo> fetchModels(ActiveLlmProvider provider) {
        OpenAiModelsResponse payload = transport.get(
            provider.baseUrl(),
            provider.modelsPath(),
            timeout(provider),
            authorizationHeaders(provider),
            OpenAiModelsResponse.class,
            "llm.provider_unavailable",
            "Unable to reach the configured LLM provider",
            "llm.provider_bad_response",
            "LLM provider returned an invalid status while listing models",
            "llm.provider_parse_failed",
            "Unable to parse the model list returned by the LLM provider",
            "llm.provider_interrupted",
            "Model list request was interrupted",
            "llm.invalid_configuration",
            "Invalid LLM configuration"
        );
        if (payload == null || payload.data() == null) {
            return List.of();
        }
        return payload.data().stream()
            .map(model -> new LlmProviderModelInfo(model.name()))
            .filter(model -> StringUtils.hasText(model.name()))
            .toList();
    }

    private boolean isUnsupportedModelsEndpoint(CodedException exception) {
        return ProviderHttpStatus.hasHttpStatus(exception, "llm.provider_bad_response", 404, 405);
    }

    private void probeChat(ActiveLlmProvider provider, String model) {
        transport.postJson(
            provider.baseUrl(),
            provider.chatCompletionsPath(),
            timeout(provider),
            authorizationHeaders(provider),
            new OpenAiChatCompletionRequest(
                model,
                List.of(new OpenAiMessage("user", "healthcheck")),
                provider.temperature(),
                false
            ),
            OpenAiChatCompletionResponse.class,
            ChatCancellationToken.none(),
            "llm.provider_unavailable",
            "Unable to reach the configured LLM provider",
            "llm.provider_bad_response",
            "LLM provider returned an invalid status",
            "llm.provider_parse_failed",
            "Unable to parse the LLM provider response",
            "llm.provider_interrupted",
            "LLM request was interrupted",
            "llm.invalid_configuration",
            "Invalid LLM configuration"
        );
    }

    private void probeEmbedding(ActiveLlmProvider provider, Integer expectedDimension) {
        if (!StringUtils.hasText(provider.embeddingModel())) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.model_unavailable",
                "No embedding model is configured for provider probing"
            );
        }
        EmbedResponse payload = transport.postJson(
            provider.baseUrl(),
            provider.embeddingsPath(),
            timeout(provider),
            authorizationHeaders(provider),
            Map.of(
                "model", provider.embeddingModel(),
                "input", "healthcheck"
            ),
            EmbedResponse.class,
            "embedding.provider_unavailable",
            "Unable to reach the configured embedding provider",
            "embedding.provider_bad_response",
            "Embedding provider returned an invalid status",
            "embedding.provider_parse_failed",
            "Unable to parse the embedding provider response",
            "embedding.provider_interrupted",
            "Embedding request was interrupted",
            "embedding.invalid_configuration",
            "Invalid embedding configuration"
        );
        if (payload == null || payload.data() == null || payload.data().isEmpty()) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned no vectors"
            );
        }
        List<Double> values = payload.data().getFirst().embedding();
        if (values == null || values.isEmpty()) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned an empty vector"
            );
        }
        if (expectedDimension != null && values.size() != expectedDimension) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_dimension_mismatch",
                "Embedding provider returned " + values.size() + " dimensions, expected " + expectedDimension
            );
        }
    }

    private LlmProviderProbeResult persistDown(
        LlmProviderConfig provider,
        Instant checkedAt,
        Instant startedAt,
        boolean modelsAvailable,
        boolean chatAvailable,
        boolean embeddingAvailable,
        String errorCode,
        String errorMessage
    ) {
        String safeMessage = sanitize(errorMessage);
        providerService.persistProbeResult(provider.id(), LlmProviderStatus.DOWN, checkedAt, errorCode, safeMessage);
        return new LlmProviderProbeResult(
            provider.id().toString(),
            LlmProviderStatus.DOWN,
            checkedAt.toString(),
            Duration.between(startedAt, Instant.now()).toMillis(),
            modelsAvailable,
            chatAvailable,
            embeddingAvailable,
            errorCode,
            safeMessage
        );
    }

    private String resolveProbeChatModel(LlmProviderConfig provider, List<LlmProviderModelInfo> models) {
        if (StringUtils.hasText(provider.defaultModel())) {
            return provider.defaultModel().trim();
        }
        return models == null || models.isEmpty() ? null : models.getFirst().name();
    }

    private Map<String, String> authorizationHeaders(ActiveLlmProvider provider) {
        if (!StringUtils.hasText(provider.apiKey())) {
            return Map.of();
        }
        String scheme = StringUtils.hasText(provider.authScheme()) ? provider.authScheme().trim() : "";
        String value = StringUtils.hasText(scheme)
            ? scheme + " " + provider.apiKey().trim()
            : provider.apiKey().trim();
        return Map.of(provider.authHeaderName(), value);
    }

    private Duration timeout(ActiveLlmProvider provider) {
        return Duration.ofSeconds(Math.max(1, provider.timeoutSeconds()));
    }

    private String rootMessage(Throwable throwable) {
        return errorSanitizer.rootMessage(throwable);
    }

    private String sanitize(String message) {
        return errorSanitizer.sanitize(message);
    }

    private record OpenAiModelsResponse(List<OpenAiModel> data) {
    }

    private record ModelProbeOutcome(
        List<LlmProviderModelInfo> models,
        boolean modelsAvailable,
        String errorCode,
        String errorMessage
    ) {
        private static ModelProbeOutcome available(List<LlmProviderModelInfo> models) {
            return new ModelProbeOutcome(models == null ? List.of() : models, true, null, null);
        }

        private static ModelProbeOutcome unavailable(String errorCode, String errorMessage) {
            return new ModelProbeOutcome(List.of(), false, errorCode, errorMessage);
        }
    }

    private record OpenAiModel(
        String id,
        @JsonProperty("name") String nameAlias
    ) {
        private String name() {
            return StringUtils.hasText(id) ? id : nameAlias;
        }
    }

    private record OpenAiChatCompletionRequest(
        String model,
        List<OpenAiMessage> messages,
        double temperature,
        boolean stream
    ) {
    }

    private record OpenAiMessage(
        String role,
        String content
    ) {
    }

    private record OpenAiChatCompletionResponse(
        String id,
        String model,
        List<OpenAiChoice> choices
    ) {
    }

    private record OpenAiChoice(OpenAiMessage message) {
    }

    private record EmbedResponse(
        String model,
        List<EmbedData> data
    ) {
    }

    private record EmbedData(
        List<Double> embedding
    ) {
    }
}
