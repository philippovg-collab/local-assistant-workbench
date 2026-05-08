import type {
  CreateKnowledgePresetRequest,
  DocumentStatus,
  DocumentType,
  KnowledgePresetDetail,
  KnowledgePresetSummary,
  MaterialLanguageCode,
} from "@/types";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

export type KnowledgePresetFormState = {
  name: string;
  description: string;
  documentTypes: DocumentType[];
  documentStatuses: DocumentStatus[];
  projectKeys: string[];
  documentNumber: string;
  languageCodes: MaterialLanguageCode[];
  tagsText: string;
  workspaceKey: string;
  periodStartFrom: string;
  periodStartTo: string;
  periodEndFrom: string;
  periodEndTo: string;
  uploadedTodayOnly: boolean;
  active: boolean;
};

export const initialPresetForm: KnowledgePresetFormState = {
  name: "",
  description: "",
  documentTypes: [],
  documentStatuses: [],
  projectKeys: [],
  documentNumber: "",
  languageCodes: [],
  tagsText: "",
  workspaceKey: "",
  periodStartFrom: "",
  periodStartTo: "",
  periodEndFrom: "",
  periodEndTo: "",
  uploadedTodayOnly: false,
  active: true,
};

export const documentTypes: DocumentType[] = [
  "POLICY",
  "CONTRACT",
  "REPORT",
  "PROCEDURE",
  "PRESENTATION",
  "SPREADSHEET",
  "LETTER",
  "MANUAL",
  "FAQ",
  "OTHER",
];

export const documentStatuses: DocumentStatus[] = ["ACTIVE", "DRAFT", "ARCHIVED", "REVOKED"];
export const languageCodes: MaterialLanguageCode[] = ["RU", "KK", "EN"];

export const presetDiffFieldLabels: Record<string, string> = {
  name: "Название",
  description: "Описание",
  documentTypes: "Типы документов",
  documentStatuses: "Статусы документов",
  projectKeys: "Проекты",
  documentNumber: "Номер документа",
  languageCodes: "Языки",
  tags: "Теги",
  workspaceKey: "Workspace key",
  periodStartFrom: "Действует с: от",
  periodStartTo: "Действует с: до",
  periodEndFrom: "Действует по: от",
  periodEndTo: "Действует по: до",
  uploadedTodayOnly: "Только загруженные сегодня",
  active: "Активность",
};

export const normalizeTags = (value: string) =>
  value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);

export const buildPresetPayload = (
  form: KnowledgePresetFormState,
  isFacet: boolean,
  normalizedActiveRagProjectKey: string,
): CreateKnowledgePresetRequest => ({
  name: form.name.trim(),
  description: form.description.trim() || null,
  active: form.active,
  kind: isFacet ? "FACET" : "PRESET",
  scope: {
    presetIds: [],
    facetIds: [],
    documentClasses: [],
    documentTypes: form.documentTypes,
    documentStatuses: form.documentStatuses,
    projectKeys: form.projectKeys,
    documentNumber: form.documentNumber.trim() || null,
    languageCodes: form.languageCodes,
    tags: normalizeTags(form.tagsText),
    workspaceKey: normalizedActiveRagProjectKey || form.workspaceKey.trim() || null,
    periodStartFrom: form.periodStartFrom || null,
    periodStartTo: form.periodStartTo || null,
    periodEndFrom: form.periodEndFrom || null,
    periodEndTo: form.periodEndTo || null,
    uploadedTodayOnly: form.uploadedTodayOnly,
  },
});

export const presetToForm = (detail: KnowledgePresetDetail): KnowledgePresetFormState => ({
  name: detail.name,
  description: detail.description ?? "",
  documentTypes: detail.scope.documentTypes ?? [],
  documentStatuses: detail.scope.documentStatuses ?? [],
  projectKeys: detail.scope.projectKeys ?? [],
  documentNumber: detail.scope.documentNumber ?? "",
  languageCodes: detail.scope.languageCodes ?? [],
  tagsText: detail.scope.tags.join(", "),
  workspaceKey: detail.scope.workspaceKey ?? "",
  periodStartFrom: detail.scope.periodStartFrom ?? "",
  periodStartTo: detail.scope.periodStartTo ?? "",
  periodEndFrom: detail.scope.periodEndFrom ?? "",
  periodEndTo: detail.scope.periodEndTo ?? "",
  uploadedTodayOnly: detail.scope.uploadedTodayOnly,
  active: detail.active,
});

export const visiblePresetsForProject = (
  presets: KnowledgePresetSummary[],
  normalizedActiveRagProjectKey: string,
) =>
  normalizedActiveRagProjectKey
    ? presets.filter((preset) => !preset.workspaceKey || preset.workspaceKey === normalizedActiveRagProjectKey)
    : presets;

export const buildSelectedScopeSummary = (
  selectedPreset: KnowledgePresetDetail | null,
  entityLabel: string,
  isFacet: boolean,
  activeRagProjectName?: string | null,
) => {
  if (!selectedPreset) {
    return `Выбери ${entityLabel}, чтобы увидеть критерии и историю ревизий.`;
  }

  const parts = [];
  const selectedDocumentTypes = selectedPreset.scope.documentTypes ?? [];
  const selectedDocumentStatuses = selectedPreset.scope.documentStatuses ?? [];
  const selectedProjectKeys = selectedPreset.scope.projectKeys ?? [];
  const selectedLanguageCodes = selectedPreset.scope.languageCodes ?? [];
  if (selectedDocumentTypes.length > 0) {
    parts.push(`типы: ${selectedDocumentTypes.map((item) => documentTypeLabels[item]).join(", ")}`);
  }
  if (selectedDocumentStatuses.length > 0) {
    parts.push(`статусы: ${selectedDocumentStatuses.map((item) => documentStatusLabels[item]).join(", ")}`);
  }
  if (selectedProjectKeys.length > 0) {
    parts.push(`проекты: ${selectedProjectKeys.join(", ")}`);
  }
  if (selectedPreset.scope.documentNumber) {
    parts.push(`номер: ${selectedPreset.scope.documentNumber}`);
  }
  if (selectedLanguageCodes.length > 0) {
    parts.push(`языки: ${selectedLanguageCodes.map((item) => materialLanguageCodeLabels[item]).join(", ")}`);
  }
  if (selectedPreset.scope.tags.length > 0) {
    parts.push(`теги: ${selectedPreset.scope.tags.join(", ")}`);
  }
  if (selectedPreset.scope.workspaceKey) {
    parts.push(`RAG-проект: ${activeRagProjectName || selectedPreset.scope.workspaceKey}`);
  }
  if (selectedPreset.scope.uploadedTodayOnly) {
    parts.push("только загруженные сегодня");
  }
  return parts.length > 0 ? parts.join(" · ") : `${isFacet ? "Фасет" : "Preset"} не ограничивает корпус дополнительными критериями.`;
};
