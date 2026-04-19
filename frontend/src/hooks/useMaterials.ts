import { useEffect, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type {
  MaterialListResponse,
  MaterialLineageResponse,
  MaterialUploadItemInput,
  MaterialMetadataInput,
  MaterialSummary,
  MaterialUploadPolicy,
} from "../types";
import { formatBytes } from "../utils/format";
import { hasActiveIndexing } from "../utils/readiness";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  buildFallbackMaterialUploadPolicy,
  normalizeMaterialUploadPolicy,
} from "../utils/materialUploadPolicy";

const POLL_INTERVAL_MS = 5_000;
const MATERIAL_PAGE_LIMIT = 100;
const POLICY_WARNING_MESSAGE = "Не удалось подтвердить capability backend; возможны ограничения при загрузке PDF.";
const DEFAULT_OCR_LANGUAGES = ["kaz", "rus", "eng"];
const isPageVisible = () => typeof document === "undefined" || document.visibilityState !== "hidden";

const formatExtensionList = (policy: MaterialUploadPolicy | null) =>
  (policy?.acceptedExtensions ?? DEFAULT_ACCEPTED_EXTENSIONS).map((extension) => `.${extension}`).join(", ");

const formatOcrLanguageList = (policy: MaterialUploadPolicy | null) => {
  const languages = policy?.pdf?.ocrLanguages?.length ? policy.pdf.ocrLanguages : DEFAULT_OCR_LANGUAGES;
  if (languages.length === 1) {
    return languages[0];
  }

  if (languages.length === 2) {
    return `${languages[0]} и ${languages[1]}`;
  }

  return `${languages.slice(0, -1).join(", ")} и ${languages[languages.length - 1]}`;
};

const extensionOf = (fileName: string) => {
  const trimmed = fileName.trim();
  if (!trimmed.includes(".")) {
    return "";
  }

  return trimmed.substring(trimmed.lastIndexOf(".") + 1).toLowerCase();
};

type UploadMaterialInput = {
  items: MaterialUploadItemInput[];
};

const fileLabel = (file: File) => file.name || "Файл";

const withFileLabel = (file: File, message: string) => `${fileLabel(file)}: ${message}`;

const translateMaterialError = (
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
        return `На сервере не установлены языковые данные OCR для ${formatOcrLanguageList(policy)}.`;
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

const validateUploadInput = (
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

const buildIngestionMessage = (material: MaterialSummary, sourceLabel: string) => {
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

const buildUploadIngestionMessage = (createdMaterials: MaterialSummary[]) => {
  if (createdMaterials.length === 1) {
    return buildIngestionMessage(createdMaterials[0], "Файл");
  }

  return `${formatUploadedFileCount(createdMaterials.length)} приняты и поставлены в очередь индексации.`;
};

const normalizeMaterialListResponse = (payload: MaterialListResponse | MaterialSummary[]): MaterialListResponse => {
  if (Array.isArray(payload)) {
    return {
      items: payload,
      total: payload.length,
      offset: 0,
      limit: payload.length,
      hasMore: false,
    };
  }

  return {
    items: payload.items ?? [],
    total: payload.total ?? payload.items?.length ?? 0,
    offset: payload.offset ?? 0,
    limit: payload.limit ?? MATERIAL_PAGE_LIMIT,
    hasMore: payload.hasMore ?? false,
  };
};

const mergeMaterialPages = (current: MaterialSummary[], next: MaterialSummary[]) => {
  const byId = new Map(current.map((material) => [material.id, material]));
  next.forEach((material) => byId.set(material.id, material));
  return Array.from(byId.values());
};

export const useMaterials = () => {
  const [materials, setMaterials] = useState<MaterialSummary[]>([]);
  const [materialTotal, setMaterialTotal] = useState(0);
  const [materialPageLimit, setMaterialPageLimit] = useState(MATERIAL_PAGE_LIMIT);
  const [hasMoreMaterials, setHasMoreMaterials] = useState(false);
  const [uploadPolicy, setUploadPolicy] = useState<MaterialUploadPolicy | null>(null);
  const [policyWarning, setPolicyWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingMaterialId, setDeletingMaterialId] = useState<string | null>(null);
  const [reindexingMaterialId, setReindexingMaterialId] = useState<string | null>(null);
  const [selectedLineage, setSelectedLineage] = useState<MaterialLineageResponse | null>(null);
  const [lineageError, setLineageError] = useState<string | null>(null);
  const [loadingLineageMaterialId, setLoadingLineageMaterialId] = useState<string | null>(null);

  const applyUploadPolicy = (payload: unknown) => {
    const normalized = normalizeMaterialUploadPolicy(payload);

    if (!normalized.policy) {
      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }

    setUploadPolicy(normalized.policy);
    setPolicyWarning(normalized.warning);
    return normalized.policy;
  };

  const loadMaterials = async (
    signal?: AbortSignal,
    options: { offset?: number; append?: boolean } = {},
  ) => {
    const offset = options.offset ?? 0;
    const append = options.append ?? false;
    if (append) {
      setIsLoadingMore(true);
    }

    try {
      const payload = normalizeMaterialListResponse(
        await apiClient.fetchMaterials({ offset, limit: MATERIAL_PAGE_LIMIT }, signal),
      );
      setMaterials((current) => (append ? mergeMaterialPages(current, payload.items) : payload.items));
      setMaterialTotal(payload.total);
      setMaterialPageLimit(payload.limit);
      setHasMoreMaterials(payload.hasMore);
      setError(null);
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateMaterialError(loadError, "Не удалось загрузить материалы", uploadPolicy);
      setError(nextError);
      return null;
    } finally {
      setIsLoading(false);
      setIsLoadingMore(false);
    }
  };

  const loadUploadPolicy = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchMaterialUploadPolicy(signal);
      return applyUploadPolicy(payload);
    } catch (policyError) {
      if (signal?.aborted) {
        return null;
      }

      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }
  };

  const ensureUploadPolicy = async () => {
    if (uploadPolicy) {
      return uploadPolicy;
    }

    try {
      const payload = await apiClient.fetchMaterialUploadPolicy();
      return applyUploadPolicy(payload);
    } catch (error) {
      const fallbackPolicy = buildFallbackMaterialUploadPolicy();
      setUploadPolicy(fallbackPolicy);
      setPolicyWarning(POLICY_WARNING_MESSAGE);
      return fallbackPolicy;
    }
  };

  useEffect(() => {
    const controller = new AbortController();
    void loadUploadPolicy(controller.signal);
    void loadMaterials(controller.signal);
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!hasActiveIndexing(materials)) {
      return;
    }

    let controller: AbortController | null = null;
    const tick = () => {
      if (!isPageVisible()) {
        return;
      }

      controller?.abort();
      controller = new AbortController();
      void loadMaterials(controller.signal);
    };

    const intervalId = window.setInterval(tick, POLL_INTERVAL_MS);
    const handleVisibilityChange = () => {
      if (isPageVisible()) {
        tick();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      controller?.abort();
      window.clearInterval(intervalId);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [materials, uploadPolicy]);

  const createTextMaterial = async (input: { title: string; content: string; metadata?: MaterialMetadataInput }) => {
    setActionError(null);
    setMessage(null);

    try {
      const created = await apiClient.createTextMaterial(input);
      await loadMaterials();
      setMessage(buildIngestionMessage(created, "Текстовый материал"));
    } catch (submissionError) {
      setActionError(translateMaterialError(submissionError, "Не удалось сохранить материал", uploadPolicy));
      throw submissionError;
    }
  };

  const uploadMaterial = async (input: UploadMaterialInput) => {
    setActionError(null);
    setMessage(null);
    let activePolicy = uploadPolicy;
    let handledError = false;

    try {
      const policy = await ensureUploadPolicy();
      activePolicy = policy;

      const validationError = validateUploadInput(input, policy);
      if (validationError) {
        setActionError(validationError);
        handledError = true;
        throw new Error(validationError);
      }

      const createdMaterials: MaterialSummary[] = [];

      for (const item of input.items) {
        try {
          const created = await apiClient.uploadMaterial({
            title: item.title ?? "",
            file: item.file,
            ...(item.metadata ? { metadata: item.metadata } : {}),
          });
          createdMaterials.push(created);
        } catch (fileError) {
          if (createdMaterials.length > 0) {
            await loadMaterials();
          }

          setActionError(withFileLabel(
            item.file,
            translateMaterialError(fileError, "Не удалось загрузить файл", activePolicy),
          ));
          handledError = true;
          throw fileError;
        }
      }

      await loadMaterials();
      setMessage(buildUploadIngestionMessage(createdMaterials));
    } catch (submissionError) {
      if (!handledError) {
        setActionError(translateMaterialError(submissionError, "Не удалось загрузить файл", activePolicy));
      }
      throw submissionError;
    }
  };

  const deleteMaterial = async (materialId: string) => {
    setDeletingMaterialId(materialId);
    setActionError(null);
    setMessage(null);

    try {
      await apiClient.deleteMaterial(materialId);
      await loadMaterials();
      setSelectedLineage((current) =>
        current?.versions.some((version) => version.id === materialId) ? null : current,
      );
      setMessage("Материал удалён.");
    } catch (deleteError) {
      setActionError(translateMaterialError(deleteError, "Не удалось удалить материал", uploadPolicy));
      throw deleteError;
    } finally {
      setDeletingMaterialId(null);
    }
  };

  const reindexMaterial = async (materialId: string) => {
    setReindexingMaterialId(materialId);
    setActionError(null);
    setMessage(null);

    try {
      const updated = await apiClient.reindexMaterial(materialId);
      await loadMaterials();
      if (selectedLineage?.versions.some((version) => version.id === materialId)) {
        const lineage = await apiClient.fetchMaterialLineage(materialId);
        setSelectedLineage(lineage);
      }
      setMessage(
        updated.status === "PENDING"
          ? "Материал повторно поставлен в очередь индексации."
          : "Материал обновил состояние после запроса на повторную индексацию.",
      );
      return updated;
    } catch (reindexError) {
      setActionError(translateMaterialError(reindexError, "Не удалось повторно запустить индексацию", uploadPolicy));
      throw reindexError;
    } finally {
      setReindexingMaterialId(null);
    }
  };

  const loadLineage = async (materialId: string, signal?: AbortSignal) => {
    setLoadingLineageMaterialId(materialId);
    setLineageError(null);

    try {
      const lineage = await apiClient.fetchMaterialLineage(materialId, signal);
      setSelectedLineage(lineage);
      return lineage;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }

      const nextError = translateMaterialError(loadError, "Не удалось загрузить историю версий материала", uploadPolicy);
      setLineageError(nextError);
      return null;
    } finally {
      setLoadingLineageMaterialId((current) => (current === materialId ? null : current));
    }
  };

  const clearLineage = () => {
    setSelectedLineage(null);
    setLineageError(null);
    setLoadingLineageMaterialId(null);
  };

  const loadMoreMaterials = async () => {
    if (isLoadingMore || !hasMoreMaterials) {
      return null;
    }

    return loadMaterials(undefined, { offset: materials.length, append: true });
  };

  return {
    materials,
    materialTotal,
    materialPageLimit,
    hasMoreMaterials,
    uploadPolicy,
    policyWarning,
    error,
    isLoading,
    isLoadingMore,
    message,
    actionError,
    deletingMaterialId,
    reindexingMaterialId,
    selectedLineage,
    lineageError,
    loadingLineageMaterialId,
    createTextMaterial,
    uploadMaterial,
    deleteMaterial,
    reindexMaterial,
    loadLineage,
    loadMoreMaterials,
    clearLineage,
  };
};
