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
}
