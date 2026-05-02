package com.example.demo.service.knowledge.port;

import com.example.demo.model.SavedKnowledgeFilterKind;
import java.util.List;
import java.util.Optional;

public interface KnowledgePresetRepository {

    List<StoredKnowledgePresetRecord> findAll();

    List<StoredKnowledgePresetRecord> findAllByKind(SavedKnowledgeFilterKind kind);

    Optional<StoredKnowledgePresetRecord> findById(String id);

    void save(StoredKnowledgePresetRecord record);

    List<StoredKnowledgePresetRevisionRecord> findRevisions(String presetId);

    Optional<StoredKnowledgePresetRevisionRecord> findRevision(String presetId, int revision);

    void appendRevision(StoredKnowledgePresetRevisionRecord record);

    void delete(String presetId);
}
