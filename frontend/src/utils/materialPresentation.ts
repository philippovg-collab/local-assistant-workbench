import { isApiClientError } from "@/api/errors";
import { translateCommonApiError } from "@/api/errorMessages";
import type { BadgeProps } from "@/components/ui/badge";
import type {
  MaterialLineageResponse,
  MaterialLineageVersion,
  MaterialPdfUploadPolicy,
  MaterialSummary,
  MaterialUploadItemInput,
  MaterialUploadPolicy,
} from "@/types";
import { formatBytes, formatDate } from "@/utils/format";
import { DEFAULT_ACCEPTED_EXTENSIONS } from "@/utils/materialUploadPolicy";

export const MATERIAL_PAGE_LIMIT = 100;
export const MATERIAL_POLL_INTERVAL_MS = 5_000;
export const MATERIAL_POLICY_WARNING_MESSAGE =
  "Не удалось подтвердить capability backend; возможны ограничения при загрузке PDF.";

const DEFAULT_OCR_LANGUAGES = ["kaz", "rus", "eng"];

export type UploadMaterialInput = {
  items: MaterialUploadItemInput[];
};

export type MaterialFilterMode = "active" | "all" | "problematic";

const isActiveMaterialVersionState = (material: { versionState?: string | null }) =>
  (material.versionState ?? "ACTIVE") === "ACTIVE";

export const formatExtensionList = (policy: MaterialUploadPolicy | null) =>
  (policy?.acceptedExtensions ?? DEFAULT_ACCEPTED_EXTENSIONS).map((extension) => `.${extension}`).join(", ");

export const formatOcrLanguageList = (languages: string[] | undefined) => {
  const safeLanguages = languages && languages.length > 0 ? languages : DEFAULT_OCR_LANGUAGES;
  if (safeLanguages.length === 1) {
    return safeLanguages[0];
  }

  if (safeLanguages.length === 2) {
    return `${safeLanguages[0]} и ${safeLanguages[1]}`;
  }

  return `${safeLanguages.slice(0, -1).join(", ")} и ${safeLanguages[safeLanguages.length - 1]}`;
};

export const translatePdfCapabilityReason = (policy: MaterialPdfUploadPolicy) => {
  const languages = formatOcrLanguageList(policy.ocrLanguages);
  switch (policy.ocrReasonCode) {
    case "material.ocr_disabled":
      return "OCR отключён на сервере.";
    case "material.ocr_unavailable":
      return "Tesseract OCR сейчас недоступен на сервере.";
    case "material.ocr_language_data_missing":
      return `На сервере не хватает языковых данных OCR для ${languages}.`;
    default:
      return policy.ocrReasonMessage ?? "OCR backend сейчас недоступен.";
  }
};

const extensionOf = (fileName: string) => {
  const trimmed = fileName.trim();
  if (!trimmed.includes(".")) {
    return "";
  }

  return trimmed.substring(trimmed.lastIndexOf(".") + 1).toLowerCase();
};

const fileLabel = (file: File) => file.name || "Файл";

export const withFileLabel = (file: File, message: string) => `${fileLabel(file)}: ${message}`;

export const translateMaterialError = (
  error: unknown,
  fallback: string,
  policy: MaterialUploadPolicy | null,
) => {
  if (isApiClientError(error)) {
    switch (error.code) {
      case "material.empty_text":
        return "Добавь содержимое материала перед сохранением.";
      case "material.empty_upload":
        return "Файл пустой. Выбери непустой файл для загрузки.";
      case "material.upload_too_large":
        return policy
          ? `Файл превышает лимит ${formatBytes(policy.maxUploadBytes)}.`
          : "Файл превышает допустимый размер загрузки.";
      case "material.unsupported_format":
        return `Формат файла не поддерживается. Разрешены: ${formatExtensionList(policy)}.`;
      case "material.empty_content":
        return "После обработки материал оказался пустым. Проверь содержимое файла и попробуй снова.";
      case "material.content_too_large":
        return "Материал получился слишком большим после обработки. Попробуй сократить его или загрузить по частям.";
      case "material.extraction_failed":
        return "Не удалось извлечь текст из файла. Проверь, что документ не повреждён.";
      case "material.extraction_timeout":
        return "Извлечение текста заняло слишком много времени. Попробуй более компактный файл.";
      case "material.extraction_interrupted":
        return "Извлечение текста было прервано на сервере. Повтори загрузку ещё раз.";
      case "material.upload_read_failed":
        return "Сервер не смог прочитать загруженный файл. Попробуй выбрать его заново.";
      case "material.ocr_disabled":
        return policy?.pdf?.ocrReasonMessage ?? "Для этого PDF нужен OCR, но он отключён на сервере.";
      case "material.ocr_unavailable":
        return policy?.pdf?.ocrReasonMessage ?? "Для scanned PDF нужен Tesseract OCR, но он недоступен на сервере.";
      case "material.ocr_language_data_missing":
        return `На сервере не установлены языковые данные OCR для ${formatOcrLanguageList(policy?.pdf?.ocrLanguages)}.`;
      case "material.ocr_failed":
        return "OCR не смог распознать текст в PDF. Проверь качество скана и попробуй снова.";
      case "material.ocr_interrupted":
        return "OCR-обработка была прервана на сервере. Повтори загрузку.";
      case "material.ocr_timeout":
        return "OCR обрабатывал PDF слишком долго. Попробуй файл с меньшим числом страниц.";
      case "material.ocr_page_limit_exceeded":
        return "PDF слишком большой для OCR по текущему лимиту страниц.";
      case "material.ocr_render_budget_exceeded":
        return "Одна из страниц PDF слишком большая для безопасной OCR-обработки на сервере.";
      case "material.ocr_temp_file_too_large":
        return "Одна из страниц PDF слишком тяжёлая для OCR-обработки.";
      case "material.invalid_multipart":
        return "Файл передан некорректно. Выбери его заново и повтори загрузку.";
      case "material.missing_file_part":
        return "Файл не был передан. Выбери файл и повтори загрузку.";
      case "material.reindex_requires_active_version":
        return "Повторная индексация доступна только для активной версии материала.";
      case "material.reindex_not_allowed_for_status":
        return "Повторная индексация доступна только для материалов со статусом FAILED или PARTIAL_READY.";
      case "material.version_upload_requires_active_version":
        return "Новую версию можно загрузить только для активной версии материала.";
      case "material.version_duplicate_content":
        return "Этот файл уже есть в истории версий материала. Выбери файл с изменённым содержимым.";
      case "material.version_upload_missing_lineage":
        return "У материала нет lineage-ключа, поэтому новую версию нельзя загрузить автоматически.";
      case "material.edit_requires_active_version":
        return "Редактировать можно только активную версию материала.";
      case "material.edit_missing_lineage":
        return "У материала нет lineage-ключа, поэтому редакцию нельзя сохранить как новую версию.";
      case "material.lineage_override_key_required":
        return "Укажи lineage override key.";
      case "material.lineage_override_reason_required":
        return "Укажи причину lineage override.";
      case "material.lineage_override_confirmation_required":
        return "Этот lineage override уже используется. Подтверди reuse, чтобы создать новую версию той же lineage.";
      case "material.lineage_override_collision":
        return "Lineage override отклонён: он попытался бы склеить две существующие истории версий.";
      case "material.structured_rollout_proof_required":
        return "Structured-v1 включён без успешного compatible eval compare proof. Запись материала заблокирована.";
      default:
        if (error.code?.startsWith("material.storage_") || error.code?.startsWith("materials.storage_")) {
          return "Не удалось сохранить материал в локальном хранилище backend. Повтори попытку ещё раз.";
        }
        return translateCommonApiError(error, fallback);
    }
  }

  return translateCommonApiError(error, fallback);
};

const validateUploadFile = (
  file: File,
  policy: MaterialUploadPolicy,
) => {
  if (!file || file.size === 0) {
    return "Файл пустой. Выбери непустой файл для загрузки.";
  }

  if (file.size > policy.maxUploadBytes) {
    return `Файл превышает лимит ${formatBytes(policy.maxUploadBytes)}.`;
  }

  const extension = extensionOf(file.name);
  if (!extension || !policy.acceptedExtensions.includes(extension)) {
    return `Формат файла не поддерживается. Разрешены: ${formatExtensionList(policy)}.`;
  }

  return null;
};

export const validateUploadInput = (
  input: UploadMaterialInput,
  policy: MaterialUploadPolicy,
) => {
  if (input.items.length === 0) {
    return "Выбери хотя бы один файл для загрузки.";
  }

  for (const item of input.items) {
    const fileError = validateUploadFile(item.file, policy);
    if (fileError) {
      return withFileLabel(item.file, fileError);
    }
  }

  return null;
};

export const buildIngestionMessage = (material: MaterialSummary, sourceLabel: string) => {
  switch (material.status) {
    case "READY":
      return `${sourceLabel} принят и уже готов для RAG-индекса.`;
    case "PARTIAL_READY":
      return `${sourceLabel} принят и доступен для RAG, но часть содержимого была извлечена только частично.`;
    case "FAILED":
      return `${sourceLabel} сохранён, но индекс собрать не удалось. Проверь backend embeddings и попробуй повторить позже.`;
    default:
      return `${sourceLabel} принят и поставлен в очередь индексации.`;
  }
};

const formatUploadedFileCount = (count: number) => {
  const mod10 = count % 10;
  const mod100 = count % 100;

  if (mod10 === 1 && mod100 !== 11) {
    return `${count} файл`;
  }

  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${count} файла`;
  }

  return `${count} файлов`;
};

export const buildUploadIngestionMessage = (createdMaterials: MaterialSummary[]) => {
  if (createdMaterials.length === 1) {
    return buildIngestionMessage(createdMaterials[0], "Файл");
  }

  return `${formatUploadedFileCount(createdMaterials.length)} приняты и поставлены в очередь индексации.`;
};

export const materialStatusLabel: Record<MaterialSummary["status"], string> = {
  PENDING: "Индексируется",
  IN_PROGRESS: "В обработке",
  READY: "Готов",
  PARTIAL_READY: "Частично готов",
  FAILED: "Ошибка",
};

export const materialStatusVariant: Record<MaterialSummary["status"], BadgeProps["variant"]> = {
  PENDING: "secondary",
  IN_PROGRESS: "secondary",
  READY: "success",
  PARTIAL_READY: "warning",
  FAILED: "destructive",
};

export const filterLabels: Record<MaterialFilterMode, string> = {
  active: "Только активные",
  all: "Все версии",
  problematic: "Проблемные",
};

export const versionStateLabel = (material: MaterialSummary | MaterialLineageVersion) =>
  isActiveMaterialVersionState(material) ? "Активная версия" : "Старая версия";

export const isProblematicMaterial = (material: MaterialSummary) =>
  material.status === "FAILED" || material.status === "PARTIAL_READY";

export const isReindexAllowed = (material: MaterialSummary) =>
  isActiveMaterialVersionState(material)
  && (material.status === "FAILED" || material.status === "PARTIAL_READY");

export const translateSupersedeReason = (reason: string | null | undefined) => {
  switch (reason) {
    case "material.superseded_by_new_active_version":
      return "Версия была вытеснена более новой активной загрузкой.";
    case "material.superseded_by_reactivated_version":
      return "Версия была вытеснена реактивацией другой версии той же lineage.";
    case "material.supersede_reason_legacy_unknown":
      return "Причина supersede не была сохранена в legacy-данных.";
    case "material.manual_promotion":
      return "Версия была переведена в историю во время ручной промоции.";
    default:
      return reason ?? "Причина supersede не указана.";
  }
};

export const formatRetryMeta = (material: MaterialSummary) => {
  if (!material.nextRetryAt) {
    return null;
  }

  return `Следующий retry: ${formatDate(material.nextRetryAt)}`;
};

export const visibleMaterialsForFilter = (materials: MaterialSummary[], filterMode: MaterialFilterMode) => {
  if (filterMode === "active") {
    return materials.filter(isActiveMaterialVersionState);
  }
  if (filterMode === "problematic") {
    return materials.filter(isProblematicMaterial);
  }
  return materials;
};

export const lineageVersions = (lineage: MaterialLineageResponse | null) => lineage?.versions ?? [];

export const formatLineageMeta = (version: MaterialLineageVersion) => {
  const segments = [
    version.originalFileName ?? version.sourceType,
    `${version.contentLength} символов`,
    `Создана ${formatDate(version.createdAt)}`,
    `Обновлена ${formatDate(version.updatedAt ?? version.createdAt)}`,
  ];

  if ((version.indexingAttempts ?? 0) > 0) {
    segments.push(`Попыток индексации: ${version.indexingAttempts}`);
  }

  if (version.nextRetryAt) {
    segments.push(`Retry: ${formatDate(version.nextRetryAt)}`);
  }

  return segments.join(" · ");
};
