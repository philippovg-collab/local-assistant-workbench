import { useRef, useState, type FormEvent } from "react";
import { Upload } from "lucide-react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { MaterialMetadataFormSection } from "@/components/MaterialMetadataFormSection";
import type {
  KnowledgeDocumentClass,
  MaterialMetadataInput,
  MaterialUploadItemInput,
  MaterialUploadPolicy,
  SourceTrustLevel,
} from "@/types";
import { formatBytes } from "@/utils/format";
import {
  DEFAULT_ACCEPTED_EXTENSIONS,
  normalizeMaterialUploadPolicy,
} from "@/utils/materialUploadPolicy";
import {
  emptyMaterialMetadataFormState,
  materialKnowledgeDocumentClassLabels,
  parseTagsInput,
  sourceTrustLabels,
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
  onUpload: (input: { items: MaterialUploadItemInput[] }) => Promise<unknown>;
};

type UploadFileOverrideState = {
  expanded: boolean;
  title: string;
  knowledgeDocumentClass: KnowledgeDocumentClass | "";
  tags: string;
  sourceTrust: SourceTrustLevel | "";
};

const sourceTrustLevels: SourceTrustLevel[] = ["HIGH", "MEDIUM", "LOW", "UNKNOWN"];
const knowledgeDocumentClasses: KnowledgeDocumentClass[] = [
  "contracts",
  "regulations",
  "correspondence",
  "techdocs",
  "other",
];

const emptyUploadOverride = (): UploadFileOverrideState => ({
  expanded: false,
  title: "",
  knowledgeDocumentClass: "",
  tags: "",
  sourceTrust: "",
});

const uploadFileKey = (file: File, index: number) =>
  `${index}:${file.name}:${file.size}:${file.lastModified}`;

const uploadOverrideMetadata = (override: UploadFileOverrideState): Partial<MaterialMetadataInput> => {
  const tags = parseTagsInput(override.tags);
  return {
    ...(override.knowledgeDocumentClass ? { knowledgeDocumentClass: override.knowledgeDocumentClass } : {}),
    ...(tags.length > 0 ? { tags } : {}),
    ...(override.sourceTrust ? { sourceTrust: override.sourceTrust } : {}),
  };
};

const mergeMetadataInput = (
  base: MaterialMetadataInput,
  override: Partial<MaterialMetadataInput>,
): MaterialMetadataInput => ({
  ...base,
  ...override,
});

export function MaterialUploadForm({
  uploadPolicy,
  policyWarning,
  metadataV1Enabled,
  metadataDisabledReason,
  onUpload,
}: MaterialUploadFormProps) {
  const [uploadTitle, setUploadTitle] = useState("");
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [uploadMetadata, setUploadMetadata] = useState(emptyMaterialMetadataFormState);
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
    const commonMetadata = metadataV1Enabled ? toMaterialMetadataInput(uploadMetadata) : undefined;
    return uploadFiles.map((file, index) => {
      const override = uploadFileOverrides[uploadFileKey(file, index)] ?? emptyUploadOverride();
      const overrideMetadata = metadataV1Enabled ? uploadOverrideMetadata(override) : {};
      const metadata = metadataV1Enabled && commonMetadata
        ? mergeMetadataInput(commonMetadata, overrideMetadata)
        : undefined;
      const title = override.title.trim() || (!isMultipleUpload ? uploadTitle.trim() : "");

      return {
        file,
        ...(title ? { title } : {}),
        ...(metadata ? { metadata } : {}),
      };
    });
  };

  const handleUploadSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (uploadFiles.length === 0) {
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
        items: buildUploadItems(),
      });
      setUploadTitle("");
      setUploadFiles([]);
      setUploadFileOverrides({});
      setUploadMetadata(emptyMaterialMetadataFormState());
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
            state={uploadMetadata}
            onChange={(next) => {
              setUploadMetadata(next);
              if (uploadMetadataValidation) {
                setUploadMetadataValidation(null);
              }
            }}
          />

          {uploadFiles.length > 0 ? (
            <div className="space-y-3">
              <div>
                <p className="text-sm font-semibold text-foreground">Выбранные файлы</p>
                <p className="text-sm text-muted-foreground">
                  Общие атрибуты применятся ко всем файлам. Для конкретного файла можно переопределить название,
                  класс корпуса, теги и доверие.
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
                          {override.expanded ? "Скрыть атрибуты" : "Настроить атрибуты"}
                        </Button>
                      </div>

                      {override.expanded ? (
                        <div className="mt-4 grid gap-4 md:grid-cols-2">
                          <div className="space-y-2 md:col-span-2">
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

                          <div className="space-y-2">
                            <Label>Класс корпуса</Label>
                            <Select
                              disabled={!metadataV1Enabled}
                              value={override.knowledgeDocumentClass || "inherit"}
                              onValueChange={(value) =>
                                updateUploadOverride(file, index, (current) => ({
                                  ...current,
                                  knowledgeDocumentClass: value === "inherit" ? "" : (value as KnowledgeDocumentClass),
                                }))
                              }
                            >
                              <SelectTrigger aria-label={`Класс корпуса для ${file.name || "файла"}`}>
                                <SelectValue placeholder="Как в общих атрибутах" />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="inherit">Как в общих атрибутах</SelectItem>
                                {knowledgeDocumentClasses.map((documentClass) => (
                                  <SelectItem key={documentClass} value={documentClass}>
                                    {materialKnowledgeDocumentClassLabels[documentClass]}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>

                          <div className="space-y-2">
                            <Label>Доверие</Label>
                            <Select
                              disabled={!metadataV1Enabled}
                              value={override.sourceTrust || "inherit"}
                              onValueChange={(value) =>
                                updateUploadOverride(file, index, (current) => ({
                                  ...current,
                                  sourceTrust: value === "inherit" ? "" : (value as SourceTrustLevel),
                                }))
                              }
                            >
                              <SelectTrigger aria-label={`Доверие для ${file.name || "файла"}`}>
                                <SelectValue placeholder="Как в общих атрибутах" />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="inherit">Как в общих атрибутах</SelectItem>
                                {sourceTrustLevels.map((sourceTrust) => (
                                  <SelectItem key={sourceTrust} value={sourceTrust}>
                                    {sourceTrustLabels[sourceTrust]}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </div>

                          <div className="space-y-2 md:col-span-2">
                            <Label htmlFor={`upload-file-tags-${index}`}>Теги / ключевые слова</Label>
                            <Input
                              disabled={!metadataV1Enabled}
                              id={`upload-file-tags-${index}`}
                              placeholder="Оставь пустым, чтобы использовать общие теги"
                              value={override.tags}
                              onChange={(event) =>
                                updateUploadOverride(file, index, (current) => ({
                                  ...current,
                                  tags: event.target.value,
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

          <Button disabled={isUploading || uploadFiles.length === 0} type="submit">
            <Upload className="h-4 w-4" />
            {isUploading ? "Загружаем..." : isMultipleUpload ? "Загрузить файлы" : "Загрузить файл"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
