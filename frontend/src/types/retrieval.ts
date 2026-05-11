import type {
  DocumentStatus,
  DocumentType,
  MaterialLanguageCode,
  MaterialVersionState,
  SourceTrustLevel,
  VersionSelectionMode,
} from "../generated/api-types";
import type { QualityLayerFlags } from "./health";

export type SupportVerdict = "none" | "weak" | "sufficient";

export type RetrievalFilters = {
  documentNumber?: string | null;
  documentDateFrom?: string | null;
  documentDateTo?: string | null;
  versionLabel?: string | null;
  effectiveDate?: string | null;
  versionSelectionMode?: VersionSelectionMode | null;
  versionState?: MaterialVersionState | null;
  uploadedAfterInclusive?: string | null;
  uploadedBeforeExclusive?: string | null;
  department?: string | null;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  language?: string | null;
  tags?: string[];
  sourceTrustMin?: SourceTrustLevel | null;
  documentTypes?: DocumentType[];
  documentStatuses?: DocumentStatus[];
  projectKeys?: string[];
  languageCodes?: MaterialLanguageCode[];
  periodStartFrom?: string | null;
  periodStartTo?: string | null;
  periodEndFrom?: string | null;
  periodEndTo?: string | null;
};

export type RetrievalFilterKey = keyof RetrievalFilters;

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
  documentTypes?: DocumentType[];
  documentStatuses?: DocumentStatus[];
  projectKeys?: string[];
  languageCodes?: MaterialLanguageCode[];
  periodStartFrom?: string | null;
  periodStartTo?: string | null;
  periodEndFrom?: string | null;
  periodEndTo?: string | null;
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

export type EvidenceLocator = {
  sourceKey?: string | null;
  materialId?: string | null;
  documentNumber?: string | null;
  versionLabel?: string | null;
  versionState?: MaterialVersionState | null;
  lineageVersion?: number | null;
  chunkIndex?: number | null;
  page?: number | null;
  sectionPath?: string[] | null;
  headingTrail?: string[] | null;
  tableId?: string | null;
  slideId?: string | null;
  rowKey?: string | null;
  columnKey?: string | null;
  spanStart?: number | null;
  spanEnd?: number | null;
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
  referenceInstant?: string | null;
  effectiveDate?: string | null;
  uploadedAfterInclusive?: string | null;
  uploadedBeforeExclusive?: string | null;
  retrievalConfigHash?: string | null;
  lexicalProvider?: string | null;
  embeddingModel?: string | null;
  chunkProfile?: string | null;
  semanticCandidateCount: number;
  lexicalCandidateCount: number;
  rerankCandidateCount: number;
  finalChunkCount: number;
  supportVerdict: SupportVerdict;
  relevanceProfile: string;
  activeRolloutFlags: QualityLayerFlags;
  appliedCapabilities: string[];
  suppressedCapabilities?: string[];
};
