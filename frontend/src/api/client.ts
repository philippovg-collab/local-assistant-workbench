import type {
  AuthLoginRequest,
  AuthSession,
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatRunTraceDetail,
  ChatRunSubmissionResponse,
  ChatExecutionRequest,
  ChatExecutionResponse,
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
  InstructionSummary,
  MaterialDetail,
  MaterialListResponse,
  MaterialMetadataInput,
  MaterialLineageResponse,
  MaterialUploadPolicy,
  MaterialSummary,
  MaterialVersionUploadInput,
  ModelInfo,
  ReferenceProject,
  ReferenceProjectInput,
  ReferenceWorkspace,
  ReferenceWorkspaceInput,
} from "../types";

const API_URL = (import.meta.env.VITE_API_URL ?? "").trim().replace(/\/$/, "");
const BACKEND_ORIGIN = (import.meta.env.VITE_BACKEND_ORIGIN ?? "").trim().replace(/\/$/, "");
const DEFAULT_BACKEND_ORIGIN = "http://127.0.0.1:8080";

const resolveApiBaseUrl = () => {
  if (API_URL) {
    return API_URL;
  }

  if (BACKEND_ORIGIN) {
    return BACKEND_ORIGIN;
  }

  if (typeof window === "undefined") {
    return DEFAULT_BACKEND_ORIGIN;
  }

  if (!window.location.port || window.location.port === "8080") {
    return window.location.origin;
  }

  if (window.location.port === "5173" || window.location.port === "4173") {
    return `${window.location.protocol}//${window.location.hostname}:8080`;
  }

  return DEFAULT_BACKEND_ORIGIN;
};

const buildApiUrl = (path: string) => `${resolveApiBaseUrl()}${path}`;
const CSRF_UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

let csrfHeaderName = "X-XSRF-TOKEN";
let csrfToken: string | null = null;

type ApiErrorPayload = {
  code?: string;
  error?: string;
  message?: string;
  requestId?: string;
  timestamp?: string;
};

export class ApiClientError extends Error {
  code?: string;
  requestId?: string;
  status: number;
  timestamp?: string;

  constructor(
    message: string,
    options: {
      code?: string;
      requestId?: string;
      status: number;
      timestamp?: string;
    },
  ) {
    super(message);
    this.name = "ApiClientError";
    this.code = options.code;
    this.requestId = options.requestId;
    this.status = options.status;
    this.timestamp = options.timestamp;
  }
}

export const isApiClientError = (error: unknown): error is ApiClientError =>
  error instanceof ApiClientError;

const readErrorPayload = async (response: Response): Promise<ApiErrorPayload> => {
  const contentType = response.headers.get("content-type") ?? "";

  if (contentType.includes("application/json")) {
    try {
      return (await response.json()) as ApiErrorPayload;
    } catch {
      return {};
    }
  }

  const text = await response.text();
  return {
    message: text || `API responded with status ${response.status}`,
  };
};

const buildApiClientError = async (response: Response) => {
  const payload = await readErrorPayload(response);
  const message = payload.message ?? payload.error ?? `API responded with status ${response.status}`;

  return new ApiClientError(message, {
    code: payload.code,
    requestId: payload.requestId,
    status: response.status,
    timestamp: payload.timestamp,
  });
};

const rememberCsrf = (session: AuthSession) => {
  if (session.csrfHeaderName) {
    csrfHeaderName = session.csrfHeaderName;
  }
  csrfToken = session.csrfToken ?? null;
};

const shouldAttachCsrf = (method?: string) =>
  CSRF_UNSAFE_METHODS.has((method ?? "GET").toUpperCase()) && csrfToken !== null;

const buildRequestInit = (init: RequestInit = {}): RequestInit => {
  const headers = new Headers(init.headers);
  if (shouldAttachCsrf(init.method)) {
    headers.set(csrfHeaderName, csrfToken ?? "");
  }
  return {
    ...init,
    credentials: "include",
    headers,
  };
};

const requestJson = async <T>(path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), buildRequestInit(init));
  if (!response.ok) {
    throw await buildApiClientError(response);
  }

  return (await response.json()) as T;
};

const requestVoid = async (path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), buildRequestInit(init));
  if (!response.ok) {
    throw await buildApiClientError(response);
  }
};

export const apiClient = {
  async fetchSession(signal?: AbortSignal) {
    const session = await requestJson<AuthSession>("/api/auth/session", { signal });
    rememberCsrf(session);
    return session;
  },
  async login(input: AuthLoginRequest) {
    const session = await requestJson<AuthSession>("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
    rememberCsrf(session);
    return session;
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
  fetchMaterials(input: { offset?: number; limit?: number } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.offset !== undefined) {
      params.set("offset", String(input.offset));
    }
    if (input.limit !== undefined) {
      params.set("limit", String(input.limit));
    }
    const query = params.toString();
    return requestJson<MaterialListResponse>(`/api/materials${query ? `?${query}` : ""}`, { signal });
  },
  fetchMaterialUploadPolicy(signal?: AbortSignal) {
    return requestJson<MaterialUploadPolicy>("/api/materials/policy", { signal });
  },
  createTextMaterial(input: { title: string; content: string; metadata?: MaterialMetadataInput }) {
    return requestJson<MaterialSummary>("/api/materials", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  uploadMaterial(input: { title: string; file: File; metadata?: MaterialMetadataInput }) {
    const formData = new FormData();
    formData.append("file", input.file);
    if (input.title.trim()) {
      formData.append("title", input.title.trim());
    }
    if (input.metadata) {
      formData.append("metadata", new Blob([JSON.stringify(input.metadata)], { type: "application/json" }));
    }

    return requestJson<MaterialSummary>("/api/materials/upload", {
      method: "POST",
      body: formData,
    });
  },
  uploadMaterialVersion(materialId: string, input: MaterialVersionUploadInput) {
    const formData = new FormData();
    formData.append("file", input.file);
    if (input.title?.trim()) {
      formData.append("title", input.title.trim());
    }
    if (input.metadata) {
      formData.append("metadata", new Blob([JSON.stringify(input.metadata)], { type: "application/json" }));
    }

    return requestJson<MaterialSummary>(`/api/materials/${materialId}/versions`, {
      method: "POST",
      body: formData,
    });
  },
  fetchMaterial(materialId: string, signal?: AbortSignal) {
    return requestJson<MaterialDetail>(`/api/materials/${materialId}`, { signal });
  },
  deleteMaterial(materialId: string) {
    return requestVoid(`/api/materials/${materialId}`, {
      method: "DELETE",
    });
  },
  fetchMaterialLineage(materialId: string, signal?: AbortSignal) {
    return requestJson<MaterialLineageResponse>(`/api/materials/${materialId}/lineage`, { signal });
  },
  reindexMaterial(materialId: string) {
    return requestJson<MaterialSummary>(`/api/materials/${materialId}/reindex`, {
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
  fetchReferenceWorkspaces(input: { activeOnly?: boolean } = {}, signal?: AbortSignal) {
    const params = new URLSearchParams();
    if (input.activeOnly !== undefined) {
      params.set("activeOnly", String(input.activeOnly));
    }
    const query = params.toString();
    return requestJson<ReferenceWorkspace[]>(`/api/reference/workspaces${query ? `?${query}` : ""}`, { signal });
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
  fetchKnowledgePresetRevisions(presetId: string, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDetail[]>(`/api/knowledge-presets/${presetId}/revisions`, { signal });
  },
  fetchKnowledgePresetDiff(presetId: string, fromRevision: number, toRevision: number, signal?: AbortSignal) {
    return requestJson<KnowledgePresetRevisionDiff>(
      `/api/knowledge-presets/${presetId}/diff?fromRevision=${fromRevision}&toRevision=${toRevision}`,
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
  updateKnowledgePreset(presetId: string, input: CreateKnowledgePresetRequest) {
    return requestJson<KnowledgePresetDetail>(`/api/knowledge-presets/${presetId}`, {
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
  deleteKnowledgePreset(presetId: string) {
    return requestVoid(`/api/knowledge-presets/${presetId}`, {
      method: "DELETE",
    });
  },
  fetchChatRuns(signal?: AbortSignal) {
    return requestJson<ChatAuditRunSummary[]>("/api/chat-runs", { signal });
  },
  fetchChatRun(runId: string, signal?: AbortSignal) {
    return requestJson<ChatAuditRunDetail>(`/api/chat-runs/${runId}`, { signal });
  },
  fetchChatRunTrace(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunTraceDetail>(`/api/chat-runs/${runId}/trace`, { signal });
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
  cancelChatRun(runId: string, signal?: AbortSignal) {
    return requestJson<ChatRunTraceDetail>(`/api/chat-runs/${runId}/cancel`, {
      method: "POST",
      signal,
    });
  },
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
  buildCurlExample(input: ChatExecutionRequest) {
    const curlBaseUrl = resolveApiBaseUrl();

    return `curl ${curlBaseUrl}/api/chat \\
  -H "Content-Type: application/json" \\
  --data-binary @- <<'JSON'
${JSON.stringify(input, null, 2)}
JSON`;
  },
};
