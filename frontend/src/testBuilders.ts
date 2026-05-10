import type {
  AppliedInstruction,
  ChatExecutionResponse,
  RetrievalDebug,
  MaterialListResponse,
  MaterialSummary,
} from "@/types";
import {
  EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  EMPTY_RETRIEVAL_DEBUG,
  EMPTY_RETRIEVAL_TRACE,
} from "@/utils/workbenchPresentation";
import { DEFAULT_MATERIAL_METADATA } from "@/utils/materialMetadata";

export const buildMaterialSummary = (overrides: Partial<MaterialSummary> = {}): MaterialSummary => {
  const base: MaterialSummary = {
    id: "material-1",
    title: "Material",
    sourceType: "text",
    originalFileName: null,
    status: "READY",
    versionState: "ACTIVE",
    statusReasonCode: null,
    statusReasonMessage: null,
    createdAt: "2026-04-16T10:00:00Z",
    contentLength: 128,
    preview: "Preview",
    metadata: DEFAULT_MATERIAL_METADATA,
    enrichmentStatus: {
      taskId: null,
      status: "NOT_REQUESTED",
      attempts: 0,
      nextRetryAt: null,
      failureCode: null,
      failureMessage: null,
      resultCode: null,
      updatedAt: null,
    },
  };

  return {
    ...base,
    ...overrides,
    metadata: overrides.metadata ?? DEFAULT_MATERIAL_METADATA,
  };
};

export const buildMaterialListResponse = (
  items: MaterialSummary[] = [],
  overrides: Partial<Omit<MaterialListResponse, "items">> = {},
): MaterialListResponse => ({
  items,
  total: overrides.total ?? items.length,
  offset: overrides.offset ?? 0,
  limit: overrides.limit ?? Math.max(items.length, 100),
  hasMore: overrides.hasMore ?? false,
});

export const buildChatExecutionResponse = (
  overrides: Partial<ChatExecutionResponse> = {},
): ChatExecutionResponse => ({
  mode: "direct",
  model: "qwen2.5:7b",
  prompt: "prompt",
  answer: "answer",
  createdAt: "2026-04-16T10:00:00Z",
  promptTokens: 1,
  completionTokens: 1,
  totalTokens: 2,
  appliedInstructions: [],
  instructionTrace: [],
  knowledgeScopeResolved: EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  retrievalTrace: EMPTY_RETRIEVAL_TRACE,
  retrievalDebug: EMPTY_RETRIEVAL_DEBUG as RetrievalDebug,
  sources: [],
  ...overrides,
});

export const buildAppliedInstruction = (overrides: Partial<AppliedInstruction> = {}): AppliedInstruction => ({
  id: "instruction-1",
  title: "Instruction",
  category: "system",
  scopeLevel: "chat_scenario",
  revision: 1,
  ...overrides,
});
