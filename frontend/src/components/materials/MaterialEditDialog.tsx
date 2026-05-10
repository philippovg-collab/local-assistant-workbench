import { type FormEvent } from "react";
import { FilePenLine, Save } from "lucide-react";
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
  MaterialMetadataInput,
  MaterialSummary,
  ReferenceProject,
  ReferenceWorkspace,
  UpdateMaterialInput,
} from "@/types";
import { useMaterialEditorDialog } from "@/hooks/useMaterialEditorDialog";
import {
  toMaterialMetadataInput,
  validateMaterialMetadata,
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
  const editor = useMaterialEditorDialog(material, activeWorkspaceKey);
  const isOpen = material !== null;
  const normalizedActiveWorkspaceKey = (activeWorkspaceKey ?? "").trim();
  const normalizedActiveWorkspaceName = activeWorkspaceName ?? "";
  const isMetadataUnavailable = metadataV1Enabled
    && (isReferenceDataLoading || Boolean(referenceDataError) || !normalizedActiveWorkspaceKey);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!material || !editor.detail || isMetadataUnavailable) {
      return;
    }

    if (!editor.content.trim()) {
      editor.setSubmitError("Добавь содержимое материала.");
      return;
    }

    let nextMetadata: MaterialMetadataInput | undefined;
    if (metadataV1Enabled) {
      const effectiveMetadata = editor.withActiveWorkspace(editor.metadata);
      const validation = validateMaterialMetadata(effectiveMetadata);
      if (!validation.isValid) {
        editor.setMetadataValidation(validation);
        return;
      }
      nextMetadata = toMaterialMetadataInput(effectiveMetadata);
    }

    try {
      editor.setSubmitError(null);
      editor.setMetadataValidation(null);
      await onUpdateMaterial(material.id, {
        title: editor.title,
        content: editor.content,
        ...(nextMetadata ? { metadata: nextMetadata } : {}),
      });
      onOpenChange(false);
    } catch (error) {
      editor.setSubmitError(translateMaterialError(error, "Не удалось сохранить редакцию материала", null));
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

        {editor.isLoadingDetail ? (
          <div className="rounded-2xl border border-border bg-background/70 p-4 text-sm text-muted-foreground">
            Загружаем карточку материала...
          </div>
        ) : null}

        {editor.loadError ? (
          <Alert variant="destructive">
            <AlertTitle>Материал не открыт</AlertTitle>
            <AlertDescription>{editor.loadError}</AlertDescription>
          </Alert>
        ) : null}

        {editor.detail ? (
          <form className="space-y-5" onSubmit={handleSubmit}>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)]">
              <div className="space-y-2">
                <Label htmlFor={`edit-material-title-${editor.detail.id}`}>Название</Label>
                <Input
                  disabled={isSaving}
                  id={`edit-material-title-${editor.detail.id}`}
                  value={editor.title}
                  onChange={(event) => editor.setTitle(event.target.value)}
                />
              </div>

              <div className="space-y-2">
                <Label>Источник</Label>
                <div className="rounded-2xl border border-border bg-background/70 px-3 py-2 text-sm text-muted-foreground">
                  {editor.detail.originalFileName ?? editor.detail.sourceType}
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor={`edit-material-content-${editor.detail.id}`}>Содержимое</Label>
              <Textarea
                disabled={isSaving}
                id={`edit-material-content-${editor.detail.id}`}
                required
                rows={12}
                value={editor.content}
                onChange={(event) => editor.setContent(event.target.value)}
              />
            </div>

            <MaterialMetadataFormSection
              disabled={!metadataV1Enabled || isSaving}
              disabledReason={isSaving && metadataV1Enabled ? "Сохраняем редакцию материала..." : metadataDisabledReason}
              errors={editor.metadataValidation?.fieldErrors}
              idPrefix={`edit-material-${editor.detail.id}`}
              isReferenceDataLoading={isReferenceDataLoading}
              lockedWorkspaceKey={normalizedActiveWorkspaceKey}
              lockedWorkspaceName={normalizedActiveWorkspaceName}
              hideProjectField
              projects={referenceProjects}
              referenceDataError={referenceDataError}
              state={editor.metadata}
              workspaces={referenceWorkspaces}
              onChange={(next) => {
                editor.setMetadata(next);
                if (editor.metadataValidation) {
                  editor.setMetadataValidation(null);
                }
              }}
            />

            {metadataV1Enabled && editor.metadataValidation?.messages.length ? (
              <Alert variant="destructive">
                <AlertTitle>Metadata заполнены не полностью</AlertTitle>
                <AlertDescription>{editor.metadataValidation.messages.join(" ")}</AlertDescription>
              </Alert>
            ) : null}

            {editor.submitError ? (
              <Alert variant="destructive">
                <AlertTitle>Редакция не сохранена</AlertTitle>
                <AlertDescription>{editor.submitError}</AlertDescription>
              </Alert>
            ) : null}

            <DialogFooter>
              <Button disabled={isSaving} type="button" variant="secondary" onClick={() => onOpenChange(false)}>
                Отмена
              </Button>
              <Button disabled={isSaving || !editor.content.trim() || isMetadataUnavailable} type="submit">
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
