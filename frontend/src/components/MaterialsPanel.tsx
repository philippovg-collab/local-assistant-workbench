import { useRef, useState, type FormEvent } from "react";
import {
  FileClock,
  FileSearch,
  FileText,
  Filter,
  History,
  RefreshCcw,
  Trash2,
  Upload,
} from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { MaterialMetadataDisplay } from "@/components/MaterialMetadataDisplay";
import { MaterialMetadataFormSection } from "@/components/MaterialMetadataFormSection";
import type {
  MaterialLineageResponse,
  MaterialMetadataInput,
  MaterialLineageVersion,
  MaterialPdfUploadPolicy,
  MaterialSummary,
  MaterialUploadPolicy,
} from "@/types";
import { formatBytes, formatDate } from "@/utils/format";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  normalizeMaterialUploadPolicy,
} from "@/utils/materialUploadPolicy";
import {
  emptyMaterialMetadataFormState,
  type MaterialMetadataValidation,
  toMaterialMetadataInput,
  validateMaterialMetadata,
} from "@/utils/materialMetadata";
import { isActiveMaterialVersion, type RagReadinessPresentation } from "@/utils/readiness";

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
  metadataV1Enabled: boolean;
  onCreateText: (input: { title: string; content: string; metadata?: MaterialMetadataInput }) => Promise<unknown>;
  onUpload: (input: { title: string; file: File; metadata?: MaterialMetadataInput }) => Promise<unknown>;
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

const materialStatusVariant: Record<MaterialSummary["status"], BadgeProps["variant"]> = {
  PENDING: "secondary",
  IN_PROGRESS: "secondary",
  READY: "success",
  PARTIAL_READY: "warning",
  FAILED: "destructive",
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
  metadataV1Enabled,
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
  const [textMetadata, setTextMetadata] = useState(emptyMaterialMetadataFormState);
  const [uploadMetadata, setUploadMetadata] = useState(emptyMaterialMetadataFormState);
  const [textMetadataValidation, setTextMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
  const [uploadMetadataValidation, setUploadMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
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
    if (metadataV1Enabled) {
      const validation = validateMaterialMetadata(textMetadata);
      if (!validation.isValid) {
        setTextMetadataValidation(validation);
        return;
      }
    }

    setIsSavingText(true);

    try {
      setTextMetadataValidation(null);
      await onCreateText({
        title: textTitle,
        content: textContent,
        ...(metadataV1Enabled ? { metadata: toMaterialMetadataInput(textMetadata) } : {}),
      });
      setTextTitle("");
      setTextContent("");
      setTextMetadata(emptyMaterialMetadataFormState());
    } finally {
      setIsSavingText(false);
    }
  };

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!uploadFile) {
      return;
    }

    if (metadataV1Enabled) {
      const validation = validateMaterialMetadata(uploadMetadata);
      if (!validation.isValid) {
        setUploadMetadataValidation(validation);
        return;
      }
    }

    setIsUploading(true);
    try {
      setUploadMetadataValidation(null);
      await onUpload({
        title: uploadTitle,
        file: uploadFile,
        ...(metadataV1Enabled ? { metadata: toMaterialMetadataInput(uploadMetadata) } : {}),
      });
      setUploadTitle("");
      setUploadFile(null);
      setUploadMetadata(emptyMaterialMetadataFormState());
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
    <div className="space-y-6">
      <SectionIntro
        badge="GET /api/materials"
        badgeVariant="default"
        description="Материалы сначала принимаются backend и попадают в каталог, а embeddings/index строятся отдельным lifecycle со статусами `PENDING`, `IN_PROGRESS`, `READY`, `PARTIAL_READY` и `FAILED`."
        eyebrow="Knowledge Base"
        title="Материалы для RAG"
      />

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <SectionIntro
              eyebrow="Create"
              title="Добавить текст"
              description={metadataV1Enabled
                ? "Быстрый путь для FAQ, заметок и кратких фрагментов. Metadata уходит в тот же material contract, а пустые рекомендованные поля backend попробует auto-fill по title и первым строкам текста."
                : "Быстрый путь для FAQ, заметок и кратких фрагментов. Metadata capture сейчас отключён rollout-флагом, поэтому текст сохраняется без metadata payload."}
            />
          </CardHeader>
          <CardContent className="mt-0">
            <form className="space-y-4" onSubmit={handleTextSubmit}>
              <div className="space-y-2">
                <Label htmlFor="text-material-title">Название</Label>
                <Input
                  id="text-material-title"
                  placeholder="Например: Pricing note"
                  value={textTitle}
                  onChange={(event) => setTextTitle(event.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="text-material-content">Содержимое</Label>
                <Textarea
                  id="text-material-content"
                  placeholder="Вставь сюда фрагмент документа, FAQ или инструкции."
                  required
                  rows={9}
                  value={textContent}
                  onChange={(event) => setTextContent(event.target.value)}
                />
              </div>

              {metadataV1Enabled ? (
                <MaterialMetadataFormSection
                  errors={textMetadataValidation?.fieldErrors}
                  idPrefix="text-material"
                  state={textMetadata}
                  onChange={(next) => {
                    setTextMetadata(next);
                    if (textMetadataValidation) {
                      setTextMetadataValidation(null);
                    }
                  }}
                />
              ) : (
                <Alert>
                  <AlertTitle>Metadata capture disabled</AlertTitle>
                  <AlertDescription>
                    Rollout flag metadata-v1 is off, so this form will not validate or send metadata.
                  </AlertDescription>
                </Alert>
              )}

              {metadataV1Enabled && textMetadataValidation?.messages.length ? (
                <Alert variant="destructive">
                  <AlertTitle>Metadata заполнены не полностью</AlertTitle>
                  <AlertDescription>{textMetadataValidation.messages.join(" ")}</AlertDescription>
                </Alert>
              ) : null}

              <Button disabled={isSavingText || !textContent.trim()} type="submit">
                <FileText className="h-4 w-4" />
                {isSavingText ? "Сохраняем..." : "Сохранить текст"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <SectionIntro
              eyebrow="Upload"
              title="Загрузить файл"
              description={metadataV1Enabled
                ? "Файлы проходят через upload policy backend, после чего индекс и OCR-обработка строятся асинхронно. Пустые рекомендованные поля backend попробует auto-fill по filename, media type и content header."
                : "Файлы проходят через upload policy backend, после чего индекс и OCR-обработка строятся асинхронно. Metadata capture сейчас отключён rollout-флагом."}
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            <form className="space-y-4" onSubmit={handleUploadSubmit}>
              <div className="space-y-2">
                <Label htmlFor="upload-material-title">Название</Label>
                <Input
                  id="upload-material-title"
                  placeholder="Опционально переименуй материал"
                  value={uploadTitle}
                  onChange={(event) => setUploadTitle(event.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="upload-material-file">Файл</Label>
                <Input
                  ref={fileInputRef}
                  accept={acceptAttribute}
                  id="upload-material-file"
                  required
                  type="file"
                  onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
                />
              </div>

              {metadataV1Enabled ? (
                <MaterialMetadataFormSection
                  errors={uploadMetadataValidation?.fieldErrors}
                  idPrefix="upload-material"
                  state={uploadMetadata}
                  onChange={(next) => {
                    setUploadMetadata(next);
                    if (uploadMetadataValidation) {
                      setUploadMetadataValidation(null);
                    }
                  }}
                />
              ) : (
                <Alert>
                  <AlertTitle>Metadata capture disabled</AlertTitle>
                  <AlertDescription>
                    Rollout flag metadata-v1 is off, so upload will omit the multipart metadata part.
                  </AlertDescription>
                </Alert>
              )}

              <Alert>
                <AlertTitle>Upload policy</AlertTitle>
                <AlertDescription>{helperText}</AlertDescription>
              </Alert>

              {pdfCapabilityWarning ? (
                <Alert variant="warning">
                  <AlertTitle>Scanned PDF ограничены</AlertTitle>
                  <AlertDescription>{pdfCapabilityWarning}</AlertDescription>
                </Alert>
              ) : null}

              {effectivePolicyWarning ? (
                <Alert variant="warning">
                  <AlertTitle>Внимание по policy</AlertTitle>
                  <AlertDescription>{effectivePolicyWarning}</AlertDescription>
                </Alert>
              ) : null}

              {metadataV1Enabled && uploadMetadataValidation?.messages.length ? (
                <Alert variant="destructive">
                  <AlertTitle>Metadata заполнены не полностью</AlertTitle>
                  <AlertDescription>{uploadMetadataValidation.messages.join(" ")}</AlertDescription>
                </Alert>
              ) : null}

              <Button disabled={isUploading || uploadFile === null} type="submit">
                <Upload className="h-4 w-4" />
                {isUploading ? "Загружаем..." : "Загрузить файл"}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>

      {message ? (
        <Alert variant="success">
          <AlertTitle>Изменения сохранены</AlertTitle>
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      ) : null}

      {actionError ? (
        <Alert variant="destructive">
          <AlertTitle>Операция не выполнена</AlertTitle>
          <AlertDescription>{actionError}</AlertDescription>
        </Alert>
      ) : null}

      {error ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить материалы</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      ) : null}

      <Card>
        <CardHeader className="gap-5">
          <SectionIntro
            badge={ragPresentation.badgeLabel}
            badgeVariant="secondary"
            description="Список версий, фильтры проблемных материалов и операции reindex/delete собраны в одном месте."
            eyebrow="Catalog"
            title="Локальное хранилище"
          />

          {ragPresentation.materialsMessage ? (
            <Alert
              variant={
                ragPresentation.materialsMessageClassName === "warning-state"
                  ? "warning"
                  : "default"
              }
            >
              <AlertTitle>Readiness context</AlertTitle>
              <AlertDescription>{ragPresentation.materialsMessage}</AlertDescription>
            </Alert>
          ) : null}

          <div className="flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center gap-2 text-sm font-medium text-foreground">
              <Filter className="h-4 w-4 text-primary" />
              Фильтр материалов
            </span>
            {(["active", "all", "problematic"] as MaterialFilterMode[]).map((mode) => (
              <Button
                key={mode}
                size="sm"
                type="button"
                variant={filterMode === mode ? "default" : "secondary"}
                onClick={() => setFilterMode(mode)}
              >
                {filterLabels[mode]}
              </Button>
            ))}
          </div>
        </CardHeader>
        <CardContent className="mt-0 space-y-4">
          {isLoading ? (
            <EmptyState
              description="Читаем локальное хранилище backend."
              icon={FileClock}
              title="Загружаем материалы"
            />
          ) : materials.length === 0 ? (
            <EmptyState
              description="Добавь хотя бы один материал, чтобы RAG-режим мог построить ответ по контексту."
              icon={FileText}
              title="Пока пусто"
            />
          ) : visibleMaterials.length === 0 ? (
            <EmptyState
              description={
                filterMode === "problematic"
                  ? "FAILED и PARTIAL_READY материалы сейчас не найдены."
                  : "Переключи фильтр, чтобы посмотреть другие версии материалов."
              }
              icon={Filter}
              title="По этому фильтру пока пусто"
            />
          ) : (
            <div className="space-y-3">
              {visibleMaterials.map((material) => (
                <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={material.id}>
                  <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                          {material.title}
                        </h3>
                        <Badge variant={materialStatusVariant[material.status]}>
                          {materialStatusLabel[material.status]}
                        </Badge>
                        <Badge
                          variant={isActiveMaterialVersion(material) ? "default" : "secondary"}
                        >
                          {versionStateLabel(material)}
                        </Badge>
                      </div>
                      <p className="text-sm leading-6 text-muted-foreground">
                        {material.originalFileName ?? material.sourceType} · {material.contentLength} символов · создан{" "}
                        {formatDate(material.createdAt)}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        disabled={loadingLineageMaterialId === material.id}
                        size="sm"
                        type="button"
                        variant="secondary"
                        onClick={() => void onLoadLineage(material.id)}
                      >
                        <History className="h-4 w-4" />
                        {loadingLineageMaterialId === material.id ? "Загружаем историю..." : "История"}
                      </Button>
                      {isReindexAllowed(material) ? (
                        <Button
                          disabled={reindexingMaterialId === material.id}
                          size="sm"
                          type="button"
                          variant="outline"
                          onClick={() => void onReindex(material.id)}
                        >
                          <RefreshCcw className="h-4 w-4" />
                          {reindexingMaterialId === material.id ? "Повторяем..." : "Повторить индекс"}
                        </Button>
                      ) : null}
                      <Button
                        disabled={deletingMaterialId === material.id}
                        size="sm"
                        type="button"
                        variant="destructive"
                        onClick={() => void handleDelete(material.id)}
                      >
                        <Trash2 className="h-4 w-4" />
                        {deletingMaterialId === material.id ? "Удаляем..." : "Удалить"}
                      </Button>
                    </div>
                  </div>

                  <Separator />

                  <p className="text-sm leading-7 text-foreground">{material.preview}</p>

                  <MaterialMetadataDisplay metadata={material.metadata} />

                  <div className="flex flex-wrap gap-3 text-sm leading-6 text-muted-foreground">
                    <span>Обновлён {formatDate(material.updatedAt ?? material.createdAt)}</span>
                    {(material.indexingAttempts ?? 0) > 0 ? (
                      <span>Попыток индексации: {material.indexingAttempts}</span>
                    ) : null}
                    {formatRetryMeta(material) ? <span>{formatRetryMeta(material)}</span> : null}
                  </div>

                  {!isActiveMaterialVersion(material) ? (
                    <Alert variant="warning">
                      <AlertTitle>Историческая версия</AlertTitle>
                      <AlertDescription>
                        Эта версия сохранена для аудита, но исключена из retrieval и ready-count.
                      </AlertDescription>
                    </Alert>
                  ) : null}

                  {material.statusReasonMessage ? (
                    <Alert variant={material.status === "FAILED" ? "destructive" : "default"}>
                      <AlertTitle>Причина статуса</AlertTitle>
                      <AlertDescription>{material.statusReasonMessage}</AlertDescription>
                    </Alert>
                  ) : null}
                </article>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {lineageError ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить lineage</AlertTitle>
          <AlertDescription>{lineageError}</AlertDescription>
        </Alert>
      ) : null}

      {selectedLineage ? (
        <Card>
          <CardHeader>
            <SectionIntro
              actions={
                <Button size="sm" type="button" variant="secondary" onClick={onClearLineage}>
                  Скрыть историю
                </Button>
              }
              description={`Активная версия: ${selectedLineage.activeMaterialId ?? "сейчас отсутствует"}`}
              eyebrow="Lineage"
              title="История версий"
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-3">
            {lineageVersions(selectedLineage).map((version) => (
              <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={version.id}>
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                        {version.title}
                      </h3>
                      <Badge variant={materialStatusVariant[version.status]}>
                        {materialStatusLabel[version.status]}
                      </Badge>
                      <Badge variant="secondary">
                        {version.id === selectedLineage.requestedMaterialId
                          ? "Запрошена"
                          : versionStateLabel(version)}
                      </Badge>
                    </div>
                    <p className="text-sm leading-6 text-muted-foreground">{formatLineageMeta(version)}</p>
                  </div>
                </div>

                <Separator />
                <p className="text-sm leading-7 text-foreground">{version.preview}</p>

                <MaterialMetadataDisplay metadata={version.metadata} />

                {version.versionState === "SUPERSEDED" ? (
                  <Alert variant="warning">
                    <AlertTitle>Почему версия ушла в историю</AlertTitle>
                    <AlertDescription>{translateSupersedeReason(version.supersedeReason)}</AlertDescription>
                  </Alert>
                ) : null}

                {version.statusReasonMessage ? (
                  <Alert variant="default">
                    <AlertTitle>Причина статуса</AlertTitle>
                    <AlertDescription>{version.statusReasonMessage}</AlertDescription>
                  </Alert>
                ) : null}
              </article>
            ))}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
