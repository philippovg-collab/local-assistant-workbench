package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.instruction.FileInstructionRepository;
import com.example.demo.infrastructure.instruction.StoredInstructionRecord;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InstructionService {

    private final FileInstructionRepository repository;

    public InstructionService(FileInstructionRepository repository) {
        this.repository = repository;
    }

    public List<InstructionSummary> listInstructions() {
        return repository.findAll().stream()
            .sorted(Comparator.comparing(StoredInstructionRecord::createdAt).reversed())
            .map(this::toSummary)
            .toList();
    }

    public List<InstructionSummary> findInstructionsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Map<String, InstructionSummary> knownInstructions = repository.findAll().stream()
            .map(this::toSummary)
            .collect(LinkedHashMap::new, (map, instruction) -> map.put(instruction.id(), instruction), Map::putAll);

        return ids.stream()
            .map(id -> {
                InstructionSummary instruction = knownInstructions.get(id);
                if (instruction == null) {
                    throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "instruction.not_found",
                        "Instruction '" + id + "' does not exist"
                    );
                }

                return instruction;
            })
            .toList();
    }

    public InstructionSummary createInstruction(CreateInstructionRequest request) {
        String title = sanitize(request == null ? null : request.title(), "title");
        String category = sanitize(request == null ? null : request.category(), "category");
        String content = sanitize(request == null ? null : request.content(), "content");
        Instant now = Instant.now();

        StoredInstructionRecord record = new StoredInstructionRecord(
            UUID.randomUUID().toString(),
            title,
            category,
            content,
            normalize(content),
            now,
            now
        );

        repository.save(record);
        return toSummary(record);
    }

    public void deleteInstruction(String id) {
        repository.delete(id);
    }

    private InstructionSummary toSummary(StoredInstructionRecord record) {
        return new InstructionSummary(
            record.id(),
            record.title(),
            record.category(),
            record.content(),
            record.createdAt()
        );
    }

    private String sanitize(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "instruction.invalid_" + fieldName,
                "Field '%s' must not be blank".formatted(fieldName)
            );
        }

        return value.trim();
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
