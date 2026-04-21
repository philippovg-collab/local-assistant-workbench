import { useEffect, useState, type FormEvent } from "react";
import { FilePenLine, Save } from "lucide-react";
import { apiClient } from "@/api/client";
import { MaterialMetadataFormSection } from "@/components/MaterialMetadataFormSection";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type {
  MaterialDetail,
  MaterialMetadataInput,
  MaterialSummary,
  ReferenceProject,
  ReferenceWorkspace,
  UpdateMaterialInput,
} from "@/types";
import {
  emptyMaterialMetadataFormState,
  toMaterialMetadataFormState,
  toMaterialMetadataInput,
  validateMaterialMetadata,
  type MaterialMetadataFormState,
  type MaterialMetadataValidation,
} from "@/utils/materialMetadata";
import { translateMaterialError } from "@/utils/materialPresentation";

type MaterialEditDialogProps = {
  material: MaterialSummary | null;
  metadataDisabledReason: string;
  metadataV1Enabled: boolean;
  referenceProjects: ReferenceProject[];
  referenceWorkspaces: ReferenceWorkspace[];
  isReferenceDataLoading: boolean;
  referenceDataError: string | null;
  isSaving: boolean;
  activeWorkspaceKey?: string | null;
  activeWorkspaceName?: string | null;
  onOpenChange: (open: boolean) => void;
  onUpdateMaterial: (materialId: string, input: UpdateMaterialInput) => Promise<unknown>;
};

export function MaterialEditDialog({
  material,
  metadataDisabledReason,
  metadataV1Enabled,
  referenceProjects,
  referenceWorkspaces,
  isReferenceDataLoading,
  referenceDataError,
  isSaving,
  activeWorkspaceKey,
  activeWorkspaceName,
  onOpenChange,
  onUpdateMaterial,
}: MaterialEditDialogProps) {
  const [detail, setDetail] = useState<MaterialDetail | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [metadata, setMetadata] = useState<MaterialMetadataFormState>(emptyMaterialMetadataFormState);
  const [metadataValidation, setMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const isOpen = material !== null;
  const normalizedActiveWorkspaceKey = (activeWorkspaceKey ?? "").trim();
  const normalizedActiveWorkspaceName = activeWorkspaceName ?? "";
  const isMetadataUnavailable = metadataV1Enabled
    && (isReferenceDataLoading || Boolean(referenceDataError) || !normalizedActiveWorkspaceKey);

  const withActiveWorkspace = (current: MaterialMetadataFormState) => ({
    ...current,
    workspaceKey: normalizedActiveWorkspaceKey,
    projectKey: "",
  });

  useEffect(() => {
    if (!material) {
      setDetail(null);
      setTitle("");
      setContent("");
      setMetadata(emptyMaterialMetadataFormState());
      setMetadataValidation(null);
      setLoadError(null);
      setSubmitError(null);
      setIsLoadingDetail(false);
      return;
    }

    const controller = new AbortController();
    setIsLoadingDetail(true);
    setLoadError(null);
    setSubmitError(null);

    apiClient.fetchMaterial(material.id, { workspaceKey: normalizedActiveWorkspaceKey }, controller.signal)
      .then((loadedDetail) => {
        setDetail(loadedDetail);
        setTitle(loadedDetail.title);
        setContent(loadedDetail.content);
        setMetadata(withActiveWorkspace(toMaterialMetadataFormState(loadedDetail.metadata)));
        setMetadataValidation(null);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLoadError(translateMaterialError(error, "Не удалось открыть материал для редактирования", null));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoadingDetail(false);
        }
      });

    return () => controller.abort();
  }, [material, normalizedActiveWorkspaceKey]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!material || !detail || isMetadataUnavailable) {
      return;
    }

    if (!content.trim()) {
      setSubmitError("Добавь содержимое материала.");
      return;
    }

    let nextMetadata: MaterialMetadataInput | undefined;
    if (metadataV1Enabled) {
      const effectiveMetadata = withActiveWorkspace(metadata);
      const validation = validateMaterialMetadata(effectiveMetadata);
      if (!validation.isValid) {
        setMetadataValidation(validation);
        return;
      }
      nextMetadata = toMaterialMetadataInput(effectiveMetadata);
    }

    try {
      setSubmitError(null);
      setMetadataValidation(null);
      await onUpdateMaterial(material.id, {
        title,
        content,
        ...(nextMetadata ? { metadata: nextMetadata } : {}),
      });
      onOpenChange(false);
    } catch (error) {
      setSubmitError(translateMaterialError(error, "Не удалось сохранить редакцию материала", null));
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[88vh] max-w-5xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FilePenLine className="h-5 w-5 text-primary" />
            Редактировать материал
          </DialogTitle>
          <DialogDescription>
            Сохранение создаст новую активную версию, а текущая версия останется в истории.
          </DialogDescription>
        </DialogHeader>

        {isLoadingDetail ? (
          <div className="rounded-2xl border border-border bg-background/70 p-4 text-sm text-muted-foreground">
            Загружаем карточку материала...
          </div>
        ) : null}

        {loadError ? (
          <Alert variant="destructive">
            <AlertTitle>Материал не открыт</AlertTitle>
            <AlertDescription>{loadError}</AlertDescription>
          </Alert>
        ) : null}

        {detail ? (
          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
              <div className="space-y-2">
                <Label htmlFor={`edit-material-title-${detail.id}`}>Название</Label>
                <Input
                  disabled={isSaving}
                  id={`edit-material-title-${detail.id}`}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label>Источник</Label>
                <div className="rounded-2xl border border-border bg-background/70 px-3 py-2 text-sm text-muted-foreground">
                  {detail.originalFileName ?? detail.sourceType}
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor={`edit-material-content-${detail.id}`}>Содержимое</Label>
              <Textarea
                disabled={isSaving}
                id={`edit-material-content-${detail.id}`}
                required
                rows={12}
                value={content}
                onChange={(event) => setContent(event.target.value)}
              />
            </div>

            <MaterialMetadataFormSection
              disabled={!metadataV1Enabled || isSaving}
              disabledReason={isSaving && metadataV1Enabled ? "Сохраняем редакцию материала..." : metadataDisabledReason}
              errors={metadataValidation?.fieldErrors}
              idPrefix={`edit-material-${detail.id}`}
              isReferenceDataLoading={isReferenceDataLoading}
              lockedWorkspaceKey={normalizedActiveWorkspaceKey}
              lockedWorkspaceName={normalizedActiveWorkspaceName}
              hideProjectField
              projects={referenceProjects}
              referenceDataError={referenceDataError}
              state={metadata}
              workspaces={referenceWorkspaces}
              onChange={(next) => {
                setMetadata(next);
                if (metadataValidation) {
                  setMetadataValidation(null);
                }
              }}
            />

            {metadataV1Enabled && metadataValidation?.messages.length ? (
              <Alert variant="destructive">
                <AlertTitle>Metadata заполнены не полностью</AlertTitle>
                <AlertDescription>{metadataValidation.messages.join(" ")}</AlertDescription>
              </Alert>
            ) : null}

            {submitError ? (
              <Alert variant="destructive">
                <AlertTitle>Редакция не сохранена</AlertTitle>
                <AlertDescription>{submitError}</AlertDescription>
              </Alert>
            ) : null}

            <DialogFooter>
              <Button disabled={isSaving} type="button" variant="secondary" onClick={() => onOpenChange(false)}>
                Отмена
              </Button>
              <Button disabled={isSaving || !content.trim() || isMetadataUnavailable} type="submit">
                <Save className="h-4 w-4" />
                {isSaving ? "Сохраняем..." : "Сохранить редакцию"}
              </Button>
            </DialogFooter>
          </form>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
