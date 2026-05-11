package com.example.demo.service.eval.port;

import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import java.util.List;
import java.util.Optional;

public interface EvalRunRepository {

    List<EvalRun> findRuns();

    Optional<EvalRun> findRun(String id);

    default Optional<EvalRunItem> findItem(String id) {
        return Optional.empty();
    }

    EvalRun saveRun(EvalRun run);

    EvalRunItem saveItem(EvalRunItem item);

    List<EvalRunItem> findItemsByRunId(String runId);

    default List<EvalRunItem> findOpenE2EItems() {
        return List.of();
    }

    default List<EvalRunItem> findOpenE2EItemsByRunId(String runId) {
        return List.of();
    }

    default EvalRunItemArtifact saveArtifact(EvalRunItemArtifact artifact) {
        return artifact;
    }

    default List<EvalRunItemArtifact> findArtifactsByItemId(String itemId) {
        return List.of();
    }

    default Optional<EvalRunItemArtifact> findArtifact(String itemId, EvalRunItemArtifactType artifactType) {
        return Optional.empty();
    }

    boolean isStorageReady();
}
