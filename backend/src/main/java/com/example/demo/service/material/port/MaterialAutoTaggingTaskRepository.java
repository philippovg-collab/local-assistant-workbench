package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialAutoTaggingLease;
import com.example.demo.service.material.MaterialAutoTaggingTask;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface MaterialAutoTaggingTaskRepository {

    MaterialAutoTaggingTask enqueue(String materialId, String contentHash, Instant now);

    Optional<MaterialAutoTaggingTask> findLatestByMaterialId(String materialId);

    Map<String, MaterialAutoTaggingTask> findLatestByMaterialIds(Collection<String> materialIds);

    Optional<MaterialAutoTaggingLease> claimNext(Instant now);

    void resetExpiredClaims(Instant staleBefore, Instant now);

    void markDone(String taskId, String resultCode, Instant now);

    void markRetry(String taskId, String failureCode, String failureMessage, Instant now, Instant nextRetryAt);

    void markFailed(String taskId, String failureCode, String failureMessage, Instant now);

    boolean hasPending(Instant now);
}
