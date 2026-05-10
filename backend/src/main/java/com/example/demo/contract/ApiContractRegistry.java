package com.example.demo.contract;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class ApiContractRegistry {

    private static final List<String> ROOT_TYPE_NAMES = List.of(
        "com.example.demo.model.AuthLoginRequest",
        "com.example.demo.model.AuthSessionResponse",
        "com.example.demo.model.ChatAuditRunDetail",
        "com.example.demo.model.ChatAuditRunSummary",
        "com.example.demo.model.ChatExecutionRequest",
        "com.example.demo.model.ChatExecutionResponse",
        "com.example.demo.model.ChatMode",
        "com.example.demo.model.ChatRunContextDetail",
        "com.example.demo.model.ChatRunStatusResponse",
        "com.example.demo.model.ChatRunSubmissionResponse",
        "com.example.demo.model.ChatRunTraceDetail",
        "com.example.demo.model.ClientEventRequest",
        "com.example.demo.model.ContextAssemblyDroppedItem",
        "com.example.demo.model.ContextAssemblyDroppedMemoryItem",
        "com.example.demo.model.ContextAssemblyHistoryItem",
        "com.example.demo.model.ContextAssemblyMemoryItem",
        "com.example.demo.model.ContextAssemblySnapshotDetail",
        "com.example.demo.model.ContextOptions",
        "com.example.demo.model.ContextSummary",
        "com.example.demo.model.ContextTokenBudget",
        "com.example.demo.model.ConversationCreateRequest",
        "com.example.demo.model.ConversationDetail",
        "com.example.demo.model.ConversationPatchRequest",
        "com.example.demo.model.ConversationRunDetail",
        "com.example.demo.model.ConversationSummaryMemory",
        "com.example.demo.model.ConversationSummarySourceRef",
        "com.example.demo.model.ConversationStickyState",
        "com.example.demo.model.ConversationSummary",
        "com.example.demo.model.CreateInstructionRequest",
        "com.example.demo.model.CreateKnowledgePresetRequest",
        "com.example.demo.model.CreateTextMaterialRequest",
        "com.example.demo.model.DocumentBlockConfidence",
        "com.example.demo.model.DocumentBlockType",
        "com.example.demo.model.HealthResponse",
        "com.example.demo.model.InstructionDetail",
        "com.example.demo.model.InstructionRevisionDetail",
        "com.example.demo.model.InstructionRevisionDiff",
        "com.example.demo.model.InstructionSummary",
        "com.example.demo.model.KnowledgePresetDetail",
        "com.example.demo.model.KnowledgePresetRevisionDetail",
        "com.example.demo.model.KnowledgePresetRevisionDiff",
        "com.example.demo.model.KnowledgePresetSummary",
        "com.example.demo.model.LlmProviderActivateRequest",
        "com.example.demo.model.LlmProviderConfigResponse",
        "com.example.demo.model.LlmProviderInput",
        "com.example.demo.model.LlmProviderModelInfo",
        "com.example.demo.model.LlmProviderProbeResult",
        "com.example.demo.model.LlmProviderPurpose",
        "com.example.demo.model.LlmProviderStatus",
        "com.example.demo.model.LlmProviderType",
        "com.example.demo.model.MemoryEntryAction",
        "com.example.demo.model.MemoryEntryRequest",
        "com.example.demo.model.MemoryEntryResponse",
        "com.example.demo.model.MemoryEntryStatus",
        "com.example.demo.model.MemoryEntryType",
        "com.example.demo.model.MemoryEntryUpdateRequest",
        "com.example.demo.model.MemoryReviewActionRequest",
        "com.example.demo.model.MaterialDetail",
        "com.example.demo.model.MaterialLineageResponse",
        "com.example.demo.model.MaterialListResponse",
        "com.example.demo.model.MaterialMetadataInput",
        "com.example.demo.model.MaterialSearchRequest",
        "com.example.demo.model.MaterialSearchResponse",
        "com.example.demo.model.MaterialSummary",
        "com.example.demo.model.MaterialUploadPolicyResponse",
        "com.example.demo.model.OllamaModelInfo",
        "com.example.demo.model.RagProjectRequest",
        "com.example.demo.model.RagProjectSummary",
        "com.example.demo.model.RechunkActiveMaterialsBatchRequest",
        "com.example.demo.model.RechunkActiveMaterialsBatchResponse",
        "com.example.demo.model.RechunkActiveMaterialsResponse",
        "com.example.demo.model.ReferenceProject",
        "com.example.demo.model.ReferenceProjectRequest",
        "com.example.demo.model.ReferenceWorkspace",
        "com.example.demo.model.ReferenceWorkspaceRequest",
        "com.example.demo.model.RetrievalQueryResolution",
        "com.example.demo.model.UpdateMaterialRequest"
    );

    private ApiContractRegistry() {
    }

    public static List<Class<?>> rootTypes() {
        return ROOT_TYPE_NAMES.stream()
            .map(ApiContractRegistry::loadClass)
            .toList();
    }

    public static Set<String> rootTypeNames() {
        return new TreeSet<>(ROOT_TYPE_NAMES);
    }

    private static Class<?> loadClass(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Registered API contract type is missing: " + typeName, ex);
        }
    }
}
