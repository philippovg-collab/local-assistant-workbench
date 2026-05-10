package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.config.LlmProviderProperties;
import com.example.demo.error.ApplicationException;
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
        llmProperties.setApiKey("llm-key");
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setBaseUrl("http://127.0.0.1:11435");
        embeddingProperties.setApiKey("embedding-key");
        embeddingProperties.setModel("nomic-embed-text");
        embeddingProperties.setExpectedDimension(1024);
        InMemoryRepository repository = new InMemoryRepository();

        ActiveLlmProviderResolver resolver = resolver(repository, llmProperties, embeddingProperties);

        ActiveLlmProvider chatProvider = resolver.resolveChatProvider();
        ActiveLlmProvider embeddingProvider = resolver.resolveEmbeddingProvider();

        assertEquals("default-local", chatProvider.id());
        assertEquals("http://127.0.0.1:11434", chatProvider.baseUrl());
        assertEquals("llm-key", chatProvider.apiKey());
        assertEquals("qwen2.5:7b", chatProvider.defaultModel());
        assertEquals("default-local-embeddings", embeddingProvider.id());
        assertEquals("http://127.0.0.1:11435", embeddingProvider.baseUrl());
        assertEquals("embedding-key", embeddingProvider.apiKey());
        assertEquals("qwen2.5:7b", embeddingProvider.defaultModel());
        assertEquals("nomic-embed-text", embeddingProvider.embeddingModel());
        assertEquals(1024, embeddingProvider.expectedEmbeddingDimension());
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

    @Test
    void resolvesSplitActiveChatAndEmbeddingProvidersIndependently() {
        LlmProviderConfig chatProvider = provider(
            null,
            LlmProviderPurpose.CHAT,
            true,
            false,
            "Chat Provider",
            "http://chat.provider.local",
            "chat-default",
            "chat-side-embedding"
        );
        LlmProviderConfig embeddingProvider = provider(
            null,
            LlmProviderPurpose.EMBEDDING,
            false,
            true,
            "Embedding Provider",
            "http://embedding.provider.local",
            "embedding-side-chat",
            "embedding-default"
        );
        ActiveLlmProviderResolver resolver = resolver(
            new InMemoryRepository(chatProvider, embeddingProvider),
            new LlmProperties(),
            new EmbeddingProperties()
        );

        ActiveLlmProvider resolvedChat = resolver.resolveChatProvider();
        ActiveLlmProvider resolvedEmbedding = resolver.resolveEmbeddingProvider();

        assertEquals(chatProvider.id().toString(), resolvedChat.id());
        assertEquals("http://chat.provider.local", resolvedChat.baseUrl());
        assertEquals("chat-default", resolvedChat.defaultModel());
        assertEquals(embeddingProvider.id().toString(), resolvedEmbedding.id());
        assertEquals("http://embedding.provider.local", resolvedEmbedding.baseUrl());
        assertEquals("embedding-default", resolvedEmbedding.embeddingModel());
    }

    @Test
    void failsClosedWhenEncryptedDatabaseProviderCannotBeDecrypted() {
        LlmProviderConfig provider = provider("v1:nonce:ciphertext");
        InMemoryRepository repository = new InMemoryRepository(provider);
        ActiveLlmProviderResolver resolver = new ActiveLlmProviderResolver(
            repository,
            new LlmProviderCryptoService(new LlmProviderProperties()),
            new LlmProperties(),
            new EmbeddingProperties()
        );

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            resolver::resolveChatProvider
        );

        assertEquals("llm_provider.secret_key_missing", exception.getCode());
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
        return provider(
            apiKeyCiphertext,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            true,
            true,
            "Corp LLM",
            "http://10.10.20.15:8000",
            "corp-model",
            "corp-embedding"
        );
    }

    private LlmProviderConfig provider(
        String apiKeyCiphertext,
        LlmProviderPurpose purpose,
        boolean activeChat,
        boolean activeEmbedding,
        String name,
        String baseUrl,
        String defaultModel,
        String embeddingModel
    ) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            UUID.randomUUID(),
            name,
            LlmProviderType.OPENAI_COMPATIBLE,
            purpose,
            baseUrl,
            apiKeyCiphertext,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            defaultModel,
            embeddingModel,
            0.2,
            600,
            768,
            activeChat,
            activeEmbedding,
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
        private final List<LlmProviderConfig> activeProviders;

        private InMemoryRepository() {
            this.activeProviders = List.of();
        }

        private InMemoryRepository(LlmProviderConfig... activeProviders) {
            this.activeProviders = activeProviders == null ? List.of() : List.of(activeProviders);
        }

        @Override
        public List<LlmProviderConfig> findAll() {
            return activeProviders;
        }

        @Override
        public Optional<LlmProviderConfig> findById(UUID id) {
            return activeProviders.stream().filter(provider -> provider.id().equals(id)).findFirst();
        }

        @Override
        public Optional<LlmProviderConfig> findActiveChat() {
            return activeProviders.stream().filter(LlmProviderConfig::activeChat).findFirst();
        }

        @Override
        public Optional<LlmProviderConfig> findActiveEmbedding() {
            return activeProviders.stream().filter(LlmProviderConfig::activeEmbedding).findFirst();
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
