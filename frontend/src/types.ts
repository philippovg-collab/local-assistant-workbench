export type ChatMode = "direct" | "rag";
export type InstructionCategory = "system" | "user" | "context" | "safety";
export type MaterialVersionState = "ACTIVE" | "SUPERSEDED";

export type HealthResponse = {
  application: string;
  status: string;
  timestamp: string;
  runtimeCachedAt?: string;
  directStatus?: "UP" | "DOWN";
  ragStatus?: "UP" | "DOWN";
  llmStatus?: "UP" | "DOWN";
  llmReasonCode?: string;
  llmReasonMessage?: string;
  embeddingStatus?: "UP" | "DOWN";
  embeddingReasonCode?: string;
  embeddingReasonMessage?: string;
  ocrStatus?: "UP" | "DOWN" | "DISABLED";
  ocrReasonCode?: string;
  ocrReasonMessage?: string;
  ocrLanguages?: string[];
  databaseStatus?: "UP" | "DOWN";
  databaseReasonMessage?: string;
  vectorStatus?: "UP" | "DOWN";
  vectorReasonMessage?: string;
  llmLastSuccessfulProbeAt?: string | null;
  embeddingLastSuccessfulProbeAt?: string | null;
  ragDegradedReasonCode?: string | null;
  ragDegradedReasonMessage?: string | null;
  indexingPendingCount?: number;
  indexingInProgressCount?: number;
  indexingFailedCount?: number;
  indexingNextRetryAt?: string | null;
};

export type ModelInfo = {
  name: string;
};

export type MaterialSummary = {
  id: string;
  title: string;
  sourceType: string;
  originalFileName: string | null;
  status: "PENDING" | "IN_PROGRESS" | "READY" | "PARTIAL_READY" | "FAILED";
  versionState?: MaterialVersionState;
  statusReasonCode?: string | null;
  statusReasonMessage?: string | null;
  createdAt: string;
  updatedAt?: string;
  indexingAttempts?: number;
  nextRetryAt?: string | null;
  contentLength: number;
  preview: string;
};

export type MaterialLineageVersion = MaterialSummary & {
  supersededByMaterialId?: string | null;
  supersedeReason?: string | null;
};

export type MaterialLineageResponse = {
  requestedMaterialId: string;
  activeMaterialId?: string | null;
  versions: MaterialLineageVersion[];
};

export type MaterialPdfUploadPolicy = {
  enabled: boolean;
  scannedPdfSupport: boolean;
  mode: "embedded_text_only" | "embedded_text_and_ocr";
  ocrReasonCode?: string;
  ocrReasonMessage?: string;
  ocrLanguages: string[];
  ocrMaxPages: number;
};

export type MaterialUploadPolicy = {
  maxUploadBytes: number;
  acceptedExtensions: string[];
  acceptedMimeHints: string[];
  richDocumentSupport: boolean;
  pdf: MaterialPdfUploadPolicy;
};

export type InstructionSummary = {
  id: string;
  title: string;
  category: InstructionCategory;
  preview: string;
  createdAt: string;
  updatedAt?: string;
};

export type InstructionDetail = {
  id: string;
  title: string;
  category: InstructionCategory;
  content: string;
  createdAt: string;
  updatedAt?: string;
};

export type AppliedInstruction = {
  id: string;
  title: string;
  category: InstructionCategory;
};

export type ChatSource = {
  materialId: string;
  title: string;
  excerpt: string;
  score: number;
  page?: number | null;
  extractor?: string | null;
  ocrUsed?: boolean;
};

export type ChatExecutionRequest = {
  mode: ChatMode;
  model: string;
  prompt: string;
  systemPrompt?: string;
  instructionIds: string[];
};

export type ChatExecutionResponse = {
  mode: ChatMode;
  model: string;
  prompt: string;
  answer: string;
  contextStatus?: "ready" | "no-context" | null;
  createdAt: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  appliedInstructions: AppliedInstruction[];
  sources: ChatSource[];
};

export type CreateInstructionRequest = {
  title: string;
  category: InstructionCategory;
  content: string;
};
