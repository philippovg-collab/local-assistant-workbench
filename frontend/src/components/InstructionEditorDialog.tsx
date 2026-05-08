import { useId, type Dispatch, type FormEvent, type SetStateAction } from "react";
import { CircleHelp, PlusCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { InstructionCategory, InstructionScopeLevel } from "@/types";
import {
  categoryTooltipLines,
  instructionCategoryOrder,
  scopeHelperText,
  scopeTooltipLines,
  type InstructionFormState,
} from "@/components/instructionPresentation";
import {
  instructionCategoryLabels,
  instructionScopeLabels,
  instructionScopeOrder,
} from "@/utils/workbenchPresentation";

type InstructionEditorFormProps = {
  form: InstructionFormState;
  setForm: Dispatch<SetStateAction<InstructionFormState>>;
  isBusy: boolean;
  busyLabel: string;
  idleLabel: string;
  normalizedActiveRagProjectKey: string;
  normalizedActiveRagProjectName: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel?: () => void;
};

export function InstructionEditorForm({
  form,
  setForm,
  isBusy,
  busyLabel,
  idleLabel,
  normalizedActiveRagProjectKey,
  normalizedActiveRagProjectName,
  onSubmit,
  onCancel,
}: InstructionEditorFormProps) {
  const categoryId = useId();
  const scopeId = useId();

  return (
    <form className="space-y-4" onSubmit={onSubmit}>
      <div className="space-y-2">
        <Label htmlFor={`${categoryId}-title`}>Название</Label>
        <Input
          id={`${categoryId}-title`}
          placeholder="Например: Базовая роль ассистента"
          required
          value={form.title}
          onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
        />
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Label id={categoryId}>Тип инструкции</Label>
            <FieldHelpTooltip label="Показать подсказку: тип инструкции" lines={categoryTooltipLines} />
          </div>
          <Select
            value={form.category}
            onValueChange={(value) => setForm((current) => ({ ...current, category: value as InstructionCategory }))}
          >
            <SelectTrigger aria-labelledby={categoryId}>
              <SelectValue placeholder="Выбери тип" />
            </SelectTrigger>
            <SelectContent>
              {instructionCategoryOrder.map((category) => (
                <SelectItem key={category} value={category}>
                  {instructionCategoryLabels[category]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Label id={scopeId}>Уровень инструкции</Label>
            <FieldHelpTooltip label="Показать подсказку: уровень инструкции" lines={scopeTooltipLines} />
          </div>
          <Select
            value={form.scopeLevel}
            onValueChange={(value) =>
              setForm((current) => ({
                ...current,
                scopeLevel: value as InstructionScopeLevel,
                scopeTargetId: value === "workspace_project" && normalizedActiveRagProjectKey
                  ? normalizedActiveRagProjectKey
                  : current.scopeTargetId,
              }))}
          >
            <SelectTrigger aria-labelledby={scopeId}>
              <SelectValue placeholder="Выбери уровень" />
            </SelectTrigger>
            <SelectContent>
              {instructionScopeOrder.map((scopeLevel) => (
                <SelectItem key={scopeLevel} value={scopeLevel}>
                  {instructionScopeLabels[scopeLevel]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor={`${categoryId}-target`}>Scope target</Label>
        {form.scopeLevel === "workspace_project" && normalizedActiveRagProjectKey ? (
          <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground" id={`${categoryId}-target`}>
            <strong className="block font-semibold">
              {normalizedActiveRagProjectName || normalizedActiveRagProjectKey}
            </strong>
            <span className="text-xs text-muted-foreground">key: {normalizedActiveRagProjectKey}</span>
          </div>
        ) : (
          <Input
            id={`${categoryId}-target`}
            placeholder={form.scopeLevel === "workspace_project" ? "Например: sales-workspace" : "Опционально"}
            value={form.scopeTargetId}
            onChange={(event) => setForm((current) => ({ ...current, scopeTargetId: event.target.value }))}
          />
        )}
        <p className="text-xs leading-5 text-muted-foreground">{scopeHelperText[form.scopeLevel]}</p>
      </div>

      <div className="space-y-2">
        <Label htmlFor={`${categoryId}-content`}>Текст инструкции</Label>
        <Textarea
          id={`${categoryId}-content`}
          placeholder="Опиши роль, ограничение или контекст."
          required
          rows={9}
          value={form.content}
          onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))}
        />
      </div>

      <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
        <Checkbox
          aria-label="Сделать инструкцию активной"
          checked={form.active}
          onCheckedChange={(checked) => setForm((current) => ({ ...current, active: Boolean(checked) }))}
        />
        <span className="text-sm text-foreground">
          Инструкция активна и может участвовать в runtime stack
        </span>
      </label>

      <div className="flex flex-wrap gap-3">
        <Button disabled={isBusy} type="submit">
          <PlusCircle className="h-4 w-4" />
          {isBusy ? busyLabel : idleLabel}
        </Button>
        {onCancel ? (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Отменить редактирование
          </Button>
        ) : null}
      </div>
    </form>
  );
}

type InstructionEditorDialogProps = InstructionEditorFormProps & {
  open: boolean;
  onClose: () => void;
};

export function InstructionEditorDialog({ open, onClose, ...formProps }: InstructionEditorDialogProps) {
  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-h-[90vh] w-[92vw] max-w-2xl overflow-y-auto bg-popover text-foreground">
        <DialogHeader className="mb-6">
          <DialogTitle>Редактировать инструкцию</DialogTitle>
          <DialogDescription className="text-muted-foreground">
            Изменения сохраняются как новая ревизия, а форма добавления инструкции на странице остаётся независимой.
          </DialogDescription>
        </DialogHeader>
        <InstructionEditorForm {...formProps} onCancel={onClose} />
      </DialogContent>
    </Dialog>
  );
}

function FieldHelpTooltip({ label, lines }: { label: string; lines: string[] }) {
  const tooltipId = useId();

  return (
    <span className="group relative inline-flex">
      <button
        aria-describedby={tooltipId}
        aria-label={label}
        className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-field-border bg-field text-muted-foreground transition hover:border-primary/40 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        type="button"
      >
        <CircleHelp className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
      <span
        className="pointer-events-none absolute left-0 top-full z-50 mt-2 w-80 max-w-[calc(100vw-3rem)] rounded-[18px] border border-border bg-popover px-4 py-3 text-left text-xs leading-5 text-popover-foreground opacity-0 shadow-soft transition group-hover:opacity-100 group-focus-within:opacity-100"
        id={tooltipId}
        role="tooltip"
      >
        {lines.map((line) => (
          <span className="block" key={line}>
            {line}
          </span>
        ))}
      </span>
    </span>
  );
}
