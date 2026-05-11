import type {
  AuthLoginRequest,
  AuthSession,
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatRunStatusResponse,
  ChatRunTraceDetail,
  ChatRunSubmissionResponse,
  ChatExecutionRequest,
  ChatExecutionResponse,
  ChatRunContextDetail,
  ConversationCreateRequest,
  ConversationDetail,
  ConversationPatchRequest,
  ConversationRunDetail,
  ConversationSummary,
  CreateKnowledgePresetRequest,
  CreateInstructionRequest,
  HealthResponse,
  InstructionRevisionDiff,
  InstructionDetail,
  InstructionRevisionDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetDetail,
  KnowledgePresetRevisionDetail,
  KnowledgePresetSummary,
  LlmProviderActivateRequest,
  LlmProviderConfigResponse,
  LlmProviderInput,
  LlmProviderModelInfo,
  LlmProviderProbeResult,
  InstructionSummary,
  MemoryEntryRequest,
  MemoryEntryResponse,
  MemoryEntryStatus,
  MemoryEntryType,
  MemoryEntryUpdateRequest,
  MemoryReviewActionRequest,
  MaterialDetail,
  MaterialListResponse,
  MaterialLineageOverrideInput,
  MaterialMetadataInput,
  MaterialLineageResponse,
  MaterialUploadPolicy,
  MaterialSummary,
  MaterialVersionUploadInput,
  UpdateMaterialInput,
  ModelInfo,
  RagProjectInput,
  RagProjectSummary,
  ReferenceProject,
  ReferenceProjectInput,
  ReferenceWorkspace,
  ReferenceWorkspaceInput,
} from "../types";
import type { ClientEventInput } from "@/utils/clientEvents";
import { rememberCsrf, requestJson, requestVoid } from "./apiTransport";
import { ApiClientError } from "./errors";

export { ApiClientError, isApiClientError } from "./errors";

const refreshCsrf = async () => {
  const session = await requestJson<AuthSession>("/api/auth/session");
  rememberCsrf(session);
  return session;
};

const performLogin = async (input: AuthLoginRequest) => {
  const session = await requestJson<AuthSession>("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(input),
  });
  rememberCsrf(session);
  return session;
};

export const apiClient = {
  async fetchSession(signal?: AbortSignal) {
    const session = await requestJson<AuthSession>("/api/auth/session", { signal });
    rememberCsrf(session);
    return session;
  },
  async login(input: AuthLoginRequest) {
    await refreshCsrf();
    try {
      return await performLogin(input);
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 403) {
        await refreshCsrf();
        return performLogin(input);
      }
      throw error;
    }
  },
  async logout() {
    const session = await requestJson<AuthSession>("/api/auth/logout", {
      method: "POST",
    });
    rememberCsrf(session);
    return session;
  },
  fetchHealth(signal?: AbortSignal) {
    return requestJson<HealthResponse>("/api/health", { signal });
  },
  fetchModels(signal?: AbortSignal) {
    return requestJson<ModelInfo[]>("/api/models", { signal });
  },
  fetchLlmProviders(signal?: AbortSignal) {
    return requestJson<LlmProviderConfigResponse[]>("/api/llm-providers", { signal });
  },
  createLlmProvider(input: LlmProviderInput) {
    return requestJson<LlmProviderConfigResponse>("/api/llm-providers", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateLlmProvider(providerId: string, input: LlmProviderInput) {
    return requestJson<LlmProviderConfigResponse>(`/api/llm-providers/${providerId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  deleteLlmProvider(providerId: string) {
    return requestVoid(`/api/llm-providers/${providerId}`, {
      method: "DELETE",
    });
  },
  probeLlmProvider(providerId: string) {
    return requestJson<LlmProviderProbeResult>(`/api/llm-providers/${providerId}/probe`, {
      method: "POST",
    });
  },
  fetchLlmProviderModels(providerId: string, signal?: AbortSignal) {
    return requestJson<LlmProviderModelInfo[]>(`/api/llm-providers/${providerId}/models`, { signal });
  },
  activateLlmProvider(providerId: string, input: LlmProviderActivateRequest) {
    return requestJson<LlmProviderConfigResponse>(`/api/llm-providers/${providerId}/activate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  activateLlmFallback(input: LlmProviderActivateRequest) {
    return requestVoid("/api/llm-providers/fallback/activate", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  logClientEvent(input: ClientEventInput, signal?: AbortSignal) {
    return requestVoid("/api/client-events", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
      signal,
    });
  },
  fetchMaterials(input: { offset?: number; limit?: number; workspaceKey?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.offset !== undefined) {
      params.set("offset", String(input.offset));
    }
    if (input.limit !== undefined) {
      params.set("limit", String(input.limit));
    }
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialListResponse>(`/api/materials${query ? `?${query}` : ""}`, { signal });
  },
  fetchMaterialUploadPolicy(signal?: AbortSignal) {
    return requestJson<MaterialUploadPolicy>("/api/materials/policy", { signal });
  },
  createTextMaterial(input: {
    title: string;
    content: string;
    metadata?: MaterialMetadataInput;
    lineageOverride?: MaterialLineageOverrideInput;
  }) {
    return requestJson<MaterialSummary>("/api/materials", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  uploadMaterial(input: {
    title: string;
    file: File;
    metadata?: MaterialMetadataInput;
    lineageOverride?: MaterialLineageOverrideInput;
  }) {
    const formData = new FormData();
    formData.append("file", input.file);
    if (input.title.trim()) {
      formData.append("title", input.title.trim());
    }
    if (input.metadata) {
      formData.append("metadata", new Blob([JSON.stringify(input.metadata)], { type: "application/json" }));
    }
    if (input.lineageOverride) {
      formData.append("lineageOverride", new Blob([JSON.stringify(input.lineageOverride)], { type: "application/json" }));
    }

    return requestJson<MaterialSummary>("/api/materials/upload", {
      method: "POST",
      body: formData,
    });
  },
  uploadMaterialVersion(materialId: string, input: MaterialVersionUploadInput, workspaceKey?: string | null) {
    const formData = new FormData();
    formData.append("file", input.file);
    if (input.title?.trim()) {
      formData.append("title", input.title.trim());
    }
    if (input.metadata) {
      formData.append("metadata", new Blob([JSON.stringify(input.metadata)], { type: "application/json" }));
    }

    const params = new URLSearchParams();
    if (workspaceKey?.trim()) {
      params.set("workspaceKey", workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialSummary>(`/api/materials/${materialId}/versions${query ? `?${query}` : ""}`, {
      method: "POST",
      body: formData,
    });
  },
  fetchMaterial(materialId: string, input: { workspaceKey?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialDetail>(`/api/materials/${materialId}${query ? `?${query}` : ""}`, { signal });
  },
  updateMaterial(materialId: string, input: UpdateMaterialInput, workspaceKey?: string | null) {
    const params = new URLSearchParams();
    if (workspaceKey?.trim()) {
      params.set("workspaceKey", workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialSummary>(`/api/materials/${materialId}${query ? `?${query}` : ""}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  deleteMaterial(materialId: string, workspaceKey?: string | null) {
    const params = new URLSearchParams();
    if (workspaceKey?.trim()) {
      params.set("workspaceKey", workspaceKey.trim());
    }
    const query = params.toString();
    return requestVoid(`/api/materials/${materialId}${query ? `?${query}` : ""}`, {
      method: "DELETE",
    });
  },
  fetchMaterialLineage(materialId: string, input: { workspaceKey?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialLineageResponse>(`/api/materials/${materialId}/lineage${query ? `?${query}` : ""}`, { signal });
  },
  reindexMaterial(materialId: string, workspaceKey?: string | null) {
    const params = new URLSearchParams();
    if (workspaceKey?.trim()) {
      params.set("workspaceKey", workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<MaterialSummary>(`/api/materials/${materialId}/reindex${query ? `?${query}` : ""}`, {
      method: "POST",
    });
  },
  fetchInstructions(signal?: AbortSignal) {
    return requestJson<InstructionSummary[]>("/api/instructions", { signal });
  },
  fetchInstruction(instructionId: string, signal?: AbortSignal) {
    return requestJson<InstructionDetail>(`/api/instructions/${instructionId}`, { signal });
  },
  fetchInstructionRevisions(instructionId: string, signal?: AbortSignal) {
    return requestJson<InstructionRevisionDetail[]>(`/api/instructions/${instructionId}/revisions`, { signal });
  },
  fetchInstructionRevision(instructionId: string, revision: number, signal?: AbortSignal) {
    return requestJson<InstructionRevisionDetail>(`/api/instructions/${instructionId}/revisions/${revision}`, { signal });
  },
  fetchInstructionDiff(instructionId: string, fromRevision: number, toRevision: number, signal?: AbortSignal) {
    return requestJson<InstructionRevisionDiff>(
      `/api/instructions/${instructionId}/diff?fromRevision=${fromRevision}&toRevision=${toRevision}`,
      { signal },
    );
  },
  restoreInstructionRevision(instructionId: string, revision: number) {
    return requestJson<InstructionDetail>(`/api/instructions/${instructionId}/restore/${revision}`, {
      method: "POST",
    });
  },
  createInstruction(input: CreateInstructionRequest) {
    return requestJson<InstructionDetail>("/api/instructions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateInstruction(instructionId: string, input: CreateInstructionRequest) {
    return requestJson<InstructionDetail>(`/api/instructions/${instructionId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  deleteInstruction(instructionId: string) {
    return requestVoid(`/api/instructions/${instructionId}`, {
      method: "DELETE",
    });
  },
  fetchKnowledgePresets(signal?: AbortSignal) {
    return requestJson<KnowledgePresetSummary[]>("/api/knowledge-presets", { signal });
  },
  fetchKnowledgeFacets(signal?: AbortSignal) {
    return requestJson<KnowledgePresetSummary[]>("/api/knowledge-facets", { signal });
  },
  fetchReferenceWorkspaces(input: { activeOnly?: boolean } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.activeOnly !== undefined) {
      params.set("activeOnly", String(input.activeOnly));
    }
    const query = params.toString();
    return requestJson<ReferenceWorkspace[]>(`/api/reference/workspaces${query ? `?${query}` : ""}`, { signal });
  },
  fetchRagProjects(input: { activeOnly?: boolean } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.activeOnly !== undefined) {
      params.set("activeOnly", String(input.activeOnly));
    }
    const query = params.toString();
    return requestJson<RagProjectSummary[]>(`/api/rag-projects${query ? `?${query}` : ""}`, { signal });
  },
  createRagProject(input: RagProjectInput) {
    return requestJson<RagProjectSummary>("/api/rag-projects", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateRagProject(projectKey: string, input: RagProjectInput) {
    return requestJson<RagProjectSummary>(`/api/rag-projects/${encodeURIComponent(projectKey)}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  createReferenceWorkspace(input: ReferenceWorkspaceInput) {
    return requestJson<ReferenceWorkspace>("/api/reference/workspaces", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateReferenceWorkspace(workspaceKey: string, input: ReferenceWorkspaceInput) {
    return requestJson<ReferenceWorkspace>(`/api/reference/workspaces/${encodeURIComponent(workspaceKey)}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  fetchReferenceProjects(input: { activeOnly?: boolean; workspaceKey?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.activeOnly !== undefined) {
      params.set("activeOnly", String(input.activeOnly));
    }
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<ReferenceProject[]>(`/api/reference/projects${query ? `?${query}` : ""}`, { signal });
  },
  createReferenceProject(input: ReferenceProjectInput) {
    return requestJson<ReferenceProject>("/api/reference/projects", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateReferenceProject(projectKey: string, input: ReferenceProjectInput) {
    return requestJson<ReferenceProject>(`/api/reference/projects/${encodeURIComponent(projectKey)}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  fetchKnowledgePreset(presetId: string, signal?: AbortSignal) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-presets/${presetId}`, { signal });
  },
  fetchKnowledgeFacet(facetId: string, signal?: AbortSignal) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-facets/${facetId}`, { signal });
  },
  fetchKnowledgePresetRevisions(presetId: string, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDetail[]>(`/api/knowledge-presets/${presetId}/revisions`, { signal });
  },
  fetchKnowledgeFacetRevisions(facetId: string, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDetail[]>(`/api/knowledge-facets/${facetId}/revisions`, { signal });
  },
  fetchKnowledgePresetDiff(presetId: string, fromRevision: number, toRevision: number, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDiff>(
      `/api/knowledge-presets/${presetId}/diff?fromRevision=${fromRevision}&toRevision=${toRevision}`,
      { signal },
    );
  },
  fetchKnowledgeFacetDiff(facetId: string, fromRevision: number, toRevision: number, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDiff>(
      `/api/knowledge-facets/${facetId}/diff?fromRevision=${fromRevision}&toRevision=${toRevision}`,
      { signal },
    );
  },
  createKnowledgePreset(input: CreateKnowledgePresetRequest) {
    return requestJson<KnowledgePresetDetail>("/api/knowledge-presets", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  createKnowledgeFacet(input: CreateKnowledgePresetRequest) {
    return requestJson<KnowledgePresetDetail>("/api/knowledge-facets", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateKnowledgePreset(presetId: string, input: CreateKnowledgePresetRequest) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-presets/${presetId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateKnowledgeFacet(facetId: string, input: CreateKnowledgePresetRequest) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-facets/${facetId}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  restoreKnowledgePresetRevision(presetId: string, revision: number) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-presets/${presetId}/restore/${revision}`, {
      method: "POST",
    });
  },
  restoreKnowledgeFacetRevision(facetId: string, revision: number) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-facets/${facetId}/restore/${revision}`, {
      method: "POST",
    });
  },
  deleteKnowledgePreset(presetId: string) {
    return requestVoid(`/api/knowledge-presets/${presetId}`, {
      method: "DELETE",
    });
  },
  deleteKnowledgeFacet(facetId: string) {
    return requestVoid(`/api/knowledge-facets/${facetId}`, {
      method: "DELETE",
    });
  },
  fetchConversations(input: { workspaceKey?: string | null; mode?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    if (input.mode?.trim()) {
      params.set("mode", input.mode.trim());
    }
    const query = params.toString();
    return requestJson<ConversationSummary[]>(`/api/conversations${query ? `?${query}` : ""}`, { signal });
  },
  createConversation(input: ConversationCreateRequest) {
    return requestJson<ConversationDetail>("/api/conversations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  fetchConversation(conversationId: string, signal?: AbortSignal) {
    return requestJson<ConversationDetail>(`/api/conversations/${conversationId}`, { signal });
  },
  patchConversation(conversationId: string, input: ConversationPatchRequest) {
    return requestJson<ConversationDetail>(`/api/conversations/${conversationId}`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  fetchConversationRuns(conversationId: string, signal?: AbortSignal) {
    return requestJson<ConversationRunDetail[]>(`/api/conversations/${conversationId}/runs`, { signal });
  },
  fetchChatRuns(input: { workspaceKey?: string | null } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    const query = params.toString();
    return requestJson<ChatAuditRunSummary[]>(`/api/chat-runs${query ? `?${query}` : ""}`, { signal });
  },
  fetchChatRun(runId: string, signal?: AbortSignal) {
    return requestJson<ChatAuditRunDetail>(`/api/chat-runs/${runId}`, { signal });
  },
  fetchChatRunTrace(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunTraceDetail>(`/api/chat-runs/${runId}/trace`, { signal });
  },
  fetchChatRunStatus(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunStatusResponse>(`/api/chat-runs/${runId}/status`, { signal });
  },
  submitChatRun(input: ChatExecutionRequest, signal?: AbortSignal) {
    return requestJson<ChatRunSubmissionResponse>("/api/chat-runs", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
      signal,
    });
  },
  fetchChatRunResult(runId: string, signal?: AbortSignal) {
    return requestJson<ChatExecutionResponse>(`/api/chat-runs/${runId}/result`, { signal });
  },
  fetchChatRunContext(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunContextDetail>(`/api/chat-runs/${runId}/context`, { signal });
  },
  fetchMemoryEntries(input: {
    status?: MemoryEntryStatus | null;
    type?: MemoryEntryType | null;
    workspaceKey?: string | null;
    projectKey?: string | null;
  } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.status?.trim()) {
      params.set("status", input.status.trim());
    }
    if (input.type?.trim()) {
      params.set("type", input.type.trim());
    }
    if (input.workspaceKey?.trim()) {
      params.set("workspaceKey", input.workspaceKey.trim());
    }
    if (input.projectKey?.trim()) {
      params.set("projectKey", input.projectKey.trim());
    }
    const query = params.toString();
    return requestJson<MemoryEntryResponse[]>(`/api/memory-entries${query ? `?${query}` : ""}`, { signal });
  },
  createMemoryEntry(input: MemoryEntryRequest) {
    return requestJson<MemoryEntryResponse>("/api/memory-entries", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  updateMemoryEntry(entryId: string, input: MemoryEntryUpdateRequest) {
    return requestJson<MemoryEntryResponse>(`/api/memory-entries/${entryId}`, {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  approveMemoryEntry(entryId: string, input: MemoryReviewActionRequest = {}) {
    return memoryAction(entryId, "approve", input);
  },
  rejectMemoryEntry(entryId: string, input: MemoryReviewActionRequest = {}) {
    return memoryAction(entryId, "reject", input);
  },
  pinMemoryEntry(entryId: string, input: MemoryReviewActionRequest = {}) {
    return memoryAction(entryId, "pin", input);
  },
  unpinMemoryEntry(entryId: string, input: MemoryReviewActionRequest = {}) {
    return memoryAction(entryId, "unpin", input);
  },
  deleteMemoryEntry(entryId: string, input: MemoryReviewActionRequest = {}) {
    return requestJson<MemoryEntryResponse>(`/api/memory-entries/${entryId}`, {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  cancelChatRun(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunTraceDetail>(`/api/chat-runs/${runId}/cancel`, {
      method: "POST",
      signal,
    });
  },
  /**
   * @deprecated Use submitChatRun + fetchChatRunStatus + fetchChatRunResult.
   */
  executeChat(input: ChatExecutionRequest, signal?: AbortSignal) {
    return requestJson<ChatExecutionResponse>("/api/chat", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
      signal,
    });
  },
};

const memoryAction = (entryId: string, action: "approve" | "reject" | "pin" | "unpin", input: MemoryReviewActionRequest) =>
  requestJson<MemoryEntryResponse>(`/api/memory-entries/${entryId}/${action}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(input),
  });
