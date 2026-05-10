package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.config.LlmProviderProperties;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveLlmProviderResolverTest {

    @Test
    void fallsBackToEnvWhenNoActiveProviderExists() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setBaseUrl("http://127.0.0.1:11434");
        llmProperties.setModel("qwen2.5:7b");
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setModel("nomic-embed-text");
        InMemoryRepository repository = new InMemoryRepository();

        ActiveLlmProviderResolver resolver = resolver(repository, llmProperties, embeddingProperties);

        assertEquals("default-local", resolver.resolveChatProvider().id());
        assertEquals("qwen2.5:7b", resolver.resolveChatProvider().defaultModel());
        assertEquals("default-local-embeddings", resolver.resolveEmbeddingProvider().id());
        assertEquals("nomic-embed-text", resolver.resolveEmbeddingProvider().embeddingModel());
    }

    @Test
    void resolvesActiveDatabaseProviderAndDecryptsApiKey() {
        LlmProviderProperties cryptoProperties = new LlmProviderProperties();
        cryptoProperties.setSecretKey("secret");
        LlmProviderCryptoService cryptoService = new LlmProviderCryptoService(cryptoProperties);
        LlmProviderConfig provider = provider(cryptoService.encrypt("provider-key"));
        InMemoryRepository repository = new InMemoryRepository(provider);

        ActiveLlmProviderResolver resolver = new ActiveLlmProviderResolver(
            repository,
            cryptoService,
            new LlmProperties(),
            new EmbeddingProperties()
        );

        assertEquals(provider.id().toString(), resolver.resolveChatProvider().id());
        assertEquals("provider-key", resolver.resolveChatProvider().apiKey());
        assertEquals("corp-model", resolver.resolveChatProvider().defaultModel());
    }

    private ActiveLlmProviderResolver resolver(
        InMemoryRepository repository,
        LlmProperties llmProperties,
        EmbeddingProperties embeddingProperties
    ) {
        return new ActiveLlmProviderResolver(
            repository,
            new LlmProviderCryptoService(new LlmProviderProperties()),
            llmProperties,
            embeddingProperties
        );
    }

    private LlmProviderConfig provider(String apiKeyCiphertext) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            UUID.randomUUID(),
            "Corp LLM",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "http://10.10.20.15:8000",
            apiKeyCiphertext,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            768,
            true,
            true,
            LlmProviderStatus.UP,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private static class InMemoryRepository implements LlmProviderRepository {
        private final LlmProviderConfig activeProvider;

        private InMemoryRepository() {
            this(null);
        }

        private InMemoryRepository(LlmProviderConfig activeProvider) {
            this.activeProvider = activeProvider;
        }

        @Override
        public List<LlmProviderConfig> findAll() {
            return activeProvider == null ? List.of() : List.of(activeProvider);
        }

        @Override
        public Optional<LlmProviderConfig> findById(UUID id) {
            return Optional.ofNullable(activeProvider).filter(provider -> provider.id().equals(id));
        }

        @Override
        public Optional<LlmProviderConfig> findActiveChat() {
            return Optional.ofNullable(activeProvider).filter(LlmProviderConfig::activeChat);
        }

        @Override
        public Optional<LlmProviderConfig> findActiveEmbedding() {
            return Optional.ofNullable(activeProvider).filter(LlmProviderConfig::activeEmbedding);
        }

        @Override
        public LlmProviderConfig insert(LlmProviderConfig provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmProviderConfig update(LlmProviderConfig provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmProviderConfig activate(UUID id, LlmProviderPurpose purpose, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activateFallback(LlmProviderPurpose purpose, Instant updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LlmProviderConfig updateProbeResult(
            UUID id,
            LlmProviderStatus status,
            Instant checkedAt,
            Instant lastSuccessfulProbeAt,
            String errorCode,
            String errorMessage
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
