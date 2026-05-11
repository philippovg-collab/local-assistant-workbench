import { useRef, useState, type FormEvent } from "react";
import { Upload } from "lucide-react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MaterialMetadataFormSection } from "@/components/MaterialMetadataFormSection";
import type {
  MaterialLineageOverrideInput,
  MaterialMetadataInput,
  MaterialUploadItemInput,
  MaterialUploadPolicy,
  ReferenceProject,
  ReferenceWorkspace,
} from "@/types";
import { formatBytes } from "@/utils/format";
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
import { translatePdfCapabilityReason } from "@/utils/materialPresentation";

type MaterialUploadFormProps = {
  uploadPolicy: MaterialUploadPolicy | null;
  policyWarning: string | null;
  metadataV1Enabled: boolean;
  metadataDisabledReason: string;
  referenceWorkspaces: ReferenceWorkspace[];
  referenceProjects: ReferenceProject[];
  isReferenceDataLoading: boolean;
  referenceDataError: string | null;
  activeWorkspaceKey?: string | null;
  activeWorkspaceName?: string | null;
  onUpload: (input: { items: MaterialUploadItemInput[] }) => Promise<unknown>;
};

type UploadFileOverrideState = {
  expanded: boolean;
  title: string;
};

const emptyUploadOverride = (): UploadFileOverrideState => ({
  expanded: false,
  title: "",
});

const uploadFileKey = (file: File, index: number) =>
  `${index}:${file.name}:${file.size}:${file.lastModified}`;

export function MaterialUploadForm({
  uploadPolicy,
  policyWarning,
  metadataV1Enabled,
  metadataDisabledReason,
  referenceWorkspaces,
  referenceProjects,
  isReferenceDataLoading,
  referenceDataError,
  activeWorkspaceKey,
  activeWorkspaceName,
  onUpload,
}: MaterialUploadFormProps) {
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [uploadMetadata, setUploadMetadata] = useState(emptyMaterialMetadataFormState);
  const [lineageOverrideEnabled, setLineageOverrideEnabled] = useState(false);
  const [lineageOverrideKey, setLineageOverrideKey] = useState("");
  const [lineageOverrideReason, setLineageOverrideReason] = useState("");
  const [confirmLineageReuse, setConfirmLineageReuse] = useState(false);
  const [uploadFileOverrides, setUploadFileOverrides] = useState<Record<string, UploadFileOverrideState>>({});
  const [uploadMetadataValidation, setUploadMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
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
  const isMultipleUpload = uploadFiles.length > 1;
  const normalizedActiveWorkspaceKey = (activeWorkspaceKey ?? "").trim();
  const normalizedActiveWorkspaceName = activeWorkspaceName ?? "";
  const isMetadataUnavailable = metadataV1Enabled
    && (isReferenceDataLoading || Boolean(referenceDataError) || !normalizedActiveWorkspaceKey);

  const withActiveWorkspace = () => ({
    ...uploadMetadata,
    workspaceKey: normalizedActiveWorkspaceKey,
    projectKey: "",
  });

  const updateUploadOverride = (
    file: File,
    index: number,
    updater: (current: UploadFileOverrideState) => UploadFileOverrideState,
  ) => {
    const key = uploadFileKey(file, index);
    setUploadFileOverrides((current) => ({
      ...current,
      [key]: updater(current[key] ?? emptyUploadOverride()),
    }));
  };

  const buildUploadItems = (): MaterialUploadItemInput[] => {
    const commonMetadata = metadataV1Enabled ? toMaterialMetadataInput(withActiveWorkspace()) : undefined;
    const lineageOverride: MaterialLineageOverrideInput | undefined = lineageOverrideEnabled
      ? {
          lineageKey: lineageOverrideKey.trim(),
          reason: lineageOverrideReason.trim(),
          confirmSupersedeExistingLineage: confirmLineageReuse,
        }
      : undefined;
    return uploadFiles.map((file, index) => {
      const override = uploadFileOverrides[uploadFileKey(file, index)] ?? emptyUploadOverride();
      const metadata = metadataV1Enabled ? commonMetadata : undefined;
      const title = override.title.trim() || (!isMultipleUpload ? uploadTitle.trim() : "");

      return {
        file,
        ...(title ? { title } : {}),
        ...(metadata ? { metadata } : {}),
        ...(lineageOverride ? { lineageOverride } : {}),
      };
    });
  };

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (uploadFiles.length === 0) {
      return;
    }

    if (isMetadataUnavailable) {
      return;
    }

    if (metadataV1Enabled) {
      const validation = validateMaterialMetadata(withActiveWorkspace());
      if (!validation.isValid) {
        setUploadMetadataValidation(validation);
        return;
      }
    }

    setIsUploading(true);
    try {
      setUploadMetadataValidation(null);
      await onUpload({
        items: buildUploadItems(),
      });
      setUploadTitle("");
      setUploadFiles([]);
      setUploadFileOverrides({});
      setUploadMetadata(emptyMaterialMetadataFormState());
      setLineageOverrideEnabled(false);
      setLineageOverrideKey("");
      setLineageOverrideReason("");
      setConfirmLineageReuse(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } finally {
      setIsUploading(false);
    }
  };

  return (
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
              disabled={isMultipleUpload}
              id="upload-material-title"
              placeholder="Опционально переименуй материал"
              value={uploadTitle}
              onChange={(event) => setUploadTitle(event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="upload-material-file">Файлы</Label>
            <Input
              ref={fileInputRef}
              accept={acceptAttribute}
              id="upload-material-file"
              multiple
              required
              type="file"
              onChange={(event) => {
                const nextFiles = Array.from(event.target.files ?? []);
                setUploadFiles(nextFiles);
                setUploadFileOverrides((current) => {
                  const nextOverrides: Record<string, UploadFileOverrideState> = {};
                  nextFiles.forEach((file, index) => {
                    const key = uploadFileKey(file, index);
                    nextOverrides[key] = current[key] ?? emptyUploadOverride();
                  });
                  return nextOverrides;
                });
                if (nextFiles.length > 1) {
                  setUploadTitle("");
                }
              }}
            />
          </div>

          {!metadataV1Enabled ? (
            <Alert>
              <AlertTitle>Metadata capture отключён</AlertTitle>
              <AlertDescription>
                Rollout flag metadata-v1 выключен, поэтому upload отправит файл без multipart metadata part.
              </AlertDescription>
            </Alert>
          ) : null}

          <MaterialMetadataFormSection
            disabled={!metadataV1Enabled}
            disabledReason={metadataDisabledReason}
            errors={uploadMetadataValidation?.fieldErrors}
            idPrefix="upload-material"
            isReferenceDataLoading={isReferenceDataLoading}
            lockedWorkspaceKey={normalizedActiveWorkspaceKey}
            lockedWorkspaceName={normalizedActiveWorkspaceName}
            hideProjectField
            projects={referenceProjects}
            referenceDataError={referenceDataError}
            state={uploadMetadata}
            workspaces={referenceWorkspaces}
            onChange={(next) => {
              setUploadMetadata(next);
              if (uploadMetadataValidation) {
                setUploadMetadataValidation(null);
              }
            }}
          />

          <div className="rounded-2xl border border-field-border bg-field px-4 py-4">
            <label className="flex items-center gap-2 text-sm font-medium text-foreground">
              <Checkbox
                checked={lineageOverrideEnabled}
                onCheckedChange={(checked) => setLineageOverrideEnabled(checked === true)}
              />
              Lineage override
            </label>
            {lineageOverrideEnabled ? (
              <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                <div className="space-y-2">
                  <Label htmlFor="upload-lineage-override-key">Override key</Label>
                  <Input
                    id="upload-lineage-override-key"
                    placeholder="golden-policy-2026"
                    value={lineageOverrideKey}
                    onChange={(event) => setLineageOverrideKey(event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="upload-lineage-override-reason">Reason</Label>
                  <Input
                    id="upload-lineage-override-reason"
                    placeholder="Operator-controlled versioned document"
                    value={lineageOverrideReason}
                    onChange={(event) => setLineageOverrideReason(event.target.value)}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm font-medium text-foreground lg:col-span-2">
                  <Checkbox
                    checked={confirmLineageReuse}
                    onCheckedChange={(checked) => setConfirmLineageReuse(checked === true)}
                  />
                  Confirm reuse
                </label>
              </div>
            ) : null}
          </div>

          {uploadFiles.length > 0 ? (
            <div className="space-y-3">
              <div>
                <p className="text-sm font-semibold text-foreground">Выбранные файлы</p>
                <p className="text-sm text-muted-foreground">
                  Общие метаданные применятся ко всем файлам. Для конкретного файла можно переопределить только название.
                </p>
              </div>

              <div className="space-y-3">
                {uploadFiles.map((file, index) => {
                  const override = uploadFileOverrides[uploadFileKey(file, index)] ?? emptyUploadOverride();
                  return (
                    <article className="rounded-[22px] border border-field-border bg-field px-4 py-4" key={uploadFileKey(file, index)}>
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <p className="text-sm font-semibold text-foreground">{file.name || "Файл"}</p>
                          <p className="text-sm text-muted-foreground">{formatBytes(file.size)}</p>
                        </div>
                        <Button
                          size="sm"
                          type="button"
                          variant="secondary"
                          onClick={() =>
                            updateUploadOverride(file, index, (current) => ({
                              ...current,
                              expanded: !current.expanded,
                            }))
                          }
                        >
                          {override.expanded ? "Скрыть название" : "Настроить название"}
                        </Button>
                      </div>

                      {override.expanded ? (
                        <div className="mt-4">
                          <div className="space-y-2">
                            <Label htmlFor={`upload-file-title-${index}`}>Название файла</Label>
                            <Input
                              id={`upload-file-title-${index}`}
                              placeholder="Оставь пустым, чтобы backend использовал имя файла"
                              value={override.title}
                              onChange={(event) =>
                                updateUploadOverride(file, index, (current) => ({
                                  ...current,
                                  title: event.target.value,
                                }))
                              }
                            />
                          </div>
                        </div>
                      ) : null}
                    </article>
                  );
                })}
              </div>
            </div>
          ) : null}

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

          <Button
            disabled={
              isUploading
              || uploadFiles.length === 0
              || isMetadataUnavailable
              || (lineageOverrideEnabled && (!lineageOverrideKey.trim() || !lineageOverrideReason.trim()))
            }
            type="submit"
          >
            <Upload className="h-4 w-4" />
            {isUploading ? "Загружаем..." : isMultipleUpload ? "Загрузить файлы" : "Загрузить файл"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
