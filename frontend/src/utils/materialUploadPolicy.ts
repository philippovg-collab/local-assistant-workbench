import type { MaterialPdfUploadPolicy, MaterialUploadPolicy } from "../types";

type UnknownRecord = Record<string, unknown>;

type MaterialUploadPolicyNormalization = {
  policy: MaterialUploadPolicy | null;
  warning: string | null;
};

const DEFAULT_MAX_UPLOAD_BYTES = 8_388_608;
const DEFAULT_ACCEPTED_MIME_HINTS: string[] = [];
const DEFAULT_OCR_LANGUAGES = ["kaz", "rus", "eng"];
const DEFAULT_OCR_MAX_PAGES = 12;
const RICH_DOCUMENT_EXTENSIONS = new Set(["doc", "docx", "rtf", "odt", "pdf"]);
const LEGACY_PDF_REASON_MESSAGE = "Backend did not provide PDF capability metadata.";

export const DEFAULT_ACCEPTED_EXTENSIONS = [
  "txt",
  "md",
  "markdown",
  "csv",
  "json",
  "xml",
  "yaml",
  "yml",
  "log",
  "sql",
  "java",
  "kt",
  "js",
  "ts",
  "tsx",
  "jsx",
  "py",
  "properties",
  "html",
  "htm",
  "doc",
  "docx",
  "rtf",
  "odt",
  "pdf",
];

export const LEGACY_POLICY_WARNING_MESSAGE =
  "Backend вернул устаревший upload policy без PDF metadata; используем безопасный fallback для PDF.";

const isRecord = (value: unknown): value is UnknownRecord =>
  typeof value === "object" && value !== null;

const asStringArray = (value: unknown, fallback: string[]) =>
  Array.isArray(value) && value.every((item) => typeof item === "string")
    ? [...value]
    : fallback;

const asNumber = (value: unknown, fallback: number) =>
  typeof value === "number" && Number.isFinite(value) ? value : fallback;

const hasPdfMode = (mode: unknown): mode is MaterialPdfUploadPolicy["mode"] =>
  mode === "embedded_text_only" || mode === "embedded_text_and_ocr";

const buildFallbackPdfPolicy = (acceptedExtensions: string[]): MaterialPdfUploadPolicy => {
  const pdfEnabled = acceptedExtensions.includes("pdf");

  return {
    enabled: pdfEnabled,
    scannedPdfSupport: false,
    mode: "embedded_text_only",
    ocrReasonCode: pdfEnabled ? "material.ocr_unavailable" : undefined,
    ocrReasonMessage: pdfEnabled ? LEGACY_PDF_REASON_MESSAGE : undefined,
    ocrLanguages: [...DEFAULT_OCR_LANGUAGES],
    ocrMaxPages: DEFAULT_OCR_MAX_PAGES,
  };
};

export const buildFallbackMaterialUploadPolicy = (): MaterialUploadPolicy => {
  const acceptedExtensions = [...DEFAULT_ACCEPTED_EXTENSIONS];

  return {
    maxUploadBytes: DEFAULT_MAX_UPLOAD_BYTES,
    acceptedExtensions,
    acceptedMimeHints: [...DEFAULT_ACCEPTED_MIME_HINTS],
    richDocumentSupport: acceptedExtensions.some((extension) => RICH_DOCUMENT_EXTENSIONS.has(extension)),
    pdf: buildFallbackPdfPolicy(acceptedExtensions),
  };
};

const normalizePdfPolicy = (
  value: unknown,
  acceptedExtensions: string[],
): { policy: MaterialPdfUploadPolicy; warning: string | null } => {
  if (!isRecord(value) || !hasPdfMode(value.mode)) {
    return {
      policy: buildFallbackPdfPolicy(acceptedExtensions),
      warning: acceptedExtensions.includes("pdf") ? LEGACY_POLICY_WARNING_MESSAGE : null,
    };
  }

  return {
    policy: {
      enabled:
        typeof value.enabled === "boolean" ? value.enabled : acceptedExtensions.includes("pdf"),
      scannedPdfSupport:
        typeof value.scannedPdfSupport === "boolean" ? value.scannedPdfSupport : false,
      mode: value.mode,
      ocrReasonCode:
        typeof value.ocrReasonCode === "string" ? value.ocrReasonCode : undefined,
      ocrReasonMessage:
        typeof value.ocrReasonMessage === "string" ? value.ocrReasonMessage : undefined,
      ocrLanguages: asStringArray(value.ocrLanguages, DEFAULT_OCR_LANGUAGES),
      ocrMaxPages: asNumber(value.ocrMaxPages, DEFAULT_OCR_MAX_PAGES),
    },
    warning: null,
  };
};

export const normalizeMaterialUploadPolicy = (
  payload: unknown,
): MaterialUploadPolicyNormalization => {
  if (!isRecord(payload)) {
    return {
      policy: null,
      warning: null,
    };
  }

  const acceptedExtensions = asStringArray(
    payload.acceptedExtensions,
    DEFAULT_ACCEPTED_EXTENSIONS,
  );
  const { policy: pdfPolicy, warning } = normalizePdfPolicy(payload.pdf, acceptedExtensions);

  return {
    policy: {
      maxUploadBytes: asNumber(payload.maxUploadBytes, DEFAULT_MAX_UPLOAD_BYTES),
      acceptedExtensions,
      acceptedMimeHints: asStringArray(payload.acceptedMimeHints, DEFAULT_ACCEPTED_MIME_HINTS),
      richDocumentSupport:
        typeof payload.richDocumentSupport === "boolean"
          ? payload.richDocumentSupport
          : acceptedExtensions.some((extension) => RICH_DOCUMENT_EXTENSIONS.has(extension)),
      pdf: pdfPolicy,
    },
    warning,
  };
};
