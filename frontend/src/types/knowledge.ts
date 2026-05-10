import type {
  DocumentStatus,
  DocumentType,
  KnowledgeDocumentClass,
  MaterialLanguageCode,
} from "../generated/api-types";
import type { RevisionDiffEntry } from "./instructions";

export type KnowledgeScope = {
  presetIds: string[];
  facetIds: string[];
  documentClasses: KnowledgeDocumentClass[];
  documentTypes: DocumentType[];
  documentStatuses: DocumentStatus[];
  projectKeys: string[];
  documentNumber?: string | null;
  languageCodes: MaterialLanguageCode[];
  tags: string[];
  workspaceKey?: string | null;
  periodStartFrom?: string | null;
  periodStartTo?: string | null;
  periodEndFrom?: string | null;
  periodEndTo?: string | null;
  uploadedTodayOnly: boolean;
};

export type KnowledgePresetReference = {
  id: string;
  name: string;
  revision: number;
};

export type KnowledgeScopeResolved = {
  presets: KnowledgePresetReference[];
  facets: KnowledgePresetReference[];
  documentClasses: KnowledgeDocumentClass[];
  documentTypes: DocumentType[];
  documentStatuses: DocumentStatus[];
  projectKeys: string[];
  documentNumber?: string | null;
  languageCodes: MaterialLanguageCode[];
  tags: string[];
  workspaceKey?: string | null;
  periodStartFrom?: string | null;
  periodStartTo?: string | null;
  periodEndFrom?: string | null;
  periodEndTo?: string | null;
  uploadedTodayOnly: boolean;
};

export type KnowledgePresetSummary = {
  id: string;
  kind?: "PRESET" | "FACET";
  name: string;
  description?: string | null;
  workspaceKey?: string | null;
  revision: number;
  active: boolean;
  createdAt: string;
  updatedAt?: string;
};

export type KnowledgePresetDetail = KnowledgePresetSummary & {
  scope: KnowledgeScope;
};

export type KnowledgePresetRevisionDiff = {
  presetId: string;
  fromRevision: number;
  toRevision: number;
  changes: RevisionDiffEntry[];
};

export type KnowledgePresetRevisionDetail = {
  presetId: string;
  kind?: "PRESET" | "FACET";
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
  kind?: "PRESET" | "FACET";
  active?: boolean;
};
