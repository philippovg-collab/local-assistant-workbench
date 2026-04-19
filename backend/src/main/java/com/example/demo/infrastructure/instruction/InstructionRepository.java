package com.example.demo.infrastructure.instruction;

import java.util.List;
import java.util.Optional;

public interface InstructionRepository {

    List<StoredInstructionRecord> findAll();

    Optional<StoredInstructionRecord> findById(String id);

    List<StoredInstructionRecord> findAllByIds(List<String> ids);

    List<StoredInstructionRecord> findByScope(StoredInstructionRecordScope scope);

    List<StoredInstructionRevisionRecord> findRevisions(String instructionId);

    Optional<StoredInstructionRevisionRecord> findRevision(String instructionId, int revision);

    void save(StoredInstructionRecord record);

    void appendRevision(StoredInstructionRevisionRecord record);

    void delete(String id);

    record StoredInstructionRecordScope(
        com.example.demo.model.InstructionScopeLevel scopeLevel,
        String scopeTargetId,
        boolean activeOnly
    ) {
    }
}
