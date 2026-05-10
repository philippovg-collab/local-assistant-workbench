package com.example.demo.service;

import com.example.demo.config.RolloutProperties;
import com.example.demo.model.MaterialEnrichmentState;
import com.example.demo.model.MaterialEnrichmentStatus;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.material.MaterialAutoTaggingStatus;
import com.example.demo.service.material.MaterialAutoTaggingTask;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialAutoTaggingTaskRepository;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MaterialAutoTaggingLifecycleService {

    public static final String RESULT_APPLIED = "APPLIED";
    public static final String RESULT_NO_TAGS = "NO_TAGS";
    public static final String RESULT_UNCHANGED = "UNCHANGED";
    public static final String RESULT_SKIPPED_STALE = "SKIPPED_STALE";
    public static final String RESULT_DISABLED = "DISABLED";
    public static final String FAILURE_PROVIDER = "FAILED_PROVIDER";
    public static final String FAILURE_PARSE = "FAILED_PARSE";

    private final MaterialAutoTaggingTaskRepository taskRepository;
    private final MaterialCatalogRepository catalogRepository;
    private final MaterialAutoTaggingService autoTaggingService;
    private final MaterialSearchSyncLifecycleService materialLifecycleService;
    private final MaterialIndexingService indexingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RolloutProperties rolloutProperties;

    public MaterialAutoTaggingLifecycleService(
        MaterialAutoTaggingTaskRepository taskRepository,
        MaterialCatalogRepository catalogRepository,
        MaterialAutoTaggingService autoTaggingService,
        MaterialSearchSyncLifecycleService materialLifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties
    ) {
        this.taskRepository = taskRepository;
        this.catalogRepository = catalogRepository;
        this.autoTaggingService = autoTaggingService;
        this.materialLifecycleService = materialLifecycleService;
        this.indexingService = indexingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
    }

    public MaterialEnrichmentStatus enqueue(String materialId, String contentHash, Instant now) {
        if (!rolloutProperties.isMetadataV1()
            || taskRepository == null
            || !StringUtils.hasText(materialId)
            || !StringUtils.hasText(contentHash)) {
            return MaterialEnrichmentStatus.notRequested();
        }
        return toStatus(taskRepository.enqueue(materialId, contentHash, now));
    }

    public MaterialEnrichmentStatus statusForMaterial(String materialId) {
        if (taskRepository == null || !StringUtils.hasText(materialId)) {
            return MaterialEnrichmentStatus.notRequested();
        }
        return taskRepository.findLatestByMaterialId(materialId)
            .map(this::toStatus)
            .orElseGet(MaterialEnrichmentStatus::notRequested);
    }

    public Map<String, MaterialEnrichmentStatus> statusesForMaterials(Collection<String> materialIds) {
        if (taskRepository == null || materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        Map<String, MaterialEnrichmentStatus> statuses = new LinkedHashMap<>();
        taskRepository.findLatestByMaterialIds(materialIds).forEach((materialId, task) -> statuses.put(materialId, toStatus(task)));
        return statuses;
    }

    public void processTask(MaterialAutoTaggingTask task) {
        if (task == null) {
            return;
        }
        if (!rolloutProperties.isMetadataV1() || autoTaggingService == null || !autoTaggingService.isEnabled()) {
            taskRepository.markDone(task.id(), RESULT_DISABLED, Instant.now());
            return;
        }

        StoredMaterialRecord record = catalogRepository.findById(task.materialId()).orElse(null);
        if (isStale(task, record)) {
            taskRepository.markDone(task.id(), RESULT_SKIPPED_STALE, Instant.now());
            return;
        }

        MaterialMetadataSnapshot currentMetadata = record.metadata();
        List<String> tags = autoTaggingService.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            currentMetadata.manualTags(),
            MaterialMetadataHints.empty()
        ));
        if (tags.isEmpty()) {
            taskRepository.markDone(task.id(), RESULT_NO_TAGS, Instant.now());
            return;
        }

        StoredMaterialRecord latestRecord = catalogRepository.findById(task.materialId()).orElse(null);
        if (isStale(task, latestRecord)) {
            taskRepository.markDone(task.id(), RESULT_SKIPPED_STALE, Instant.now());
            return;
        }

        MaterialMetadataSnapshot latestMetadata = latestRecord.metadata();
        MaterialMetadataSnapshot enrichedMetadata = latestMetadata.withInferredAutoTags(
            tags,
            MaterialAutoTaggingService.LLM_TAG_CONFIDENCE
        );
        if (latestMetadata.effectiveTags().equals(enrichedMetadata.effectiveTags())
            && latestMetadata.autoTags().equals(enrichedMetadata.autoTags())) {
            taskRepository.markDone(task.id(), RESULT_UNCHANGED, Instant.now());
            return;
        }

        materialLifecycleService.updateMetadataAndMarkIndexingPending(
            latestRecord.id(),
            enrichedMetadata,
            "material.auto_tags_updated",
            "Material auto-tags were enriched after save.",
            Instant.now()
        );
        afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        taskRepository.markDone(task.id(), RESULT_APPLIED, Instant.now());
    }

    private boolean isStale(MaterialAutoTaggingTask task, StoredMaterialRecord record) {
        return record == null
            || record.versionState() != MaterialVersionState.ACTIVE
            || !task.contentHash().equals(record.contentHash());
    }

    private MaterialEnrichmentStatus toStatus(MaterialAutoTaggingTask task) {
        if (task == null) {
            return MaterialEnrichmentStatus.notRequested();
        }
        return new MaterialEnrichmentStatus(
            task.id(),
            toApiState(task.status()),
            task.attemptCount(),
            task.nextRetryAt(),
            task.failureCode(),
            task.failureMessage(),
            task.resultCode(),
            task.updatedAt()
        );
    }

    private MaterialEnrichmentState toApiState(MaterialAutoTaggingStatus status) {
        if (status == null) {
            return MaterialEnrichmentState.NOT_REQUESTED;
        }
        return switch (status) {
            case PENDING -> MaterialEnrichmentState.PENDING;
            case RUNNING -> MaterialEnrichmentState.RUNNING;
            case FAILED -> MaterialEnrichmentState.FAILED;
            case DONE -> MaterialEnrichmentState.DONE;
        };
    }
}
