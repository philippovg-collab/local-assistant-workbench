import { useEffect, useId, useMemo, useState, type Dispatch, type FormEvent, type SetStateAction } from "react";
import { BookText, CircleHelp, Eye, History, Pencil, PlusCircle, RotateCcw, Trash2 } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
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
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import type {
  CreateInstructionRequest,
  InstructionCategory,
  InstructionDetail,
  InstructionRevisionDiff,
  InstructionRevisionDetail,
  InstructionScopeLevel,
  InstructionSummary,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  formatScopeTarget,
  instructionCategoryLabels,
  instructionScopeLabels,
  instructionScopeOrder,
} from "@/utils/workbenchPresentation";

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

type InstructionFormState = {
  title: string;
  category: InstructionCategory;
  content: string;
  scopeLevel: InstructionScopeLevel;
  scopeTargetId: string;
  active: boolean;
};

const initialInstructionForm: InstructionFormState = {
  title: "",
  category: "system",
  content: "",
  scopeLevel: "chat_scenario",
  scopeTargetId: "",
  active: true,
};

const instructionCategoryOrder: InstructionCategory[] = ["system", "user", "context", "safety"];

const scopeHelperText: Record<InstructionScopeLevel, string> = {
  assistant_system: "Глобальная роль ассистента. Подставляется автоматически во все запросы.",
  workspace_project: "Правила конкретного workspace или проекта. Подставляются автоматически по target.",
  chat_scenario: "Переиспользуемые сценарные инструкции, которые пользователь выбирает в конкретном чате.",
  request_temporary: "Справочная запись для одноразовой инструкции. Обычно не сохраняется, но можно зафиксировать шаблон.",
};

const instructionDiffFieldLabels: Record<string, string> = {
  title: "Название",
  category: "Категория",
  content: "Текст",
  scopeLevel: "Уровень",
  scopeTargetId: "Scope target",
  active: "Активность",
};

const categoryTooltipLines = [
  "Системная: системные правила, роль и базовое поведение модели.",
  "Безопасность: ограничения и запреты, которые добавляются в системную часть промпта.",
  "Контекстная: правила работы с контекстом и материалами, попадают перед контекстом или запросом.",
  "Пользовательская: сценарные пожелания к ответу, попадают перед пользовательским запросом.",
];

const scopeTooltipLines = [
  "Системная роль ассистента: применяется автоматически во всех запросах.",
  "Рабочая область / проект: применяется автоматически при совпадении Scope target с ключом рабочей области запроса.",
  "Сценарий / чат: доступна для ручного выбора в RAG/Direct чате.",
  "Временная инструкция: одноразовое правило для текущего запроса.",
];

function FieldHelpTooltip({
  label,
  lines,
}: {
  label: string;
  lines: string[];
}) {
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
  const categoryLabelId = useId();
  const scopeLabelId = useId();
  const editCategoryLabelId = useId();
  const editScopeLabelId = useId();
  const normalizedActiveRagProjectKey = (activeRagProjectKey ?? "").trim();
  const normalizedActiveRagProjectName = activeRagProjectName ?? "";
  const groupedInstructions = useMemo(
    () => [
      {
        id: "project",
        label: "Инструкции этого RAG-проекта",
        description: normalizedActiveRagProjectKey
          ? `Автоматически применяются, когда активен проект ${normalizedActiveRagProjectName || normalizedActiveRagProjectKey}.`
          : "Автоматически применяются при совпадении workspaceKey запроса.",
        items: instructions.filter((instruction) =>
          (instruction.scopeLevel ?? "chat_scenario") === "workspace_project"
          && instruction.scopeTargetId === normalizedActiveRagProjectKey),
      },
      {
        id: "scenario",
        label: "Сценарии",
        description: scopeHelperText.chat_scenario,
        items: instructions.filter((instruction) => (instruction.scopeLevel ?? "chat_scenario") === "chat_scenario"),
      },
      {
        id: "global",
        label: "Глобальные",
        description: "Системные и одноразовые шаблоны, не привязанные к конкретному RAG-проекту.",
        items: instructions.filter((instruction) =>
          ["assistant_system", "request_temporary"].includes(instruction.scopeLevel ?? "chat_scenario")),
      },
    ],
    [instructions, normalizedActiveRagProjectKey, normalizedActiveRagProjectName],
  );

  useEffect(() => {
    if (!normalizedActiveRagProjectKey) {
      return;
    }
    setCreateForm((current) => {
      if (
        current.title
        || current.content
        || current.scopeLevel !== "chat_scenario"
        || current.scopeTargetId
      ) {
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

  const buildPayload = (form: InstructionFormState): CreateInstructionRequest => ({
      title: form.title,
      category: form.category,
      content: form.content,
      scopeLevel: form.scopeLevel,
      scopeTargetId: form.scopeLevel === "workspace_project" && normalizedActiveRagProjectKey
        ? normalizedActiveRagProjectKey
        : form.scopeTargetId.trim() || null,
      active: form.active,
  });

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

  const renderInstructionForm = ({
    form,
    setForm,
    categoryId,
    scopeId,
    isBusy,
    busyLabel,
    idleLabel,
    onSubmit,
    showCancel = false,
  }: {
    form: InstructionFormState;
    setForm: Dispatch<SetStateAction<InstructionFormState>>;
    categoryId: string;
    scopeId: string;
    isBusy: boolean;
    busyLabel: string;
    idleLabel: string;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    showCancel?: boolean;
  }) => (
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
            <FieldHelpTooltip
              label="Показать подсказку: тип инструкции"
              lines={categoryTooltipLines}
            />
          </div>
          <Select
            value={form.category}
            onValueChange={(value) =>
              setForm((current) => ({ ...current, category: value as InstructionCategory }))
            }
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
            <FieldHelpTooltip
              label="Показать подсказку: уровень инструкции"
              lines={scopeTooltipLines}
            />
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
              }))
            }
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
          <div
            className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground"
            id={`${categoryId}-target`}
          >
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
            onChange={(event) =>
              setForm((current) => ({ ...current, scopeTargetId: event.target.value }))
            }
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
          onCheckedChange={(checked) =>
            setForm((current) => ({ ...current, active: Boolean(checked) }))
          }
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
        {showCancel ? (
          <Button type="button" variant="secondary" onClick={cancelEditing}>
            Отменить редактирование
          </Button>
        ) : null}
      </div>
    </form>
  );

  return (
    <div className="grid gap-6">
      <Card className="order-2">
        <CardHeader>
          <SectionIntro
            badge={`${instructions.length} saved`}
            badgeVariant="secondary"
            description="Проектные инструкции автоматически применяются только к активному RAG-проекту; сценарии остаются ручным выбором в Studio, а глобальные действуют шире."
            eyebrow="Instruction Stack"
            title="Инструкции RAG-проекта"
          />
        </CardHeader>
        <CardContent className="mt-0 space-y-5">
          {isLoading ? (
            <EmptyState
              description="Читаем инструкции из локального backend."
              title="Загружаем библиотеку"
            />
          ) : error ? (
            <EmptyState
              description={error}
              tone="danger"
              title="Не удалось загрузить инструкции"
            />
          ) : instructions.length === 0 ? (
            <EmptyState
              description="Добавь первую инструкцию, чтобы система могла строить явный instruction stack."
              title="Пока пусто"
            />
          ) : (
            groupedInstructions.map(({ id, label, description, items }) => (
              <section className="space-y-3" key={id}>
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="text-base font-semibold text-foreground">
                    {label}
                  </h3>
                  <Badge variant="secondary">{items.length}</Badge>
                </div>
                <p className="text-sm leading-6 text-muted-foreground">{description}</p>

                {items.length === 0 ? (
                  <div className="rounded-[22px] border border-dashed border-border px-4 py-4 text-sm text-muted-foreground">
                    На этом уровне инструкций пока ничего не сохранено.
                  </div>
                ) : (
                  <div className="space-y-3">
                    {items.map((instruction) => (
                      <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={instruction.id}>
                        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                          <div className="space-y-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                                {instruction.title}
                              </h3>
                              <Badge variant="secondary">
                                {instructionCategoryLabels[instruction.category]}
                              </Badge>
                              <Badge variant={(instruction.active ?? true) ? "success" : "outline"}>
                                {(instruction.active ?? true) ? "active" : "inactive"}
                              </Badge>
                              <Badge variant="outline">rev {instruction.revision ?? 1}</Badge>
                            </div>
                            <p className="text-sm leading-6 text-muted-foreground">
                              target: {formatScopeTarget(instruction.scopeTargetId)}
                              {" · "}
                              обновлена {formatDate(instruction.updatedAt ?? instruction.createdAt)}
                            </p>
                          </div>

                          <div className="flex flex-wrap items-center gap-2">
                            <Button
                              size="sm"
                              type="button"
                              variant="secondary"
                              onClick={() => void handleInspect(instruction.id)}
                            >
                              <Eye className="h-4 w-4" />
                              Открыть
                            </Button>
                            <Button
                              size="sm"
                              type="button"
                              variant="outline"
                              onClick={() => void handleEdit(instruction.id)}
                            >
                              <Pencil className="h-4 w-4" />
                              Редактировать
                            </Button>
                            <Button
                              disabled={deletingInstructionId === instruction.id}
                              size="sm"
                              type="button"
                              variant="destructive"
                              onClick={() => void handleDelete(instruction.id)}
                            >
                              <Trash2 className="h-4 w-4" />
                              {deletingInstructionId === instruction.id ? "Удаляем..." : "Удалить"}
                            </Button>
                          </div>
                        </div>

                        <Separator />
                        <p className="text-sm leading-7 text-foreground">{instruction.preview}</p>
                      </article>
                    ))}
                  </div>
                )}
              </section>
            ))
          )}
        </CardContent>
      </Card>

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
            {renderInstructionForm({
              form: createForm,
              setForm: setCreateForm,
              categoryId: categoryLabelId,
              scopeId: scopeLabelId,
              isBusy: isSubmitting,
              busyLabel: "Создаём...",
              idleLabel: "Сохранить инструкцию",
              onSubmit: handleSubmit,
            })}

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

        <Card className="order-3">
          <CardHeader>
            <SectionIntro
              badge={selectedInstruction ? `rev ${selectedInstruction.revision ?? 1}` : "preview"}
              badgeVariant="secondary"
              eyebrow="Inspector"
              title="Текст и история выбранной инструкции"
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            {isLoadingDetail ? (
              <EmptyState description="Подтягиваем текст и историю ревизий." title="Загружаем инструкцию" />
            ) : detailError ? (
              <EmptyState description={detailError} tone="danger" title="Не удалось загрузить инструкцию" />
            ) : selectedInstruction ? (
              <>
                <div className="space-y-3 rounded-[24px] border border-field-border bg-field p-5">
                  <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="secondary">
                      {instructionScopeLabels[selectedInstruction.scopeLevel ?? "chat_scenario"]}
                    </Badge>
                    <Badge variant="outline">
                      target: {formatScopeTarget(selectedInstruction.scopeTargetId)}
                    </Badge>
                    <Badge variant={(selectedInstruction.active ?? true) ? "success" : "outline"}>
                      {(selectedInstruction.active ?? true) ? "active" : "inactive"}
                    </Badge>
                  </div>
                  <p className="text-sm leading-7 text-foreground">{selectedInstruction.content}</p>
                </div>

                <Separator />

                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                    <History className="h-4 w-4 text-primary" />
                    История ревизий
                  </div>

                  {revisionDiff ? (
                    <div className="rounded-[22px] border border-field-border bg-field px-4 py-4">
                      <p className="text-sm font-semibold text-foreground">
                        Сравнение rev {revisionDiff.fromRevision} -&gt; rev {revisionDiff.toRevision}
                      </p>
                      <Separator className="my-3" />
                      {revisionDiff.changes.length === 0 ? (
                        <p className="text-sm leading-6 text-muted-foreground">Изменений между ревизиями нет.</p>
                      ) : (
                        <div className="space-y-3">
                          {revisionDiff.changes.map((change) => (
                            <div className="space-y-1 text-sm leading-6" key={`${change.field}-${change.fromValue}-${change.toValue}`}>
                              <p className="font-medium text-foreground">
                                {instructionDiffFieldLabels[change.field] ?? change.field}
                              </p>
                              <p className="text-muted-foreground">Было: {change.fromValue ?? "пусто"}</p>
                              <p className="text-foreground">Стало: {change.toValue ?? "пусто"}</p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ) : null}

                  {revisions.length === 0 ? (
                    <p className="text-sm leading-6 text-muted-foreground">
                      История ревизий пока не загружена или ещё не содержит прошлых версий.
                    </p>
                  ) : (
                    <div className="space-y-3">
                      {revisions.map((revision) => (
                        <article
                          className="rounded-[22px] border border-field-border bg-field px-4 py-4"
                          key={`${revision.instructionId}-${revision.revision}`}
                        >
                          <div className="flex flex-wrap items-start justify-between gap-3">
                            <div className="space-y-2">
                              <div className="flex flex-wrap items-center gap-2">
                                <strong className="text-sm font-semibold text-foreground">
                                  rev {revision.revision}
                                </strong>
                                {revision.restoredFromRevision ? (
                                  <Badge variant="warning">
                                    restore from rev {revision.restoredFromRevision}
                                  </Badge>
                                ) : null}
                              </div>
                              <p className="text-sm leading-6 text-muted-foreground">
                                {formatDate(revision.updatedAt ?? revision.createdAt)}
                              </p>
                            </div>

                            <Button
                              size="sm"
                              type="button"
                              variant="outline"
                              onClick={() =>
                                selectedInstruction
                                  ? void onLoadInstructionDiff(
                                    revision.instructionId,
                                    selectedInstruction.revision ?? revision.revision,
                                    revision.revision,
                                  )
                                  : undefined
                              }
                            >
                              <BookText className="h-4 w-4" />
                              Сравнить
                            </Button>
                            <Button
                              size="sm"
                              type="button"
                              variant="outline"
                              onClick={() =>
                                void onRestoreInstructionRevision(revision.instructionId, revision.revision)
                              }
                            >
                              <RotateCcw className="h-4 w-4" />
                              Восстановить
                            </Button>
                          </div>
                          <Separator className="my-3" />
                          <p className="text-sm leading-6 text-foreground">{revision.content}</p>
                        </article>
                      ))}
                    </div>
                  )}
                </div>
              </>
            ) : (
              <EmptyState
                description="Открой любую инструкцию слева, чтобы увидеть полный текст и историю её ревизий."
                icon={BookText}
                title="Инструкция не выбрана"
              />
            )}
          </CardContent>
        </Card>
      </div>

      <Dialog
        open={isEditDialogOpen}
        onOpenChange={(open) => {
          if (!open) {
            cancelEditing();
          } else {
            setIsEditDialogOpen(true);
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

          {renderInstructionForm({
            form: editForm,
            setForm: setEditForm,
            categoryId: editCategoryLabelId,
            scopeId: editScopeLabelId,
            isBusy: isEditingSubmitting,
            busyLabel: "Сохраняем...",
            idleLabel: "Сохранить изменения",
            onSubmit: handleEditSubmit,
            showCancel: true,
          })}
        </DialogContent>
      </Dialog>
    </div>
  );
}
