package com.example.demo.llm;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final OllamaApiTransport transport;
    private final Supplier<ActiveLlmProvider> chatProviderSupplier;

    @Autowired
    public OpenAiCompatibleLlmClient(
        OllamaApiTransport transport,
        ActiveLlmProviderResolver activeProviderResolver
    ) {
        this(transport, activeProviderResolver::resolveChatProvider);
    }

    protected OpenAiCompatibleLlmClient(
        OllamaApiTransport transport,
        Supplier<ActiveLlmProvider> chatProviderSupplier
    ) {
        this.transport = transport;
        this.chatProviderSupplier = chatProviderSupplier;
    }

    @Override
    public List<OllamaModelInfo> listModels() {
        return listModels(chatProviderSupplier.get());
    }

    public List<OllamaModelInfo> listModels(ActiveLlmProvider provider) {
        return listModelsWithFallback(provider).models();
    }

    public ModelListResult listModelsForReadiness(ActiveLlmProvider provider) {
        return listModelsWithFallback(provider);
    }

    private ModelListResult listModelsWithFallback(ActiveLlmProvider provider) {
        try {
            OpenAiModelsResponse payload = transport.get(
                provider.baseUrl(),
                provider.modelsPath(),
                Duration.ofSeconds(provider.timeoutSeconds()),
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
            if (payload == null || payload.data() == null || payload.data().isEmpty()) {
                return ModelListResult.available(fallbackConfiguredModel(provider));
            }

            return ModelListResult.available(payload.data().stream()
                .map(model -> new OllamaModelInfo(model.name()))
                .toList());
        } catch (ProviderException exception) {
            if (ProviderHttpStatus.hasHttpStatus(exception, "llm.provider_bad_response", 404, 405)) {
                return ModelListResult.unavailable(
                    fallbackConfiguredModel(provider),
                    "llm_provider.models_unsupported",
                    "Provider models endpoint is unavailable or unsupported"
                );
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "llm.invalid_configuration",
                "Invalid LLM configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        return chat(request, ChatCancellationToken.none());
    }

    @Override
    public ChatResult chat(ChatRequest request, ChatCancellationToken cancellationToken) {
        return chat(chatProviderSupplier.get(), request, cancellationToken);
    }

    public ChatResult chat(ActiveLlmProvider provider, ChatRequest request, ChatCancellationToken cancellationToken) {
        ChatCancellationToken effectiveToken = cancellationToken == null
            ? ChatCancellationToken.none()
            : cancellationToken;
        effectiveToken.throwIfCancellationRequested();
        Instant startedAt = Instant.now();
        OpenAiChatCompletionRequest payload = new OpenAiChatCompletionRequest(
            provider.chatModelOrDefault(request.model()),
            request.messages().stream()
                .map(message -> new OpenAiMessage(message.role(), message.content()))
                .toList(),
            provider.temperature(),
            provider.topP(),
            false
        );

        try {
            OpenAiChatCompletionResponse completion = transport.postJson(
                provider.baseUrl(),
                provider.chatCompletionsPath(),
                chatTimeout(provider, request),
                authorizationHeaders(provider),
                payload,
                OpenAiChatCompletionResponse.class,
                effectiveToken,
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
            effectiveToken.throwIfCancellationRequested();

            if (completion.choices() == null || completion.choices().isEmpty()) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    "llm.provider_empty_choices",
                    "LLM provider returned no choices"
                );
            }

            OpenAiMessage message = completion.choices().getFirst().message();
            String finishReason = completion.choices().getFirst().finishReason();
            OpenAiUsage usage = completion.usage();
            String createdAt = completion.created() == null
                ? Instant.now().toString()
                : Instant.ofEpochSecond(completion.created()).toString();

            return new ChatResult(
                completion.model(),
                message == null ? "" : message.content(),
                createdAt,
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens(),
                usage == null ? null : usage.totalTokens(),
                rawResponseOf(completion),
                finishReason,
                Duration.between(startedAt, Instant.now()).toMillis()
            );
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "llm.invalid_configuration",
                "Invalid LLM configuration: " + exception.getMessage(),
                exception
            );
        }
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

    private List<OllamaModelInfo> fallbackConfiguredModel(ActiveLlmProvider provider) {
        if (!StringUtils.hasText(provider.defaultModel())) {
            return List.of();
        }
        return List.of(new OllamaModelInfo(provider.defaultModel().trim()));
    }

    private Duration chatTimeout(ActiveLlmProvider provider, ChatRequest request) {
        Integer requestTimeoutSeconds = request == null ? null : request.timeoutSeconds();
        int timeoutSeconds = requestTimeoutSeconds != null && requestTimeoutSeconds > 0
            ? requestTimeoutSeconds
            : provider.timeoutSeconds();
        return Duration.ofSeconds(timeoutSeconds);
    }

    private String rawResponseOf(OpenAiChatCompletionResponse completion) {
        try {
            return JSON_MAPPER.writeValueAsString(completion);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private record OpenAiModelsResponse(List<OpenAiModel> data) {
    }

    public record ModelListResult(
        List<OllamaModelInfo> models,
        boolean modelsAvailable,
        String errorCode,
        String errorMessage
    ) {
        private static ModelListResult available(List<OllamaModelInfo> models) {
            return new ModelListResult(models == null ? List.of() : models, true, null, null);
        }

        private static ModelListResult unavailable(
            List<OllamaModelInfo> models,
            String errorCode,
            String errorMessage
        ) {
            return new ModelListResult(models == null ? List.of() : models, false, errorCode, errorMessage);
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
        @JsonProperty("top_p") double topP,
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
        Long created,
        List<OpenAiChoice> choices,
        OpenAiUsage usage
    ) {
    }

    private record OpenAiChoice(
        int index,
        OpenAiMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record OpenAiUsage(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
