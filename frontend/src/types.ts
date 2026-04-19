export type ChatMode = "direct" | "rag";
export type AnswerMode =
  | "brief"
  | "with_quotes"
  | "documents_only"
  | "broader_reasoning"
  | "strict_sources_only";
export type SupportVerdict = "none" | "weak" | "sufficient";
export type InstructionCategory = "system" | "user" | "context" | "safety";
export type InstructionScopeLevel =
  | "assistant_system"
  | "workspace_project"
  | "chat_scenario"
  | "request_temporary";
export type MaterialVersionState = "ACTIVE" | "SUPERSEDED";
export type KnowledgeDocumentClass = "contracts" | "regulations" | "correspondence" | "techdocs" | "other";
export type DocumentType =
  | "POLICY"
  | "CONTRACT"
  | "REPORT"
  | "PROCEDURE"
  | "PRESENTATION"
  | "SPREADSHEET"
  | "LETTER"
  | "MANUAL"
  | "FAQ"
  | "OTHER";
export type SourceTrustLevel = "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN";
export type MetadataValueOrigin = "MANUAL" | "INFERRED" | "DEFAULT";
export type DocumentBlockType =
  | "TITLE"
  | "NARRATIVE"
  | "TABLE"
  | "LIST"
  | "QA"
  | "APPENDIX"
  | "SLIDE"
  | "CAPTION";

export type QualityLayerFlags = {
  metadataV1: boolean;
  structuredV1: boolean;
  metadataFiltersV1: boolean;
  searchApiV1: boolean;
  rerankerV1: boolean;
  queryHintsV1: boolean;
};

export type HealthResponse = {
  application: string;
  status: string;
  timestamp: string;
  runtimeCachedAt?: string;
  directStatus?: "UP" | "DOWN";
  directReasonCode?: string | null;
  directReasonMessage?: string | null;
  directLastSuccessfulProbeAt?: string | null;
  ragStatus?: "UP" | "DOWN";
  knowledgeStatus?: "EMPTY" | "HISTORICAL_ONLY" | "INDEXING" | "READY" | "DEGRADED";
  knowledgeReasonCode?: string | null;
  knowledgeReasonMessage?: string | null;
  materialCount?: number;
  activeMaterialCount?: number;
  historicalMaterialCount?: number;
  readyMaterialCount?: number;
  searchStatus?: "UP" | "DEGRADED" | "DOWN" | "DISABLED" | "FALLBACK_ACTIVE";
  searchMode?: "postgres" | "elasticsearch" | "auto";
  searchProvider?: string;
  searchReasonCode?: string | null;
  searchReasonMessage?: string | null;
  searchSyncBacklog?: {
    pendingCount?: number;
    inProgressCount?: number;
    failedCount?: number;
    nextRetryAt?: string | null;
    oldestOutstandingAt?: string | null;
    lastSuccessfulSyncAt?: string | null;
  } | null;
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
  qualityLayer?: {
    flags: QualityLayerFlags;
    metadataCoverage: {
      activeTotal: number;
      activeWithEffectiveMetadata: number;
      ratio: number;
      documentType: {
        covered: number;
        ratio: number;
      };
      sourceTrust: {
        covered: number;
        ratio: number;
      };
      authorOrDepartment: {
        covered: number;
        ratio: number;
      };
    };
    activeBackfillCoverage: {
      activeTotal: number;
      structuredProfileActive: number;
      ratio: number;
      pendingBackfill: number;
      partialReadyActive: number;
    };
    retrievalWindow: {
      sampleSize: number;
      noContextRate: number;
      hitDistributionByChunkType: Record<string, number>;
      rerankerDelta: {
        top1ChangedCount: number;
        top1ImprovedCount: number;
        appendixDemotions: number;
        highTrustPromotions: number;
      };
    };
  } | null;
  readiness?: Record<string, {
    status: "UP" | "DEGRADED" | "DOWN" | "DISABLED" | "FALLBACK_ACTIVE" | "UNKNOWN" | string;
    reasonCode?: string | null;
    reasonMessage?: string | null;
    observedAt?: string | null;
  }>;
};

export type ModelInfo = {
  name: string;
};

export type MaterialMetadataProvenance = {
  fieldOrigins: Record<string, MetadataValueOrigin>;
  fieldConfidence: Record<string, number>;
};

export type MaterialMetadata = {
  documentType: DocumentType;
  knowledgeDocumentClass: KnowledgeDocumentClass;
  documentDate?: string | null;
  documentNumber?: string | null;
  author?: string | null;
  department?: string | null;
  versionLabel?: string | null;
  language?: string | null;
  tags: string[];
  sourceTrust: SourceTrustLevel;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  workspaceKey?: string | null;
  provenance: MaterialMetadataProvenance;
};

export type MaterialMetadataInput = {
  documentType?: DocumentType;
  knowledgeDocumentClass?: KnowledgeDocumentClass;
  documentDate?: string | null;
  documentNumber?: string | null;
  author?: string | null;
  department?: string | null;
  versionLabel?: string | null;
  language?: string | null;
  tags?: string[];
  sourceTrust?: SourceTrustLevel;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  workspaceKey?: string | null;
};

export type RetrievalFilters = {
  documentNumber?: string | null;
  documentDateFrom?: string | null;
  documentDateTo?: string | null;
  department?: string | null;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  language?: string | null;
  tags?: string[];
  sourceTrustMin?: SourceTrustLevel | null;
};

export type RetrievalQueryHints = {
  documentNumber?: string | null;
  documentDateFrom?: string | null;
  documentDateTo?: string | null;
  versionLabel?: string | null;
  language?: string | null;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  department?: string | null;
};

export type ChunkScoreBreakdown = {
  baseRrf: number;
  semanticRankBonus: number;
  lexicalRankBonus: number;
  identifierBonus: number;
  headingBonus: number;
  metadataBonus: number;
  sourceTrustBoost: number;
  appendixPenalty: number;
  boilerplatePenalty: number;
  lowConfidencePenalty: number;
  finalScore: number;
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
  metadata?: MaterialMetadata;
};

export type MaterialListResponse = {
  items: MaterialSummary[];
  total: number;
  offset: number;
  limit: number;
  hasMore: boolean;
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

export type MaterialUploadItemInput = {
  file: File;
  title?: string;
  metadata?: MaterialMetadataInput;
};

export type MaterialChunkDetail = {
  chunkId: string;
  chunkIndex: number;
  text: string;
  page?: number | null;
  extractor?: string | null;
  ocrUsed?: boolean;
};

export type MaterialDetail = {
  id: string;
  title: string;
  sourceType: string;
  originalFileName?: string | null;
  mediaType?: string | null;
  content: string;
  status: MaterialSummary["status"];
  versionState?: MaterialVersionState;
  createdAt: string;
  updatedAt?: string;
  metadata?: MaterialMetadata;
  chunks: MaterialChunkDetail[];
};

export type InstructionSummary = {
  id: string;
  title: string;
  category: InstructionCategory;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
  active?: boolean;
  preview: string;
  createdAt: string;
  updatedAt?: string;
};

export type InstructionDetail = {
  id: string;
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
  active?: boolean;
  createdAt: string;
  updatedAt?: string;
};

export type AppliedInstruction = {
  id: string;
  title: string;
  category: InstructionCategory;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision?: number;
};

export type InstructionTraceEntry = {
  instructionId?: string | null;
  title: string;
  category: InstructionCategory;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId?: string | null;
  revision: number;
  active: boolean;
  temporary: boolean;
  contentPreview: string;
};

export type InstructionRevisionDetail = {
  instructionId: string;
  revision: number;
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId?: string | null;
  active: boolean;
  restoredFromRevision?: number | null;
  createdAt: string;
  updatedAt?: string;
};

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

export type KnowledgeScope = {
  presetIds: string[];
  documentClasses: KnowledgeDocumentClass[];
  tags: string[];
  workspaceKey?: string | null;
  uploadedTodayOnly: boolean;
};

export type KnowledgePresetReference = {
  id: string;
  name: string;
  revision: number;
};

export type KnowledgeScopeResolved = {
  presets: KnowledgePresetReference[];
  documentClasses: KnowledgeDocumentClass[];
  tags: string[];
  workspaceKey?: string | null;
  uploadedTodayOnly: boolean;
};

export type RetrievalTrace = {
  totalMaterials: number;
  totalActiveMaterials: number;
  totalReadyMaterials: number;
  scopedMaterials: number;
  scopedActiveMaterials: number;
  scopedReadyMaterials: number;
  semanticCandidates: number;
  lexicalCandidates: number;
  finalChunks: number;
  supportVerdict: SupportVerdict;
};

export type RetrievalDebug = {
  queryHints: RetrievalQueryHints;
  manualFilters: RetrievalFilters;
  effectiveFilters: RetrievalFilters;
  semanticCandidateCount: number;
  lexicalCandidateCount: number;
  rerankCandidateCount: number;
  finalChunkCount: number;
  supportVerdict: SupportVerdict;
  relevanceProfile: string;
  activeRolloutFlags: {
    metadataV1: boolean;
    structuredV1: boolean;
    metadataFiltersV1: boolean;
    searchApiV1: boolean;
    rerankerV1: boolean;
    queryHintsV1: boolean;
  };
  appliedCapabilities: string[];
};

export type RevisionDiffEntry = {
  field: string;
  fromValue?: string | null;
  toValue?: string | null;
};

export type InstructionRevisionDiff = {
  instructionId: string;
  fromRevision: number;
  toRevision: number;
  changes: RevisionDiffEntry[];
};

export type KnowledgePresetRevisionDiff = {
  presetId: string;
  fromRevision: number;
  toRevision: number;
  changes: RevisionDiffEntry[];
};

export type KnowledgePresetSummary = {
  id: string;
  name: string;
  description?: string | null;
  revision: number;
  active: boolean;
  createdAt: string;
  updatedAt?: string;
};

export type KnowledgePresetDetail = KnowledgePresetSummary & {
  scope: KnowledgeScope;
};

export type KnowledgePresetRevisionDetail = {
  presetId: string;
  revision: number;
  name: string;
  description?: string | null;
  scope: KnowledgeScope;
  active: boolean;
  restoredFromRevision?: number | null;
  createdAt: string;
  updatedAt?: string;
};

export type CreateKnowledgePresetRequest = {
  name: string;
  description?: string | null;
  scope: KnowledgeScope;
  active?: boolean;
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
  mode: ChatMode;
  model: string;
  prompt: string;
  systemPrompt?: string;
  instructionIds: string[];
  answerMode?: AnswerMode;
  knowledgeScope?: KnowledgeScope;
  instructionWorkspaceKey?: string | null;
  retrievalFilters?: RetrievalFilters;
  scenarioInstructionIds?: string[];
  temporaryInstruction?: string;
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
};

export type CreateInstructionRequest = {
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel?: InstructionScopeLevel;
  scopeTargetId?: string | null;
  active?: boolean;
};
