package com.example.demo.service;

import static com.example.demo.service.MaterialMetadataDefaults.AUTO_TAGS;
import static com.example.demo.service.MaterialMetadataDefaults.AUTHOR;
import static com.example.demo.service.MaterialMetadataDefaults.BUSINESS_STATUS;
import static com.example.demo.service.MaterialMetadataDefaults.COUNTERPARTY;
import static com.example.demo.service.MaterialMetadataDefaults.DEFAULT_WORKSPACE_KEY;
import static com.example.demo.service.MaterialMetadataDefaults.DEPARTMENT;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_DATE;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_NUMBER;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_STATUS;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_TYPE;
import static com.example.demo.service.MaterialMetadataDefaults.EFFECTIVE_TAGS;
import static com.example.demo.service.MaterialMetadataDefaults.KNOWLEDGE_DOCUMENT_CLASS;
import static com.example.demo.service.MaterialMetadataDefaults.LANGUAGE;
import static com.example.demo.service.MaterialMetadataDefaults.LANGUAGE_CODE;
import static com.example.demo.service.MaterialMetadataDefaults.MANUAL_TAGS;
import static com.example.demo.service.MaterialMetadataDefaults.PERIOD_END;
import static com.example.demo.service.MaterialMetadataDefaults.PERIOD_START;
import static com.example.demo.service.MaterialMetadataDefaults.PROJECT;
import static com.example.demo.service.MaterialMetadataDefaults.PROJECT_KEY;
import static com.example.demo.service.MaterialMetadataDefaults.SOURCE_TRUST;
import static com.example.demo.service.MaterialMetadataDefaults.TAG_HINT_CONFIDENCE;
import static com.example.demo.service.MaterialMetadataDefaults.TAGS;
import static com.example.demo.service.MaterialMetadataDefaults.VERSION_LABEL;
import static com.example.demo.service.MaterialMetadataDefaults.WORKSPACE_KEY;

import com.example.demo.api.ApiException;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataProvenance;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MetadataValueOrigin;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

final class MaterialMetadataMergePolicy {

    private final ReferenceDataRepository referenceDataRepository;

    MaterialMetadataMergePolicy(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }

    MaterialMetadataHints mergeHints(MaterialMetadataHints primaryHints, MaterialMetadataHints fallbackHints) {
        MaterialMetadataHints primary = primaryHints == null ? MaterialMetadataHints.empty() : primaryHints;
        MaterialMetadataHints fallback = fallbackHints == null ? MaterialMetadataHints.empty() : fallbackHints;
        Map<String, Double> mergedConfidence = new LinkedHashMap<>(fallback.fieldConfidence());
        mergedConfidence.putAll(primary.fieldConfidence());

        return new MaterialMetadataHints(
            chooseHint(primary.documentType(), fallback.documentType()),
            chooseHint(primary.documentDate(), fallback.documentDate()),
            chooseHint(primary.documentNumber(), fallback.documentNumber()),
            chooseHint(primary.author(), fallback.author()),
            chooseHint(primary.department(), fallback.department()),
            chooseHint(primary.versionLabel(), fallback.versionLabel()),
            chooseHint(primary.language(), fallback.language()),
            !primary.tags().isEmpty() ? primary.tags() : fallback.tags(),
            chooseHint(primary.project(), fallback.project()),
            chooseHint(primary.counterparty(), fallback.counterparty()),
            chooseHint(primary.businessStatus(), fallback.businessStatus()),
            chooseHint(primary.periodStart(), fallback.periodStart()),
            chooseHint(primary.periodEnd(), fallback.periodEnd()),
            mergedConfidence
        );
    }

    MaterialMetadataSnapshot merge(MaterialMetadataInput manualInput, MaterialMetadataHints hints) {
        DocumentType documentType = chooseDocumentType(manualInput, hints);
        KnowledgeDocumentClass knowledgeDocumentClass = MaterialMetadataSnapshot.deriveKnowledgeDocumentClass(documentType);
        SourceTrustLevel sourceTrust = manualInput != null && manualInput.sourceTrust() != null
            ? manualInput.sourceTrust()
            : SourceTrustLevel.UNKNOWN;
        String workspaceKey = resolveWorkspaceKey(manualInput);
        String projectKey = resolveProjectKey(manualInput, workspaceKey);
        String project = chooseString(manualInput != null ? manualInput.project() : null, hints.project());
        String businessStatus = chooseString(manualInput != null ? manualInput.businessStatus() : null, hints.businessStatus());
        DocumentStatus documentStatus = manualInput != null && manualInput.documentStatus() != null
            ? manualInput.documentStatus()
            : DocumentStatus.ACTIVE;
        MaterialLanguageCode languageCode = manualInput != null && manualInput.languageCode() != null
            ? manualInput.languageCode()
            : languageCodeFromHint(hints.language());
        String language = languageCode != null
            ? languageCode.name().toLowerCase(Locale.ROOT)
            : chooseString(manualInput != null ? manualInput.language() : null, hints.language());
        LocalDate documentDate = chooseValue(manualInput != null ? manualInput.documentDate() : null, hints.documentDate());
        String documentNumber = chooseString(manualInput != null ? manualInput.documentNumber() : null, hints.documentNumber());
        String author = chooseString(manualInput != null ? manualInput.author() : null, hints.author());
        String department = chooseString(manualInput != null ? manualInput.department() : null, hints.department());
        String versionLabel = chooseString(manualInput != null ? manualInput.versionLabel() : null, hints.versionLabel());
        String counterparty = chooseString(manualInput != null ? manualInput.counterparty() : null, hints.counterparty());
        LocalDate periodStart = chooseValue(manualInput != null ? manualInput.periodStart() : null, hints.periodStart());
        LocalDate periodEnd = chooseValue(manualInput != null ? manualInput.periodEnd() : null, hints.periodEnd());

        Map<String, MetadataValueOrigin> origins = new LinkedHashMap<>();
        Map<String, Double> confidence = new LinkedHashMap<>();
        recordFieldOrigin(origins, confidence, DOCUMENT_TYPE, manualInput != null ? manualInput.documentType() : null, hints.documentType(), hints);
        recordFieldOrigin(origins, confidence, DOCUMENT_DATE, manualInput != null ? manualInput.documentDate() : null, hints.documentDate(), hints);
        recordFieldOrigin(origins, confidence, DOCUMENT_NUMBER, manualInput != null ? manualInput.documentNumber() : null, hints.documentNumber(), hints);
        recordFieldOrigin(origins, confidence, AUTHOR, manualInput != null ? manualInput.author() : null, hints.author(), hints);
        recordFieldOrigin(origins, confidence, DEPARTMENT, manualInput != null ? manualInput.department() : null, hints.department(), hints);
        recordFieldOrigin(origins, confidence, VERSION_LABEL, manualInput != null ? manualInput.versionLabel() : null, hints.versionLabel(), hints);
        recordLanguageOrigin(manualInput, hints, languageCode, origins, confidence);
        TagMergeResult tagMerge = mergeTags(manualInput, hints, origins, confidence);

        if ((manualInput != null && manualInput.documentType() != null) || hints.documentType() != null) {
            origins.put(KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.INFERRED);
        } else {
            origins.put(KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.DEFAULT);
        }
        origins.put(SOURCE_TRUST, manualInput != null && manualInput.sourceTrust() != null
            ? MetadataValueOrigin.MANUAL
            : MetadataValueOrigin.DEFAULT);
        origins.put(DOCUMENT_STATUS, documentStatus == DocumentStatus.ACTIVE && (manualInput == null || manualInput.documentStatus() == null)
            ? MetadataValueOrigin.DEFAULT
            : MetadataValueOrigin.MANUAL);
        if (projectKey != null) {
            origins.put(PROJECT_KEY, MetadataValueOrigin.MANUAL);
        }
        recordFieldOrigin(origins, confidence, PROJECT, manualInput != null ? manualInput.project() : null, hints.project(), hints);
        origins.put(WORKSPACE_KEY, manualInput != null && manualInput.workspaceKey() != null
            ? MetadataValueOrigin.MANUAL
            : MetadataValueOrigin.DEFAULT);
        recordFieldOrigin(origins, confidence, COUNTERPARTY, manualInput != null ? manualInput.counterparty() : null, hints.counterparty(), hints);
        recordFieldOrigin(origins, confidence, BUSINESS_STATUS, manualInput != null ? manualInput.businessStatus() : null, hints.businessStatus(), hints);
        recordFieldOrigin(origins, confidence, PERIOD_START, manualInput != null ? manualInput.periodStart() : null, hints.periodStart(), hints);
        recordFieldOrigin(origins, confidence, PERIOD_END, manualInput != null ? manualInput.periodEnd() : null, hints.periodEnd(), hints);
        if ((manualInput == null || manualInput.documentType() == null) && hints.documentType() == null) {
            origins.put(DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT);
        }

        return new MaterialMetadataSnapshot(
            documentType,
            knowledgeDocumentClass,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            languageCode,
            tagMerge.tags(),
            tagMerge.manualTags(),
            tagMerge.autoTags(),
            tagMerge.tags(),
            sourceTrust,
            project,
            projectKey,
            workspaceKey,
            counterparty,
            businessStatus,
            documentStatus,
            periodStart,
            periodEnd,
            new MaterialMetadataProvenance(origins, confidence)
        );
    }

    private void recordLanguageOrigin(
        MaterialMetadataInput manualInput,
        MaterialMetadataHints hints,
        MaterialLanguageCode languageCode,
        Map<String, MetadataValueOrigin> origins,
        Map<String, Double> confidence
    ) {
        recordFieldOrigin(origins, confidence, LANGUAGE_CODE, manualInput != null ? manualInput.languageCode() : null, languageCodeFromHint(hints.language()), hints);
        if (languageCode != null) {
            origins.putIfAbsent(LANGUAGE, manualInput != null && manualInput.languageCode() != null
                ? MetadataValueOrigin.MANUAL
                : MetadataValueOrigin.INFERRED);
            if ((manualInput == null || manualInput.languageCode() == null) && hints.fieldConfidence().containsKey(LANGUAGE)) {
                confidence.put(LANGUAGE, hints.fieldConfidence().get(LANGUAGE));
                confidence.put(LANGUAGE_CODE, hints.fieldConfidence().get(LANGUAGE));
            }
        } else {
            recordFieldOrigin(origins, confidence, LANGUAGE, manualInput != null ? manualInput.language() : null, hints.language(), hints);
        }
    }

    private TagMergeResult mergeTags(
        MaterialMetadataInput manualInput,
        MaterialMetadataHints hints,
        Map<String, MetadataValueOrigin> origins,
        Map<String, Double> confidence
    ) {
        List<String> manualTags = manualInput == null ? List.of() : manualInput.effectiveManualTags();
        List<String> autoTags = autoOnlyTags(hints.tags(), manualTags);
        List<String> tags = unionTags(manualTags, autoTags);
        if (!manualTags.isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.MANUAL);
            origins.put(MANUAL_TAGS, MetadataValueOrigin.MANUAL);
            origins.put(EFFECTIVE_TAGS, MetadataValueOrigin.MANUAL);
        } else if (!autoTags.isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.INFERRED);
            origins.put(EFFECTIVE_TAGS, MetadataValueOrigin.INFERRED);
        }
        if (!autoTags.isEmpty()) {
            origins.put(AUTO_TAGS, MetadataValueOrigin.INFERRED);
            confidence.put(AUTO_TAGS, hints.fieldConfidence().getOrDefault(TAGS, TAG_HINT_CONFIDENCE));
            confidence.put(TAGS, hints.fieldConfidence().getOrDefault(TAGS, TAG_HINT_CONFIDENCE));
        }
        return new TagMergeResult(tags, manualTags, autoTags);
    }

    private String resolveWorkspaceKey(MaterialMetadataInput manualInput) {
        String workspaceKey = manualInput != null && manualInput.workspaceKey() != null
            ? manualInput.workspaceKey()
            : DEFAULT_WORKSPACE_KEY;
        if (referenceDataRepository != null && !referenceDataRepository.workspaceExists(workspaceKey)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.workspace_not_found",
                "Reference workspace '" + workspaceKey + "' does not exist"
            );
        }
        return workspaceKey;
    }

    private String resolveProjectKey(MaterialMetadataInput manualInput, String workspaceKey) {
        if (manualInput == null) {
            return null;
        }
        if (manualInput.projectKey() != null) {
            return requireProjectInWorkspace(manualInput.projectKey(), workspaceKey);
        }
        if (manualInput.project() == null || referenceDataRepository == null) {
            return null;
        }
        return referenceDataRepository.findProjectByKey(manualInput.project())
            .filter(project -> project.workspaceKey().equals(workspaceKey))
            .map(StoredReferenceProjectRecord::key)
            .orElse(null);
    }

    private String requireProjectInWorkspace(String projectKey, String workspaceKey) {
        if (referenceDataRepository == null) {
            return projectKey;
        }
        StoredReferenceProjectRecord project = referenceDataRepository.findProjectByKey(projectKey)
            .orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.project_not_found",
                "Reference project '" + projectKey + "' does not exist"
            ));
        if (!project.workspaceKey().equals(workspaceKey)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.project_workspace_mismatch",
                "Reference project '" + projectKey + "' does not belong to workspace '" + workspaceKey + "'"
            );
        }
        return project.key();
    }

    private DocumentType chooseDocumentType(MaterialMetadataInput manualInput, MaterialMetadataHints hints) {
        if (manualInput != null && manualInput.documentType() != null) {
            return manualInput.documentType();
        }
        return hints.documentType() != null ? hints.documentType() : DocumentType.OTHER;
    }

    private void recordFieldOrigin(
        Map<String, MetadataValueOrigin> origins,
        Map<String, Double> confidence,
        String field,
        Object manualValue,
        Object inferredValue,
        MaterialMetadataHints hints
    ) {
        if (isPresent(manualValue)) {
            origins.put(field, MetadataValueOrigin.MANUAL);
            return;
        }
        if (isPresent(inferredValue)) {
            origins.put(field, MetadataValueOrigin.INFERRED);
            Double hintedConfidence = hints.fieldConfidence().get(field);
            if (hintedConfidence != null) {
                confidence.put(field, hintedConfidence);
            }
        }
    }

    private MaterialLanguageCode languageCodeFromHint(String language) {
        String normalizedLanguage = normalizeText(language);
        if (normalizedLanguage == null) {
            return null;
        }
        return switch (normalizedLanguage.toLowerCase(Locale.ROOT)) {
            case "ru", "rus", "russian" -> MaterialLanguageCode.RU;
            case "kk", "kz", "kaz", "kazakh" -> MaterialLanguageCode.KK;
            case "en", "eng", "english" -> MaterialLanguageCode.EN;
            default -> null;
        };
    }

    private String chooseString(String manualValue, String inferredValue) {
        return StringUtils.hasText(manualValue) ? manualValue : inferredValue;
    }

    private <T> T chooseHint(T preferredValue, T fallbackValue) {
        return isPresent(preferredValue) ? preferredValue : fallbackValue;
    }

    private <T> T chooseValue(T manualValue, T inferredValue) {
        return isPresent(manualValue) ? manualValue : inferredValue;
    }

    private static List<String> autoOnlyTags(List<String> rawAutoTags, List<String> manualTags) {
        if (rawAutoTags == null || rawAutoTags.isEmpty()) {
            return List.of();
        }
        Set<String> manualSet = new LinkedHashSet<>();
        if (manualTags != null) {
            for (String tag : manualTags) {
                String normalized = normalizeText(tag);
                if (normalized != null) {
                    manualSet.add(normalized);
                }
            }
        }
        Set<String> normalizedAutoTags = new LinkedHashSet<>();
        for (String tag : rawAutoTags) {
            String normalized = normalizeText(tag);
            if (normalized != null && !manualSet.contains(normalized)) {
                normalizedAutoTags.add(normalized);
            }
        }
        return List.copyOf(normalizedAutoTags);
    }

    private static List<String> unionTags(List<String> manualTags, List<String> autoTags) {
        Set<String> union = new LinkedHashSet<>();
        if (manualTags != null) {
            for (String tag : manualTags) {
                String normalized = normalizeText(tag);
                if (normalized != null) {
                    union.add(normalized);
                }
            }
        }
        union.addAll(autoOnlyTags(autoTags, List.copyOf(union)));
        return List.copyOf(union);
    }

    private static boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return StringUtils.hasText(stringValue);
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record TagMergeResult(List<String> tags, List<String> manualTags, List<String> autoTags) {
    }
}
