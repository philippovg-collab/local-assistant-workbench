import { useMemo, useState, type FormEvent } from "react";
import { KnowledgePresetCriteriaForm } from "@/components/KnowledgePresetCriteriaForm";
import { KnowledgePresetEditorDialog } from "@/components/KnowledgePresetEditorDialog";
import { KnowledgePresetList } from "@/components/KnowledgePresetList";
import { KnowledgePresetRevisionPanel } from "@/components/KnowledgePresetRevisionPanel";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import type {
  CreateKnowledgePresetRequest,
  KnowledgePresetDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetRevisionDetail,
  KnowledgePresetSummary,
  ReferenceProject,
} from "@/types";
import {
  buildPresetPayload,
  buildSelectedScopeSummary,
  initialPresetForm,
  presetToForm,
  visiblePresetsForProject,
  type KnowledgePresetFormState,
} from "@/components/knowledgePresetPresentation";

type KnowledgePresetLibraryPanelProps = {
  presets: KnowledgePresetSummary[];
  filterKind?: "preset" | "facet";
  activeRagProjectKey?: string | null;
  activeRagProjectName?: string | null;
  projects?: ReferenceProject[];
  selectedPreset: KnowledgePresetDetail | null;
  revisions: KnowledgePresetRevisionDetail[];
  revisionDiff: KnowledgePresetRevisionDiff | null;
  error: string | null;
  actionError: string | null;
  message: string | null;
  isLoading: boolean;
  onLoadPreset: (presetId: string) => Promise<KnowledgePresetDetail | null>;
  onLoadRevisions: (presetId: string) => Promise<KnowledgePresetRevisionDetail[] | null>;
  onLoadRevisionDiff: (
    presetId: string,
    fromRevision: number,
    toRevision: number,
  ) => Promise<KnowledgePresetRevisionDiff | null>;
  onCreatePreset: (input: CreateKnowledgePresetRequest) => Promise<unknown>;
  onUpdatePreset: (presetId: string, input: CreateKnowledgePresetRequest) => Promise<unknown>;
  onRestoreRevision: (presetId: string, revision: number) => Promise<unknown>;
  onDeletePreset: (presetId: string) => Promise<unknown>;
};

export function KnowledgePresetLibraryPanel({
  presets,
  filterKind = "preset",
  activeRagProjectKey = null,
  activeRagProjectName = null,
  projects = [],
  selectedPreset,
  revisions,
  revisionDiff,
  error,
  actionError,
  message,
  isLoading,
  onLoadPreset,
  onLoadRevisions,
  onLoadRevisionDiff,
  onCreatePreset,
  onUpdatePreset,
  onRestoreRevision,
  onDeletePreset,
}: KnowledgePresetLibraryPanelProps) {
  const [createForm, setCreateForm] = useState<KnowledgePresetFormState>(initialPresetForm);
  const [editForm, setEditForm] = useState<KnowledgePresetFormState>(initialPresetForm);
  const [editingPresetId, setEditingPresetId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isEditingSubmitting, setIsEditingSubmitting] = useState(false);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const normalizedActiveRagProjectKey = activeRagProjectKey?.trim() ?? "";
  const isFacet = filterKind === "facet";
  const entityLabel = isFacet ? "фасет" : "preset";
  const entityTitle = isFacet ? "Фасеты проекта" : "Пресеты проекта";
  const activeProjectLabel = activeRagProjectName || normalizedActiveRagProjectKey || "general";
  const activeProjects = useMemo(
    () => projects.filter((project) => project.active && project.workspaceKey === normalizedActiveRagProjectKey),
    [normalizedActiveRagProjectKey, projects],
  );
  const visiblePresets = useMemo(
    () => visiblePresetsForProject(presets, normalizedActiveRagProjectKey),
    [normalizedActiveRagProjectKey, presets],
  );
  const selectedScopeSummary = useMemo(
    () => buildSelectedScopeSummary(selectedPreset, entityLabel, isFacet, activeRagProjectName),
    [activeRagProjectName, entityLabel, isFacet, selectedPreset],
  );

  const buildPayload = (form: KnowledgePresetFormState) =>
    buildPresetPayload(form, isFacet, normalizedActiveRagProjectKey);

  const loadInspector = async (presetId: string) => {
    await Promise.all([onLoadPreset(presetId), onLoadRevisions(presetId)]);
  };

  const handleEdit = async (presetId: string) => {
    const detail = selectedPreset?.id === presetId ? selectedPreset : await onLoadPreset(presetId);
    if (!detail) {
      return;
    }
    await onLoadRevisions(presetId);
    setEditingPresetId(presetId);
    setEditForm(presetToForm(detail));
    setIsEditDialogOpen(true);
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      await onCreatePreset(buildPayload(createForm));
      setCreateForm(initialPresetForm);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editingPresetId) {
      return;
    }
    setIsEditingSubmitting(true);

    try {
      await onUpdatePreset(editingPresetId, buildPayload(editForm));
      cancelEditing();
    } finally {
      setIsEditingSubmitting(false);
    }
  };

  const cancelEditing = () => {
    setIsEditDialogOpen(false);
    setEditingPresetId(null);
    setEditForm(initialPresetForm);
  };

  const handleDelete = async (presetId: string) => {
    const preset = presets.find((item) => item.id === presetId);
    const confirmed = window.confirm(`Удалить ${entityLabel} "${preset?.name ?? presetId}"?`);
    if (!confirmed) {
      return;
    }
    await onDeletePreset(presetId);
    if (editingPresetId === presetId) {
      cancelEditing();
    }
  };

  return (
    <div className="grid gap-6">
      <KnowledgePresetList
        activeProjectLabel={activeProjectLabel}
        entityTitle={entityTitle}
        error={error}
        isFacet={isFacet}
        isLoading={isLoading}
        visiblePresets={visiblePresets}
        onDelete={(presetId) => void handleDelete(presetId)}
        onEdit={(presetId) => void handleEdit(presetId)}
        onLoadInspector={(presetId) => void loadInspector(presetId)}
      />

      <div className="contents">
        <Card className="order-1">
          <CardHeader>
            <SectionIntro
              badge="Создание"
              badgeVariant="secondary"
              description={isFacet
                ? "Фасет задаёт сохранённый фильтр поиска внутри активного RAG-проекта."
                : "Preset задаёт, по какому корпусу документов стоит искать ответ. Редактирование существующего preset открывается отдельно по кнопке в списке."}
              eyebrow={isFacet ? "Facet Editor" : "Preset Editor"}
              title={isFacet ? "Создать фасет проекта" : "Создать preset проекта"}
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            <KnowledgePresetCriteriaForm
              activeProjects={activeProjects}
              activeRagProjectKey={normalizedActiveRagProjectKey}
              activeRagProjectName={activeRagProjectName}
              busyLabel="Создаём..."
              entityLabel={entityLabel}
              form={createForm}
              idPrefix="create"
              idleLabel={isFacet ? "Сохранить фасет" : "Сохранить preset"}
              isBusy={isSubmitting}
              isFacet={isFacet}
              setForm={setCreateForm}
              onSubmit={handleSubmit}
            />

            {message ? (
              <Alert variant="success">
                <AlertTitle>{isFacet ? "Фасет сохранён" : "Knowledge preset сохранён"}</AlertTitle>
                <AlertDescription>{message}</AlertDescription>
              </Alert>
            ) : null}

            {actionError ? (
              <Alert variant="destructive">
                <AlertTitle>Не удалось применить изменение</AlertTitle>
                <AlertDescription>{actionError}</AlertDescription>
              </Alert>
            ) : null}
          </CardContent>
        </Card>

        <KnowledgePresetRevisionPanel
          entityLabel={entityLabel}
          isFacet={isFacet}
          revisionDiff={revisionDiff}
          revisions={revisions}
          selectedPreset={selectedPreset}
          selectedScopeSummary={selectedScopeSummary}
          onLoadRevisionDiff={onLoadRevisionDiff}
          onRestoreRevision={onRestoreRevision}
        />
      </div>

      <KnowledgePresetEditorDialog
        activeProjects={activeProjects}
        activeRagProjectKey={normalizedActiveRagProjectKey}
        activeRagProjectName={activeRagProjectName}
        entityLabel={entityLabel}
        form={editForm}
        isBusy={isEditingSubmitting}
        isFacet={isFacet}
        open={isEditDialogOpen}
        setForm={setEditForm}
        onClose={cancelEditing}
        onSubmit={handleEditSubmit}
      />
    </div>
  );
}
