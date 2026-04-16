export type ChatMode = "direct" | "rag";

export type HealthResponse = {
  application: string;
  status: string;
  timestamp: string;
  ocrStatus?: "UP" | "DOWN" | "DISABLED";
  ocrReasonCode?: string;
  ocrReasonMessage?: string;
  ocrLanguages?: string[];
};

export type ModelInfo = {
  name: string;
};

export type MaterialSummary = {
  id: string;
  title: string;
  sourceType: string;
  originalFileName: string | null;
  extractable: boolean;
  createdAt: string;
  contentLength: number;
  preview: string;
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

export type Instruction = {
  id: string;
  title: string;
  category: string;
  content: string;
  createdAt: string;
};

export type AppliedInstruction = {
  id: string;
  title: string;
  category: string;
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
  createdAt: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  appliedInstructions: AppliedInstruction[];
  sources: ChatSource[];
};

export type CreateInstructionRequest = {
  title: string;
  category: string;
  content: string;
};
