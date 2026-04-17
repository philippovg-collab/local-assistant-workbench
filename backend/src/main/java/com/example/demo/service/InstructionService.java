package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.infrastructure.instruction.InstructionRepository;
import com.example.demo.infrastructure.instruction.StoredInstructionRecord;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionDetail;
import com.example.demo.model.InstructionSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InstructionService {

    private final InstructionRepository repository;

    public InstructionService(InstructionRepository repository) {
        this.repository = repository;
    }

    public List<InstructionSummary> listInstructions() {
        return repository.findAll().stream()
            .sorted(Comparator.comparing(StoredInstructionRecord::createdAt).reversed())
            .map(this::toSummary)
            .toList();
    }

    public InstructionDetail getInstruction(String id) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        return repository.findById(instructionId)
            .map(this::toDetail)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + instructionId + "' does not exist"
            ));
    }

    public List<InstructionDetail> findInstructionsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<String> normalizedIds = ids.stream()
            .map(id -> sanitize(id, "id"))
            .toList();

        normalizedIds = normalizedIds.stream().map(this::requireValidInstructionId).toList();
        List<InstructionDetail> resolved = repository.findAllByIds(normalizedIds).stream()
            .map(this::toDetail)
            .toList();

        if (resolved.size() != normalizedIds.size()) {
            Set<String> resolvedIds = resolved.stream().map(InstructionDetail::id).collect(java.util.stream.Collectors.toSet());
            String missingId = normalizedIds.stream()
                .filter(id -> !resolvedIds.contains(id))
                .findFirst()
                .orElse("unknown");
            throw new ApiException(
                HttpStatus.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + missingId + "' does not exist"
            );
        }

        return resolved;
    }

    public InstructionDetail createInstruction(CreateInstructionRequest request) {
        String title = sanitize(request == null ? null : request.title(), "title");
        InstructionCategory category = parseCategory(request == null ? null : request.category());
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
        return toDetail(record);
    }

    public InstructionDetail updateInstruction(String id, CreateInstructionRequest request) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        StoredInstructionRecord existingRecord = repository.findById(instructionId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + instructionId + "' does not exist"
            ));

        String title = sanitize(request == null ? null : request.title(), "title");
        InstructionCategory category = parseCategory(request == null ? null : request.category());
        String content = sanitize(request == null ? null : request.content(), "content");
        Instant now = Instant.now();

        StoredInstructionRecord updatedRecord = new StoredInstructionRecord(
            existingRecord.id(),
            title,
            category,
            content,
            normalize(content),
            existingRecord.createdAt(),
            now
        );
        repository.save(updatedRecord);
        return toDetail(updatedRecord);
    }

    public void deleteInstruction(String id) {
        repository.delete(requireValidInstructionId(sanitize(id, "id")));
    }

    public boolean importLegacyRecord(StoredInstructionRecord record) {
        if (record == null) {
            return false;
        }

        String title = sanitize(record.title(), "title");
        InstructionCategory category = record.category() == null
            ? InstructionCategory.SYSTEM
            : record.category();
        String content = sanitize(record.content(), "content");
        Instant createdAt = record.createdAt() == null ? Instant.now() : record.createdAt();
        Instant updatedAt = record.updatedAt() == null ? createdAt : record.updatedAt();

        repository.save(new StoredInstructionRecord(
            record.id() == null ? UUID.randomUUID().toString() : record.id(),
            title,
            category,
            content,
            normalize(content),
            createdAt,
            updatedAt
        ));
        return true;
    }

    private InstructionSummary toSummary(StoredInstructionRecord record) {
        return new InstructionSummary(
            record.id(),
            record.title(),
            record.category(),
            record.createdAt(),
            record.updatedAt(),
            clip(record.content(), 160)
        );
    }

    private InstructionDetail toDetail(StoredInstructionRecord record) {
        return new InstructionDetail(
            record.id(),
            record.title(),
            record.category(),
            record.content(),
            record.createdAt(),
            record.updatedAt()
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

    private InstructionCategory parseCategory(String rawCategory) {
        String category = sanitize(rawCategory, "category");
        try {
            return InstructionCategory.fromValue(category);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "instruction.invalid_category",
                "Field 'category' must be one of: system, user, context, safety",
                exception
            );
        }
    }

    private String requireValidInstructionId(String id) {
        try {
            UUID.fromString(id);
            return id;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "instruction.invalid_id",
                "Instruction id must be a valid UUID",
                exception
            );
        }
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String clip(String value, int limit) {
        String normalized = normalize(value);
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
