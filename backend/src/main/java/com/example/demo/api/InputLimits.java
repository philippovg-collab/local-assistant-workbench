package com.example.demo.api;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.CreateInstructionRequest;
import com.example.demo.model.CreateKnowledgePresetRequest;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.RetrievalFilters;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public final class InputLimits {

    public static final int PROMPT_MAX = 20_000;
    public static final int TEMPORARY_INSTRUCTION_MAX = 8_000;
    public static final int MODEL_MAX = 128;
    public static final int INSTRUCTION_IDS_MAX = 32;
    public static final int INSTRUCTION_TITLE_MAX = 160;
    public static final int INSTRUCTION_CONTENT_MAX = 20_000;
    public static final int PRESET_NAME_MAX = 160;
    public static final int PRESET_DESCRIPTION_MAX = 2_000;
    public static final int TAGS_MAX = 32;
    public static final int TAG_MAX = 64;
    public static final int FILTER_TEXT_MAX = 128;
    public static final int WORKSPACE_KEY_MAX = 128;

    private InputLimits() {
    }

    public static void validateChatRequest(ChatExecutionRequest request) {
        if (request == null || !StringUtils.hasText(request.prompt())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_request",
                "Field 'prompt' is required"
            );
        }
        requireMaxLength(request.prompt(), PROMPT_MAX, "chat.prompt");
        requireMaxLength(request.systemPrompt(), TEMPORARY_INSTRUCTION_MAX, "chat.systemPrompt");
        requireMaxLength(request.temporaryInstruction(), TEMPORARY_INSTRUCTION_MAX, "chat.temporaryInstruction");
        requireMaxLength(request.model(), MODEL_MAX, "chat.model");
        requireMaxListSize(request.instructionIds(), INSTRUCTION_IDS_MAX, "chat.instructionIds");
        requireMaxStringListEntries(request.instructionIds(), MODEL_MAX, "chat.instructionIds");
        requireMaxListSize(request.scenarioInstructionIds(), INSTRUCTION_IDS_MAX, "chat.scenarioInstructionIds");
        requireMaxStringListEntries(request.scenarioInstructionIds(), MODEL_MAX, "chat.scenarioInstructionIds");
        requireMaxListSize(request.dismissedRetrievalHintKeys(), TAGS_MAX, "chat.dismissedRetrievalHintKeys");
        requireMaxStringListEntries(request.dismissedRetrievalHintKeys(), FILTER_TEXT_MAX, "chat.dismissedRetrievalHintKeys");
        requireMaxLength(request.instructionWorkspaceKey(), WORKSPACE_KEY_MAX, "chat.instructionWorkspaceKey");
        validateKnowledgeScope(request.knowledgeScope(), "chat.knowledgeScope");
        validateRetrievalFilters(request.retrievalFilters(), "chat.retrievalFilters");
    }

    public static void validateInstructionRequest(CreateInstructionRequest request) {
        requireMaxLength(request == null ? null : request.title(), INSTRUCTION_TITLE_MAX, "instruction.title");
        requireMaxLength(request == null ? null : request.content(), INSTRUCTION_CONTENT_MAX, "instruction.content");
        requireMaxLength(request == null ? null : request.scopeTargetId(), WORKSPACE_KEY_MAX, "instruction.scopeTargetId");
    }

    public static void validateKnowledgePresetRequest(CreateKnowledgePresetRequest request) {
        requireMaxLength(request == null ? null : request.name(), PRESET_NAME_MAX, "knowledgePreset.name");
        requireMaxLength(request == null ? null : request.description(), PRESET_DESCRIPTION_MAX, "knowledgePreset.description");
        validateKnowledgeScope(request == null ? null : request.scope(), "knowledgePreset.scope");
    }

    public static void validateMaterialSearchRequest(MaterialSearchRequest request) {
        requireMaxLength(request == null ? null : request.query(), PROMPT_MAX, "search.query");
        validateRetrievalFilters(request == null ? null : request.filters(), "search.filters");
    }

    public static void validateMaterialMetadata(MaterialMetadataInput metadata) {
        if (metadata == null) {
            return;
        }
        requireMaxLength(metadata.documentNumber(), FILTER_TEXT_MAX, "material.metadata.documentNumber");
        requireMaxLength(metadata.author(), FILTER_TEXT_MAX, "material.metadata.author");
        requireMaxLength(metadata.department(), FILTER_TEXT_MAX, "material.metadata.department");
        requireMaxLength(metadata.versionLabel(), FILTER_TEXT_MAX, "material.metadata.versionLabel");
        requireMaxLength(metadata.language(), FILTER_TEXT_MAX, "material.metadata.language");
        requireMaxLength(metadata.project(), FILTER_TEXT_MAX, "material.metadata.project");
        requireMaxLength(metadata.workspaceKey(), WORKSPACE_KEY_MAX, "material.metadata.workspaceKey");
        requireMaxLength(metadata.counterparty(), FILTER_TEXT_MAX, "material.metadata.counterparty");
        requireMaxLength(metadata.businessStatus(), FILTER_TEXT_MAX, "material.metadata.businessStatus");
        requireMaxListSize(metadata.tags(), TAGS_MAX, "material.metadata.tags");
        requireMaxStringListEntries(metadata.tags(), TAG_MAX, "material.metadata.tags");
    }

    public static void validateKnowledgeScope(KnowledgeScope scope, String fieldPrefix) {
        if (scope == null) {
            return;
        }
        requireMaxListSize(scope.presetIds(), INSTRUCTION_IDS_MAX, fieldPrefix + ".presetIds");
        requireMaxStringListEntries(scope.presetIds(), MODEL_MAX, fieldPrefix + ".presetIds");
        requireMaxListSize(scope.documentClasses(), TAGS_MAX, fieldPrefix + ".documentClasses");
        requireMaxListSize(scope.tags(), TAGS_MAX, fieldPrefix + ".tags");
        requireMaxStringListEntries(scope.tags(), TAG_MAX, fieldPrefix + ".tags");
        requireMaxLength(scope.workspaceKey(), WORKSPACE_KEY_MAX, fieldPrefix + ".workspaceKey");
    }

    public static void validateRetrievalFilters(RetrievalFilters filters, String fieldPrefix) {
        if (filters == null) {
            return;
        }
        requireMaxLength(filters.documentNumber(), FILTER_TEXT_MAX, fieldPrefix + ".documentNumber");
        requireMaxLength(filters.department(), FILTER_TEXT_MAX, fieldPrefix + ".department");
        requireMaxLength(filters.project(), FILTER_TEXT_MAX, fieldPrefix + ".project");
        requireMaxLength(filters.counterparty(), FILTER_TEXT_MAX, fieldPrefix + ".counterparty");
        requireMaxLength(filters.businessStatus(), FILTER_TEXT_MAX, fieldPrefix + ".businessStatus");
        requireMaxLength(filters.language(), FILTER_TEXT_MAX, fieldPrefix + ".language");
        requireMaxListSize(filters.tags(), TAGS_MAX, fieldPrefix + ".tags");
        requireMaxStringListEntries(filters.tags(), TAG_MAX, fieldPrefix + ".tags");
    }

    public static void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.field_too_large",
                "Field '" + fieldName + "' exceeds the configured limit of " + maxLength + " characters"
            );
        }
    }

    public static void requireMaxListSize(List<?> values, int maxSize, String fieldName) {
        if (values != null && values.size() > maxSize) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "request.too_many_items",
                "Field '" + fieldName + "' exceeds the configured limit of " + maxSize + " entries"
            );
        }
    }

    private static void requireMaxStringListEntries(List<String> values, int maxLength, String fieldName) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            requireMaxLength(value, maxLength, fieldName + "[]");
        }
    }
}
