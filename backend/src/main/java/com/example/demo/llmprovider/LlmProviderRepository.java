package com.example.demo.llmprovider;

import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LlmProviderRepository {

    List<LlmProviderConfig> findAll();

    Optional<LlmProviderConfig> findById(UUID id);

    Optional<LlmProviderConfig> findActiveChat();

    Optional<LlmProviderConfig> findActiveEmbedding();

    LlmProviderConfig insert(LlmProviderConfig provider);

    LlmProviderConfig update(LlmProviderConfig provider);

    default void deleteById(UUID id) {
        deleteInactiveById(id);
    }

    default boolean deleteInactiveById(UUID id) {
        deleteById(id);
        return true;
    }

    LlmProviderConfig activate(UUID id, LlmProviderPurpose purpose, Instant updatedAt);

    void activateFallback(LlmProviderPurpose purpose, Instant updatedAt);

    LlmProviderConfig updateProbeResult(
        UUID id,
        LlmProviderStatus status,
        Instant checkedAt,
        Instant lastSuccessfulProbeAt,
        String errorCode,
        String errorMessage
    );
}
