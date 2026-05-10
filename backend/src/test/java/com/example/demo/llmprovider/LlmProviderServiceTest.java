package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.LlmProviderActivateRequest;
import com.example.demo.model.LlmProviderInput;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.MaterialIndexingService;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LlmProviderServiceTest {

    @Test
    void rejectsAbsoluteProviderPaths() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));
        LlmProviderInput input = input("/v1/chat/completions", "https://evil.internal/v1/models", "/v1/embeddings", "Authorization", "Bearer");

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createProvider(input));

        assertEquals("llm_provider.invalid_path", exception.getCode());
    }

    @Test
    void rejectsInvalidAuthHeaderNames() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));
        LlmProviderInput input = input("/v1/chat/completions", "/v1/models", "/v1/embeddings", "Authorization\nX-Evil", "Bearer");

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createProvider(input));

        assertEquals("llm_provider.invalid_auth_header", exception.getCode());
    }

    @Test
    void rejectsProviderPathsWithWhitespace() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));
        LlmProviderInput input = input("/v1/chat/completions", "/v1/models\nX-Evil: yes", "/v1/embeddings", "Authorization", "Bearer");

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createProvider(input));

        assertEquals("llm_provider.invalid_path", exception.getCode());
    }

    @Test
    void rejectsBaseUrlWithoutHostOrWithCredentials() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));

        ApplicationException missingHost = assertThrows(
            ApplicationException.class,
            () -> service.createProvider(inputWithBaseUrl("http:///v1"))
        );
        ApplicationException credentials = assertThrows(
            ApplicationException.class,
            () -> service.createProvider(inputWithBaseUrl("https://user:password@llm.internal"))
        );

        assertEquals("llm_provider.invalid_base_url", missingHost.getCode());
        assertEquals("llm_provider.invalid_base_url", credentials.getCode());
    }

    @Test
    void createProviderWithoutApiKeyStoresNullSecretAndReturnsHasApiKeyFalse() {
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.empty());
        LlmProviderService service = service(repository);

        var response = service.createProvider(input(
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "Authorization",
            "Bearer"
        ));

        ArgumentCaptor<LlmProviderConfig> providerCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);
        verify(repository).insert(providerCaptor.capture());
        LlmProviderConfig stored = providerCaptor.getValue();
        assertNull(stored.apiKeyCiphertext());
        assertEquals("Authorization", stored.authHeaderName());
        assertEquals("Bearer", stored.authScheme());
        assertEquals(0.2d, stored.temperature());
        assertEquals(600, stored.timeoutSeconds());
        assertEquals(false, response.hasApiKey());
    }

    @Test
    void createProviderEncryptsApiKeyAndDoesNotReturnSecret() {
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        LlmProviderCryptoService cryptoService = mock(LlmProviderCryptoService.class);
        when(cryptoService.encrypt("secret-api-key")).thenReturn("ciphertext-v1");
        when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findById(any())).thenReturn(Optional.empty());
        LlmProviderService service = service(repository, cryptoService);

        var response = service.createProvider(inputWithApiKey("secret-api-key"));

        ArgumentCaptor<LlmProviderConfig> providerCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);
        verify(repository).insert(providerCaptor.capture());
        assertEquals("ciphertext-v1", providerCaptor.getValue().apiKeyCiphertext());
        assertEquals(true, response.hasApiKey());
    }

    @Test
    void updateWithoutApiKeyPreservesExistingSecretAndProbeState() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        LlmProviderConfig existing = providerWithSecretAndProbe(id, "old-ciphertext", now);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(existing), Optional.empty());
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmProviderService service = service(repository);

        service.updateProvider(id, input(
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "Authorization",
            "Bearer"
        ));

        ArgumentCaptor<LlmProviderConfig> providerCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);
        verify(repository).update(providerCaptor.capture());
        LlmProviderConfig updated = providerCaptor.getValue();
        assertEquals("old-ciphertext", updated.apiKeyCiphertext());
        assertEquals(LlmProviderStatus.DEGRADED, updated.status());
        assertEquals(now.minusSeconds(60), updated.lastProbeAt());
        assertEquals(now.minusSeconds(120), updated.lastSuccessfulProbeAt());
        assertEquals("models_unsupported", updated.lastErrorCode());
        assertEquals("Models endpoint unsupported", updated.lastErrorMessage());
    }

    @Test
    void updateWithClearApiKeyRemovesExistingSecret() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig existing = providerWithSecretAndProbe(id, "old-ciphertext", Instant.parse("2026-05-10T10:00:00Z"));
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(existing), Optional.empty());
        when(repository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LlmProviderService service = service(repository);

        service.updateProvider(id, inputWithClearApiKey());

        ArgumentCaptor<LlmProviderConfig> providerCaptor = ArgumentCaptor.forClass(LlmProviderConfig.class);
        verify(repository).update(providerCaptor.capture());
        assertNull(providerCaptor.getValue().apiKeyCiphertext());
    }

    @Test
    void deleteActiveProviderReturnsConflictBeforeStorageDelete() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig active = provider(id, false, null, true, false);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(active));
        LlmProviderService service = service(repository);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.deleteProvider(id));

        assertEquals("llm_provider.active_delete_forbidden", exception.getCode());
    }

    @Test
    void embeddingActivationMarksActiveMaterialsForReindexAndRequestsProcessing() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig provider = provider(id, false);
        LlmProviderConfig activated = provider(id, true);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(provider));
        when(repository.activate(eq(id), eq(LlmProviderPurpose.EMBEDDING), any())).thenReturn(activated);
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.resolveEmbeddingProvider()).thenReturn(activeProvider("old-embedding"));
        when(resolver.fromStoredEmbeddingProvider(activated)).thenReturn(activeProvider("new-embedding"));
        EmbeddingDimensionInspector dimensionInspector = mock(EmbeddingDimensionInspector.class);
        when(dimensionInspector.materialChunkEmbeddingDimension()).thenReturn(3);
        MaterialIndexingQueueRepository queueRepository = mock(MaterialIndexingQueueRepository.class);
        MaterialIndexingService indexingService = mock(MaterialIndexingService.class);

        LlmProviderService service = new LlmProviderService(
            repository,
            mock(LlmProviderCryptoService.class),
            resolver,
            dimensionInspector,
            queueRepository,
            indexingService,
            new AfterCommitExecutor(),
            immediateTransactionManager(),
            event -> {
            },
            new LlmProviderErrorSanitizer()
        );

        service.activateProvider(id, new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));

        verify(queueRepository).markActiveMaterialsIndexingPending(
            eq("embedding.provider_changed"),
            eq("Active embedding provider changed; material embeddings must be regenerated."),
            any()
        );
        verify(indexingService).requestProcessing();
    }

    @Test
    void deleteReturnsConflictWhenProviderBecomesActiveConcurrently() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig inactive = provider(id, false);
        LlmProviderConfig active = provider(id, false, null, true, false);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(inactive), Optional.of(active));
        when(repository.deleteInactiveById(id)).thenReturn(false);
        LlmProviderService service = service(repository);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.deleteProvider(id));

        assertEquals("llm_provider.active_delete_forbidden", exception.getCode());
    }

    @Test
    void chatActivationRequiresDefaultModel() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig provider = chatProvider(id, null, false);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(provider));
        LlmProviderService service = service(repository);

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.activateProvider(id, new LlmProviderActivateRequest(LlmProviderPurpose.CHAT))
        );

        assertEquals("llm_provider.default_model_required", exception.getCode());
    }

    @Test
    void embeddingActivationRequiresModelAndDimension() {
        UUID missingModelId = UUID.randomUUID();
        LlmProviderRepository missingModelRepository = mock(LlmProviderRepository.class);
        when(missingModelRepository.findById(missingModelId)).thenReturn(Optional.of(embeddingProvider(
            missingModelId,
            null,
            3
        )));
        LlmProviderService missingModelService = service(missingModelRepository);

        ApplicationException missingModel = assertThrows(
            ApplicationException.class,
            () -> missingModelService.activateProvider(missingModelId, new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING))
        );

        UUID missingDimensionId = UUID.randomUUID();
        LlmProviderRepository missingDimensionRepository = mock(LlmProviderRepository.class);
        when(missingDimensionRepository.findById(missingDimensionId)).thenReturn(Optional.of(embeddingProvider(
            missingDimensionId,
            "corp-embedding",
            null
        )));
        LlmProviderService missingDimensionService = service(missingDimensionRepository);

        ApplicationException missingDimension = assertThrows(
            ApplicationException.class,
            () -> missingDimensionService.activateProvider(missingDimensionId, new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING))
        );

        assertEquals("llm_provider.embedding_model_required", missingModel.getCode());
        assertEquals("llm_provider.embedding_dimension_required", missingDimension.getCode());
    }

    @Test
    void embeddingActivationDimensionMismatchReturnsConflict() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig provider = embeddingProvider(id, "corp-embedding", 3);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(provider));
        EmbeddingDimensionInspector dimensionInspector = mock(EmbeddingDimensionInspector.class);
        when(dimensionInspector.materialChunkEmbeddingDimension()).thenReturn(4);
        LlmProviderService service = service(
            repository,
            mock(LlmProviderCryptoService.class),
            mock(ActiveLlmProviderResolver.class),
            dimensionInspector,
            mock(MaterialIndexingQueueRepository.class),
            mock(MaterialIndexingService.class)
        );

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.activateProvider(id, new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING))
        );

        assertEquals("llm_provider.embedding_dimension_mismatch", exception.getCode());
    }

    @Test
    void chatActivationDoesNotReindexMaterials() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig provider = chatProvider(id, "corp-model", false);
        LlmProviderConfig activated = chatProvider(id, "corp-model", true);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(provider));
        when(repository.activate(eq(id), eq(LlmProviderPurpose.CHAT), any())).thenReturn(activated);
        MaterialIndexingQueueRepository queueRepository = mock(MaterialIndexingQueueRepository.class);
        MaterialIndexingService indexingService = mock(MaterialIndexingService.class);
        LlmProviderService service = service(
            repository,
            mock(LlmProviderCryptoService.class),
            mock(ActiveLlmProviderResolver.class),
            mock(EmbeddingDimensionInspector.class),
            queueRepository,
            indexingService
        );

        service.activateProvider(id, new LlmProviderActivateRequest(LlmProviderPurpose.CHAT));

        verify(queueRepository, never()).markActiveMaterialsIndexingPending(any(), any(), any());
        verify(indexingService, never()).requestProcessing();
    }

    @Test
    void embeddingActivationDoesNotReindexWhenFingerprintIsUnchanged() {
        UUID id = UUID.randomUUID();
        LlmProviderConfig provider = provider(id, false);
        LlmProviderConfig activated = provider(id, true);
        LlmProviderRepository repository = mock(LlmProviderRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(provider));
        when(repository.activate(eq(id), eq(LlmProviderPurpose.EMBEDDING), any())).thenReturn(activated);
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.resolveEmbeddingProvider()).thenReturn(activeProvider("same-embedding"));
        when(resolver.fromStoredEmbeddingProvider(activated)).thenReturn(activeProvider("same-embedding"));
        EmbeddingDimensionInspector dimensionInspector = mock(EmbeddingDimensionInspector.class);
        when(dimensionInspector.materialChunkEmbeddingDimension()).thenReturn(3);
        MaterialIndexingQueueRepository queueRepository = mock(MaterialIndexingQueueRepository.class);
        MaterialIndexingService indexingService = mock(MaterialIndexingService.class);
        LlmProviderService service = service(
            repository,
            mock(LlmProviderCryptoService.class),
            resolver,
            dimensionInspector,
            queueRepository,
            indexingService
        );

        service.activateProvider(id, new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));

        verify(queueRepository, never()).markActiveMaterialsIndexingPending(any(), any(), any());
        verify(indexingService, never()).requestProcessing();
    }

    @Test
    void embeddingFallbackReindexesOnlyWhenFingerprintChanges() {
        LlmProviderRepository changedRepository = mock(LlmProviderRepository.class);
        ActiveLlmProviderResolver changedResolver = mock(ActiveLlmProviderResolver.class);
        when(changedResolver.resolveEmbeddingProvider()).thenReturn(activeProvider("old-embedding"), activeProvider("new-embedding"));
        MaterialIndexingQueueRepository changedQueue = mock(MaterialIndexingQueueRepository.class);
        MaterialIndexingService changedIndexing = mock(MaterialIndexingService.class);
        LlmProviderService changedService = service(
            changedRepository,
            mock(LlmProviderCryptoService.class),
            changedResolver,
            mock(EmbeddingDimensionInspector.class),
            changedQueue,
            changedIndexing
        );

        changedService.activateFallback(new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));

        verify(changedRepository).activateFallback(eq(LlmProviderPurpose.EMBEDDING), any());
        verify(changedQueue).markActiveMaterialsIndexingPending(
            eq("embedding.provider_changed"),
            eq("Active embedding provider changed; material embeddings must be regenerated."),
            any()
        );
        verify(changedIndexing).requestProcessing();

        LlmProviderRepository unchangedRepository = mock(LlmProviderRepository.class);
        ActiveLlmProviderResolver unchangedResolver = mock(ActiveLlmProviderResolver.class);
        when(unchangedResolver.resolveEmbeddingProvider()).thenReturn(activeProvider("same-embedding"), activeProvider("same-embedding"));
        MaterialIndexingQueueRepository unchangedQueue = mock(MaterialIndexingQueueRepository.class);
        MaterialIndexingService unchangedIndexing = mock(MaterialIndexingService.class);
        LlmProviderService unchangedService = service(
            unchangedRepository,
            mock(LlmProviderCryptoService.class),
            unchangedResolver,
            mock(EmbeddingDimensionInspector.class),
            unchangedQueue,
            unchangedIndexing
        );

        unchangedService.activateFallback(new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));

        verify(unchangedRepository).activateFallback(eq(LlmProviderPurpose.EMBEDDING), any());
        verify(unchangedQueue, never()).markActiveMaterialsIndexingPending(any(), any(), any());
        verify(unchangedIndexing, never()).requestProcessing();
    }

    @Test
    void activationRejectsCombinedPurposeRequest() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.activateProvider(UUID.randomUUID(), new LlmProviderActivateRequest(LlmProviderPurpose.CHAT_AND_EMBEDDING))
        );

        assertEquals("llm_provider.activation_purpose_invalid", exception.getCode());
    }

    @Test
    void rejectsApiKeyClearAndReplacementInSameRequest() {
        LlmProviderService service = service(mock(LlmProviderRepository.class));
        LlmProviderInput input = new LlmProviderInput(
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "http://10.10.20.15:8000",
            "new-secret",
            true,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            false,
            false
        );

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createProvider(input));

        assertEquals("llm_provider.api_key_clear_conflict", exception.getCode());
    }

    private LlmProviderService service(LlmProviderRepository repository) {
        return service(repository, mock(LlmProviderCryptoService.class));
    }

    private LlmProviderService service(LlmProviderRepository repository, LlmProviderCryptoService cryptoService) {
        return service(
            repository,
            cryptoService,
            mock(ActiveLlmProviderResolver.class),
            mock(EmbeddingDimensionInspector.class),
            mock(MaterialIndexingQueueRepository.class),
            mock(MaterialIndexingService.class)
        );
    }

    private LlmProviderService service(
        LlmProviderRepository repository,
        LlmProviderCryptoService cryptoService,
        ActiveLlmProviderResolver resolver,
        EmbeddingDimensionInspector dimensionInspector,
        MaterialIndexingQueueRepository queueRepository,
        MaterialIndexingService indexingService
    ) {
        return new LlmProviderService(
            repository,
            cryptoService,
            resolver,
            dimensionInspector,
            queueRepository,
            indexingService,
            new AfterCommitExecutor(),
            immediateTransactionManager(),
            event -> {
            },
            new LlmProviderErrorSanitizer()
        );
    }

    private LlmProviderInput inputWithBaseUrl(String baseUrl) {
        return new LlmProviderInput(
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            baseUrl,
            null,
            false,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            false,
            false
        );
    }

    private LlmProviderInput inputWithApiKey(String apiKey) {
        return new LlmProviderInput(
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "http://10.10.20.15:8000",
            apiKey,
            false,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            false,
            false
        );
    }

    private LlmProviderInput inputWithClearApiKey() {
        return new LlmProviderInput(
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "http://10.10.20.15:8000",
            null,
            true,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            false,
            false
        );
    }

    private LlmProviderInput input(
        String chatPath,
        String modelsPath,
        String embeddingsPath,
        String authHeaderName,
        String authScheme
    ) {
        return new LlmProviderInput(
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT_AND_EMBEDDING,
            "http://10.10.20.15:8000",
            null,
            false,
            authHeaderName,
            authScheme,
            chatPath,
            modelsPath,
            embeddingsPath,
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            false,
            false
        );
    }

    private LlmProviderConfig provider(UUID id, boolean activeEmbedding) {
        return provider(id, activeEmbedding, activeEmbedding ? "new-embedding" : "old-embedding", false, activeEmbedding);
    }

    private LlmProviderConfig embeddingProvider(UUID id, String embeddingModel, Integer expectedDimension) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            id,
            "Corp Embeddings",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.EMBEDDING,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            null,
            embeddingModel,
            0.2,
            600,
            expectedDimension,
            false,
            false,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private LlmProviderConfig chatProvider(UUID id, String defaultModel, boolean activeChat) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            id,
            "Corp Chat",
            LlmProviderType.OPENAI_COMPATIBLE,
            LlmProviderPurpose.CHAT,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            defaultModel,
            null,
            0.2,
            600,
            null,
            activeChat,
            false,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private LlmProviderConfig provider(
        UUID id,
        boolean activeEmbedding,
        String embeddingModel,
        boolean activeChat,
        boolean activeEmbeddingFlag
    ) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            id,
            "Corp Embeddings",
            LlmProviderType.OPENAI_COMPATIBLE,
            activeChat ? LlmProviderPurpose.CHAT : LlmProviderPurpose.EMBEDDING,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            null,
            embeddingModel,
            0.2,
            600,
            3,
            activeChat,
            activeEmbeddingFlag,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private LlmProviderConfig providerWithSecretAndProbe(UUID id, String apiKeyCiphertext, Instant now) {
        return new LlmProviderConfig(
            id,
            "Corp Provider",
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
            3,
            false,
            false,
            LlmProviderStatus.DEGRADED,
            now.minusSeconds(60),
            now.minusSeconds(120),
            "models_unsupported",
            "Models endpoint unsupported",
            now.minusSeconds(600),
            now.minusSeconds(60)
        );
    }

    private ActiveLlmProvider activeProvider(String embeddingModel) {
        return new ActiveLlmProvider(
            UUID.randomUUID().toString(),
            "Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            null,
            embeddingModel,
            0.2,
            0.9d,
            600,
            3,
            LlmProviderStatus.UNKNOWN,
            false
        );
    }

    private PlatformTransactionManager immediateTransactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
