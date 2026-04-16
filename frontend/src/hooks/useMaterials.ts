import { useEffect, useState } from "react";
import { apiClient, isApiClientError } from "../api/client";
import { translateCommonApiError } from "../api/errorMessages";
import type { MaterialSummary, MaterialUploadPolicy } from "../types";
import { formatBytes } from "../utils/format";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  buildFallbackMaterialUploadPolicy,
  normalizeMaterialUploadPolicy,
} from "../utils/materialUploadPolicy";

const POLICY_WARNING_MESSAGE = "Не удалось подтвердить capability backend; возможны ограничения при загрузке PDF.";
const DEFAULT_OCR_LANGUAGES = ["kaz", "rus", "eng"];

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
      default:
        if (error.code?.startsWith("materials.storage_")) {
          return "Не удалось сохранить материал в локальном хранилище backend. Повтори попытку ещё раз.";
        }
        return translateCommonApiError(error, fallback);
    }
  }

  return translateCommonApiError(error, fallback);
};

const validateUploadInput = (
  input: { title: string; file: File },
  policy: MaterialUploadPolicy,
) => {
  if (!input.file || input.file.size === 0) {
    return "Файл пустой. Выбери непустой файл для загрузки.";
  }

  if (input.file.size > policy.maxUploadBytes) {
    return `Файл превышает лимит ${formatBytes(policy.maxUploadBytes)}.`;
  }

  const extension = extensionOf(input.file.name);
  if (!extension || !policy.acceptedExtensions.includes(extension)) {
    return `Формат файла не поддерживается. Разрешены: ${formatExtensionList(policy)}.`;
  }

  return null;
};

export const useMaterials = () => {
  const [materials, setMaterials] = useState<MaterialSummary[]>([]);
  const [uploadPolicy, setUploadPolicy] = useState<MaterialUploadPolicy | null>(null);
  const [policyWarning, setPolicyWarning] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deletingMaterialId, setDeletingMaterialId] = useState<string | null>(null);

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

  const loadMaterials = async (signal?: AbortSignal) => {
    try {
      const payload = await apiClient.fetchMaterials(signal);
      setMaterials(payload);
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

  const createTextMaterial = async (input: { title: string; content: string }) => {
    setActionError(null);
    setMessage(null);

    try {
      await apiClient.createTextMaterial(input);
      await loadMaterials();
      setMessage("Текстовый материал сохранён локально.");
    } catch (submissionError) {
      setActionError(translateMaterialError(submissionError, "Не удалось сохранить материал", uploadPolicy));
      throw submissionError;
    }
  };

  const uploadMaterial = async (input: { title: string; file: File }) => {
    setActionError(null);
    setMessage(null);
    let activePolicy = uploadPolicy;

    try {
      const policy = await ensureUploadPolicy();
      activePolicy = policy;

      const validationError = validateUploadInput(input, policy);
      if (validationError) {
        setActionError(validationError);
        throw new Error(validationError);
      }

      await apiClient.uploadMaterial(input);
      await loadMaterials();
      setMessage("Файл загружен и добавлен в локальное хранилище.");
    } catch (submissionError) {
      setActionError(translateMaterialError(submissionError, "Не удалось загрузить файл", activePolicy));
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
      setMessage("Материал удалён.");
    } catch (deleteError) {
      setActionError(translateMaterialError(deleteError, "Не удалось удалить материал", uploadPolicy));
      throw deleteError;
    } finally {
      setDeletingMaterialId(null);
    }
  };

  return {
    materials,
    uploadPolicy,
    policyWarning,
    error,
    isLoading,
    message,
    actionError,
    deletingMaterialId,
    createTextMaterial,
    uploadMaterial,
    deleteMaterial,
  };
};
