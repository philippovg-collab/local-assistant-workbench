import type {
  DocumentStatus,
  DocumentType,
  KnowledgeDocumentClass,
  MaterialLanguageCode,
  MaterialMetadata,
  MaterialMetadataInput,
  MetadataValueOrigin,
  ReferenceProject,
  ReferenceWorkspace,
  SourceTrustLevel,
} from "@/types";

export type MaterialLanguageFormValue = MaterialLanguageCode | "AUTO";

export type MaterialMetadataFormState = {
  workspaceKey: string;
  documentType: DocumentType | "";
  documentStatus: DocumentStatus | "";
  projectKey: string;
  documentNumber: string;
  languageCode: MaterialLanguageFormValue;
  periodStart: string;
  periodEnd: string;
  openEnded: boolean;
  manualTags: string;
};

export type MaterialMetadataFormErrors = Partial<Record<keyof MaterialMetadataFormState, string>>;

export type MaterialMetadataValidation = {
  fieldErrors: MaterialMetadataFormErrors;
  messages: string[];
  isValid: boolean;
};

export type MaterialMetadataDisplayEntry = {
  key: keyof Omit<MaterialMetadata, "provenance">;
  label: string;
  value: string;
  origin: MetadataValueOrigin;
};

export type MaterialMetadataDisplayReferences = {
  projects?: Pick<ReferenceProject, "key" | "nameRu">[];
  workspaces?: Pick<ReferenceWorkspace, "key" | "nameRu">[];
};

export const documentTypeLabels: Record<DocumentType, string> = {
  POLICY: "Политика",
  CONTRACT: "Договор",
  REPORT: "Отчёт",
  PROCEDURE: "Процедура",
  PRESENTATION: "Презентация",
  SPREADSHEET: "Таблица",
  LETTER: "Письмо",
  MANUAL: "Руководство",
  FAQ: "FAQ",
  OTHER: "Другое",
};

export const documentStatusLabels: Record<DocumentStatus, string> = {
  ACTIVE: "Действует",
  DRAFT: "Черновик",
  ARCHIVED: "Архив",
  REVOKED: "Отозван",
};

export const materialLanguageCodeLabels: Record<MaterialLanguageCode, string> = {
  RU: "Русский",
  KK: "Казахский",
  EN: "Английский",
};

export const sourceTrustLabels: Record<SourceTrustLevel, string> = {
  HIGH: "Высокое доверие",
  MEDIUM: "Среднее доверие",
  LOW: "Низкое доверие",
  UNKNOWN: "Неизвестно",
};

export const materialKnowledgeDocumentClassLabels: Record<KnowledgeDocumentClass, string> = {
  contracts: "Договоры",
  regulations: "Регламенты",
  correspondence: "Переписка",
  techdocs: "Техдоки",
  other: "Прочее",
};

export const DEFAULT_MATERIAL_METADATA: MaterialMetadata = {
  documentType: "OTHER",
  documentStatus: "ACTIVE",
  workspaceKey: null,
  projectKey: null,
  documentNumber: null,
  languageCode: null,
  manualTags: [],
  autoTags: [],
  effectiveTags: [],
  periodStart: null,
  periodEnd: null,
  tags: [],
  knowledgeDocumentClass: "other",
  documentDate: null,
  author: null,
  department: null,
  versionLabel: null,
  language: null,
  sourceTrust: "UNKNOWN",
  project: null,
  counterparty: null,
  businessStatus: null,
  provenance: {
    fieldOrigins: {
      documentType: "DEFAULT",
      documentStatus: "DEFAULT",
      sourceTrust: "DEFAULT",
    },
    fieldConfidence: {},
  },
};

const metadataFieldLabels: Record<keyof Omit<MaterialMetadata, "provenance">, string> = {
  documentType: "Тип документа",
  documentStatus: "Статус документа",
  workspaceKey: "Рабочая область",
  projectKey: "Проект",
  documentNumber: "Номер документа",
  languageCode: "Язык",
  manualTags: "Ручные теги",
  autoTags: "Авто-теги",
  effectiveTags: "Теги",
  periodStart: "Действует с",
  periodEnd: "Действует по",
  tags: "Теги",
  knowledgeDocumentClass: "Класс знаний",
  documentDate: "Дата документа",
  author: "Автор",
  department: "Подразделение",
  versionLabel: "Версия",
  language: "Язык",
  sourceTrust: "Доверие",
  project: "Проект",
  counterparty: "Контрагент",
  businessStatus: "Статус",
};

export const emptyMaterialMetadataFormState = (): MaterialMetadataFormState => ({
  workspaceKey: "",
  documentType: "",
  documentStatus: "ACTIVE",
  projectKey: "",
  documentNumber: "",
  languageCode: "AUTO",
  periodStart: "",
  periodEnd: "",
  openEnded: true,
  manualTags: "",
});

export const toMaterialMetadataFormState = (
  metadata?: Partial<MaterialMetadata> | null,
): MaterialMetadataFormState => {
  const resolved = materialMetadataWithDefaults(metadata);
  const manualTags = resolved.manualTags?.length
    ? resolved.manualTags
    : resolved.tags ?? [];

  return {
    workspaceKey: resolved.workspaceKey ?? "",
    documentType: resolved.documentType,
    documentStatus: resolved.documentStatus,
    projectKey: resolved.projectKey ?? "",
    documentNumber: resolved.documentNumber ?? "",
    languageCode: resolved.languageCode ?? "AUTO",
    periodStart: resolved.periodStart ?? "",
    periodEnd: resolved.periodEnd ?? "",
    openEnded: !resolved.periodEnd,
    manualTags: formatTagsInput(manualTags),
  };
};

export const parseTagsInput = (value: string) => {
  const normalized = new Set<string>();
  value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean)
    .forEach((item) => normalized.add(item));
  return Array.from(normalized);
};

export const formatTagsInput = (tags: string[]) => tags.join(", ");

const normalizeOptionalText = (value: string) => {
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : undefined;
};

export const toMaterialMetadataInput = (state: MaterialMetadataFormState): MaterialMetadataInput => {
  const manualTags = parseTagsInput(state.manualTags);
  const projectKey = normalizeOptionalText(state.projectKey);
  const documentNumber = normalizeOptionalText(state.documentNumber);
  const periodStart = normalizeOptionalText(state.periodStart);
  const periodEnd = state.openEnded ? undefined : normalizeOptionalText(state.periodEnd);

  return {
    workspaceKey: normalizeOptionalText(state.workspaceKey),
    documentType: state.documentType || undefined,
    documentStatus: state.documentStatus || undefined,
    ...(projectKey ? { projectKey } : {}),
    ...(documentNumber ? { documentNumber } : {}),
    languageCode: state.languageCode === "AUTO" ? null : state.languageCode,
    ...(periodStart ? { periodStart } : {}),
    ...(periodEnd ? { periodEnd } : {}),
    ...(manualTags.length > 0 ? { manualTags } : {}),
  };
};

export const validateMaterialMetadata = (state: MaterialMetadataFormState): MaterialMetadataValidation => {
  const fieldErrors: MaterialMetadataFormErrors = {};
  const messages: string[] = [];

  if (!state.workspaceKey.trim()) {
    const message = "Выберите рабочую область.";
    fieldErrors.workspaceKey = message;
    messages.push(message);
  }

  if (!state.documentType) {
    const message = "Выберите тип документа.";
    fieldErrors.documentType = message;
    messages.push(message);
  }

  if (!state.documentStatus) {
    const message = "Выберите статус документа.";
    fieldErrors.documentStatus = message;
    messages.push(message);
  }

  if (!state.openEnded && state.periodStart && state.periodEnd && state.periodStart > state.periodEnd) {
    const message = "Дата начала периода не может быть позже даты окончания.";
    fieldErrors.periodStart = message;
    fieldErrors.periodEnd = message;
    messages.push(message);
  }

  return {
    fieldErrors,
    messages,
    isValid: messages.length === 0,
  };
};

export const materialMetadataWithDefaults = (
  metadata?: Partial<MaterialMetadata> | null,
): MaterialMetadata => ({
  ...DEFAULT_MATERIAL_METADATA,
  ...metadata,
  tags: metadata?.tags ?? DEFAULT_MATERIAL_METADATA.tags,
  manualTags: metadata?.manualTags ?? DEFAULT_MATERIAL_METADATA.manualTags,
  autoTags: metadata?.autoTags ?? DEFAULT_MATERIAL_METADATA.autoTags,
  effectiveTags: metadata?.effectiveTags ?? DEFAULT_MATERIAL_METADATA.effectiveTags,
  provenance: {
    fieldOrigins: {
      ...DEFAULT_MATERIAL_METADATA.provenance.fieldOrigins,
      ...(metadata?.provenance?.fieldOrigins ?? {}),
    },
    fieldConfidence: metadata?.provenance?.fieldConfidence ?? {},
  },
});

export const provenanceLabel = (origin: MetadataValueOrigin) => {
  switch (origin) {
    case "MANUAL":
      return "вручную";
    case "INFERRED":
      return "авто";
    default:
      return "по умолчанию";
  }
};

const originFor = (metadata: MaterialMetadata, field: string): MetadataValueOrigin =>
  metadata.provenance.fieldOrigins[field] ?? "DEFAULT";

const languageLabel = (metadata: MaterialMetadata) => {
  if (metadata.languageCode) {
    return materialLanguageCodeLabels[metadata.languageCode];
  }
  if (metadata.language) {
    return metadata.language;
  }
  return null;
};

const tagsForDisplay = (metadata: MaterialMetadata) => {
  const manualTags = metadata.manualTags ?? [];
  const autoTags = metadata.autoTags ?? [];
  const effectiveTags = metadata.effectiveTags ?? [];
  const legacyTags = metadata.tags ?? [];
  const hasSplitTags = manualTags.length > 0 || autoTags.length > 0 || effectiveTags.length > 0;

  if (!hasSplitTags && legacyTags.length > 0) {
    return {
      manualTags: legacyTags,
      autoTags: [],
      effectiveTags: [],
      isLegacyFallback: true,
    };
  }

  return {
    manualTags,
    autoTags,
    effectiveTags,
    isLegacyFallback: false,
  };
};

export const buildMaterialMetadataEntries = (
  metadata?: Partial<MaterialMetadata> | null,
  references: MaterialMetadataDisplayReferences = {},
): MaterialMetadataDisplayEntry[] => {
  const resolved = materialMetadataWithDefaults(metadata);
  const tagDisplay = tagsForDisplay(resolved);
  const language = languageLabel(resolved);
  const workspaceName = references.workspaces?.find((workspace) => workspace.key === resolved.workspaceKey)?.nameRu;
  const projectName = references.projects?.find((project) => project.key === resolved.projectKey)?.nameRu;
  const entries: Array<MaterialMetadataDisplayEntry | null> = [
    resolved.workspaceKey ? {
      key: "workspaceKey",
      label: metadataFieldLabels.workspaceKey,
      value: workspaceName || resolved.workspaceKey,
      origin: originFor(resolved, "workspaceKey"),
    } : null,
    {
      key: "documentType",
      label: metadataFieldLabels.documentType,
      value: documentTypeLabels[resolved.documentType],
      origin: originFor(resolved, "documentType"),
    },
    {
      key: "documentStatus",
      label: metadataFieldLabels.documentStatus,
      value: documentStatusLabels[resolved.documentStatus],
      origin: originFor(resolved, "documentStatus"),
    },
    resolved.projectKey ? {
      key: "projectKey",
      label: metadataFieldLabels.projectKey,
      value: projectName || resolved.projectKey,
      origin: originFor(resolved, "projectKey"),
    } : null,
    resolved.documentNumber ? {
      key: "documentNumber",
      label: metadataFieldLabels.documentNumber,
      value: resolved.documentNumber,
      origin: originFor(resolved, "documentNumber"),
    } : null,
    language ? {
      key: "languageCode",
      label: metadataFieldLabels.languageCode,
      value: language,
      origin: originFor(resolved, resolved.languageCode ? "languageCode" : "language"),
    } : null,
    resolved.periodStart ? {
      key: "periodStart",
      label: metadataFieldLabels.periodStart,
      value: resolved.periodStart,
      origin: originFor(resolved, "periodStart"),
    } : null,
    resolved.periodEnd ? {
      key: "periodEnd",
      label: metadataFieldLabels.periodEnd,
      value: resolved.periodEnd,
      origin: originFor(resolved, "periodEnd"),
    } : null,
    tagDisplay.manualTags.length > 0 ? {
      key: "manualTags",
      label: metadataFieldLabels.manualTags,
      value: tagDisplay.manualTags.join(", "),
      origin: originFor(resolved, tagDisplay.isLegacyFallback ? "tags" : "manualTags"),
    } : null,
    tagDisplay.autoTags.length > 0 ? {
      key: "autoTags",
      label: metadataFieldLabels.autoTags,
      value: tagDisplay.autoTags.join(", "),
      origin: originFor(resolved, "autoTags"),
    } : null,
    tagDisplay.manualTags.length > 0 && tagDisplay.autoTags.length > 0 && tagDisplay.effectiveTags.length > 0 ? {
      key: "effectiveTags",
      label: metadataFieldLabels.effectiveTags,
      value: tagDisplay.effectiveTags.join(", "),
      origin: originFor(resolved, "effectiveTags"),
    } : null,
  ];

  return entries.filter((entry): entry is MaterialMetadataDisplayEntry => entry !== null);
};
