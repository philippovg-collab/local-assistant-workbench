import type {
  DocumentStatus,
  DocumentType,
  MaterialLanguageCode,
  SourceTrustLevel,
} from "../generated/api-types";
import type { QualityLayerFlags } from "./health";

export type SupportVerdict = "none" | "weak" | "sufficient";

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
  activeRolloutFlags: QualityLayerFlags;
  appliedCapabilities: string[];
  suppressedCapabilities?: string[];
};
