import { useRef, useState, type FormEvent } from "react";
import type {
  MaterialLineageResponse,
  MaterialLineageVersion,
  MaterialPdfUploadPolicy,
  MaterialSummary,
  MaterialUploadPolicy,
} from "../types";
import { formatBytes, formatDate } from "../utils/format";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  normalizeMaterialUploadPolicy,
} from "../utils/materialUploadPolicy";
import { isActiveMaterialVersion, type RagReadinessPresentation } from "../utils/readiness";

type MaterialsPanelProps = {
  materials: MaterialSummary[];
  ragPresentation: RagReadinessPresentation;
  uploadPolicy: MaterialUploadPolicy | null;
  policyWarning: string | null;
  isLoading: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingMaterialId: string | null;
  reindexingMaterialId: string | null;
  selectedLineage: MaterialLineageResponse | null;
  lineageError: string | null;
  loadingLineageMaterialId: string | null;
  onCreateText: (input: { title: string; content: string }) => Promise<unknown>;
  onUpload: (input: { title: string; file: File }) => Promise<unknown>;
  onDelete: (materialId: string) => Promise<unknown>;
  onReindex: (materialId: string) => Promise<unknown>;
  onLoadLineage: (materialId: string) => Promise<unknown>;
  onClearLineage: () => void;
};

type MaterialFilterMode = "active" | "all" | "problematic";

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

const materialStatusLabel: Record<MaterialSummary["status"], string> = {
  PENDING: "Индексируется",
  IN_PROGRESS: "В обработке",
  READY: "Готов",
  PARTIAL_READY: "Частично готов",
  FAILED: "Ошибка",
};

const filterLabels: Record<MaterialFilterMode, string> = {
  active: "Только активные",
  all: "Все версии",
  problematic: "Проблемные",
};

const versionStateLabel = (material: MaterialSummary) =>
  isActiveMaterialVersion(material) ? "Активная версия" : "Старая версия";

const isProblematicMaterial = (material: MaterialSummary) =>
  material.status === "FAILED" || material.status === "PARTIAL_READY";

const isReindexAllowed = (material: MaterialSummary) =>
  isActiveMaterialVersion(material)
  && (material.status === "FAILED" || material.status === "PARTIAL_READY");

const translateSupersedeReason = (reason: string | null | undefined) => {
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

const formatRetryMeta = (material: MaterialSummary) => {
  if (!material.nextRetryAt) {
    return null;
  }

  return `Следующий retry: ${formatDate(material.nextRetryAt)}`;
};

const visibleMaterialsForFilter = (materials: MaterialSummary[], filterMode: MaterialFilterMode) => {
  if (filterMode === "active") {
    return materials.filter(isActiveMaterialVersion);
  }
  if (filterMode === "problematic") {
    return materials.filter(isProblematicMaterial);
  }
  return materials;
};

const lineageVersions = (lineage: MaterialLineageResponse | null) => lineage?.versions ?? [];

const formatLineageMeta = (version: MaterialLineageVersion) => {
  const segments = [
    version.originalFileName ?? version.sourceType,
    `${version.contentLength} символов`,
    `Создана ${formatDate(version.createdAt)}`,
    `Обновлена ${formatDate(version.updatedAt)}`,
  ];

  if (version.indexingAttempts > 0) {
    segments.push(`Попыток индексации: ${version.indexingAttempts}`);
  }

  if (version.nextRetryAt) {
    segments.push(`Retry: ${formatDate(version.nextRetryAt)}`);
  }

  return segments.join(" · ");
};

export function MaterialsPanel({
  materials,
  ragPresentation,
  uploadPolicy,
  policyWarning,
  isLoading,
  error,
  message,
  actionError,
  deletingMaterialId,
  reindexingMaterialId,
  selectedLineage,
  lineageError,
  loadingLineageMaterialId,
  onCreateText,
  onUpload,
  onDelete,
  onReindex,
  onLoadLineage,
  onClearLineage,
}: MaterialsPanelProps) {
  const [textTitle, setTextTitle] = useState("");
  const [textContent, setTextContent] = useState("");
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [isSavingText, setIsSavingText] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [filterMode, setFilterMode] = useState<MaterialFilterMode>("active");
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
  const visibleMaterials = visibleMaterialsForFilter(materials, filterMode);

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
        Материалы сначала принимаются backend и попадают в каталог, а embeddings/index строятся отдельным lifecycle со статусами `PENDING`, `IN_PROGRESS`, `READY`, `PARTIAL_READY` и `FAILED`.
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
        <span className="badge">{ragPresentation.badgeLabel}</span>
      </div>
      {ragPresentation.materialsMessage ? (
        <p className={ragPresentation.materialsMessageClassName}>{ragPresentation.materialsMessage}</p>
      ) : null}

      <div className="filter-strip" role="tablist" aria-label="Фильтр материалов">
        {(["active", "all", "problematic"] as MaterialFilterMode[]).map((mode) => (
          <button
            key={mode}
            className={`filter-chip ${filterMode === mode ? "active" : ""}`}
            type="button"
            onClick={() => setFilterMode(mode)}
          >
            {filterLabels[mode]}
          </button>
        ))}
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
      ) : visibleMaterials.length === 0 ? (
        <div className="empty-state">
          <strong>По этому фильтру пока пусто</strong>
          <span>
            {filterMode === "problematic"
              ? "FAILED и PARTIAL_READY материалы сейчас не найдены."
              : "Переключи фильтр, чтобы посмотреть другие версии материалов."}
          </span>
        </div>
      ) : (
        <div className="stack-list">
          {visibleMaterials.map((material) => (
            <article className="item-card" key={material.id}>
              <div className="item-row">
                <div>
                  <h3>{material.title}</h3>
                  <p className="item-meta">
                    {material.originalFileName ?? material.sourceType} · {material.contentLength} символов · создан {formatDate(material.createdAt)}
                  </p>
                </div>
                <div className="item-actions">
                  <span className="badge subtle">{materialStatusLabel[material.status]}</span>
                  <span className="badge subtle">{versionStateLabel(material)}</span>
                  <button
                    className="secondary-button"
                    disabled={loadingLineageMaterialId === material.id}
                    type="button"
                    onClick={() => void onLoadLineage(material.id)}
                  >
                    {loadingLineageMaterialId === material.id ? "Загружаем историю..." : "История"}
                  </button>
                  {isReindexAllowed(material) ? (
                    <button
                      className="secondary-button"
                      disabled={reindexingMaterialId === material.id}
                      type="button"
                      onClick={() => void onReindex(material.id)}
                    >
                      {reindexingMaterialId === material.id ? "Повторяем..." : "Повторить индекс"}
                    </button>
                  ) : null}
                  <button
                    className="danger-button"
                    disabled={deletingMaterialId === material.id}
                    type="button"
                    onClick={() => void handleDelete(material.id)}
                  >
                    {deletingMaterialId === material.id ? "Удаляем..." : "Удалить"}
                  </button>
                </div>
              </div>
              <p>{material.preview}</p>
              <p className="item-meta">Обновлён {formatDate(material.updatedAt ?? material.createdAt)}</p>
              {(material.indexingAttempts ?? 0) > 0 ? (
                <p className="item-meta">Попыток индексации: {material.indexingAttempts}</p>
              ) : null}
              {formatRetryMeta(material) ? <p className="item-meta">{formatRetryMeta(material)}</p> : null}
              {!isActiveMaterialVersion(material) ? (
                <p className="item-meta">Эта версия сохранена для аудита, но исключена из retrieval и ready-count.</p>
              ) : null}
              {material.statusReasonMessage ? <p className="item-meta">{material.statusReasonMessage}</p> : null}
            </article>
          ))}
        </div>
      )}

      {lineageError ? (
        <div className="empty-state warning-state">
          <strong>Не удалось загрузить lineage</strong>
          <span>{lineageError}</span>
        </div>
      ) : null}

      {selectedLineage ? (
        <section className="lineage-panel">
          <div className="panel-header compact">
            <div>
              <h3>История версий</h3>
              <p className="item-meta">
                Активная версия: {selectedLineage.activeMaterialId ?? "сейчас отсутствует"}
              </p>
            </div>
            <button className="secondary-button" type="button" onClick={onClearLineage}>
              Скрыть историю
            </button>
          </div>

          <div className="stack-list">
            {lineageVersions(selectedLineage).map((version) => (
              <article className="item-card" key={version.id}>
                <div className="item-row">
                  <div>
                    <h3>{version.title}</h3>
                    <p className="item-meta">{formatLineageMeta(version)}</p>
                  </div>
                  <div className="item-actions">
                    <span className="badge subtle">{materialStatusLabel[version.status]}</span>
                    <span className="badge subtle">
                      {version.id === selectedLineage.requestedMaterialId ? "Запрошена" : versionStateLabel(version)}
                    </span>
                  </div>
                </div>
                <p>{version.preview}</p>
                {version.versionState === "SUPERSEDED" ? (
                  <p className="item-meta">{translateSupersedeReason(version.supersedeReason)}</p>
                ) : null}
                {version.statusReasonMessage ? <p className="item-meta">{version.statusReasonMessage}</p> : null}
              </article>
            ))}
          </div>
        </section>
      ) : null}
    </article>
  );
}
