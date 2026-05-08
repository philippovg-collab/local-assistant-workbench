import type {
  KnowledgePresetSummary,
  KnowledgeScope,
  RetrievalFilters,
  RetrievalQueryHints,
} from "@/types";
import {
  formatRetrievalFilterValue,
  hasRetrievalFilterValue,
  type RetrievalFilterKey,
} from "@/utils/retrievalHints";

export type SourceTarget = {
  materialId: string;
  chunkId?: string | null;
  chunkIndex?: number | null;
  page?: number | null;
};

export type RetrievalFilterChip = {
  key: RetrievalFilterKey;
  label: string;
  value: string;
};

export const retrievalFilterLabels: Record<RetrievalFilterKey, string> = {
  documentNumber: "Номер документа",
  documentDateFrom: "Дата с",
  documentDateTo: "Дата по",
  department: "Подразделение",
  project: "Проект",
  counterparty: "Контрагент",
  businessStatus: "Статус",
  language: "Язык",
  tags: "Теги / ключевые слова",
  sourceTrustMin: "Минимальное доверие",
  documentTypes: "Тип документа",
  documentStatuses: "Статус документа",
  projectKeys: "Проект",
  languageCodes: "Язык документа",
  periodStartFrom: "Действует с: от",
  periodStartTo: "Действует с: до",
  periodEndFrom: "Действует по: от",
  periodEndTo: "Действует по: до",
};

export const parseSourceTarget = (materialId: string, openSourceUrl?: string | null): SourceTarget => {
  if (!openSourceUrl) {
    return {
      materialId,
      chunkId: null,
      chunkIndex: null,
      page: null,
    };
  }

  try {
    const parsed = new URL(openSourceUrl, window.location.origin);
    const pathParts = parsed.pathname.split("/").filter(Boolean);
    const resolvedMaterialId = pathParts[pathParts.length - 1] ?? materialId;
    const chunkIndexRaw = parsed.searchParams.get("chunkIndex");
    const pageRaw = parsed.searchParams.get("page");

    return {
      materialId: resolvedMaterialId,
      chunkId: parsed.searchParams.get("chunkId"),
      chunkIndex: chunkIndexRaw ? Number.parseInt(chunkIndexRaw, 10) : null,
      page: pageRaw ? Number.parseInt(pageRaw, 10) : null,
    };
  } catch {
    return {
      materialId,
      chunkId: null,
      chunkIndex: null,
      page: null,
    };
  }
};

export const buildScopeSummary = ({
  knowledgeScope,
  activeRagProjectKey,
  activeRagProjectName,
  visibleKnowledgePresets,
  visibleKnowledgeFacets,
}: {
  knowledgeScope: KnowledgeScope;
  activeRagProjectKey: string;
  activeRagProjectName: string;
  visibleKnowledgePresets: KnowledgePresetSummary[];
  visibleKnowledgeFacets: KnowledgePresetSummary[];
}) => {
  const projectLabel = activeRagProjectName
    ? `${activeRagProjectName} (${activeRagProjectKey || "general"})`
    : activeRagProjectKey || "general";
  const parts = [`RAG-проект: ${projectLabel}`];
  const selectedPresets = visibleKnowledgePresets
    .filter((preset) => knowledgeScope.presetIds.includes(preset.id))
    .map((preset) => preset.name);

  if (selectedPresets.length > 0) {
    parts.push(`presets: ${selectedPresets.join(", ")}`);
  }
  const selectedFacets = visibleKnowledgeFacets
    .filter((facet) => (knowledgeScope.facetIds ?? []).includes(facet.id))
    .map((facet) => facet.name);
  if (selectedFacets.length > 0) {
    parts.push(`фасеты: ${selectedFacets.join(", ")}`);
  }
  if ((knowledgeScope.tags ?? []).length > 0) {
    parts.push(`теги: ${knowledgeScope.tags.join(", ")}`);
  }
  if (knowledgeScope.uploadedTodayOnly) {
    parts.push("только загруженные сегодня");
  }

  return `Запрос будет искать внутри активного корпуса: ${parts.join(" · ")}.`;
};

export const buildRetrievalFilterChips = (
  fields: RetrievalFilterKey[],
  filters: RetrievalFilters,
): RetrievalFilterChip[] =>
  fields
    .filter((key) => hasRetrievalFilterValue(filters[key]))
    .map((key) => ({
      key,
      label: retrievalFilterLabels[key],
      value: formatRetrievalFilterValue(key, filters[key]),
    }));

export const buildRetrievalFilterLines = (filters: RetrievalFilters): string[] =>
  Object.entries(filters)
    .filter(([, value]) => hasRetrievalFilterValue(value))
    .map(([key, value]) =>
      `${retrievalFilterLabels[key as RetrievalFilterKey] ?? key}: ${formatDisplayValue(value)}`);

export const buildQueryHintLines = (queryHints: RetrievalQueryHints): string[] =>
  Object.entries(queryHints)
    .filter(([, value]) => hasDisplayValue(value))
    .map(([key, value]) => `${key}: ${formatDisplayValue(value)}`);

const hasDisplayValue = (value: unknown): boolean => {
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  if (value === null || value === undefined) {
    return false;
  }
  if (typeof value === "string") {
    return value.trim().length > 0;
  }
  return true;
};

const formatDisplayValue = (value: unknown): string => (
  Array.isArray(value) ? value.join(", ") : String(value)
);
