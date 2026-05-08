import { useEffect, useMemo, useState, type FormEvent } from "react";
import { PlusCircle } from "lucide-react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { InstructionEditorDialog, InstructionEditorForm } from "@/components/InstructionEditorDialog";
import { InstructionList } from "@/components/InstructionList";
import { InstructionRevisionPanel } from "@/components/InstructionRevisionPanel";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import type {
  CreateInstructionRequest,
  InstructionDetail,
  InstructionRevisionDiff,
  InstructionRevisionDetail,
  InstructionSummary,
} from "@/types";
import {
  buildInstructionPayload,
  groupInstructions,
  initialInstructionForm,
  type InstructionFormState,
} from "@/components/instructionPresentation";

type InstructionLibraryPanelProps = {
  instructions: InstructionSummary[];
  activeRagProjectKey?: string | null;
  activeRagProjectName?: string | null;
  selectedInstruction: InstructionDetail | null;
  revisions: InstructionRevisionDetail[];
  revisionDiff: InstructionRevisionDiff | null;
  detailError: string | null;
  isLoadingDetail: boolean;
  isLoading: boolean;
  error: string | null;
  message: string | null;
  actionError: string | null;
  deletingInstructionId: string | null;
  onCreateInstruction: (input: CreateInstructionRequest) => Promise<unknown>;
  onUpdateInstruction: (instructionId: string, input: CreateInstructionRequest) => Promise<unknown>;
  onDeleteInstruction: (instructionId: string) => Promise<unknown>;
  onLoadInstruction: (instructionId: string) => Promise<InstructionDetail | null>;
  onLoadInstructionRevisions: (instructionId: string) => Promise<InstructionRevisionDetail[] | null>;
  onLoadInstructionDiff: (
    instructionId: string,
    fromRevision: number,
    toRevision: number,
  ) => Promise<InstructionRevisionDiff | null>;
  onRestoreInstructionRevision: (instructionId: string, revision: number) => Promise<unknown>;
};

export function InstructionLibraryPanel({
  instructions,
  activeRagProjectKey,
  activeRagProjectName,
  selectedInstruction,
  revisions,
  revisionDiff,
  detailError,
  isLoadingDetail,
  isLoading,
  error,
  message,
  actionError,
  deletingInstructionId,
  onCreateInstruction,
  onUpdateInstruction,
  onDeleteInstruction,
  onLoadInstruction,
  onLoadInstructionRevisions,
  onLoadInstructionDiff,
  onRestoreInstructionRevision,
}: InstructionLibraryPanelProps) {
  const [createForm, setCreateForm] = useState<InstructionFormState>(initialInstructionForm);
  const [editForm, setEditForm] = useState<InstructionFormState>(initialInstructionForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isEditingSubmitting, setIsEditingSubmitting] = useState(false);
  const [editingInstructionId, setEditingInstructionId] = useState<string | null>(null);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const normalizedActiveRagProjectKey = (activeRagProjectKey ?? "").trim();
  const normalizedActiveRagProjectName = activeRagProjectName ?? "";
  const groupedInstructions = useMemo(
    () => groupInstructions(instructions, normalizedActiveRagProjectKey, normalizedActiveRagProjectName),
    [instructions, normalizedActiveRagProjectKey, normalizedActiveRagProjectName],
  );

  useEffect(() => {
    if (!normalizedActiveRagProjectKey) {
      return;
    }
    setCreateForm((current) => {
      if (current.title || current.content || current.scopeLevel !== "chat_scenario" || current.scopeTargetId) {
        return current.scopeLevel === "workspace_project"
          ? { ...current, scopeTargetId: normalizedActiveRagProjectKey }
          : current;
      }
      return {
        ...current,
        category: "context",
        scopeLevel: "workspace_project",
        scopeTargetId: normalizedActiveRagProjectKey,
      };
    });
  }, [normalizedActiveRagProjectKey]);

  const buildPayload = (form: InstructionFormState) =>
    buildInstructionPayload(form, normalizedActiveRagProjectKey);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);

    try {
      await onCreateInstruction(buildPayload(createForm));
      setCreateForm(initialInstructionForm);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleEditSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!editingInstructionId) {
      return;
    }
    setIsEditingSubmitting(true);

    try {
      await onUpdateInstruction(editingInstructionId, buildPayload(editForm));
      cancelEditing();
    } finally {
      setIsEditingSubmitting(false);
    }
  };

  const handleDelete = async (instructionId: string) => {
    const instruction = instructions.find((item) => item.id === instructionId);
    const confirmed = window.confirm(`Удалить инструкцию "${instruction?.title ?? instructionId}"?`);
    if (!confirmed) {
      return;
    }

    await onDeleteInstruction(instructionId);
    if (editingInstructionId === instructionId) {
      cancelEditing();
    }
  };

  const handleInspect = async (instructionId: string) => {
    await Promise.all([
      onLoadInstruction(instructionId),
      onLoadInstructionRevisions(instructionId),
    ]);
  };

  const handleEdit = async (instructionId: string) => {
    const detail = selectedInstruction?.id === instructionId
      ? selectedInstruction
      : await onLoadInstruction(instructionId);

    if (!detail) {
      return;
    }

    await onLoadInstructionRevisions(instructionId);
    setEditingInstructionId(instructionId);
    setEditForm({
      title: detail.title,
      category: detail.category,
      content: detail.content,
      scopeLevel: detail.scopeLevel ?? "chat_scenario",
      scopeTargetId: detail.scopeTargetId ?? "",
      active: detail.active ?? true,
    });
    setIsEditDialogOpen(true);
  };

  const cancelEditing = () => {
    setIsEditDialogOpen(false);
    setEditingInstructionId(null);
    setEditForm(initialInstructionForm);
  };

  return (
    <div className="grid gap-6">
      <InstructionList
        deletingInstructionId={deletingInstructionId}
        error={error}
        groupedInstructions={groupedInstructions}
        instructionsCount={instructions.length}
        isLoading={isLoading}
        onDelete={(instructionId) => void handleDelete(instructionId)}
        onEdit={(instructionId) => void handleEdit(instructionId)}
        onInspect={(instructionId) => void handleInspect(instructionId)}
      />

      <div className="contents">
        <Card className="order-1">
          <CardHeader>
            <SectionIntro
              badge="Создание"
              badgeVariant="secondary"
              description="Добавь новую инструкцию с уровнем применения. Редактирование существующих инструкций открывается отдельно по кнопке в списке."
              eyebrow="Prompt Governance"
              title="Добавить инструкцию"
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            <InstructionEditorForm
              busyLabel="Создаём..."
              form={createForm}
              idleLabel="Сохранить инструкцию"
              isBusy={isSubmitting}
              normalizedActiveRagProjectKey={normalizedActiveRagProjectKey}
              normalizedActiveRagProjectName={normalizedActiveRagProjectName}
              setForm={setCreateForm}
              onSubmit={handleSubmit}
            />

            {message ? (
              <Alert variant="success">
                <AlertTitle>Изменения сохранены</AlertTitle>
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

        <InstructionRevisionPanel
          detailError={detailError}
          isLoadingDetail={isLoadingDetail}
          revisionDiff={revisionDiff}
          revisions={revisions}
          selectedInstruction={selectedInstruction}
          onLoadInstructionDiff={onLoadInstructionDiff}
          onRestoreInstructionRevision={onRestoreInstructionRevision}
        />
      </div>

      <InstructionEditorDialog
        busyLabel="Сохраняем..."
        form={editForm}
        idleLabel="Сохранить изменения"
        isBusy={isEditingSubmitting}
        normalizedActiveRagProjectKey={normalizedActiveRagProjectKey}
        normalizedActiveRagProjectName={normalizedActiveRagProjectName}
        open={isEditDialogOpen}
        setForm={setEditForm}
        onClose={cancelEditing}
        onSubmit={handleEditSubmit}
      />
    </div>
  );
}
