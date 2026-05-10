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
  contextFeatures?: {
    context?: boolean | null;
    conversations?: boolean | null;
    history?: boolean | null;
    sticky?: boolean | null;
    rewrite?: boolean | null;
    summary?: boolean | null;
    longTermMemory?: boolean | null;
  } | null;
  activeChatProvider?: {
    id?: string | null;
    name?: string | null;
    providerType?: "OPENAI_COMPATIBLE" | null;
    baseUrl?: string | null;
    status?: "UNKNOWN" | "UP" | "DOWN" | "DEGRADED" | null;
    fallback?: boolean | null;
  } | null;
  activeEmbeddingProvider?: {
    id?: string | null;
    name?: string | null;
    providerType?: "OPENAI_COMPATIBLE" | null;
    baseUrl?: string | null;
    status?: "UNKNOWN" | "UP" | "DOWN" | "DEGRADED" | null;
    fallback?: boolean | null;
  } | null;
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
  indexingOldestPendingAt?: string | null;
  indexingOldestInProgressAt?: string | null;
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
      workspace: {
        covered: number;
        ratio: number;
      };
      documentStatus: {
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
