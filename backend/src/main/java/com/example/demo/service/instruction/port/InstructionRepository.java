package com.example.demo.service.instruction.port;

import java.util.List;
import java.util.Optional;

public interface InstructionRepository {

    List<StoredInstructionRecord> findAll();

    Optional<StoredInstructionRecord> findById(String id);

    List<StoredInstructionRecord> findAllByIds(List<String> ids);

    List<StoredInstructionRecord> findByScope(InstructionScopeQuery scope);

    List<StoredInstructionRevisionRecord> findRevisions(String instructionId);

    Optional<StoredInstructionRevisionRecord> findRevision(String instructionId, int revision);

    void save(StoredInstructionRecord record);

    void appendRevision(StoredInstructionRevisionRecord record);

    void delete(String id);
}
