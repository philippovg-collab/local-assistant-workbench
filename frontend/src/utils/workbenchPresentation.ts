import type {
  AnswerMode,
  InstructionCategory,
  InstructionScopeLevel,
  KnowledgeDocumentClass,
  KnowledgeScope,
  KnowledgeScopeResolved,
  RetrievalDebug,
  RetrievalTrace,
  SupportVerdict,
} from "@/types";
import {
  DEFAULT_MATERIAL_METADATA,
  materialMetadataWithDefaults,
} from "@/utils/materialMetadata";

export const DEFAULT_KNOWLEDGE_SCOPE: KnowledgeScope = {
  presetIds: [],
  facetIds: [],
  documentClasses: [],
  documentTypes: [],
  documentStatuses: [],
  projectKeys: [],
  documentNumber: null,
  languageCodes: [],
  tags: [],
  workspaceKey: null,
  periodStartFrom: null,
  periodStartTo: null,
  periodEndFrom: null,
  periodEndTo: null,
  uploadedTodayOnly: false,
};

export const EMPTY_KNOWLEDGE_SCOPE_RESOLVED: KnowledgeScopeResolved = {
  presets: [],
  facets: [],
  documentClasses: [],
  documentTypes: [],
  documentStatuses: [],
  projectKeys: [],
  documentNumber: null,
  languageCodes: [],
  tags: [],
  workspaceKey: null,
  periodStartFrom: null,
  periodStartTo: null,
  periodEndFrom: null,
  periodEndTo: null,
  uploadedTodayOnly: false,
};

export const EMPTY_RETRIEVAL_TRACE: RetrievalTrace = {
  totalMaterials: 0,
  totalActiveMaterials: 0,
  totalReadyMaterials: 0,
  scopedMaterials: 0,
  scopedActiveMaterials: 0,
  scopedReadyMaterials: 0,
  semanticCandidates: 0,
  lexicalCandidates: 0,
  finalChunks: 0,
  supportVerdict: "none",
};

export const EMPTY_RETRIEVAL_DEBUG: RetrievalDebug = {
  queryHints: {},
  manualFilters: {
    documentNumber: null,
    documentDateFrom: null,
    documentDateTo: null,
    department: null,
    project: null,
    counterparty: null,
    businessStatus: null,
    language: null,
    tags: [],
    sourceTrustMin: null,
    documentTypes: [],
    documentStatuses: [],
    projectKeys: [],
    languageCodes: [],
    periodStartFrom: null,
    periodStartTo: null,
    periodEndFrom: null,
    periodEndTo: null,
  },
  effectiveFilters: {
    documentNumber: null,
    documentDateFrom: null,
    documentDateTo: null,
    department: null,
    project: null,
    counterparty: null,
    businessStatus: null,
    language: null,
    tags: [],
    sourceTrustMin: null,
    documentTypes: [],
    documentStatuses: [],
    projectKeys: [],
    languageCodes: [],
    periodStartFrom: null,
    periodStartTo: null,
    periodEndFrom: null,
    periodEndTo: null,
  },
  semanticCandidateCount: 0,
  lexicalCandidateCount: 0,
  rerankCandidateCount: 0,
  finalChunkCount: 0,
  supportVerdict: "none",
  relevanceProfile: "hybrid-rerank-v1",
  activeRolloutFlags: {
    metadataV1: false,
    structuredV1: false,
    metadataFiltersV1: false,
    searchApiV1: false,
    rerankerV1: false,
    queryHintsV1: false,
  },
  appliedCapabilities: [],
};

export const instructionScopeLabels: Record<InstructionScopeLevel, string> = {
  assistant_system: "Системная роль ассистента",
  workspace_project: "Инструкция workspace / проекта",
  chat_scenario: "Инструкция сценария / чата",
  request_temporary: "Временная инструкция на запрос",
};

export const instructionCategoryLabels: Record<InstructionCategory, string> = {
  system: "Системная",
  user: "Пользовательская",
  context: "Контекстная",
  safety: "Безопасность",
};

export const knowledgeDocumentClassLabels: Record<KnowledgeDocumentClass, string> = {
  contracts: "Договоры",
  regulations: "Регламенты",
  correspondence: "Переписка",
  techdocs: "Техдоки",
  other: "Прочее",
};

export const answerModeLabels: Record<AnswerMode, string> = {
  brief: "Кратко",
  with_quotes: "С цитатами",
  documents_only: "Только по документам",
  broader_reasoning: "Можно рассуждать шире",
  strict_sources_only: "Строгий режим",
};

export const answerModeDescriptions: Record<AnswerMode, string> = {
  brief: "Компактный ответ без лишних развилок.",
  with_quotes: "Подтягивает короткие цитаты и опирается на найденные фрагменты.",
  documents_only: "Ограничивает ответ загруженными документами и retrieval-контекстом.",
  broader_reasoning: "Позволяет шире объяснять ответ поверх найденных источников.",
  strict_sources_only: "Если подтверждения в источниках нет, система говорит, что не найдено.",
};

export const supportVerdictLabels: Record<SupportVerdict, string> = {
  none: "нет опоры",
  weak: "слабая опора",
  sufficient: "достаточная опора",
};

export const instructionScopeOrder: InstructionScopeLevel[] = [
  "assistant_system",
  "workspace_project",
  "chat_scenario",
  "request_temporary",
];

export const knowledgeScopeResolvedWithDefaults = (
  scope?: Partial<KnowledgeScopeResolved> | null,
): KnowledgeScopeResolved => ({
  ...EMPTY_KNOWLEDGE_SCOPE_RESOLVED,
  ...scope,
  presets: scope?.presets ?? [],
  facets: scope?.facets ?? [],
  documentClasses: scope?.documentClasses ?? [],
  documentTypes: scope?.documentTypes ?? [],
  documentStatuses: scope?.documentStatuses ?? [],
  projectKeys: scope?.projectKeys ?? [],
  languageCodes: scope?.languageCodes ?? [],
  tags: scope?.tags ?? [],
});

export const retrievalTraceWithDefaults = (trace?: Partial<RetrievalTrace> | null): RetrievalTrace => ({
  ...EMPTY_RETRIEVAL_TRACE,
  ...trace,
});

export const knowledgeScopeWithDefaults = (scope?: Partial<KnowledgeScope> | null): KnowledgeScope => ({
  ...DEFAULT_KNOWLEDGE_SCOPE,
  ...scope,
  presetIds: scope?.presetIds ?? [],
  facetIds: scope?.facetIds ?? [],
  documentClasses: scope?.documentClasses ?? [],
  documentTypes: scope?.documentTypes ?? [],
  documentStatuses: scope?.documentStatuses ?? [],
  projectKeys: scope?.projectKeys ?? [],
  languageCodes: scope?.languageCodes ?? [],
  tags: scope?.tags ?? [],
});

export const formatScopeTarget = (scopeTargetId?: string | null) =>
  scopeTargetId && scopeTargetId.trim().length > 0 ? scopeTargetId : "default";
