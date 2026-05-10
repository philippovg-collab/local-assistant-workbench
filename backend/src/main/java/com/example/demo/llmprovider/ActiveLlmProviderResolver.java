package com.example.demo.llmprovider;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.model.HealthResponse;
import com.example.demo.model.LlmProviderStatus;
import org.springframework.stereotype.Service;

@Service
public class ActiveLlmProviderResolver {

    private final LlmProviderRepository repository;
    private final LlmProviderCryptoService cryptoService;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;

    public ActiveLlmProviderResolver(
        LlmProviderRepository repository,
        LlmProviderCryptoService cryptoService,
        LlmProperties llmProperties,
        EmbeddingProperties embeddingProperties
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
    }

    public ActiveLlmProvider resolveChatProvider() {
        return repository.findActiveChat()
            .map(this::fromStoredChatProvider)
            .orElseGet(() -> ActiveLlmProvider.fromLlmProperties(llmProperties));
    }

    public ActiveLlmProvider resolveEmbeddingProvider() {
        return repository.findActiveEmbedding()
            .map(this::fromStoredEmbeddingProvider)
            .orElseGet(() -> ActiveLlmProvider.fromEmbeddingProperties(embeddingProperties, llmProperties));
    }

    public String defaultChatModel() {
        return resolveChatProvider().defaultModel();
    }

    public String activeEmbeddingModel() {
        return resolveEmbeddingProvider().embeddingModel();
    }

    public HealthResponse.ActiveProviderSummary activeChatProviderSummary(LlmProviderStatus status) {
        return toSummary(resolveChatProvider(), status);
    }

    public HealthResponse.ActiveProviderSummary activeEmbeddingProviderSummary(LlmProviderStatus status) {
        return toSummary(resolveEmbeddingProvider(), status);
    }

    ActiveLlmProvider fromStoredChatProvider(LlmProviderConfig provider) {
        return fromStoredProvider(provider, provider.defaultModel(), provider.embeddingModel());
    }

    ActiveLlmProvider fromStoredEmbeddingProvider(LlmProviderConfig provider) {
        return fromStoredProvider(provider, provider.defaultModel(), provider.embeddingModel());
    }

    private ActiveLlmProvider fromStoredProvider(
        LlmProviderConfig provider,
        String defaultModel,
        String embeddingModel
    ) {
        return new ActiveLlmProvider(
            provider.id().toString(),
            provider.name(),
            provider.providerType(),
            provider.baseUrl(),
            cryptoService.decrypt(provider.apiKeyCiphertext()),
            provider.authHeaderName(),
            provider.authScheme(),
            provider.chatCompletionsPath(),
            provider.modelsPath(),
            provider.embeddingsPath(),
            defaultModel,
            embeddingModel,
            provider.temperature(),
            llmProperties.getTopP(),
            provider.timeoutSeconds(),
            provider.expectedEmbeddingDimension(),
            provider.status(),
            false
        );
    }

    private HealthResponse.ActiveProviderSummary toSummary(
        ActiveLlmProvider provider,
        LlmProviderStatus status
    ) {
        return new HealthResponse.ActiveProviderSummary(
            provider.id(),
            provider.name(),
            provider.providerType(),
            provider.baseUrl(),
            status == null ? provider.status() : status,
            provider.fallback()
        );
    }
}
