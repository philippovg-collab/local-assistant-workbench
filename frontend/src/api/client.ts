import type {
  ChatExecutionRequest,
  ChatExecutionResponse,
  CreateInstructionRequest,
  HealthResponse,
  InstructionDetail,
  InstructionSummary,
  MaterialLineageResponse,
  MaterialUploadPolicy,
  MaterialSummary,
  ModelInfo,
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

const requestJson = async <T>(path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), init);
  if (!response.ok) {
    throw await buildApiClientError(response);
  }

  return (await response.json()) as T;
};

const requestVoid = async (path: string, init?: RequestInit) => {
  const response = await fetch(buildApiUrl(path), init);
  if (!response.ok) {
    throw await buildApiClientError(response);
  }
};

export const apiClient = {
  fetchHealth(signal?: AbortSignal) {
    return requestJson<HealthResponse>("/api/health", { signal });
  },
  fetchModels(signal?: AbortSignal) {
    return requestJson<ModelInfo[]>("/api/models", { signal });
  },
  fetchMaterials(signal?: AbortSignal) {
    return requestJson<MaterialSummary[]>("/api/materials", { signal });
  },
  fetchMaterialUploadPolicy(signal?: AbortSignal) {
    return requestJson<MaterialUploadPolicy>("/api/materials/policy", { signal });
  },
  createTextMaterial(input: { title: string; content: string }) {
    return requestJson<MaterialSummary>("/api/materials", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(input),
    });
  },
  uploadMaterial(input: { title: string; file: File }) {
    const formData = new FormData();
    formData.append("file", input.file);
    if (input.title.trim()) {
      formData.append("title", input.title.trim());
    }

    return requestJson<MaterialSummary>("/api/materials/upload", {
      method: "POST",
      body: formData,
    });
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
  -d '${JSON.stringify(input, null, 2)}'`;
  },
};
