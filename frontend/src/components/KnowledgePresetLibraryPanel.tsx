import { useMemo, useState, type Dispatch, type FormEvent, type SetStateAction } from "react";
import { BookText, Database, Eye, History, Pencil, PlusCircle, RotateCcw, Trash2 } from "lucide-react";
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
  CreateKnowledgePresetRequest,
  DocumentStatus,
  DocumentType,
  KnowledgePresetDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetRevisionDetail,
  KnowledgePresetSummary,
  MaterialLanguageCode,
  ReferenceProject,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

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

type KnowledgePresetFormState = {
  name: string;
  description: string;
  documentTypes: DocumentType[];
  documentStatuses: DocumentStatus[];
  projectKeys: string[];
  documentNumber: string;
  languageCodes: MaterialLanguageCode[];
  tagsText: string;
  workspaceKey: string;
  periodStartFrom: string;
  periodStartTo: string;
  periodEndFrom: string;
  periodEndTo: string;
  uploadedTodayOnly: boolean;
  active: boolean;
};

const initialPresetForm: KnowledgePresetFormState = {
  name: "",
  description: "",
  documentTypes: [],
  documentStatuses: [],
  projectKeys: [],
  documentNumber: "",
  languageCodes: [],
  tagsText: "",
  workspaceKey: "",
  periodStartFrom: "",
  periodStartTo: "",
  periodEndFrom: "",
  periodEndTo: "",
  uploadedTodayOnly: false,
  active: true,
};

const documentTypes: DocumentType[] = [
  "POLICY",
  "CONTRACT",
  "REPORT",
  "PROCEDURE",
  "PRESENTATION",
  "SPREADSHEET",
  "LETTER",
  "MANUAL",
  "FAQ",
  "OTHER",
];
const documentStatuses: DocumentStatus[] = ["ACTIVE", "DRAFT", "ARCHIVED", "REVOKED"];
const languageCodes: MaterialLanguageCode[] = ["RU", "KK", "EN"];

const normalizeTags = (value: string) =>
  value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);

const presetDiffFieldLabels: Record<string, string> = {
  name: "Название",
  description: "Описание",
  documentTypes: "Типы документов",
  documentStatuses: "Статусы документов",
  projectKeys: "Проекты",
  documentNumber: "Номер документа",
  languageCodes: "Языки",
  tags: "Теги",
  workspaceKey: "Workspace key",
  periodStartFrom: "Действует с: от",
  periodStartTo: "Действует с: до",
  periodEndFrom: "Действует по: от",
  periodEndTo: "Действует по: до",
  uploadedTodayOnly: "Только загруженные сегодня",
  active: "Активность",
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
  const activeProjects = useMemo(
    () => projects.filter((project) => project.active && project.workspaceKey === normalizedActiveRagProjectKey),
    [normalizedActiveRagProjectKey, projects],
  );
  const visiblePresets = useMemo(
    () =>
      normalizedActiveRagProjectKey
        ? presets.filter((preset) => !preset.workspaceKey || preset.workspaceKey === normalizedActiveRagProjectKey)
        : presets,
    [normalizedActiveRagProjectKey, presets],
  );

  const selectedScopeSummary = useMemo(() => {
    if (!selectedPreset) {
      return `Выбери ${entityLabel}, чтобы увидеть критерии и историю ревизий.`;
    }

    const parts = [];
    const selectedDocumentTypes = selectedPreset.scope.documentTypes ?? [];
    const selectedDocumentStatuses = selectedPreset.scope.documentStatuses ?? [];
    const selectedProjectKeys = selectedPreset.scope.projectKeys ?? [];
    const selectedLanguageCodes = selectedPreset.scope.languageCodes ?? [];
    if (selectedDocumentTypes.length > 0) {
      parts.push(`типы: ${selectedDocumentTypes.map((item) => documentTypeLabels[item]).join(", ")}`);
    }
    if (selectedDocumentStatuses.length > 0) {
      parts.push(`статусы: ${selectedDocumentStatuses.map((item) => documentStatusLabels[item]).join(", ")}`);
    }
    if (selectedProjectKeys.length > 0) {
      parts.push(`проекты: ${selectedProjectKeys.join(", ")}`);
    }
    if (selectedPreset.scope.documentNumber) {
      parts.push(`номер: ${selectedPreset.scope.documentNumber}`);
    }
    if (selectedLanguageCodes.length > 0) {
      parts.push(`языки: ${selectedLanguageCodes.map((item) => materialLanguageCodeLabels[item]).join(", ")}`);
    }
    if (selectedPreset.scope.tags.length > 0) {
      parts.push(`теги: ${selectedPreset.scope.tags.join(", ")}`);
    }
    if (selectedPreset.scope.workspaceKey) {
      parts.push(`RAG-проект: ${activeRagProjectName || selectedPreset.scope.workspaceKey}`);
    }
    if (selectedPreset.scope.uploadedTodayOnly) {
      parts.push("только загруженные сегодня");
    }
    return parts.length > 0 ? parts.join(" · ") : `${isFacet ? "Фасет" : "Preset"} не ограничивает корпус дополнительными критериями.`;
  }, [activeRagProjectName, entityLabel, isFacet, selectedPreset]);

  const toggleListValue = <Value extends string>(
    form: KnowledgePresetFormState,
    setForm: Dispatch<SetStateAction<KnowledgePresetFormState>>,
    field: "documentTypes" | "documentStatuses" | "projectKeys" | "languageCodes",
    value: Value,
  ) => {
    const currentValues = form[field] as Value[];
    setForm({
      ...form,
      [field]: currentValues.includes(value)
        ? currentValues.filter((item) => item !== value)
        : [...currentValues, value],
    });
  };

  const buildPayload = (form: KnowledgePresetFormState): CreateKnowledgePresetRequest => ({
    name: form.name.trim(),
    description: form.description.trim() || null,
    active: form.active,
    kind: isFacet ? "FACET" : "PRESET",
    scope: {
      presetIds: [],
      facetIds: [],
      documentClasses: [],
      documentTypes: form.documentTypes,
      documentStatuses: form.documentStatuses,
      projectKeys: form.projectKeys,
      documentNumber: form.documentNumber.trim() || null,
      languageCodes: form.languageCodes,
      tags: normalizeTags(form.tagsText),
      workspaceKey: normalizedActiveRagProjectKey || form.workspaceKey.trim() || null,
      periodStartFrom: form.periodStartFrom || null,
      periodStartTo: form.periodStartTo || null,
      periodEndFrom: form.periodEndFrom || null,
      periodEndTo: form.periodEndTo || null,
      uploadedTodayOnly: form.uploadedTodayOnly,
    },
  });

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
    setEditForm({
      name: detail.name,
      description: detail.description ?? "",
      documentTypes: detail.scope.documentTypes ?? [],
      documentStatuses: detail.scope.documentStatuses ?? [],
      projectKeys: detail.scope.projectKeys ?? [],
      documentNumber: detail.scope.documentNumber ?? "",
      languageCodes: detail.scope.languageCodes ?? [],
      tagsText: detail.scope.tags.join(", "),
      workspaceKey: detail.scope.workspaceKey ?? "",
      periodStartFrom: detail.scope.periodStartFrom ?? "",
      periodStartTo: detail.scope.periodStartTo ?? "",
      periodEndFrom: detail.scope.periodEndFrom ?? "",
      periodEndTo: detail.scope.periodEndTo ?? "",
      uploadedTodayOnly: detail.scope.uploadedTodayOnly,
      active: detail.active,
    });
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

  const renderPresetForm = ({
    form,
    setForm,
    isBusy,
    busyLabel,
    idleLabel,
    idPrefix,
    onSubmit,
    showCancel = false,
  }: {
    form: KnowledgePresetFormState;
    setForm: Dispatch<SetStateAction<KnowledgePresetFormState>>;
    isBusy: boolean;
    busyLabel: string;
    idleLabel: string;
    idPrefix: string;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    showCancel?: boolean;
  }) => (
    <form className="space-y-4" onSubmit={onSubmit}>
      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-preset-name`}>Название</Label>
        <Input
          id={`${idPrefix}-preset-name`}
          placeholder="Например: Договоры"
          required
          value={form.name}
          onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-preset-description`}>Описание</Label>
        <Textarea
          id={`${idPrefix}-preset-description`}
          placeholder="Кратко опиши, когда использовать этот набор знаний."
          rows={3}
          value={form.description}
          onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
        />
      </div>

      <div className="space-y-3">
        <Label>Типы документов</Label>
        <div className="grid gap-3 sm:grid-cols-2">
          {documentTypes.map((documentType) => (
            <label
              className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
              key={documentType}
            >
              <Checkbox
                aria-label={`Выбрать тип ${documentTypeLabels[documentType]}`}
                checked={form.documentTypes.includes(documentType)}
                onCheckedChange={() => toggleListValue(form, setForm, "documentTypes", documentType)}
              />
              <span className="text-sm text-foreground">
                {documentTypeLabels[documentType]}
              </span>
            </label>
          ))}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-3">
          <Label>Статусы документов</Label>
          <div className="grid gap-3">
            {documentStatuses.map((documentStatus) => (
              <label
                className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
                key={documentStatus}
              >
                <Checkbox
                  aria-label={`Выбрать статус ${documentStatusLabels[documentStatus]}`}
                  checked={form.documentStatuses.includes(documentStatus)}
                  onCheckedChange={() => toggleListValue(form, setForm, "documentStatuses", documentStatus)}
                />
                <span className="text-sm text-foreground">{documentStatusLabels[documentStatus]}</span>
              </label>
            ))}
          </div>
        </div>

        <div className="space-y-3">
          <Label>Язык документа</Label>
          <div className="grid gap-3">
            {languageCodes.map((languageCode) => (
              <label
                className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
                key={languageCode}
              >
                <Checkbox
                  aria-label={`Выбрать язык ${materialLanguageCodeLabels[languageCode]}`}
                  checked={form.languageCodes.includes(languageCode)}
                  onCheckedChange={() => toggleListValue(form, setForm, "languageCodes", languageCode)}
                />
                <span className="text-sm text-foreground">{materialLanguageCodeLabels[languageCode]}</span>
              </label>
            ))}
          </div>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-preset-project`}>Проект</Label>
          <Select
            value=""
            onValueChange={(value) => toggleListValue(form, setForm, "projectKeys", value)}
          >
            <SelectTrigger id={`${idPrefix}-preset-project`}>
              <SelectValue placeholder={activeProjects.length === 0 ? "Нет активных проектов" : "Добавить проект"} />
            </SelectTrigger>
            <SelectContent>
              {activeProjects.map((project) => (
                <SelectItem key={project.key} value={project.key}>
                  {project.nameRu}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {form.projectKeys.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {form.projectKeys.map((projectKey) => (
                <Button
                  key={projectKey}
                  size="sm"
                  type="button"
                  variant="secondary"
                  onClick={() => toggleListValue(form, setForm, "projectKeys", projectKey)}
                >
                  {activeProjects.find((project) => project.key === projectKey)?.nameRu ?? projectKey}
                </Button>
              ))}
            </div>
          ) : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-preset-document-number`}>Номер документа</Label>
          <Input
            id={`${idPrefix}-preset-document-number`}
            placeholder="Например: POL-2026-17"
            value={form.documentNumber}
            onChange={(event) => setForm((current) => ({ ...current, documentNumber: event.target.value }))}
          />
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-period-start-from`}>Действует с: от</Label>
          <Input
            id={`${idPrefix}-period-start-from`}
            type="date"
            value={form.periodStartFrom}
            onChange={(event) => setForm((current) => ({ ...current, periodStartFrom: event.target.value }))}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-period-start-to`}>Действует с: до</Label>
          <Input
            id={`${idPrefix}-period-start-to`}
            type="date"
            value={form.periodStartTo}
            onChange={(event) => setForm((current) => ({ ...current, periodStartTo: event.target.value }))}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-period-end-from`}>Действует по: от</Label>
          <Input
            id={`${idPrefix}-period-end-from`}
            type="date"
            value={form.periodEndFrom}
            onChange={(event) => setForm((current) => ({ ...current, periodEndFrom: event.target.value }))}
          />
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-period-end-to`}>Действует по: до</Label>
          <Input
            id={`${idPrefix}-period-end-to`}
            type="date"
            value={form.periodEndTo}
            onChange={(event) => setForm((current) => ({ ...current, periodEndTo: event.target.value }))}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-preset-tags`}>Теги</Label>
        <Textarea
          id={`${idPrefix}-preset-tags`}
          placeholder="Например: procurement, premium, invoice"
          rows={2}
          value={form.tagsText}
          onChange={(event) => setForm((current) => ({ ...current, tagsText: event.target.value }))}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor={`${idPrefix}-preset-workspace`}>RAG-проект</Label>
        {normalizedActiveRagProjectKey ? (
          <div
            className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground"
            id={`${idPrefix}-preset-workspace`}
          >
            <strong className="block font-semibold">
              {activeRagProjectName || normalizedActiveRagProjectKey}
            </strong>
            <span className="text-xs text-muted-foreground">key: {normalizedActiveRagProjectKey}</span>
          </div>
        ) : (
          <Input
            id={`${idPrefix}-preset-workspace`}
            placeholder="Например: legal-assistant"
            value={form.workspaceKey}
            onChange={(event) => setForm((current) => ({ ...current, workspaceKey: event.target.value }))}
          />
        )}
      </div>

      <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
        <Checkbox
          aria-label={`Ограничить ${entityLabel} файлами, загруженными сегодня`}
          checked={form.uploadedTodayOnly}
          onCheckedChange={(checked) =>
            setForm((current) => ({ ...current, uploadedTodayOnly: Boolean(checked) }))
          }
        />
        <span className="text-sm text-foreground">Только загруженные сегодня файлы</span>
      </label>

      <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
        <Checkbox
          aria-label={`Сделать ${entityLabel} активным`}
          checked={form.active}
          onCheckedChange={(checked) =>
            setForm((current) => ({ ...current, active: Boolean(checked) }))
          }
        />
        <span className="text-sm text-foreground">{isFacet ? "Фасет" : "Preset"} активен и доступен в выборе корпуса</span>
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
            badge={`${visiblePresets.length} ${isFacet ? "facet" : "preset"}(s)`}
            badgeVariant="secondary"
            description={isFacet
              ? "Фасеты проекта задают сохранённые фильтры поиска по тем же полям, которые используются при загрузке материалов."
              : "Пресеты проекта ограничивают retrieval внутри активного RAG-проекта. Shared preset без workspace тоже можно видеть, но итоговый запрос всё равно остаётся в активном проекте."}
            eyebrow="Knowledge Scope"
            title={`${entityTitle}: ${activeRagProjectName || normalizedActiveRagProjectKey || "general"}`}
          />
        </CardHeader>
        <CardContent className="mt-0 space-y-4">
          {isLoading ? (
            <EmptyState description={isFacet ? "Читаем фасеты проекта." : "Читаем presets корпуса."} title={isFacet ? "Загружаем фасеты" : "Загружаем knowledge presets"} />
          ) : error ? (
            <EmptyState description={error} tone="danger" title={isFacet ? "Не удалось загрузить фасеты" : "Не удалось загрузить presets"} />
          ) : visiblePresets.length === 0 ? (
            <EmptyState
              description={isFacet
                ? "Сохрани первый фасет, чтобы быстро включать нужные фильтры поиска внутри проекта."
                : "Сохрани первый preset, чтобы retrieval можно было ограничивать релевантными фасетами внутри проекта."}
              title={isFacet ? "Фасеты проекта пока пусты" : "Пресеты проекта пока пусты"}
            />
          ) : (
            <div className="space-y-3">
              {visiblePresets.map((preset) => (
                <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={preset.id}>
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                          {preset.name}
                        </h3>
                        <Badge variant={preset.active ? "success" : "outline"}>
                          {preset.active ? "active" : "inactive"}
                        </Badge>
                        <Badge variant="outline">rev {preset.revision}</Badge>
                        {!preset.workspaceKey ? <Badge variant="secondary">shared</Badge> : null}
                      </div>
                      <p className="text-sm leading-6 text-muted-foreground">
                        {preset.description?.trim() || "Описание не задано."}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <Button size="sm" type="button" variant="secondary" onClick={() => void loadInspector(preset.id)}>
                        <Eye className="h-4 w-4" />
                        Открыть
                      </Button>
                      <Button size="sm" type="button" variant="outline" onClick={() => void handleEdit(preset.id)}>
                        <Pencil className="h-4 w-4" />
                        Редактировать
                      </Button>
                      <Button size="sm" type="button" variant="destructive" onClick={() => void handleDelete(preset.id)}>
                        <Trash2 className="h-4 w-4" />
                        Удалить
                      </Button>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

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
            {renderPresetForm({
              form: createForm,
              setForm: setCreateForm,
              idPrefix: "create",
              isBusy: isSubmitting,
              busyLabel: "Создаём...",
              idleLabel: isFacet ? "Сохранить фасет" : "Сохранить preset",
              onSubmit: handleSubmit,
            })}

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

        <Card className="order-3">
          <CardHeader>
            <SectionIntro
              badge={selectedPreset ? `rev ${selectedPreset.revision}` : "preview"}
              badgeVariant="secondary"
              eyebrow={isFacet ? "Facet Inspector" : "Preset Inspector"}
              title="Scope и история выбранного набора знаний"
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            {selectedPreset ? (
              <>
                <div className="rounded-[24px] border border-field-border bg-field p-5">
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <Badge variant={selectedPreset.active ? "success" : "outline"}>
                      {selectedPreset.active ? "active" : "inactive"}
                    </Badge>
                    <Badge variant="outline">rev {selectedPreset.revision}</Badge>
                  </div>
                  <p className="text-sm leading-7 text-foreground">{selectedScopeSummary}</p>
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
                                {presetDiffFieldLabels[change.field] ?? change.field}
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
                      История ревизий пока не загружена.
                    </p>
                  ) : (
                    revisions.map((revision) => (
                      <article
                        className="rounded-[22px] border border-field-border bg-field px-4 py-4"
                        key={`${revision.presetId}-${revision.revision}`}
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
                              selectedPreset
                                ? void onLoadRevisionDiff(
                                  revision.presetId,
                                  selectedPreset.revision,
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
                            onClick={() => void onRestoreRevision(revision.presetId, revision.revision)}
                          >
                            <RotateCcw className="h-4 w-4" />
                            Восстановить
                          </Button>
                        </div>

                        <Separator className="my-3" />
                        <p className="text-sm leading-6 text-foreground">
                          {(revision.scope.documentTypes ?? []).length > 0
                            ? (revision.scope.documentTypes ?? []).map((item) => documentTypeLabels[item]).join(", ")
                            : "Без ограничения по типам"}
                          {(revision.scope.documentStatuses ?? []).length > 0
                            ? ` · statuses: ${(revision.scope.documentStatuses ?? []).map((item) => documentStatusLabels[item]).join(", ")}`
                            : ""}
                          {(revision.scope.projectKeys ?? []).length > 0 ? ` · projects: ${(revision.scope.projectKeys ?? []).join(", ")}` : ""}
                          {(revision.scope.languageCodes ?? []).length > 0
                            ? ` · languages: ${(revision.scope.languageCodes ?? []).map((item) => materialLanguageCodeLabels[item]).join(", ")}`
                            : ""}
                          {revision.scope.tags.length > 0 ? ` · tags: ${revision.scope.tags.join(", ")}` : ""}
                          {revision.scope.workspaceKey ? ` · workspace: ${revision.scope.workspaceKey}` : ""}
                          {revision.scope.uploadedTodayOnly ? " · today uploads only" : ""}
                        </p>
                      </article>
                    ))
                  )}
                </div>
              </>
            ) : (
              <EmptyState
                description={`Открой любой ${entityLabel} слева, чтобы увидеть его scope и историю ревизий.`}
                icon={Database}
                title={isFacet ? "Фасет не выбран" : "Preset не выбран"}
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
        <DialogContent className="max-h-[88vh] max-w-2xl overflow-y-auto">
          <DialogHeader className="mb-2">
            <DialogTitle>{isFacet ? "Редактировать фасет" : "Редактировать knowledge preset"}</DialogTitle>
            <DialogDescription>
              Изменения сохраняются отдельной ревизией. Блок создания на странице остаётся независимым.
            </DialogDescription>
          </DialogHeader>

          {renderPresetForm({
            form: editForm,
            setForm: setEditForm,
            idPrefix: "edit",
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
