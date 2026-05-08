import { type Dispatch, type FormEvent, type SetStateAction } from "react";
import { KnowledgePresetCriteriaForm } from "@/components/KnowledgePresetCriteriaForm";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { ReferenceProject } from "@/types";
import type { KnowledgePresetFormState } from "@/components/knowledgePresetPresentation";

type KnowledgePresetEditorDialogProps = {
  open: boolean;
  form: KnowledgePresetFormState;
  setForm: Dispatch<SetStateAction<KnowledgePresetFormState>>;
  activeProjects: ReferenceProject[];
  activeRagProjectKey: string;
  activeRagProjectName?: string | null;
  isFacet: boolean;
  entityLabel: string;
  isBusy: boolean;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onClose: () => void;
};

export function KnowledgePresetEditorDialog({
  open,
  form,
  setForm,
  activeProjects,
  activeRagProjectKey,
  activeRagProjectName,
  isFacet,
  entityLabel,
  isBusy,
  onSubmit,
  onClose,
}: KnowledgePresetEditorDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[88vh] max-w-2xl overflow-y-auto">
        <DialogHeader className="mb-2">
          <DialogTitle>{isFacet ? "Редактировать фасет" : "Редактировать knowledge preset"}</DialogTitle>
          <DialogDescription>
            Изменения сохраняются отдельной ревизией. Блок создания на странице остаётся независимым.
          </DialogDescription>
        </DialogHeader>

        <KnowledgePresetCriteriaForm
          activeProjects={activeProjects}
          activeRagProjectKey={activeRagProjectKey}
          activeRagProjectName={activeRagProjectName}
          busyLabel="Сохраняем..."
          entityLabel={entityLabel}
          form={form}
          idPrefix="edit"
          idleLabel="Сохранить изменения"
          isBusy={isBusy}
          isFacet={isFacet}
          setForm={setForm}
          onCancel={onClose}
          onSubmit={onSubmit}
        />
      </DialogContent>
    </Dialog>
  );
}
