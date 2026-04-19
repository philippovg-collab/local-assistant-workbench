import type {
  DocumentType,
  MaterialMetadata,
  MaterialMetadataInput,
  MetadataValueOrigin,
  SourceTrustLevel,
} from "@/types";

export type MaterialMetadataFormState = {
  documentType: DocumentType | "";
  documentDate: string;
  documentNumber: string;
  author: string;
  department: string;
  versionLabel: string;
  language: string;
  tags: string;
  sourceTrust: SourceTrustLevel | "";
  project: string;
  counterparty: string;
  businessStatus: string;
  periodStart: string;
  periodEnd: string;
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

export const documentTypeLabels: Record<DocumentType, string> = {
  POLICY: "Policy",
  CONTRACT: "Contract",
  REPORT: "Report",
  PROCEDURE: "Procedure",
  PRESENTATION: "Presentation",
  SPREADSHEET: "Spreadsheet",
  LETTER: "Letter",
  MANUAL: "Manual",
  FAQ: "FAQ",
  OTHER: "Other",
};

export const sourceTrustLabels: Record<SourceTrustLevel, string> = {
  HIGH: "High trust",
  MEDIUM: "Medium trust",
  LOW: "Low trust",
  UNKNOWN: "Unknown trust",
};

export const DEFAULT_MATERIAL_METADATA: MaterialMetadata = {
  documentType: "OTHER",
  documentDate: null,
  documentNumber: null,
  author: null,
  department: null,
  versionLabel: null,
  language: null,
  tags: [],
  sourceTrust: "UNKNOWN",
  project: null,
  counterparty: null,
  businessStatus: null,
  periodStart: null,
  periodEnd: null,
  provenance: {
    fieldOrigins: {
      documentType: "DEFAULT",
      sourceTrust: "DEFAULT",
    },
    fieldConfidence: {},
  },
};

const metadataFieldLabels: Record<keyof Omit<MaterialMetadata, "provenance">, string> = {
  documentType: "Тип",
  documentDate: "Дата",
  documentNumber: "Номер",
  author: "Автор",
  department: "Подразделение",
  versionLabel: "Версия",
  language: "Язык",
  tags: "Теги",
  sourceTrust: "Доверие",
  project: "Проект",
  counterparty: "Контрагент",
  businessStatus: "Статус",
  periodStart: "Период с",
  periodEnd: "Период по",
};

export const emptyMaterialMetadataFormState = (): MaterialMetadataFormState => ({
  documentType: "",
  documentDate: "",
  documentNumber: "",
  author: "",
  department: "",
  versionLabel: "",
  language: "",
  tags: "",
  sourceTrust: "",
  project: "",
  counterparty: "",
  businessStatus: "",
  periodStart: "",
  periodEnd: "",
});

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
  const tags = parseTagsInput(state.tags);

  return {
    documentType: state.documentType || undefined,
    documentDate: state.documentDate || undefined,
    documentNumber: normalizeOptionalText(state.documentNumber),
    author: normalizeOptionalText(state.author),
    department: normalizeOptionalText(state.department),
    versionLabel: normalizeOptionalText(state.versionLabel),
    language: normalizeOptionalText(state.language),
    tags: tags.length > 0 ? tags : undefined,
    sourceTrust: state.sourceTrust || undefined,
    project: normalizeOptionalText(state.project),
    counterparty: normalizeOptionalText(state.counterparty),
    businessStatus: normalizeOptionalText(state.businessStatus),
    periodStart: state.periodStart || undefined,
    periodEnd: state.periodEnd || undefined,
  };
};

export const validateMaterialMetadata = (state: MaterialMetadataFormState): MaterialMetadataValidation => {
  const fieldErrors: MaterialMetadataFormErrors = {};
  const messages: string[] = [];

  if (!state.documentType) {
    fieldErrors.documentType = "Выбери тип документа.";
    messages.push("Выбери тип документа.");
  }

  if (!state.sourceTrust) {
    fieldErrors.sourceTrust = "Выбери уровень доверия к источнику.";
    messages.push("Выбери уровень доверия к источнику.");
  }

  if (!state.author.trim() && !state.department.trim()) {
    const message = "Заполни автора или подразделение.";
    fieldErrors.author = message;
    fieldErrors.department = message;
    messages.push(message);
  }

  if (state.periodStart && state.periodEnd && state.periodStart > state.periodEnd) {
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
      return "manual";
    case "INFERRED":
      return "auto";
    default:
      return "default";
  }
};

export const buildMaterialMetadataEntries = (
  metadata?: Partial<MaterialMetadata> | null,
): MaterialMetadataDisplayEntry[] => {
  const resolved = materialMetadataWithDefaults(metadata);
  const rawEntries: Array<MaterialMetadataDisplayEntry | null> = [
    {
      key: "documentType",
      label: metadataFieldLabels.documentType,
      value: documentTypeLabels[resolved.documentType],
      origin: resolved.provenance.fieldOrigins.documentType ?? "DEFAULT",
    },
    resolved.documentDate ? {
      key: "documentDate",
      label: metadataFieldLabels.documentDate,
      value: resolved.documentDate,
      origin: resolved.provenance.fieldOrigins.documentDate ?? "DEFAULT",
    } : null,
    resolved.documentNumber ? {
      key: "documentNumber",
      label: metadataFieldLabels.documentNumber,
      value: resolved.documentNumber,
      origin: resolved.provenance.fieldOrigins.documentNumber ?? "DEFAULT",
    } : null,
    resolved.author ? {
      key: "author",
      label: metadataFieldLabels.author,
      value: resolved.author,
      origin: resolved.provenance.fieldOrigins.author ?? "DEFAULT",
    } : null,
    resolved.department ? {
      key: "department",
      label: metadataFieldLabels.department,
      value: resolved.department,
      origin: resolved.provenance.fieldOrigins.department ?? "DEFAULT",
    } : null,
    resolved.versionLabel ? {
      key: "versionLabel",
      label: metadataFieldLabels.versionLabel,
      value: resolved.versionLabel,
      origin: resolved.provenance.fieldOrigins.versionLabel ?? "DEFAULT",
    } : null,
    resolved.language ? {
      key: "language",
      label: metadataFieldLabels.language,
      value: resolved.language,
      origin: resolved.provenance.fieldOrigins.language ?? "DEFAULT",
    } : null,
    resolved.tags.length > 0 ? {
      key: "tags",
      label: metadataFieldLabels.tags,
      value: resolved.tags.join(", "),
      origin: resolved.provenance.fieldOrigins.tags ?? "DEFAULT",
    } : null,
    {
      key: "sourceTrust",
      label: metadataFieldLabels.sourceTrust,
      value: sourceTrustLabels[resolved.sourceTrust],
      origin: resolved.provenance.fieldOrigins.sourceTrust ?? "DEFAULT",
    },
    resolved.project ? {
      key: "project",
      label: metadataFieldLabels.project,
      value: resolved.project,
      origin: resolved.provenance.fieldOrigins.project ?? "DEFAULT",
    } : null,
    resolved.counterparty ? {
      key: "counterparty",
      label: metadataFieldLabels.counterparty,
      value: resolved.counterparty,
      origin: resolved.provenance.fieldOrigins.counterparty ?? "DEFAULT",
    } : null,
    resolved.businessStatus ? {
      key: "businessStatus",
      label: metadataFieldLabels.businessStatus,
      value: resolved.businessStatus,
      origin: resolved.provenance.fieldOrigins.businessStatus ?? "DEFAULT",
    } : null,
    resolved.periodStart ? {
      key: "periodStart",
      label: metadataFieldLabels.periodStart,
      value: resolved.periodStart,
      origin: resolved.provenance.fieldOrigins.periodStart ?? "DEFAULT",
    } : null,
    resolved.periodEnd ? {
      key: "periodEnd",
      label: metadataFieldLabels.periodEnd,
      value: resolved.periodEnd,
      origin: resolved.provenance.fieldOrigins.periodEnd ?? "DEFAULT",
    } : null,
  ];

  return rawEntries.filter((entry): entry is MaterialMetadataDisplayEntry => entry !== null);
};
