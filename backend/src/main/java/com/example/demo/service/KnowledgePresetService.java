package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.api.InputLimits;
import com.example.demo.infrastructure.knowledge.PostgresKnowledgePresetRepository;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRecord;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRevisionRecord;
import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.KnowledgePresetDetail;
import com.example.demo.model.KnowledgePresetReference;
import com.example.demo.model.KnowledgePresetRevisionDiff;
import com.example.demo.model.KnowledgePresetRevisionDetail;
import com.example.demo.model.KnowledgePresetSummary;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RevisionDiffEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgePresetService {

    private final PostgresKnowledgePresetRepository repository;

    public KnowledgePresetService(PostgresKnowledgePresetRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgePresetSummary> listPresets() {
        return repository.findAll().stream().map(this::toSummary).toList();
    }

    public KnowledgePresetDetail getPreset(String id) {
        String presetId = requireValidId(id);
        return repository.findById(presetId)
            .map(this::toDetail)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge preset '" + presetId + "' does not exist"
            ));
    }

    public KnowledgePresetDetail createPreset(CreateKnowledgePresetRequest request) {
        InputLimits.validateKnowledgePresetRequest(request);
        String name = sanitize(request == null ? null : request.name(), "name");
        String description = sanitizeOptional(request == null ? null : request.description());
        KnowledgeScope scope = request == null || request.scope() == null ? KnowledgeScope.empty() : request.scope();
        boolean active = request == null || request.active() == null || request.active();
        Instant now = Instant.now();

        StoredKnowledgePresetRecord record = new StoredKnowledgePresetRecord(
            UUID.randomUUID().toString(),
            name,
            description,
            scope,
            1,
            active,
            now,
            now
        );
        repository.save(record);
        repository.appendRevision(toRevision(record, null));
        return toDetail(record);
    }

    public KnowledgePresetDetail updatePreset(String id, CreateKnowledgePresetRequest request) {
        InputLimits.validateKnowledgePresetRequest(request);
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord current = repository.findById(presetId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge preset '" + presetId + "' does not exist"
            ));

        Instant now = Instant.now();
        StoredKnowledgePresetRecord updated = new StoredKnowledgePresetRecord(
            current.id(),
            sanitize(request == null ? null : request.name(), "name"),
            sanitizeOptional(request == null ? null : request.description()),
            request == null || request.scope() == null ? KnowledgeScope.empty() : request.scope(),
            current.revision() + 1,
            request == null || request.active() == null || request.active(),
            current.createdAt(),
            now
        );
        repository.save(updated);
        repository.appendRevision(toRevision(updated, null));
        return toDetail(updated);
    }

    public void deletePreset(String id) {
        repository.delete(requireValidId(id));
    }

    public List<KnowledgePresetRevisionDetail> listRevisions(String id) {
        return repository.findRevisions(requireValidId(id)).stream().map(this::toRevisionDetail).toList();
    }

    public KnowledgePresetRevisionDetail getRevision(String id, int revision) {
        return repository.findRevision(requireValidId(id), revision)
            .map(this::toRevisionDetail)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.revision_not_found",
                "Knowledge preset revision '" + revision + "' does not exist"
            ));
    }

    public KnowledgePresetDetail restoreRevision(String id, int revision) {
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord current = repository.findById(presetId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge preset '" + presetId + "' does not exist"
            ));
        StoredKnowledgePresetRevisionRecord sourceRevision = repository.findRevision(presetId, revision)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.revision_not_found",
                "Knowledge preset revision '" + revision + "' does not exist"
            ));

        Instant now = Instant.now();
        StoredKnowledgePresetRecord restored = new StoredKnowledgePresetRecord(
            current.id(),
            sourceRevision.name(),
            sourceRevision.description(),
            sourceRevision.scope(),
            current.revision() + 1,
            sourceRevision.active(),
            current.createdAt(),
            now
        );
        repository.save(restored);
        repository.appendRevision(toRevision(restored, revision));
        return toDetail(restored);
    }

    public KnowledgePresetRevisionDiff diffRevisions(String id, int fromRevision, int toRevision) {
        String presetId = requireValidId(id);
        KnowledgePresetRevisionDetail from = getRevision(presetId, fromRevision);
        KnowledgePresetRevisionDetail to = getRevision(presetId, toRevision);
        List<RevisionDiffEntry> changes = new ArrayList<>();
        appendDiff(changes, "name", from.name(), to.name());
        appendDiff(changes, "description", from.description(), to.description());
        appendDiff(
            changes,
            "documentClasses",
            String.join(", ", from.scope().documentClasses().stream().map(Enum::name).toList()),
            String.join(", ", to.scope().documentClasses().stream().map(Enum::name).toList())
        );
        appendDiff(changes, "tags", String.join(", ", from.scope().tags()), String.join(", ", to.scope().tags()));
        appendDiff(changes, "workspaceKey", from.scope().workspaceKey(), to.scope().workspaceKey());
        appendDiff(
            changes,
            "uploadedTodayOnly",
            Boolean.toString(from.scope().uploadedTodayOnly()),
            Boolean.toString(to.scope().uploadedTodayOnly())
        );
        appendDiff(changes, "active", Boolean.toString(from.active()), Boolean.toString(to.active()));
        return new KnowledgePresetRevisionDiff(presetId, fromRevision, toRevision, List.copyOf(changes));
    }

    public ResolvedKnowledgeScopeContext resolveScope(KnowledgeScope requestScope) {
        KnowledgeScope safeRequestScope = requestScope == null ? KnowledgeScope.empty() : requestScope;
        InputLimits.validateKnowledgeScope(safeRequestScope, "knowledgeScope");
        List<KnowledgePresetReference> references = new ArrayList<>();
        LinkedHashSet<com.example.demo.model.KnowledgeDocumentClass> documentClasses = new LinkedHashSet<>();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String workspaceKey = safeRequestScope.workspaceKey();
        boolean uploadedTodayOnly = safeRequestScope.uploadedTodayOnly();

        for (String presetId : safeRequestScope.presetIds()) {
            StoredKnowledgePresetRecord preset = repository.findById(requireValidId(presetId))
                .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "knowledge_preset.not_found",
                    "Knowledge preset '" + presetId + "' does not exist"
                ));
            if (!preset.active()) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "knowledge_preset.inactive",
                    "Knowledge preset '%s' is inactive and cannot be used in chat execution".formatted(preset.id())
                );
            }
            references.add(new KnowledgePresetReference(preset.id(), preset.name(), preset.revision()));
            documentClasses.addAll(preset.scope().documentClasses());
            tags.addAll(preset.scope().tags());
            if (!StringUtils.hasText(workspaceKey) && StringUtils.hasText(preset.scope().workspaceKey())) {
                workspaceKey = preset.scope().workspaceKey();
            }
            uploadedTodayOnly = uploadedTodayOnly || preset.scope().uploadedTodayOnly();
        }

        documentClasses.addAll(safeRequestScope.documentClasses());
        tags.addAll(safeRequestScope.tags());

        KnowledgeScope effectiveScope = new KnowledgeScope(
            safeRequestScope.presetIds(),
            List.copyOf(documentClasses),
            List.copyOf(tags),
            workspaceKey,
            uploadedTodayOnly
        );
        KnowledgeScopeResolved resolved = new KnowledgeScopeResolved(
            List.copyOf(references),
            effectiveScope.documentClasses(),
            effectiveScope.tags(),
            effectiveScope.workspaceKey(),
            effectiveScope.uploadedTodayOnly()
        );
        return new ResolvedKnowledgeScopeContext(effectiveScope, resolved);
    }

    private KnowledgePresetSummary toSummary(StoredKnowledgePresetRecord record) {
        return new KnowledgePresetSummary(
            record.id(),
            record.name(),
            record.description(),
            record.revision(),
            record.active(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private KnowledgePresetDetail toDetail(StoredKnowledgePresetRecord record) {
        return new KnowledgePresetDetail(
            record.id(),
            record.name(),
            record.description(),
            record.scope(),
            record.revision(),
            record.active(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private KnowledgePresetRevisionDetail toRevisionDetail(StoredKnowledgePresetRevisionRecord record) {
        return new KnowledgePresetRevisionDetail(
            record.presetId(),
            record.revision(),
            record.name(),
            record.description(),
            record.scope(),
            record.active(),
            record.restoredFromRevision(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private StoredKnowledgePresetRevisionRecord toRevision(StoredKnowledgePresetRecord record, Integer restoredFromRevision) {
        return new StoredKnowledgePresetRevisionRecord(
            record.id(),
            record.revision(),
            record.name(),
            record.description(),
            record.scope(),
            record.active(),
            restoredFromRevision,
            record.createdAt(),
            record.updatedAt()
        );
    }

    private String requireValidId(String id) {
        try {
            return UUID.fromString(sanitize(id, "id")).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "knowledge_preset.invalid_id",
                "Knowledge preset id must be a valid UUID",
                exception
            );
        }
    }

    private String sanitize(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "knowledge_preset.invalid_" + field,
                "Field '%s' must not be blank".formatted(field)
            );
        }
        return value.trim();
    }

    private String sanitizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    public record ResolvedKnowledgeScopeContext(
        KnowledgeScope effectiveScope,
        KnowledgeScopeResolved resolvedScope
    ) {
    }
}
