package com.example.demo.llmprovider;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.LlmProviderActivateRequest;
import com.example.demo.model.LlmProviderConfigResponse;
import com.example.demo.model.LlmProviderInput;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.MaterialIndexingService;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class LlmProviderService {

    private static final String DEFAULT_AUTH_HEADER = "Authorization";
    private static final String DEFAULT_AUTH_SCHEME = "Bearer";
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODELS_PATH = "/v1/models";
    private static final String DEFAULT_EMBEDDINGS_PATH = "/v1/embeddings";
    private static final Pattern HTTP_HEADER_NAME = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern AUTH_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9._~-]*$");

    private final LlmProviderRepository repository;
    private final LlmProviderCryptoService cryptoService;
    private final ActiveLlmProviderResolver activeProviderResolver;
    private final EmbeddingDimensionInspector embeddingDimensionInspector;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final MaterialIndexingService materialIndexingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final LlmProviderErrorSanitizer errorSanitizer;

    @Autowired
    public LlmProviderService(
        LlmProviderRepository repository,
        LlmProviderCryptoService cryptoService,
        ActiveLlmProviderResolver activeProviderResolver,
        EmbeddingDimensionInspector embeddingDimensionInspector,
        MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialIndexingService materialIndexingService,
        AfterCommitExecutor afterCommitExecutor,
        PlatformTransactionManager transactionManager,
        ApplicationEventPublisher eventPublisher,
        LlmProviderErrorSanitizer errorSanitizer
    ) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.activeProviderResolver = activeProviderResolver;
        this.embeddingDimensionInspector = embeddingDimensionInspector;
        this.indexingQueueRepository = indexingQueueRepository;
        this.materialIndexingService = materialIndexingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.eventPublisher = eventPublisher;
        this.errorSanitizer = errorSanitizer;
    }

    public LlmProviderService(
        LlmProviderRepository repository,
        LlmProviderCryptoService cryptoService,
        ActiveLlmProviderResolver activeProviderResolver,
        EmbeddingDimensionInspector embeddingDimensionInspector,
        MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialIndexingService materialIndexingService,
        AfterCommitExecutor afterCommitExecutor,
        PlatformTransactionManager transactionManager,
        ApplicationEventPublisher eventPublisher
    ) {
        this(
            repository,
            cryptoService,
            activeProviderResolver,
            embeddingDimensionInspector,
            indexingQueueRepository,
            materialIndexingService,
            afterCommitExecutor,
            transactionManager,
            eventPublisher,
            new LlmProviderErrorSanitizer()
        );
    }

    public List<LlmProviderConfigResponse> listProviders() {
        return repository.findAll().stream()
            .map(LlmProviderMapper::toResponse)
            .toList();
    }

    public LlmProviderConfig getProvider(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "llm_provider.not_found",
            "LLM provider '" + id + "' does not exist"
        ));
    }

    public LlmProviderConfigResponse createProvider(LlmProviderInput input) {
        return transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            LlmProviderConfig provider = fromInput(input, null, now);
            validateRequestedActivation(input, provider);
            provider = repository.insert(provider);
            if (Boolean.TRUE.equals(input.activeChat())) {
                provider = activateProvider(provider.id(), new LlmProviderActivateRequest(LlmProviderPurpose.CHAT));
            }
            if (Boolean.TRUE.equals(input.activeEmbedding())) {
                provider = activateProvider(provider.id(), new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));
            }
            return LlmProviderMapper.toResponse(repository.findById(provider.id()).orElse(provider));
        });
    }

    public LlmProviderConfigResponse updateProvider(UUID id, LlmProviderInput input) {
        return transactionTemplate.execute(status -> {
            LlmProviderConfig existing = getProvider(id);
            ActiveLlmProvider previousEmbeddingProvider = existing.activeEmbedding()
                ? activeProviderResolver.resolveEmbeddingProvider()
                : null;
            LlmProviderConfig updated = fromInput(input, existing, Instant.now());
            validateActivePurposeCompatibility(updated);
            if (updated.activeEmbedding()) {
                validateEmbeddingActivation(updated);
            }
            updated = repository.update(updated);
            if (updated.activeEmbedding()) {
                ActiveLlmProvider nextEmbeddingProvider = activeProviderResolver.fromStoredEmbeddingProvider(updated);
                reindexIfEmbeddingChanged(previousEmbeddingProvider, nextEmbeddingProvider);
            }
            if (updated.activeChat() || updated.activeEmbedding()) {
                publishActivationChangedAfterCommit();
            }
            if (Boolean.TRUE.equals(input.activeChat())) {
                updated = activateProvider(updated.id(), new LlmProviderActivateRequest(LlmProviderPurpose.CHAT));
            }
            if (Boolean.TRUE.equals(input.activeEmbedding())) {
                updated = activateProvider(updated.id(), new LlmProviderActivateRequest(LlmProviderPurpose.EMBEDDING));
            }
            return LlmProviderMapper.toResponse(repository.findById(updated.id()).orElse(updated));
        });
    }

    public void deleteProvider(UUID id) {
        LlmProviderConfig provider = getProvider(id);
        if (provider.activeChat() || provider.activeEmbedding()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "llm_provider.active_delete_forbidden",
                "Switch the active LLM provider to another provider or env fallback before deleting this provider"
            );
        }
        if (repository.deleteInactiveById(id)) {
            return;
        }
        LlmProviderConfig latest = getProvider(id);
        if (latest.activeChat() || latest.activeEmbedding()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "llm_provider.active_delete_forbidden",
                "Switch the active LLM provider to another provider or env fallback before deleting this provider"
            );
        }
        throw new ApplicationException(
            ErrorType.CONFLICT,
            "llm_provider.delete_conflict",
            "LLM provider could not be deleted because it changed concurrently; retry the request"
        );
    }

    public LlmProviderConfig activateProvider(UUID id, LlmProviderActivateRequest request) {
        return transactionTemplate.execute(status -> {
            LlmProviderPurpose purpose = normalizeActivationPurpose(request == null ? null : request.purpose());
            LlmProviderConfig provider = getProvider(id);
            if (purpose == LlmProviderPurpose.CHAT && !provider.canServeChat()) {
                throw new ApplicationException(
                    ErrorType.INVALID_REQUEST,
                    "llm_provider.purpose_mismatch",
                    "LLM provider purpose does not allow chat activation"
                );
            }
            if (purpose == LlmProviderPurpose.CHAT) {
                validateChatActivation(provider);
            }
            if (purpose == LlmProviderPurpose.EMBEDDING && !provider.canServeEmbedding()) {
                throw new ApplicationException(
                    ErrorType.INVALID_REQUEST,
                    "llm_provider.purpose_mismatch",
                    "LLM provider purpose does not allow embedding activation"
                );
            }

            ActiveLlmProvider previousEmbeddingProvider = purpose == LlmProviderPurpose.EMBEDDING
                ? activeProviderResolver.resolveEmbeddingProvider()
                : null;
            if (purpose == LlmProviderPurpose.EMBEDDING) {
                validateEmbeddingActivation(provider);
            }
            LlmProviderConfig activated = repository.activate(id, purpose, Instant.now());
            if (purpose == LlmProviderPurpose.EMBEDDING) {
                reindexIfEmbeddingChanged(
                    previousEmbeddingProvider,
                    activeProviderResolver.fromStoredEmbeddingProvider(activated)
                );
            }
            publishActivationChangedAfterCommit();
            return activated;
        });
    }

    public void activateFallback(LlmProviderActivateRequest request) {
        transactionTemplate.executeWithoutResult(status -> {
            LlmProviderPurpose purpose = normalizeActivationPurpose(request == null ? null : request.purpose());
            ActiveLlmProvider previousEmbeddingProvider = purpose == LlmProviderPurpose.EMBEDDING
                ? activeProviderResolver.resolveEmbeddingProvider()
                : null;
            repository.activateFallback(purpose, Instant.now());
            if (purpose == LlmProviderPurpose.EMBEDDING) {
                reindexIfEmbeddingChanged(previousEmbeddingProvider, activeProviderResolver.resolveEmbeddingProvider());
            }
            publishActivationChangedAfterCommit();
        });
    }

    public LlmProviderConfig persistProbeResult(
        UUID id,
        LlmProviderStatus status,
        Instant checkedAt,
        String errorCode,
        String errorMessage
    ) {
        return repository.updateProbeResult(
            id,
            status,
            checkedAt,
            status == LlmProviderStatus.UP || status == LlmProviderStatus.DEGRADED ? checkedAt : null,
            errorCode,
            sanitizeError(errorMessage)
        );
    }

    private LlmProviderConfig fromInput(LlmProviderInput input, LlmProviderConfig existing, Instant now) {
        if (input == null) {
            throw invalid("llm_provider.invalid_payload", "LLM provider payload is required");
        }
        LlmProviderType providerType = input.providerType() == null
            ? LlmProviderType.OPENAI_COMPATIBLE
            : input.providerType();
        if (providerType != LlmProviderType.OPENAI_COMPATIBLE) {
            throw invalid("llm_provider.unsupported_type", "Only OPENAI_COMPATIBLE LLM providers are supported");
        }
        LlmProviderPurpose purpose = input.purpose() == null ? LlmProviderPurpose.CHAT : input.purpose();
        String apiKeyCiphertext = resolveApiKeyCiphertext(input, existing);
        Instant createdAt = existing == null ? now : existing.createdAt();
        boolean activeChat = existing != null && existing.activeChat();
        boolean activeEmbedding = existing != null && existing.activeEmbedding();
        if (existing == null) {
            activeChat = false;
            activeEmbedding = false;
        }
        return new LlmProviderConfig(
            existing == null ? UUID.randomUUID() : existing.id(),
            required(input.name(), "name"),
            providerType,
            purpose,
            normalizeBaseUrl(required(input.baseUrl(), "baseUrl")),
            apiKeyCiphertext,
            authHeaderNameOrDefault(input.authHeaderName()),
            authSchemeOrDefault(input.authScheme()),
            pathOrDefault(input.chatCompletionsPath(), DEFAULT_CHAT_PATH),
            pathOrDefault(input.modelsPath(), DEFAULT_MODELS_PATH),
            pathOrDefault(input.embeddingsPath(), DEFAULT_EMBEDDINGS_PATH),
            nullable(input.defaultModel()),
            nullable(input.embeddingModel()),
            input.temperature() == null ? 0.2d : input.temperature(),
            input.timeoutSeconds() == null ? 600 : input.timeoutSeconds(),
            input.expectedEmbeddingDimension(),
            activeChat,
            activeEmbedding,
            existing == null ? LlmProviderStatus.UNKNOWN : existing.status(),
            existing == null ? null : existing.lastProbeAt(),
            existing == null ? null : existing.lastSuccessfulProbeAt(),
            existing == null ? null : existing.lastErrorCode(),
            existing == null ? null : existing.lastErrorMessage(),
            createdAt,
            now
        );
    }

    private String resolveApiKeyCiphertext(LlmProviderInput input, LlmProviderConfig existing) {
        if (Boolean.TRUE.equals(input.clearApiKey()) && StringUtils.hasText(input.apiKey())) {
            throw invalid(
                "llm_provider.api_key_clear_conflict",
                "Do not provide apiKey when clearApiKey is true"
            );
        }
        if (Boolean.TRUE.equals(input.clearApiKey())) {
            return null;
        }
        if (StringUtils.hasText(input.apiKey())) {
            return cryptoService.encrypt(input.apiKey());
        }
        return existing == null ? null : existing.apiKeyCiphertext();
    }

    private void validateActivePurposeCompatibility(LlmProviderConfig provider) {
        if (provider.activeChat() && !provider.canServeChat()) {
            throw invalid("llm_provider.purpose_mismatch", "Active chat provider must support CHAT purpose");
        }
        if (provider.activeChat()) {
            validateChatActivation(provider);
        }
        if (provider.activeEmbedding() && !provider.canServeEmbedding()) {
            throw invalid("llm_provider.purpose_mismatch", "Active embedding provider must support EMBEDDING purpose");
        }
    }

    private void validateRequestedActivation(LlmProviderInput input, LlmProviderConfig provider) {
        if (Boolean.TRUE.equals(input.activeChat()) && !provider.canServeChat()) {
            throw invalid("llm_provider.purpose_mismatch", "LLM provider purpose does not allow chat activation");
        }
        if (Boolean.TRUE.equals(input.activeChat())) {
            validateChatActivation(provider);
        }
        if (Boolean.TRUE.equals(input.activeEmbedding())) {
            if (!provider.canServeEmbedding()) {
                throw invalid(
                    "llm_provider.purpose_mismatch",
                    "LLM provider purpose does not allow embedding activation"
                );
            }
            validateEmbeddingActivation(provider);
        }
    }

    private void validateChatActivation(LlmProviderConfig provider) {
        if (!StringUtils.hasText(provider.defaultModel())) {
            throw invalid("llm_provider.default_model_required", "Chat provider activation requires defaultModel");
        }
    }

    private void validateEmbeddingActivation(LlmProviderConfig provider) {
        if (!StringUtils.hasText(provider.embeddingModel())) {
            throw invalid("llm_provider.embedding_model_required", "Embedding provider activation requires embeddingModel");
        }
        if (provider.expectedEmbeddingDimension() == null || provider.expectedEmbeddingDimension() < 1) {
            throw invalid(
                "llm_provider.embedding_dimension_required",
                "Embedding provider activation requires expectedEmbeddingDimension"
            );
        }
        Integer databaseDimension = embeddingDimensionInspector.materialChunkEmbeddingDimension();
        if (databaseDimension != null && !databaseDimension.equals(provider.expectedEmbeddingDimension())) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "llm_provider.embedding_dimension_mismatch",
                "Embedding provider dimension " + provider.expectedEmbeddingDimension()
                    + " does not match database material_chunks.embedding vector(" + databaseDimension + ")"
            );
        }
    }

    private void reindexIfEmbeddingChanged(ActiveLlmProvider previous, ActiveLlmProvider next) {
        if (previous == null || next == null || previous.embeddingFingerprint().equals(next.embeddingFingerprint())) {
            return;
        }
        indexingQueueRepository.markActiveMaterialsIndexingPending(
            "embedding.provider_changed",
            "Active embedding provider changed; material embeddings must be regenerated.",
            Instant.now()
        );
        afterCommitExecutor.afterCommit(materialIndexingService::requestProcessing);
    }

    private void publishActivationChangedAfterCommit() {
        afterCommitExecutor.afterCommit(() -> eventPublisher.publishEvent(new LlmProviderActivationChangedEvent()));
    }

    private LlmProviderPurpose normalizeActivationPurpose(LlmProviderPurpose purpose) {
        if (purpose == null) {
            throw invalid("llm_provider.activation_purpose_required", "Activation purpose is required");
        }
        if (purpose == LlmProviderPurpose.CHAT_AND_EMBEDDING) {
            throw invalid("llm_provider.activation_purpose_invalid", "Activate CHAT and EMBEDDING separately");
        }
        return purpose;
    }

    private String normalizeBaseUrl(String value) {
        try {
            String normalized = value.trim();
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw invalid("llm_provider.invalid_base_url", "baseUrl must start with http:// or https://");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw invalid("llm_provider.invalid_base_url", "baseUrl must include a host");
            }
            if (StringUtils.hasText(uri.getRawUserInfo())) {
                throw invalid("llm_provider.invalid_base_url", "baseUrl must not include credentials");
            }
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (IllegalArgumentException exception) {
            throw invalid("llm_provider.invalid_base_url", "baseUrl must be a valid http:// or https:// URL");
        }
    }

    private String pathOrDefault(String value, String fallback) {
        String path = defaulted(value, fallback);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            throw invalid("llm_provider.invalid_path", "Provider paths must start with '/'");
        }
        if (!path.startsWith("/")) {
            throw invalid("llm_provider.invalid_path", "Provider paths must start with '/'");
        }
        if (containsWhitespace(path)) {
            throw invalid("llm_provider.invalid_path", "Provider paths must not contain whitespace");
        }
        return path;
    }

    private String authHeaderNameOrDefault(String value) {
        String headerName = defaulted(value, DEFAULT_AUTH_HEADER);
        if (!HTTP_HEADER_NAME.matcher(headerName).matches()) {
            throw invalid("llm_provider.invalid_auth_header", "Auth header name must be a valid HTTP header token");
        }
        return headerName;
    }

    private String authSchemeOrDefault(String value) {
        String scheme = defaulted(value, DEFAULT_AUTH_SCHEME);
        if (!AUTH_SCHEME.matcher(scheme).matches()) {
            throw invalid("llm_provider.invalid_auth_scheme", "Auth scheme must be a valid HTTP auth scheme token");
        }
        return scheme;
    }

    private boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalid("llm_provider.validation_failed", "Field '" + fieldName + "' is required");
        }
        return value.trim();
    }

    private String defaulted(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String nullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String sanitizeError(String message) {
        return errorSanitizer == null ? message : errorSanitizer.sanitize(message);
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(ErrorType.INVALID_REQUEST, code, message);
    }
}
