package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.api.InputLimits;
import com.example.demo.infrastructure.knowledge.PostgresKnowledgePresetRepository;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRecord;
import com.example.demo.infrastructure.knowledge.StoredKnowledgePresetRevisionRecord;
import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgePresetDetail;
import com.example.demo.model.KnowledgePresetReference;
import com.example.demo.model.KnowledgePresetRevisionDiff;
import com.example.demo.model.KnowledgePresetRevisionDetail;
import com.example.demo.model.KnowledgePresetSummary;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.RevisionDiffEntry;
import com.example.demo.model.SavedKnowledgeFilterKind;
import java.time.LocalDate;
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
        return repository.findAllByKind(SavedKnowledgeFilterKind.PRESET).stream().map(this::toSummary).toList();
    }

    public List<KnowledgePresetSummary> listFacets() {
        return repository.findAllByKind(SavedKnowledgeFilterKind.FACET).stream().map(this::toSummary).toList();
    }

    public KnowledgePresetDetail getPreset(String id) {
        return getFilter(id, SavedKnowledgeFilterKind.PRESET);
    }

    public KnowledgePresetDetail getFacet(String id) {
        return getFilter(id, SavedKnowledgeFilterKind.FACET);
    }

    public KnowledgePresetDetail createPreset(CreateKnowledgePresetRequest request) {
        return createFilter(request, SavedKnowledgeFilterKind.PRESET);
    }

    public KnowledgePresetDetail createFacet(CreateKnowledgePresetRequest request) {
        return createFilter(request, SavedKnowledgeFilterKind.FACET);
    }

    public KnowledgePresetDetail updatePreset(String id, CreateKnowledgePresetRequest request) {
        return updateFilter(id, request, SavedKnowledgeFilterKind.PRESET);
    }

    public KnowledgePresetDetail updateFacet(String id, CreateKnowledgePresetRequest request) {
        return updateFilter(id, request, SavedKnowledgeFilterKind.FACET);
    }

    public void deletePreset(String id) {
        deleteFilter(id, SavedKnowledgeFilterKind.PRESET);
    }

    public void deleteFacet(String id) {
        deleteFilter(id, SavedKnowledgeFilterKind.FACET);
    }

    private KnowledgePresetDetail getFilter(String id, SavedKnowledgeFilterKind expectedKind) {
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord record = repository.findById(presetId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge filter '" + presetId + "' does not exist"
            ));
        requireKind(record, expectedKind);
        return toDetail(record);
    }

    private KnowledgePresetDetail createFilter(CreateKnowledgePresetRequest request, SavedKnowledgeFilterKind kind) {
        InputLimits.validateKnowledgePresetRequest(request);
        String name = sanitize(request == null ? null : request.name(), "name");
        String description = sanitizeOptional(request == null ? null : request.description());
        KnowledgeScope scope = normalizeSavedScope(request == null || request.scope() == null ? KnowledgeScope.empty() : request.scope());
        boolean active = request == null || request.active() == null || request.active();
        Instant now = Instant.now();

        StoredKnowledgePresetRecord record = new StoredKnowledgePresetRecord(
            UUID.randomUUID().toString(),
            kind,
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

    private KnowledgePresetDetail updateFilter(String id, CreateKnowledgePresetRequest request, SavedKnowledgeFilterKind expectedKind) {
        InputLimits.validateKnowledgePresetRequest(request);
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord current = repository.findById(presetId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge preset '" + presetId + "' does not exist"
            ));
        requireKind(current, expectedKind);

        Instant now = Instant.now();
        StoredKnowledgePresetRecord updated = new StoredKnowledgePresetRecord(
            current.id(),
            current.kind(),
            sanitize(request == null ? null : request.name(), "name"),
            sanitizeOptional(request == null ? null : request.description()),
            normalizeSavedScope(request == null || request.scope() == null ? KnowledgeScope.empty() : request.scope()),
            current.revision() + 1,
            request == null || request.active() == null || request.active(),
            current.createdAt(),
            now
        );
        repository.save(updated);
        repository.appendRevision(toRevision(updated, null));
        return toDetail(updated);
    }

    private void deleteFilter(String id, SavedKnowledgeFilterKind expectedKind) {
        String presetId = requireValidId(id);
        repository.findById(presetId).ifPresent(record -> requireKind(record, expectedKind));
        repository.delete(presetId);
    }

    public List<KnowledgePresetRevisionDetail> listRevisions(String id) {
        return repository.findRevisions(requireValidId(id)).stream().map(this::toRevisionDetail).toList();
    }

    public List<KnowledgePresetRevisionDetail> listPresetRevisions(String id) {
        return listRevisions(id, SavedKnowledgeFilterKind.PRESET);
    }

    public List<KnowledgePresetRevisionDetail> listFacetRevisions(String id) {
        return listRevisions(id, SavedKnowledgeFilterKind.FACET);
    }

    private List<KnowledgePresetRevisionDetail> listRevisions(String id, SavedKnowledgeFilterKind expectedKind) {
        String presetId = requireValidId(id);
        requireFilterRecord(presetId, expectedKind);
        return repository.findRevisions(presetId).stream().map(this::toRevisionDetail).toList();
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

    public KnowledgePresetRevisionDetail getPresetRevision(String id, int revision) {
        return getRevision(id, revision, SavedKnowledgeFilterKind.PRESET);
    }

    public KnowledgePresetRevisionDetail getFacetRevision(String id, int revision) {
        return getRevision(id, revision, SavedKnowledgeFilterKind.FACET);
    }

    private KnowledgePresetRevisionDetail getRevision(String id, int revision, SavedKnowledgeFilterKind expectedKind) {
        String presetId = requireValidId(id);
        requireFilterRecord(presetId, expectedKind);
        return getRevision(presetId, revision);
    }

    public KnowledgePresetDetail restoreRevision(String id, int revision) {
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord current = requireFilterRecord(presetId, null);
        return restoreRevision(current, revision);
    }

    public KnowledgePresetDetail restorePresetRevision(String id, int revision) {
        StoredKnowledgePresetRecord current = requireFilterRecord(requireValidId(id), SavedKnowledgeFilterKind.PRESET);
        return restoreRevision(current, revision);
    }

    public KnowledgePresetDetail restoreFacetRevision(String id, int revision) {
        StoredKnowledgePresetRecord current = requireFilterRecord(requireValidId(id), SavedKnowledgeFilterKind.FACET);
        return restoreRevision(current, revision);
    }

    private KnowledgePresetDetail restoreRevision(StoredKnowledgePresetRecord current, int revision) {
        String presetId = current.id();
        StoredKnowledgePresetRevisionRecord sourceRevision = repository.findRevision(presetId, revision)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.revision_not_found",
                "Knowledge preset revision '" + revision + "' does not exist"
            ));

        Instant now = Instant.now();
        StoredKnowledgePresetRecord restored = new StoredKnowledgePresetRecord(
            current.id(),
            current.kind(),
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
        return diffRevisions(presetId, fromRevision, toRevision, null);
    }

    public KnowledgePresetRevisionDiff diffPresetRevisions(String id, int fromRevision, int toRevision) {
        return diffRevisions(requireValidId(id), fromRevision, toRevision, SavedKnowledgeFilterKind.PRESET);
    }

    public KnowledgePresetRevisionDiff diffFacetRevisions(String id, int fromRevision, int toRevision) {
        return diffRevisions(requireValidId(id), fromRevision, toRevision, SavedKnowledgeFilterKind.FACET);
    }

    private KnowledgePresetRevisionDiff diffRevisions(
        String presetId,
        int fromRevision,
        int toRevision,
        SavedKnowledgeFilterKind expectedKind
    ) {
        if (expectedKind != null) {
            requireFilterRecord(presetId, expectedKind);
        }
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
        appendDiff(
            changes,
            "documentTypes",
            String.join(", ", from.scope().documentTypes().stream().map(Enum::name).toList()),
            String.join(", ", to.scope().documentTypes().stream().map(Enum::name).toList())
        );
        appendDiff(
            changes,
            "documentStatuses",
            String.join(", ", from.scope().documentStatuses().stream().map(Enum::name).toList()),
            String.join(", ", to.scope().documentStatuses().stream().map(Enum::name).toList())
        );
        appendDiff(changes, "projectKeys", String.join(", ", from.scope().projectKeys()), String.join(", ", to.scope().projectKeys()));
        appendDiff(changes, "documentNumber", from.scope().documentNumber(), to.scope().documentNumber());
        appendDiff(
            changes,
            "languageCodes",
            String.join(", ", from.scope().languageCodes().stream().map(Enum::name).toList()),
            String.join(", ", to.scope().languageCodes().stream().map(Enum::name).toList())
        );
        appendDiff(changes, "tags", String.join(", ", from.scope().tags()), String.join(", ", to.scope().tags()));
        appendDiff(changes, "workspaceKey", from.scope().workspaceKey(), to.scope().workspaceKey());
        appendDiff(changes, "periodStartFrom", dateText(from.scope().periodStartFrom()), dateText(to.scope().periodStartFrom()));
        appendDiff(changes, "periodStartTo", dateText(from.scope().periodStartTo()), dateText(to.scope().periodStartTo()));
        appendDiff(changes, "periodEndFrom", dateText(from.scope().periodEndFrom()), dateText(to.scope().periodEndFrom()));
        appendDiff(changes, "periodEndTo", dateText(from.scope().periodEndTo()), dateText(to.scope().periodEndTo()));
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
        List<KnowledgePresetReference> facetReferences = new ArrayList<>();
        LinkedHashSet<com.example.demo.model.KnowledgeDocumentClass> documentClasses = new LinkedHashSet<>();
        LinkedHashSet<DocumentType> documentTypes = new LinkedHashSet<>();
        LinkedHashSet<DocumentStatus> documentStatuses = new LinkedHashSet<>();
        LinkedHashSet<String> projectKeys = new LinkedHashSet<>();
        LinkedHashSet<MaterialLanguageCode> languageCodes = new LinkedHashSet<>();
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String workspaceKey = safeRequestScope.workspaceKey();
        String documentNumber = safeRequestScope.documentNumber();
        LocalDate periodStartFrom = safeRequestScope.periodStartFrom();
        LocalDate periodStartTo = safeRequestScope.periodStartTo();
        LocalDate periodEndFrom = safeRequestScope.periodEndFrom();
        LocalDate periodEndTo = safeRequestScope.periodEndTo();
        boolean uploadedTodayOnly = safeRequestScope.uploadedTodayOnly();

        for (String presetId : safeRequestScope.presetIds()) {
            StoredKnowledgePresetRecord preset = repository.findById(requireValidId(presetId))
                .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "knowledge_preset.not_found",
                    "Knowledge preset '" + presetId + "' does not exist"
                ));
            requireKind(preset, SavedKnowledgeFilterKind.PRESET);
            requireActiveForExecution(preset);
            references.add(new KnowledgePresetReference(preset.id(), preset.name(), preset.revision()));
            MergeResult merge = mergeScope(
                preset.scope(),
                workspaceKey,
                documentNumber,
                periodStartFrom,
                periodStartTo,
                periodEndFrom,
                periodEndTo,
                uploadedTodayOnly,
                documentClasses,
                documentTypes,
                documentStatuses,
                projectKeys,
                languageCodes,
                tags
            );
            workspaceKey = merge.workspaceKey();
            documentNumber = merge.documentNumber();
            periodStartFrom = merge.periodStartFrom();
            periodStartTo = merge.periodStartTo();
            periodEndFrom = merge.periodEndFrom();
            periodEndTo = merge.periodEndTo();
            uploadedTodayOnly = merge.uploadedTodayOnly();
        }

        for (String facetId : safeRequestScope.facetIds()) {
            StoredKnowledgePresetRecord facet = repository.findById(requireValidId(facetId))
                .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND,
                    "knowledge_preset.not_found",
                    "Knowledge facet '" + facetId + "' does not exist"
                ));
            requireKind(facet, SavedKnowledgeFilterKind.FACET);
            requireActiveForExecution(facet);
            facetReferences.add(new KnowledgePresetReference(facet.id(), facet.name(), facet.revision()));
            MergeResult merge = mergeScope(
                facet.scope(),
                workspaceKey,
                documentNumber,
                periodStartFrom,
                periodStartTo,
                periodEndFrom,
                periodEndTo,
                uploadedTodayOnly,
                documentClasses,
                documentTypes,
                documentStatuses,
                projectKeys,
                languageCodes,
                tags
            );
            workspaceKey = merge.workspaceKey();
            documentNumber = merge.documentNumber();
            periodStartFrom = merge.periodStartFrom();
            periodStartTo = merge.periodStartTo();
            periodEndFrom = merge.periodEndFrom();
            periodEndTo = merge.periodEndTo();
            uploadedTodayOnly = merge.uploadedTodayOnly();
        }

        documentClasses.addAll(safeRequestScope.documentClasses());
        documentTypes.addAll(legacyClassesToDocumentTypes(safeRequestScope.documentClasses()));
        documentTypes.addAll(safeRequestScope.documentTypes());
        documentStatuses.addAll(safeRequestScope.documentStatuses());
        projectKeys.addAll(safeRequestScope.projectKeys());
        languageCodes.addAll(safeRequestScope.languageCodes());
        tags.addAll(safeRequestScope.tags());

        KnowledgeScope effectiveScope = new KnowledgeScope(
            safeRequestScope.presetIds(),
            safeRequestScope.facetIds(),
            List.copyOf(documentClasses),
            List.copyOf(documentTypes),
            List.copyOf(documentStatuses),
            List.copyOf(projectKeys),
            documentNumber,
            List.copyOf(languageCodes),
            List.copyOf(tags),
            workspaceKey,
            periodStartFrom,
            periodStartTo,
            periodEndFrom,
            periodEndTo,
            uploadedTodayOnly
        );
        KnowledgeScopeResolved resolved = new KnowledgeScopeResolved(
            List.copyOf(references),
            List.copyOf(facetReferences),
            effectiveScope.documentClasses(),
            effectiveScope.documentTypes(),
            effectiveScope.documentStatuses(),
            effectiveScope.projectKeys(),
            effectiveScope.documentNumber(),
            effectiveScope.languageCodes(),
            effectiveScope.tags(),
            effectiveScope.workspaceKey(),
            effectiveScope.periodStartFrom(),
            effectiveScope.periodStartTo(),
            effectiveScope.periodEndFrom(),
            effectiveScope.periodEndTo(),
            effectiveScope.uploadedTodayOnly()
        );
        return new ResolvedKnowledgeScopeContext(effectiveScope, resolved);
    }

    private MergeResult mergeScope(
        KnowledgeScope scope,
        String workspaceKey,
        String documentNumber,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo,
        boolean uploadedTodayOnly,
        LinkedHashSet<com.example.demo.model.KnowledgeDocumentClass> documentClasses,
        LinkedHashSet<DocumentType> documentTypes,
        LinkedHashSet<DocumentStatus> documentStatuses,
        LinkedHashSet<String> projectKeys,
        LinkedHashSet<MaterialLanguageCode> languageCodes,
        LinkedHashSet<String> tags
    ) {
        KnowledgeScope safeScope = scope == null ? KnowledgeScope.empty() : scope;
        documentClasses.addAll(safeScope.documentClasses());
        documentTypes.addAll(legacyClassesToDocumentTypes(safeScope.documentClasses()));
        documentTypes.addAll(safeScope.documentTypes());
        documentStatuses.addAll(safeScope.documentStatuses());
        projectKeys.addAll(safeScope.projectKeys());
        languageCodes.addAll(safeScope.languageCodes());
        tags.addAll(safeScope.tags());
        if (!StringUtils.hasText(workspaceKey) && StringUtils.hasText(safeScope.workspaceKey())) {
            workspaceKey = safeScope.workspaceKey();
        }
        if (!StringUtils.hasText(documentNumber) && StringUtils.hasText(safeScope.documentNumber())) {
            documentNumber = safeScope.documentNumber();
        }
        periodStartFrom = firstDate(periodStartFrom, safeScope.periodStartFrom());
        periodStartTo = firstDate(periodStartTo, safeScope.periodStartTo());
        periodEndFrom = firstDate(periodEndFrom, safeScope.periodEndFrom());
        periodEndTo = firstDate(periodEndTo, safeScope.periodEndTo());
        uploadedTodayOnly = uploadedTodayOnly || safeScope.uploadedTodayOnly();
        return new MergeResult(
            workspaceKey,
            documentNumber,
            periodStartFrom,
            periodStartTo,
            periodEndFrom,
            periodEndTo,
            uploadedTodayOnly
        );
    }

    private LocalDate firstDate(LocalDate current, LocalDate candidate) {
        return current == null ? candidate : current;
    }

    private List<DocumentType> legacyClassesToDocumentTypes(List<com.example.demo.model.KnowledgeDocumentClass> documentClasses) {
        if (documentClasses == null || documentClasses.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<DocumentType> documentTypes = new LinkedHashSet<>();
        for (com.example.demo.model.KnowledgeDocumentClass documentClass : documentClasses) {
            if (documentClass == null) {
                continue;
            }
            switch (documentClass) {
                case CONTRACTS -> documentTypes.add(DocumentType.CONTRACT);
                case REGULATIONS -> {
                    documentTypes.add(DocumentType.POLICY);
                    documentTypes.add(DocumentType.PROCEDURE);
                }
                case CORRESPONDENCE -> documentTypes.add(DocumentType.LETTER);
                case TECHDOCS -> {
                    documentTypes.add(DocumentType.REPORT);
                    documentTypes.add(DocumentType.PRESENTATION);
                    documentTypes.add(DocumentType.SPREADSHEET);
                    documentTypes.add(DocumentType.MANUAL);
                    documentTypes.add(DocumentType.FAQ);
                }
                case OTHER -> documentTypes.add(DocumentType.OTHER);
            }
        }
        return List.copyOf(documentTypes);
    }

    private KnowledgeScope normalizeSavedScope(KnowledgeScope scope) {
        KnowledgeScope safeScope = scope == null ? KnowledgeScope.empty() : scope;
        LinkedHashSet<DocumentType> documentTypes = new LinkedHashSet<>(legacyClassesToDocumentTypes(safeScope.documentClasses()));
        documentTypes.addAll(safeScope.documentTypes());
        return new KnowledgeScope(
            List.of(),
            List.of(),
            safeScope.documentClasses(),
            List.copyOf(documentTypes),
            safeScope.documentStatuses(),
            safeScope.projectKeys(),
            safeScope.documentNumber(),
            safeScope.languageCodes(),
            safeScope.tags(),
            safeScope.workspaceKey(),
            safeScope.periodStartFrom(),
            safeScope.periodStartTo(),
            safeScope.periodEndFrom(),
            safeScope.periodEndTo(),
            safeScope.uploadedTodayOnly()
        );
    }

    private void requireActiveForExecution(StoredKnowledgePresetRecord record) {
        if (!record.active()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "knowledge_preset.inactive",
                "Knowledge filter '%s' is inactive and cannot be used in chat execution".formatted(record.id())
            );
        }
    }

    private StoredKnowledgePresetRecord requireFilterRecord(String id, SavedKnowledgeFilterKind expectedKind) {
        String presetId = requireValidId(id);
        StoredKnowledgePresetRecord record = repository.findById(presetId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "knowledge_preset.not_found",
                "Knowledge filter '" + presetId + "' does not exist"
            ));
        if (expectedKind != null) {
            requireKind(record, expectedKind);
        }
        return record;
    }

    private void requireKind(StoredKnowledgePresetRecord record, SavedKnowledgeFilterKind expectedKind) {
        if (record.kind() != expectedKind) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "knowledge_preset.kind_mismatch",
                "Knowledge filter '%s' is %s but %s was expected".formatted(record.id(), record.kind(), expectedKind)
            );
        }
    }

    private record MergeResult(
        String workspaceKey,
        String documentNumber,
        LocalDate periodStartFrom,
        LocalDate periodStartTo,
        LocalDate periodEndFrom,
        LocalDate periodEndTo,
        boolean uploadedTodayOnly
    ) {
    }

    private KnowledgePresetSummary toSummary(StoredKnowledgePresetRecord record) {
        return new KnowledgePresetSummary(
            record.id(),
            record.kind(),
            record.name(),
            record.description(),
            record.scope().workspaceKey(),
            record.revision(),
            record.active(),
            record.createdAt(),
            record.updatedAt()
        );
    }

    private KnowledgePresetDetail toDetail(StoredKnowledgePresetRecord record) {
        return new KnowledgePresetDetail(
            record.id(),
            record.kind(),
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
            record.kind(),
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
            record.kind(),
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

    private String dateText(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public record ResolvedKnowledgeScopeContext(
        KnowledgeScope effectiveScope,
        KnowledgeScopeResolved resolvedScope
    ) {
    }
}
