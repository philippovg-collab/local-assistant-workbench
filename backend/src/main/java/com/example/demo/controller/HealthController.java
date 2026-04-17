package com.example.demo.controller;

import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.model.HealthResponse;
import com.example.demo.service.RagStorageHealthService;
import com.example.demo.service.RuntimeReadinessService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OcrProperties ocrProperties;
    private final OcrCapabilityProvider ocrCapabilityProvider;
    private final RagStorageHealthService ragStorageHealthService;
    private final RuntimeReadinessService runtimeReadinessService;
    private final MaterialIndexingQueueRepository indexingQueueRepository;

    public HealthController(
        OcrProperties ocrProperties,
        OcrCapabilityProvider ocrCapabilityProvider,
        RagStorageHealthService ragStorageHealthService,
        RuntimeReadinessService runtimeReadinessService,
        MaterialIndexingQueueRepository indexingQueueRepository
    ) {
        this.ocrProperties = ocrProperties;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
        this.ragStorageHealthService = ragStorageHealthService;
        this.runtimeReadinessService = runtimeReadinessService;
        this.indexingQueueRepository = indexingQueueRepository;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        OcrCapability capability = ocrCapabilityProvider.currentCapability();
        RagStorageHealthService.StorageHealth storageHealth = ragStorageHealthService.currentHealth();
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness = runtimeReadinessService.currentReadiness();
        MaterialIndexingQueueRepository.IndexingQueueSnapshot indexingQueueSnapshot = indexingQueueRepository.getIndexingQueueSnapshot();
        String ocrStatus = !ocrProperties.isEnabled()
            ? "DISABLED"
            : capability.scannedPdfSupport() ? "UP" : "DOWN";
        boolean ragReady = "UP".equals(runtimeReadiness.directStatus())
            && "UP".equals(runtimeReadiness.embeddingStatus())
            && storageHealth.ready();
        String ragStatus = ragReady ? "UP" : "DOWN";
        boolean overallReady = "UP".equals(runtimeReadiness.directStatus()) && ragReady;
        String ragDegradedReasonCode = resolveRagDegradedReasonCode(runtimeReadiness, storageHealth);
        String ragDegradedReasonMessage = resolveRagDegradedReasonMessage(runtimeReadiness, storageHealth);

        return new HealthResponse(
            "spring-backend",
            overallReady ? "UP" : "DEGRADED",
            Instant.now().toString(),
            runtimeReadiness.directStatus(),
            ragStatus,
            runtimeReadiness.llmStatus(),
            runtimeReadiness.llmReasonCode(),
            runtimeReadiness.llmReasonMessage(),
            runtimeReadiness.embeddingStatus(),
            runtimeReadiness.embeddingReasonCode(),
            runtimeReadiness.embeddingReasonMessage(),
            runtimeReadiness.cachedAt(),
            ocrStatus,
            capability.reasonCode(),
            capability.reasonMessage(),
            capability.languages(),
            storageHealth.databaseStatus(),
            storageHealth.databaseReasonMessage(),
            storageHealth.vectorStatus(),
            storageHealth.vectorReasonMessage(),
            runtimeReadiness.llmLastSuccessfulProbeAt(),
            runtimeReadiness.embeddingLastSuccessfulProbeAt(),
            ragDegradedReasonCode,
            ragDegradedReasonMessage,
            indexingQueueSnapshot.pendingCount(),
            indexingQueueSnapshot.inProgressCount(),
            indexingQueueSnapshot.failedCount(),
            indexingQueueSnapshot.nextRetryAt() == null ? null : indexingQueueSnapshot.nextRetryAt().toString()
        );
    }

    private String resolveRagDegradedReasonCode(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth
    ) {
        if ("DOWN".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.llmReasonCode();
        }
        if ("DOWN".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonCode();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return "rag.database_unavailable";
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return "rag.vector_unavailable";
        }
        return null;
    }

    private String resolveRagDegradedReasonMessage(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth
    ) {
        if ("DOWN".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.llmReasonMessage();
        }
        if ("DOWN".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonMessage();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return storageHealth.databaseReasonMessage();
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return storageHealth.vectorReasonMessage();
        }
        return null;
    }
}
