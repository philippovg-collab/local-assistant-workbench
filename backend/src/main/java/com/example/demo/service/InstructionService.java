package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.validation.InputLimits;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.InstructionTraceEntry;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionDetail;
import com.example.demo.model.InstructionRevisionDiff;
import com.example.demo.model.InstructionRevisionDetail;
import com.example.demo.model.InstructionScopeLevel;
import com.example.demo.model.InstructionSummary;
import com.example.demo.model.RevisionDiffEntry;
import com.example.demo.service.instruction.port.InstructionRepository;
import com.example.demo.service.instruction.port.InstructionScopeQuery;
import com.example.demo.service.instruction.port.StoredInstructionRecord;
import com.example.demo.service.instruction.port.StoredInstructionRevisionRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InstructionService {

    public static final String DEFAULT_WORKSPACE_TARGET = "default-workspace";

    private final InstructionRepository repository;

    public InstructionService(InstructionRepository repository) {
        this.repository = repository;
    }

    public List<InstructionSummary> listInstructions() {
        return repository.findAll().stream()
            .sorted(Comparator.comparing(StoredInstructionRecord::updatedAt).reversed())
            .map(this::toSummary)
            .toList();
    }

    public InstructionDetail getInstruction(String id) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        return repository.findById(instructionId)
            .map(this::toDetail)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
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
            throw new ApplicationException(
                ErrorType.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + missingId + "' does not exist"
            );
        }

        return resolved;
    }

    public ResolvedInstructionContext resolveRuntimeInstructions(ChatExecutionRequest request) {
        List<InstructionDetail> runtimeInstructions = new ArrayList<>();
        List<InstructionTraceEntry> trace = new ArrayList<>();
        String workspaceTarget = workspaceTargetOf(request);

        List<InstructionDetail> assistantInstructions = repository.findByScope(new InstructionScopeQuery(
                InstructionScopeLevel.ASSISTANT_SYSTEM,
                null,
                true
            )).stream()
            .map(this::toDetail)
            .toList();
        runtimeInstructions.addAll(assistantInstructions);
        trace.addAll(assistantInstructions.stream().map(this::toTraceEntry).toList());

        List<InstructionDetail> workspaceInstructions = repository.findByScope(new InstructionScopeQuery(
                InstructionScopeLevel.WORKSPACE_PROJECT,
                workspaceTarget,
                true
            )).stream()
            .map(this::toDetail)
            .toList();
        runtimeInstructions.addAll(workspaceInstructions);
        trace.addAll(workspaceInstructions.stream().map(this::toTraceEntry).toList());

        List<String> scenarioInstructionIds = mergeInstructionIds(
            request == null ? null : request.instructionIds(),
            request == null ? null : request.scenarioInstructionIds()
        );
        List<InstructionDetail> scenarioInstructions = findInstructionsByIds(scenarioInstructionIds);
        assertInstructionsActive(scenarioInstructions);
        runtimeInstructions.addAll(scenarioInstructions);
        trace.addAll(scenarioInstructions.stream().map(this::toTraceEntry).toList());

        String temporaryInstruction = sanitizeOptional(request == null ? null : request.temporaryInstruction());
        if (temporaryInstruction == null) {
            temporaryInstruction = sanitizeOptional(request == null ? null : request.systemPrompt());
        }
        if (temporaryInstruction != null) {
            trace.add(new InstructionTraceEntry(
                null,
                "Temporary request instruction",
                InstructionCategory.SYSTEM,
                InstructionScopeLevel.REQUEST_TEMPORARY,
                null,
                0,
                true,
                true,
                clip(temporaryInstruction, 240)
            ));
        }

        return new ResolvedInstructionContext(
            List.copyOf(runtimeInstructions),
            temporaryInstruction,
            List.copyOf(trace)
        );
    }

    public InstructionRevisionDiff diffRevisions(String id, int fromRevision, int toRevision) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        InstructionRevisionDetail from = getRevision(instructionId, fromRevision);
        InstructionRevisionDetail to = getRevision(instructionId, toRevision);
        List<RevisionDiffEntry> changes = new ArrayList<>();
        appendDiff(changes, "title", from.title(), to.title());
        appendDiff(changes, "category", from.category().value(), to.category().value());
        appendDiff(changes, "content", from.content(), to.content());
        appendDiff(changes, "scopeLevel", from.scopeLevel().value(), to.scopeLevel().value());
        appendDiff(changes, "scopeTargetId", from.scopeTargetId(), to.scopeTargetId());
        appendDiff(changes, "active", Boolean.toString(from.active()), Boolean.toString(to.active()));
        return new InstructionRevisionDiff(instructionId, fromRevision, toRevision, List.copyOf(changes));
    }

    public InstructionDetail createInstruction(CreateInstructionRequest request) {
        InputLimits.validateInstructionRequest(request);
        String title = sanitize(request == null ? null : request.title(), "title");
        InstructionCategory category = parseCategory(request == null ? null : request.category());
        String content = sanitize(request == null ? null : request.content(), "content");
        InstructionScopeLevel scopeLevel = parseScopeLevel(request == null ? null : request.scopeLevel());
        String scopeTargetId = normalizeScopeTargetId(scopeLevel, request == null ? null : request.scopeTargetId());
        boolean active = request == null || request.active() == null || request.active();
        Instant now = Instant.now();

        StoredInstructionRecord record = new StoredInstructionRecord(
            UUID.randomUUID().toString(),
            title,
            category,
            content,
            normalize(content),
            scopeLevel,
            scopeTargetId,
            1,
            active,
            now,
            now
        );

        repository.save(record);
        repository.appendRevision(toRevisionRecord(record, null));
        return toDetail(record);
    }

    public InstructionDetail updateInstruction(String id, CreateInstructionRequest request) {
        InputLimits.validateInstructionRequest(request);
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        StoredInstructionRecord existingRecord = repository.findById(instructionId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + instructionId + "' does not exist"
            ));

        String title = sanitize(request == null ? null : request.title(), "title");
        InstructionCategory category = parseCategory(request == null ? null : request.category());
        String content = sanitize(request == null ? null : request.content(), "content");
        InstructionScopeLevel scopeLevel = parseScopeLevel(request == null ? null : request.scopeLevel());
        String scopeTargetId = normalizeScopeTargetId(scopeLevel, request == null ? null : request.scopeTargetId());
        boolean active = request == null || request.active() == null || request.active();
        Instant now = Instant.now();

        StoredInstructionRecord updatedRecord = new StoredInstructionRecord(
            existingRecord.id(),
            title,
            category,
            content,
            normalize(content),
            scopeLevel,
            scopeTargetId,
            existingRecord.revision() + 1,
            active,
            existingRecord.createdAt(),
            now
        );
        repository.save(updatedRecord);
        repository.appendRevision(toRevisionRecord(updatedRecord, null));
        return toDetail(updatedRecord);
    }

    public void deleteInstruction(String id) {
        repository.delete(requireValidInstructionId(sanitize(id, "id")));
    }

    public List<InstructionRevisionDetail> listRevisions(String id) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        return repository.findRevisions(instructionId).stream()
            .map(this::toRevisionDetail)
            .toList();
    }

    public InstructionRevisionDetail getRevision(String id, int revision) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        return repository.findRevision(instructionId, revision)
            .map(this::toRevisionDetail)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "instruction.revision_not_found",
                "Instruction revision '" + revision + "' does not exist"
            ));
    }

    public InstructionDetail restoreRevision(String id, int revision) {
        String instructionId = requireValidInstructionId(sanitize(id, "id"));
        StoredInstructionRecord currentRecord = repository.findById(instructionId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "instruction.not_found",
                "Instruction '" + instructionId + "' does not exist"
            ));

        StoredInstructionRevisionRecord revisionRecord = repository.findRevision(instructionId, revision)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "instruction.revision_not_found",
                "Instruction revision '" + revision + "' does not exist"
            ));

        Instant now = Instant.now();
        StoredInstructionRecord restoredRecord = new StoredInstructionRecord(
            currentRecord.id(),
            revisionRecord.title(),
            revisionRecord.category(),
            revisionRecord.content(),
            revisionRecord.normalizedContent(),
            revisionRecord.scopeLevel(),
            revisionRecord.scopeTargetId(),
            currentRecord.revision() + 1,
            revisionRecord.active(),
            currentRecord.createdAt(),
            now
        );
        repository.save(restoredRecord);
        repository.appendRevision(toRevisionRecord(restoredRecord, revision));
        return toDetail(restoredRecord);
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
        InstructionScopeLevel scopeLevel = record.scopeLevel() == null
            ? InstructionScopeLevel.CHAT_SCENARIO
            : record.scopeLevel();
        String scopeTargetId = normalizeScopeTargetId(scopeLevel, record.scopeTargetId());
        Instant createdAt = record.createdAt() == null ? Instant.now() : record.createdAt();
        Instant updatedAt = record.updatedAt() == null ? createdAt : record.updatedAt();
        int revision = record.revision() <= 0 ? 1 : record.revision();
        boolean active = record.active();

        StoredInstructionRecord importedRecord = new StoredInstructionRecord(
            record.id() == null ? UUID.randomUUID().toString() : record.id(),
            title,
            category,
            content,
            normalize(content),
            scopeLevel,
            scopeTargetId,
            revision,
            active,
            createdAt,
            updatedAt
        );
        repository.save(importedRecord);
        repository.appendRevision(toRevisionRecord(importedRecord, null));
        return true;
    }

    private InstructionSummary toSummary(StoredInstructionRecord record) {
        return new InstructionSummary(
            record.id(),
            record.title(),
            record.category(),
            record.scopeLevel(),
            record.scopeTargetId(),
            record.revision(),
            record.active(),
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
            record.scopeLevel(),
            record.scopeTargetId(),
            record.revision(),
            record.active(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private InstructionTraceEntry toTraceEntry(InstructionDetail instruction) {
        return new InstructionTraceEntry(
            instruction.id(),
            instruction.title(),
            instruction.category(),
            instruction.scopeLevel(),
            instruction.scopeTargetId(),
            instruction.revision(),
            instruction.active(),
            false,
            clip(instruction.content(), 240)
        );
    }

    private InstructionRevisionDetail toRevisionDetail(StoredInstructionRevisionRecord record) {
        return new InstructionRevisionDetail(
            record.instructionId(),
            record.revision(),
            record.title(),
            record.category(),
            record.content(),
            record.scopeLevel(),
            record.scopeTargetId(),
            record.active(),
            record.restoredFromRevision(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private StoredInstructionRevisionRecord toRevisionRecord(StoredInstructionRecord record, Integer restoredFromRevision) {
        return new StoredInstructionRevisionRecord(
            record.id(),
            record.revision(),
            record.title(),
            record.category(),
            record.content(),
            record.normalizedContent(),
            record.scopeLevel(),
            record.scopeTargetId(),
            record.active(),
            restoredFromRevision,
            record.createdAt(),
            record.updatedAt()
        );
    }

    private String sanitize(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
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
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "instruction.invalid_category",
                "Field 'category' must be one of: system, user, context, safety",
                exception
            );
        }
    }

    private InstructionScopeLevel parseScopeLevel(InstructionScopeLevel scopeLevel) {
        return scopeLevel == null ? InstructionScopeLevel.CHAT_SCENARIO : scopeLevel;
    }

    private String requireValidInstructionId(String id) {
        try {
            UUID.fromString(id);
            return id;
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "instruction.invalid_id",
                "Instruction id must be a valid UUID",
                exception
            );
        }
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private String sanitizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<String> mergeInstructionIds(List<String> legacyInstructionIds, List<String> scenarioInstructionIds) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (legacyInstructionIds != null) {
            legacyInstructionIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(ordered::add);
        }
        if (scenarioInstructionIds != null) {
            scenarioInstructionIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(ordered::add);
        }
        return List.copyOf(ordered);
    }

    private String normalizeScopeTargetId(InstructionScopeLevel scopeLevel, String rawScopeTargetId) {
        String normalizedTargetId = sanitizeOptional(rawScopeTargetId);
        return switch (scopeLevel) {
            case ASSISTANT_SYSTEM, REQUEST_TEMPORARY -> null;
            case WORKSPACE_PROJECT -> normalizedTargetId == null ? DEFAULT_WORKSPACE_TARGET : normalizedTargetId;
            case CHAT_SCENARIO -> normalizedTargetId;
        };
    }

    private String workspaceTargetOf(ChatExecutionRequest request) {
        if (request == null) {
            return DEFAULT_WORKSPACE_TARGET;
        }
        String workspaceKey = sanitizeOptional(request.instructionWorkspaceKey());
        return workspaceKey == null ? DEFAULT_WORKSPACE_TARGET : workspaceKey;
    }

    private void assertInstructionsActive(List<InstructionDetail> instructions) {
        if (instructions == null) {
            return;
        }

        instructions.stream()
            .filter(instruction -> !instruction.active())
            .findFirst()
            .ifPresent(instruction -> {
                throw new ApplicationException(
                    ErrorType.INVALID_REQUEST,
                    "instruction.inactive",
                    "Instruction '%s' is inactive and cannot be applied to chat execution".formatted(instruction.id())
                );
            });
    }

    private void appendDiff(List<RevisionDiffEntry> changes, String field, String fromValue, String toValue) {
        if (Objects.equals(fromValue, toValue)) {
            return;
        }
        changes.add(new RevisionDiffEntry(field, emptyToNull(fromValue), emptyToNull(toValue)));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String clip(String value, int limit) {
        String normalized = normalize(value);
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    public record ResolvedInstructionContext(
        List<InstructionDetail> instructions,
        String temporaryInstruction,
        List<InstructionTraceEntry> trace
    ) {
    }
}
