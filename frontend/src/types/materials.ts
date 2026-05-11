import type {
  DocumentStatus,
  DocumentType,
  KnowledgeDocumentClass,
  MaterialEnrichmentState,
  MaterialLanguageCode,
  MaterialVersionState,
  MetadataValueOrigin,
  SourceTrustLevel,
} from "../generated/api-types";

export type MaterialMetadataProvenance = {
  fieldOrigins: Record<string, MetadataValueOrigin>;
  fieldConfidence: Record<string, number>;
};

export type MaterialMetadata = {
  documentType: DocumentType;
  documentStatus: DocumentStatus;
  workspaceKey?: string | null;
  projectKey?: string | null;
  documentNumber?: string | null;
  languageCode?: MaterialLanguageCode | null;
  manualTags?: string[];
  autoTags?: string[];
  effectiveTags?: string[];
  periodStart?: string | null;
  periodEnd?: string | null;
  tags?: string[];
  knowledgeDocumentClass?: KnowledgeDocumentClass;
  documentDate?: string | null;
  author?: string | null;
  department?: string | null;
  versionLabel?: string | null;
  language?: string | null;
  sourceTrust?: SourceTrustLevel | null;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
  provenance: MaterialMetadataProvenance;
};

export type MaterialMetadataInput = {
  workspaceKey?: string | null;
  documentType?: DocumentType;
  documentStatus?: DocumentStatus;
  projectKey?: string | null;
  documentNumber?: string | null;
  languageCode?: MaterialLanguageCode | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  manualTags?: string[];
};

export type MaterialLineageOverrideInput = {
  lineageKey: string;
  reason: string;
  confirmSupersedeExistingLineage?: boolean;
};

export type MaterialLineageOverrideInfo = {
  lineageKey: string;
  sourceKey: string;
  active: boolean;
  reason?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
};

export type CompatibilityMetadataInput = {
  knowledgeDocumentClass?: KnowledgeDocumentClass;
  documentDate?: string | null;
  author?: string | null;
  department?: string | null;
  versionLabel?: string | null;
  language?: string | null;
  tags?: string[];
  sourceTrust?: SourceTrustLevel | null;
  project?: string | null;
  counterparty?: string | null;
  businessStatus?: string | null;
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
  enrichmentStatus?: MaterialEnrichmentStatus | null;
};

export type MaterialEnrichmentStatus = {
  taskId?: string | null;
  status?: MaterialEnrichmentState | null;
  attempts: number;
  nextRetryAt?: string | null;
  failureCode?: string | null;
  failureMessage?: string | null;
  resultCode?: string | null;
  updatedAt?: string | null;
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
  lineageOverride?: MaterialLineageOverrideInfo | null;
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
  lineageOverride?: MaterialLineageOverrideInput;
};

export type MaterialVersionUploadInput = {
  file: File;
  title?: string;
  metadata?: MaterialMetadataInput;
};

export type UpdateMaterialInput = {
  title: string;
  content: string;
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
  sourceKey?: string | null;
  lineageVersion?: number | null;
  lineageOverride?: MaterialLineageOverrideInfo | null;
  enrichmentStatus?: MaterialEnrichmentStatus | null;
};
