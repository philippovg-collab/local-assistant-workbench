import { useRef, useState, type FormEvent } from "react";
import type {
  MaterialPdfUploadPolicy,
  MaterialSummary,
  MaterialUploadPolicy,
} from "../types";
import { formatBytes, formatDate } from "../utils/format";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  normalizeMaterialUploadPolicy,
} from "../utils/materialUploadPolicy";

type MaterialsPanelProps = {
  materials: MaterialSummary[];
  uploadPolicy: MaterialUploadPolicy | null;
  policyWarning: string | null;
  isLoading: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingMaterialId: string | null;
  onCreateText: (input: { title: string; content: string }) => Promise<unknown>;
  onUpload: (input: { title: string; file: File }) => Promise<unknown>;
  onDelete: (materialId: string) => Promise<unknown>;
};

const DEFAULT_OCR_LANGUAGES = ["kaz", "rus", "eng"];

const formatOcrLanguageList = (languages: string[] | undefined) => {
  const safeLanguages = languages && languages.length > 0 ? languages : DEFAULT_OCR_LANGUAGES;
  if (safeLanguages.length === 1) {
    return safeLanguages[0];
  }

  if (safeLanguages.length === 2) {
    return `${safeLanguages[0]} и ${safeLanguages[1]}`;
  }

  return `${safeLanguages.slice(0, -1).join(", ")} и ${safeLanguages[safeLanguages.length - 1]}`;
};

const translatePdfCapabilityReason = (policy: MaterialPdfUploadPolicy) => {
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

export function MaterialsPanel({
  materials,
  uploadPolicy,
  policyWarning,
  isLoading,
  error,
  message,
  actionError,
  deletingMaterialId,
  onCreateText,
  onUpload,
  onDelete,
}: MaterialsPanelProps) {
  const [textTitle, setTextTitle] = useState("");
  const [textContent, setTextContent] = useState("");
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [isSavingText, setIsSavingText] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const normalizedUploadPolicy = normalizeMaterialUploadPolicy(uploadPolicy);
  const effectiveUploadPolicy = normalizedUploadPolicy.policy;
  const effectivePolicyWarning = policyWarning ?? normalizedUploadPolicy.warning;
  const acceptedFormats = (
    effectiveUploadPolicy?.acceptedExtensions ?? DEFAULT_ACCEPTED_EXTENSIONS
  ).map((extension) => `.${extension}`);
  const acceptAttribute = acceptedFormats.join(",");
  const pdfPolicy = effectiveUploadPolicy?.pdf;
  const helperText = effectiveUploadPolicy
    ? pdfPolicy?.enabled
      ? pdfPolicy.mode === "embedded_text_and_ocr"
        ? `Лимит: ${formatBytes(effectiveUploadPolicy.maxUploadBytes)}. Поддерживаемые форматы: ${acceptedFormats.join(", ")}. PDF со встроенным текстом читается напрямую, а scanned PDF поддерживается через OCR до ${pdfPolicy.ocrMaxPages} стр.`
        : `Лимит: ${formatBytes(effectiveUploadPolicy.maxUploadBytes)}. Поддерживаемые форматы: ${acceptedFormats.join(", ")}. PDF со встроенным текстом всё ещё поддерживаются, а scanned PDF временно недоступны, пока backend не подтвердит OCR readiness.`
      : `Лимит: ${formatBytes(effectiveUploadPolicy.maxUploadBytes)}. Поддерживаемые форматы: ${acceptedFormats.join(", ")}. Загрузка PDF сейчас недоступна на backend.`
    : "Подтягиваем upload policy backend, затем валидируем размер и формат до отправки файла.";
  const pdfCapabilityWarning = pdfPolicy?.enabled && pdfPolicy.mode === "embedded_text_only"
    ? `Scanned PDF сейчас не поддерживаются: ${translatePdfCapabilityReason(pdfPolicy)}`
    : null;

  const handleTextSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSavingText(true);

    try {
      await onCreateText({
        title: textTitle,
        content: textContent,
      });
      setTextTitle("");
      setTextContent("");
    } finally {
      setIsSavingText(false);
    }
  };

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!uploadFile) {
      return;
    }

    setIsUploading(true);
    try {
      await onUpload({
        title: uploadTitle,
        file: uploadFile,
      });
      setUploadTitle("");
      setUploadFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } finally {
      setIsUploading(false);
    }
  };

  const handleDelete = async (materialId: string) => {
    const material = materials.find((item) => item.id === materialId);
    const confirmed = window.confirm(
      `Удалить материал "${material?.title ?? materialId}" из локального хранилища?`,
    );

    if (!confirmed) {
      return;
    }

    await onDelete(materialId);
  };

  return (
    <article className="panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Knowledge Base</p>
          <h2>Материалы для RAG</h2>
        </div>
        <span className="badge">GET /api/materials</span>
      </div>

      <p className="section-copy">
        Материалы сохраняются через backend, нормализуются, дедуплицируются и индексируются сразу при загрузке.
      </p>

      <div className="split-stack">
        <form className="form-card" onSubmit={handleTextSubmit}>
          <h3>Добавить текст</h3>
          <label className="field">
            <span>Название</span>
            <input
              value={textTitle}
              onChange={(event) => setTextTitle(event.target.value)}
              placeholder="Например: Pricing note"
            />
          </label>

          <label className="field">
            <span>Содержимое</span>
            <textarea
              value={textContent}
              onChange={(event) => setTextContent(event.target.value)}
              placeholder="Вставь сюда фрагмент документа, FAQ или инструкции."
              rows={7}
              required
            />
          </label>

          <button
            className="primary-button"
            disabled={isSavingText || !textContent.trim()}
            type="submit"
          >
            {isSavingText ? "Сохраняем..." : "Сохранить текст"}
          </button>
        </form>

        <form className="form-card" onSubmit={handleUploadSubmit}>
          <h3>Загрузить файл</h3>
          <label className="field">
            <span>Название</span>
            <input
              value={uploadTitle}
              onChange={(event) => setUploadTitle(event.target.value)}
              placeholder="Опционально переименуй материал"
            />
          </label>

          <label className="field">
            <span>Файл</span>
            <input
              ref={fileInputRef}
              accept={acceptAttribute}
              type="file"
              onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
              required
            />
          </label>

          <p className="helper">{helperText}</p>
          {pdfCapabilityWarning ? <p className="warning-state">{pdfCapabilityWarning}</p> : null}
          {effectivePolicyWarning ? <p className="warning-state">{effectivePolicyWarning}</p> : null}

          <button
            className="primary-button"
            disabled={isUploading || uploadFile === null}
            type="submit"
          >
            {isUploading ? "Загружаем..." : "Загрузить файл"}
          </button>
        </form>
      </div>

      {message ? <p className="inline-success">{message}</p> : null}
      {actionError ? <p className="inline-error">{actionError}</p> : null}
      {error ? <p className="inline-error">{error}</p> : null}

      <div className="panel-header compact">
        <h3>Локальное хранилище</h3>
        <span className="badge">{materials.length} items</span>
      </div>

      {isLoading ? (
        <div className="empty-state">
          <strong>Загружаем материалы</strong>
          <span>Читаем локальное хранилище backend.</span>
        </div>
      ) : materials.length === 0 ? (
        <div className="empty-state">
          <strong>Пока пусто</strong>
          <span>Добавь хотя бы один материал, чтобы RAG-режим мог построить ответ по контексту.</span>
        </div>
      ) : (
        <div className="stack-list">
          {materials.map((material) => (
            <article className="item-card" key={material.id}>
              <div className="item-row">
                <div>
                  <h3>{material.title}</h3>
                  <p className="item-meta">
                    {material.originalFileName ?? material.sourceType} · {material.contentLength} символов ·{" "}
                    {formatDate(material.createdAt)}
                  </p>
                </div>
                <button
                  className="danger-button"
                  disabled={deletingMaterialId === material.id}
                  type="button"
                  onClick={() => void handleDelete(material.id)}
                >
                  {deletingMaterialId === material.id ? "Удаляем..." : "Удалить"}
                </button>
              </div>
              <p>{material.preview}</p>
            </article>
          ))}
        </div>
      )}
    </article>
  );
}
