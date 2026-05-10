package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcLlmProviderRepositoryIT extends PostgresIntegrationTestSupport {

    private JdbcTemplate jdbcTemplate;
    private JdbcLlmProviderRepository repository;

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        jdbcTemplate = database.jdbcTemplate();
        repository = new JdbcLlmProviderRepository(jdbcTemplate, database.transactionManager());
    }

    @Test
    void insertUpdateListAndDeleteInactiveProvider() {
        Instant createdAt = Instant.parse("2026-05-10T10:00:00Z");
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        LlmProviderConfig first = repository.insert(provider(
            firstId,
            "First Provider",
            "https://first.internal",
            null,
            false,
            false,
            createdAt
        ));
        repository.insert(provider(
            secondId,
            "Second Provider",
            "https://second.internal",
            "v1:cipher",
            false,
            false,
            createdAt.plusSeconds(5)
        ));

        LlmProviderConfig updated = repository.update(new LlmProviderConfig(
            first.id(),
            "First Provider Updated",
            first.providerType(),
            first.purpose(),
            first.baseUrl(),
            "v1:updated-cipher",
            first.authHeaderName(),
            first.authScheme(),
            first.chatCompletionsPath(),
            first.modelsPath(),
            first.embeddingsPath(),
            "updated-chat",
            first.embeddingModel(),
            0.4,
            120,
            first.expectedEmbeddingDimension(),
            false,
            false,
            LlmProviderStatus.UP,
            createdAt.plusSeconds(10),
            createdAt.plusSeconds(10),
            null,
            null,
            first.createdAt(),
            createdAt.plusSeconds(30)
        ));

        List<LlmProviderConfig> providers = repository.findAll();

        assertEquals("First Provider Updated", updated.name());
        assertEquals("v1:updated-cipher", updated.apiKeyCiphertext());
        assertEquals(LlmProviderStatus.UP, updated.status());
        assertEquals(createdAt.plusSeconds(10), updated.lastProbeAt());
        assertEquals(firstId, providers.getFirst().id());
        assertEquals(secondId, providers.get(1).id());
        assertTrue(repository.deleteInactiveById(firstId));
        assertFalse(repository.findById(firstId).isPresent());
    }

    @Test
    void activeUniqueConflictIsReturnedAsActivationConflict() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        repository.insert(provider(UUID.randomUUID(), "Active Chat", "https://chat-a.internal", null, true, false, now));
        LlmProviderConfig inactive = repository.insert(provider(
            UUID.randomUUID(),
            "Inactive Chat",
            "https://chat-b.internal",
            null,
            false,
            false,
            now.plusSeconds(1)
        ));

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> repository.update(new LlmProviderConfig(
                inactive.id(),
                inactive.name(),
                inactive.providerType(),
                inactive.purpose(),
                inactive.baseUrl(),
                inactive.apiKeyCiphertext(),
                inactive.authHeaderName(),
                inactive.authScheme(),
                inactive.chatCompletionsPath(),
                inactive.modelsPath(),
                inactive.embeddingsPath(),
                inactive.defaultModel(),
                inactive.embeddingModel(),
                inactive.temperature(),
                inactive.timeoutSeconds(),
                inactive.expectedEmbeddingDimension(),
                true,
                false,
                inactive.status(),
                inactive.lastProbeAt(),
                inactive.lastSuccessfulProbeAt(),
                inactive.lastErrorCode(),
                inactive.lastErrorMessage(),
                inactive.createdAt(),
                now.plusSeconds(2)
            ))
        );

        assertEquals("llm_provider.activation_conflict", exception.getCode());
    }

    @Test
    void activateAndFallbackSwitchOnlyRequestedPurpose() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        UUID oldChatId = UUID.randomUUID();
        UUID oldEmbeddingId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();
        repository.insert(provider(oldChatId, "Old Chat", "https://old-chat.internal", null, true, false, now));
        repository.insert(provider(oldEmbeddingId, "Old Embedding", "https://old-embedding.internal", null, false, true, now));
        repository.insert(provider(nextId, "Next Provider", "https://next.internal", null, false, false, now));

        LlmProviderConfig activeChat = repository.activate(
            nextId,
            LlmProviderPurpose.CHAT,
            now.plusSeconds(10)
        );

        assertTrue(activeChat.activeChat());
        assertFalse(activeChat.activeEmbedding());
        assertEquals(nextId, repository.findActiveChat().orElseThrow().id());
        assertEquals(oldEmbeddingId, repository.findActiveEmbedding().orElseThrow().id());
        assertFalse(repository.findById(oldChatId).orElseThrow().activeChat());

        repository.activateFallback(LlmProviderPurpose.CHAT, now.plusSeconds(20));

        assertTrue(repository.findActiveChat().isEmpty());
        assertEquals(oldEmbeddingId, repository.findActiveEmbedding().orElseThrow().id());

        repository.activateFallback(LlmProviderPurpose.EMBEDDING, now.plusSeconds(30));

        assertTrue(repository.findActiveEmbedding().isEmpty());
    }

    @Test
    void updateProbeResultPersistsStatusAndPreservesLastSuccessOnFailure() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        UUID id = UUID.randomUUID();
        repository.insert(provider(id, "Probe Target", "https://probe.internal", null, false, false, now));
        Instant successfulProbeAt = now.plusSeconds(60);

        LlmProviderConfig up = repository.updateProbeResult(
            id,
            LlmProviderStatus.UP,
            successfulProbeAt,
            successfulProbeAt,
            null,
            null
        );
        LlmProviderConfig down = repository.updateProbeResult(
            id,
            LlmProviderStatus.DOWN,
            now.plusSeconds(120),
            null,
            "llm.provider_unavailable",
            "Unable to reach provider"
        );

        assertEquals(LlmProviderStatus.UP, up.status());
        assertEquals(successfulProbeAt, up.lastSuccessfulProbeAt());
        assertEquals(LlmProviderStatus.DOWN, down.status());
        assertEquals(now.plusSeconds(120), down.lastProbeAt());
        assertEquals(successfulProbeAt, down.lastSuccessfulProbeAt());
        assertEquals("llm.provider_unavailable", down.lastErrorCode());
        assertEquals("Unable to reach provider", down.lastErrorMessage());
    }

    @Test
    void updatePreservesExistingCiphertextWhenCarriedAndClearsExplicitNullCiphertext() {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        UUID id = UUID.randomUUID();
        LlmProviderConfig original = repository.insert(provider(
            id,
            "Secret Provider",
            "https://secret.internal",
            "v1:original-cipher",
            false,
            false,
            now
        ));

        LlmProviderConfig preserved = repository.update(withCiphertext(
            original,
            "Renamed Secret Provider",
            "v1:original-cipher",
            now.plusSeconds(30)
        ));
        LlmProviderConfig cleared = repository.update(withCiphertext(
            preserved,
            "Cleared Secret Provider",
            null,
            now.plusSeconds(60)
        ));

        assertEquals("Renamed Secret Provider", preserved.name());
        assertEquals("v1:original-cipher", preserved.apiKeyCiphertext());
        assertEquals("Cleared Secret Provider", cleared.name());
        assertEquals(null, cleared.apiKeyCiphertext());
    }

    @Test
    void deleteInactiveByIdDoesNotDeleteActiveProvider() {
        UUID id = UUID.randomUUID();
        repository.insert(provider(
            id,
            "Active Chat",
            "https://chat.internal",
            null,
            true,
            false,
            Instant.parse("2026-05-10T10:00:00Z")
        ));

        assertFalse(repository.deleteInactiveById(id));
        assertTrue(repository.findById(id).isPresent());
    }

    @Test
    void migrationRejectsUnsafeProviderShape() {
        assertThrows(
            DataIntegrityViolationException.class,
            () -> rawInsert("Bad Base", "http:///v1", "Bearer", "/v1/models")
        );
        assertThrows(
            DataIntegrityViolationException.class,
            () -> rawInsert("Bad Credentials", "https://user:password@llm.internal", "Bearer", "/v1/models")
        );
        assertThrows(
            DataIntegrityViolationException.class,
            () -> rawInsert("Bad Path", "https://llm.internal", "Bearer", "/v1/models\nX-Evil")
        );
        assertThrows(
            DataIntegrityViolationException.class,
            () -> rawInsert("Bad Scheme", "https://llm.internal", "Bearer Token", "/v1/models")
        );
    }

    private void rawInsert(String name, String baseUrl, String authScheme, String modelsPath) {
        jdbcTemplate.update(
            """
                INSERT INTO llm_provider_configs (
                    id,
                    name,
                    provider_type,
                    purpose,
                    base_url,
                    auth_scheme,
                    models_path
                ) VALUES (?, ?, 'OPENAI_COMPATIBLE', 'CHAT', ?, ?, ?)
                """,
            UUID.randomUUID(),
            name,
            baseUrl,
            authScheme,
            modelsPath
        );
    }

    private LlmProviderConfig provider(
        UUID id,
        String name,
        String baseUrl,
        String apiKeyCiphertext,
        boolean activeChat,
        boolean activeEmbedding,
        Instant now
    ) {
        return new LlmProviderConfig(
            id,
            name,
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            baseUrl,
            apiKeyCiphertext,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-chat",
            "corp-embedding",
            0.2,
            600,
            1024,
            activeChat,
            activeEmbedding,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private LlmProviderConfig withCiphertext(
        LlmProviderConfig provider,
        String name,
        String apiKeyCiphertext,
        Instant updatedAt
    ) {
        return new LlmProviderConfig(
            provider.id(),
            name,
            provider.providerType(),
            provider.purpose(),
            provider.baseUrl(),
            apiKeyCiphertext,
            provider.authHeaderName(),
            provider.authScheme(),
            provider.chatCompletionsPath(),
            provider.modelsPath(),
            provider.embeddingsPath(),
            provider.defaultModel(),
            provider.embeddingModel(),
            provider.temperature(),
            provider.timeoutSeconds(),
            provider.expectedEmbeddingDimension(),
            provider.activeChat(),
            provider.activeEmbedding(),
            provider.status(),
            provider.lastProbeAt(),
            provider.lastSuccessfulProbeAt(),
            provider.lastErrorCode(),
            provider.lastErrorMessage(),
            provider.createdAt(),
            updatedAt
        );
    }
}
