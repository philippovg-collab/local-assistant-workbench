import type {
  AnswerMode,
  ChatMode,
  DocumentBlockType,
  RetrievalQueryResolutionDecision,
} from "../generated/api-types";
import type {
  AppliedInstruction,
  InstructionTraceEntry,
} from "./instructions";
import type {
  KnowledgeScope,
  KnowledgeScopeResolved,
} from "./knowledge";
import type { MaterialMetadata } from "./materials";
import type {
  ChunkScoreBreakdown,
  RetrievalDebug,
  RetrievalFilterKey,
  RetrievalFilters,
  RetrievalTrace,
} from "./retrieval";

export type ChatSource = {
  materialId: string;
  chunkId: string;
  title: string;
  excerpt: string;
  score: number;
  confidence: number;
  matchedTerms: string[];
  openSourceUrl?: string | null;
  chunkIndex?: number | null;
  page?: number | null;
  extractor?: string | null;
  ocrUsed?: boolean;
  chunkType?: DocumentBlockType;
  metadata?: MaterialMetadata;
  semanticDistance?: number | null;
  lexicalScore?: number | null;
  scoreBreakdown?: ChunkScoreBreakdown | null;
};

export type ChatAuditRunSummary = {
  id: string;
  mode: ChatMode;
  model: string;
  answerMode?: AnswerMode | null;
  promptPreview: string;
  answerPreview: string;
  createdAt: string;
  status?: ChatRunStatus | string | null;
  failureStage?: string | null;
  failureCode?: string | null;
  failedAt?: string | null;
  latencyMsTotal?: number | null;
  workspaceKey?: string | null;
};

export type ChatAuditRunDetail = {
  id: string;
  mode: ChatMode;
  model: string;
  prompt: string;
  answer: string;
  contextStatus?: "ready" | "no-context" | null;
  answerMode?: AnswerMode | null;
  createdAt: string;
  instructionTrace: InstructionTraceEntry[];
  knowledgeScopeResolved: KnowledgeScopeResolved;
  retrievalTrace: RetrievalTrace;
  sources: ChatSource[];
  status?: ChatRunStatus | string | null;
  failureStage?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  completedAt?: string | null;
  failedAt?: string | null;
  latencyMsTotal?: number | null;
};

export type ChatRunStatus =
  | "RECEIVED"
  | "PROMPT_RESOLVED"
  | "RETRIEVAL_DONE"
  | "LLM_DONE"
  | "POSTPROCESSED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type ChatRunSubmissionResponse = {
  id: string;
  status: ChatRunStatus | string;
  createdAt: string;
  statusUrl?: string;
  traceUrl: string;
  resultUrl: string;
  conversationId?: string | null;
  turnNo?: number | null;
  cancelUrl?: string | null;
};

export type ChatRunStatusResponse = {
  id: string;
  status: ChatRunStatus | string;
  createdAt: string;
  completedAt?: string | null;
  failedAt?: string | null;
  latencyMsTotal?: number | null;
  failureStage?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
};

export type ChatRunMessage = {
  role: string;
  content: string;
};

export type ChatRunRequestSnapshot = {
  requestPayload?: unknown;
  normalizedRequestPayload?: unknown;
  prompt: string;
  knowledgeScope?: unknown;
  retrievalFilters?: unknown;
};

export type PromptPolicySnapshot = {
  baseSystemPrompt?: string | null;
  systemInstructionsText?: string | null;
  safetyInstructionsText?: string | null;
  contextInstructionsText?: string | null;
  userInstructionsText?: string | null;
  temporaryInstructionText?: string | null;
  answerModeBlockText?: string | null;
  groundingBlockText?: string | null;
  resolvedSystemPrompt?: string | null;
  messages: ChatRunMessage[];
  promptHash?: string | null;
  instructionTrace: InstructionTraceEntry[];
  knowledgeScopeResolved: KnowledgeScopeResolved;
  groundingRulesApplied: boolean;
};

export type RetrievalSummaryTrace = {
  retrievalStatus: "DONE" | "NOT_APPLICABLE" | "FAILED" | string;
  trace?: RetrievalTrace | null;
  debug?: RetrievalDebug | null;
  lexicalProvider?: string | null;
  relevanceProfile?: string | null;
  embeddingModel?: string | null;
  chunkProfile?: string | null;
  queryHints?: unknown;
  manualFilters?: unknown;
  effectiveFilters?: unknown;
  rolloutFlags?: unknown;
  appliedCapabilities?: unknown;
};

export type LlmCallTrace = {
  id: string;
  provider: string;
  model?: string | null;
  requestMessages: ChatRunMessage[];
  rawResponseText?: string | null;
  parsedAnswerText?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  latencyMs?: number | null;
  retryCount: number;
  timeoutSeconds?: number | null;
  finishReason?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createdAt: string;
};

export type ChatRunOutputTrace = {
  rawModelAnswer?: string | null;
  finalUserAnswer?: string | null;
  sources: ChatSource[];
  postprocess?: unknown;
  abstained?: boolean | null;
  strictSourcesBlockedAnswer?: boolean | null;
};

export type ChatRunEventTrace = {
  id: string;
  eventType: string;
  eventPayload?: unknown;
  createdAt: string;
};

export type ChatRunTraceDetail = {
  id: string;
  mode: ChatMode;
  status: ChatRunStatus | string;
  requestedModel?: string | null;
  resolvedModel?: string | null;
  requestedAnswerMode?: AnswerMode | null;
  appliedAnswerMode?: AnswerMode | null;
  contextStatus?: "ready" | "no-context" | string | null;
  createdAt: string;
  completedAt?: string | null;
  failedAt?: string | null;
  latencyMsTotal?: number | null;
  failureStage?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  requestSnapshot?: ChatRunRequestSnapshot | null;
  promptSnapshot?: PromptPolicySnapshot | null;
  retrievalSummary?: RetrievalSummaryTrace | null;
  llmCalls: LlmCallTrace[];
  output?: ChatRunOutputTrace | null;
  events: ChatRunEventTrace[];
};

export type ChatExecutionRequest = {
  mode?: ChatMode | null;
  model?: string | null;
  prompt?: string | null;
  systemPrompt?: string;
  instructionIds?: string[] | null;
  answerMode?: AnswerMode;
  knowledgeScope?: KnowledgeScope;
  instructionWorkspaceKey?: string | null;
  retrievalFilters?: RetrievalFilters;
  dismissedRetrievalHintKeys?: RetrievalFilterKey[];
  scenarioInstructionIds?: string[];
  temporaryInstruction?: string;
  conversationId?: string | null;
  parentRunId?: string | null;
  clientTurnId?: string | null;
  persistConversation?: boolean | null;
  contextDebug?: boolean | null;
  contextOptions?: ContextOptions | null;
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
  answerModeApplied?: AnswerMode | null;
  appliedInstructions: AppliedInstruction[];
  instructionTrace?: InstructionTraceEntry[];
  knowledgeScopeResolved?: KnowledgeScopeResolved;
  retrievalTrace?: RetrievalTrace;
  retrievalDebug?: RetrievalDebug | null;
  sources: ChatSource[];
  auditRunId?: string | null;
  conversationId?: string | null;
  turnNo?: number | null;
  contextAssemblyId?: string | null;
  contextSummary?: ContextSummary | null;
};

export type ContextOptions = {
  includeHistory?: boolean | null;
  useHistory?: boolean | null;
  useStickyState?: boolean | null;
  resolveRetrievalQuery?: boolean | null;
  useSummary?: boolean | null;
  useLongTermMemory?: boolean | null;
  maxHistoryTurns?: number | null;
  maxContextTokens?: number | null;
};

export type ContextSummary = {
  status?: string | null;
  historyTurns?: number | null;
  droppedItems?: number | null;
  estimatedTokens?: number | null;
  degraded?: boolean | null;
  resolvedRetrievalQuery?: string | null;
  degradedReason?: string | null;
  retrievalQueryResolution?: RetrievalQueryResolutionSummary | null;
  summaryUsed?: boolean | null;
  summaryStatus?: string | null;
  summaryThroughTurnNo?: number | null;
  summaryDegradedReason?: string | null;
};

export type RetrievalQueryResolutionSummary = {
  decision?: RetrievalQueryResolutionDecision | null;
  confidence?: number | null;
  degraded?: boolean | null;
  degradedReason?: string | null;
};

export type RetrievalQueryReferencedSource = {
  sourceIndex?: number | null;
  documentIndex?: number | null;
  materialId?: string | null;
  title?: string | null;
  documentNumber?: string | null;
  project?: string | null;
  counterparty?: string | null;
  page?: number | null;
};

export type RetrievalQueryResolution = {
  originalQuery?: string | null;
  queryForRetrieval?: string | null;
  resolvedQuery?: string | null;
  decision?: RetrievalQueryResolutionDecision | null;
  confidence?: number | null;
  markers?: string[] | null;
  referencedRunIds?: string[] | null;
  referencedSources?: RetrievalQueryReferencedSource[] | null;
  degraded?: boolean | null;
  degradedReason?: string | null;
};

export type ContextAssemblyHistoryItem = {
  runId?: string | null;
  turnNo?: number | null;
  role?: "user" | "assistant" | string | null;
  content?: string | null;
  estimatedTokens?: number | null;
  createdAt?: string | null;
};

export type ContextAssemblyDroppedItem = {
  runId?: string | null;
  turnNo?: number | null;
  reason?: string | null;
  estimatedTokens?: number | null;
};

export type MemoryEntryStatus = "pending_review" | "approved" | "rejected" | "deleted" | string;

export type MemoryEntryType =
  | "user_preference"
  | "user_alias"
  | "workspace_note"
  | "project_note"
  | "pinned_user_fact"
  | string;

export type MemoryEntryRequest = {
  entryType?: MemoryEntryType | null;
  contentText?: string | null;
  normalizedKey?: string | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  confidence?: number | null;
  provenance?: Record<string, unknown> | null;
  reason?: string | null;
};

export type MemoryEntryUpdateRequest = {
  entryType?: MemoryEntryType | null;
  contentText?: string | null;
  normalizedKey?: string | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  confidence?: number | null;
  reason?: string | null;
};

export type MemoryReviewActionRequest = {
  reason?: string | null;
};

export type MemoryEntryResponse = {
  id: string;
  status: MemoryEntryStatus;
  entryType: MemoryEntryType;
  contentText?: string | null;
  normalizedKey?: string | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  confidence?: number | null;
  provenance?: Record<string, unknown> | null;
  sourceConversationId?: string | null;
  sourceRunId?: string | null;
  sourceTurnNo?: number | null;
  sourceTextPreview?: string | null;
  sourceTextHash?: string | null;
  approvedAt?: string | null;
  rejectedAt?: string | null;
  deletedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ContextAssemblyMemoryItem = {
  id?: string | null;
  entryType?: MemoryEntryType | null;
  contentText?: string | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  confidence?: number | null;
  estimatedTokens?: number | null;
  updatedAt?: string | null;
};

export type ContextAssemblyDroppedMemoryItem = {
  id?: string | null;
  entryType?: MemoryEntryType | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  reason?: string | null;
  estimatedTokens?: number | null;
};

export type ContextTokenBudget = {
  maxHistoryTurns?: number | null;
  maxHistoryTokens?: number | null;
  selectedHistoryTurns?: number | null;
  selectedHistoryTokens?: number | null;
  droppedHistoryItems?: number | null;
  droppedHistoryTokens?: number | null;
};

export type ContextAssemblySnapshotDetail = {
  id?: string | null;
  runId?: string | null;
  conversationId?: string | null;
  turnNo?: number | null;
  assemblyMode?: ChatMode | null;
  originalPrompt?: string | null;
  resolvedRetrievalQuery?: string | null;
  selectedHistory?: ContextAssemblyHistoryItem[] | null;
  droppedItems?: ContextAssemblyDroppedItem[] | null;
  selectedMemory?: ContextAssemblyMemoryItem[] | null;
  droppedMemory?: ContextAssemblyDroppedMemoryItem[] | null;
  tokenBudget?: ContextTokenBudget | null;
  finalMessagesHash?: string | null;
  degradedMode?: boolean | null;
  retrievalQueryResolution?: RetrievalQueryResolution | null;
  stickyResolution?: Record<string, unknown> | null;
  summaryUsed?: boolean | null;
  summaryThroughTurnNo?: number | null;
  summaryStatus?: string | null;
  summaryTokenEstimate?: number | null;
  summaryDegradedReason?: string | null;
  memoryStatus?: string | null;
  memoryDegradedReason?: string | null;
  createdAt?: string | null;
};

export type ChatRunContextFeatureState = {
  context?: boolean | null;
  conversations?: boolean | null;
  history?: boolean | null;
  sticky?: boolean | null;
  rewrite?: boolean | null;
  summary?: boolean | null;
  longTermMemory?: boolean | null;
};

export type ChatRunContextLinks = {
  run?: string | null;
  status?: string | null;
  trace?: string | null;
  result?: string | null;
  conversationRuns?: string | null;
};

export type ChatRunContextHistoryItem = {
  runId?: string | null;
  turnNo?: number | null;
  mode?: ChatMode | null;
  promptPreview?: string | null;
  answerPreview?: string | null;
  status?: string | null;
  tokenEstimate?: number | null;
  links?: ChatRunContextLinks | null;
};

export type ChatRunContextDroppedItem = {
  itemType?: string | null;
  runId?: string | null;
  turnNo?: number | null;
  reason?: string | null;
  tokenEstimate?: number | null;
  links?: ChatRunContextLinks | null;
};

export type ChatRunContextMemoryItem = {
  id?: string | null;
  entryType?: MemoryEntryType | null;
  contentPreview?: string | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  tokenEstimate?: number | null;
};

export type ChatRunContextDroppedMemoryItem = {
  id?: string | null;
  entryType?: MemoryEntryType | null;
  workspaceKey?: string | null;
  projectKey?: string | null;
  pinned?: boolean | null;
  reason?: string | null;
  tokenEstimate?: number | null;
};

export type ChatRunContextSummaryState = {
  used?: boolean | null;
  status?: string | null;
  throughTurnNo?: number | null;
  tokenEstimate?: number | null;
  degradedReason?: string | null;
};

export type ChatRunContextMemoryState = {
  enabled?: boolean | null;
  requested?: boolean | null;
  status?: string | null;
  degradedReason?: string | null;
};

export type ChatRunContextTokenBudget = {
  max?: number | null;
  used?: number | null;
  remaining?: number | null;
  history?: number | null;
  summary?: number | null;
  memory?: number | null;
  retrieval?: number | null;
  dropped?: number | null;
};

export type ChatRunContextDetail = {
  status?: "AVAILABLE" | "DEGRADED" | "EXPIRED" | "DISABLED" | "NOT_AVAILABLE" | string | null;
  runId?: string | null;
  conversationId?: string | null;
  turnNo?: number | null;
  contextAssemblyId?: string | null;
  createdAt?: string | null;
  featureState?: ChatRunContextFeatureState | null;
  promptPreview?: string | null;
  selectedHistory?: ChatRunContextHistoryItem[] | null;
  droppedItems?: ChatRunContextDroppedItem[] | null;
  selectedMemory?: ChatRunContextMemoryItem[] | null;
  droppedMemory?: ChatRunContextDroppedMemoryItem[] | null;
  stickyStateResolution?: Record<string, unknown> | null;
  retrievalQueryResolution?: RetrievalQueryResolution | null;
  summaryState?: ChatRunContextSummaryState | null;
  memoryState?: ChatRunContextMemoryState | null;
  tokenBudget?: ChatRunContextTokenBudget | null;
  links?: ChatRunContextLinks | null;
  reasonCode?: string | null;
  reasonMessage?: string | null;
};

export type ConversationStickyState = {
  model?: string | null;
  answerMode?: AnswerMode | null;
  instructionWorkspaceKey?: string | null;
  knowledgeScope?: KnowledgeScope | null;
  retrievalFilters?: RetrievalFilters | null;
  instructionIds?: string[] | null;
  scenarioInstructionIds?: string[] | null;
  updatedFromRunId?: string | null;
  updatedThroughTurnNo?: number | null;
  version?: number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ConversationCreateRequest = {
  workspaceKey?: string | null;
  title?: string | null;
  mode?: ChatMode | null;
  defaultModel?: string | null;
  defaultAnswerMode?: AnswerMode | null;
};

export type ConversationPatchRequest = {
  title?: string | null;
  status?: "ACTIVE" | "ARCHIVED" | string | null;
};

export type ConversationSummary = {
  id: string;
  workspaceKey?: string | null;
  title: string;
  mode: ChatMode;
  status: "ACTIVE" | "ARCHIVED" | string;
  defaultModel?: string | null;
  defaultAnswerMode?: AnswerMode | null;
  createdAt: string;
  updatedAt: string;
  lastRunAt?: string | null;
  turnCount: number;
};

export type ConversationDetail = ConversationSummary & {
  stickyState?: ConversationStickyState | null;
};

export type ConversationRunDetail = {
  conversationId: string;
  runId: string;
  turnNo: number;
  parentRunId?: string | null;
  clientTurnId?: string | null;
  userPrompt: string;
  contextAssemblyId?: string | null;
  contextAssemblyStatus?: "NONE" | "AVAILABLE" | "EXPIRED" | string | null;
  createdAt: string;
  status: ChatRunStatus | string;
  completedAt?: string | null;
  failedAt?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  statusUrl: string;
  traceUrl: string;
  resultUrl: string;
  cancelUrl?: string | null;
};
