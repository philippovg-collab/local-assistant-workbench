package com.example.demo.infrastructure.instruction;

import java.util.List;
import java.util.Optional;

public interface InstructionRepository {

    List<StoredInstructionRecord> findAll();

    Optional<StoredInstructionRecord> findById(String id);

    List<StoredInstructionRecord> findAllByIds(List<String> ids);

    void save(StoredInstructionRecord record);

    void delete(String id);
}
