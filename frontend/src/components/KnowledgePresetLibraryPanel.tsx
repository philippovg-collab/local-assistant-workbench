import { useMemo, useState, type FormEvent } from "react";
import { BookText, Database, Eye, History, Pencil, PlusCircle, RotateCcw, Trash2 } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import type {
  CreateKnowledgePresetRequest,
  KnowledgeDocumentClass,
  KnowledgePresetDetail,
  KnowledgePresetRevisionDiff,
  KnowledgePresetRevisionDetail,
  KnowledgePresetSummary,
} from "@/types";
import { formatDate } from "@/utils/format";
import { knowledgeDocumentClassLabels } from "@/utils/workbenchPresentation";

type KnowledgePresetLibraryPanelProps = {
  presets: KnowledgePresetSummary[];
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
  documentClasses: KnowledgeDocumentClass[];
  tagsText: string;
  workspaceKey: string;
  uploadedTodayOnly: boolean;
  active: boolean;
};

const initialPresetForm: KnowledgePresetFormState = {
  name: "",
  description: "",
  documentClasses: [],
  tagsText: "",
  workspaceKey: "",
  uploadedTodayOnly: false,
  active: true,
};

const documentClasses: KnowledgeDocumentClass[] = [
  "contracts",
  "regulations",
  "correspondence",
  "techdocs",
  "other",
];

const normalizeTags = (value: string) =>
  value
    .split(",")
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);

const presetDiffFieldLabels: Record<string, string> = {
  name: "Название",
  description: "Описание",
  documentClasses: "Классы документов",
  tags: "Теги",
  workspaceKey: "Workspace key",
  uploadedTodayOnly: "Только загруженные сегодня",
  active: "Активность",
};

export function KnowledgePresetLibraryPanel({
  presets,
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
  const [form, setForm] = useState<KnowledgePresetFormState>(initialPresetForm);
  const [editingPresetId, setEditingPresetId] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const formModeLabel = editingPresetId ? "Редактирование" : "Создание";

  const selectedScopeSummary = useMemo(() => {
    if (!selectedPreset) {
      return "Выбери preset, чтобы увидеть детальный scope и историю ревизий.";
    }

    const parts = [];
    if (selectedPreset.scope.documentClasses.length > 0) {
      parts.push(
        `классы: ${selectedPreset.scope.documentClasses
          .map((item) => knowledgeDocumentClassLabels[item])
          .join(", ")}`,
      );
    }
    if (selectedPreset.scope.tags.length > 0) {
      parts.push(`теги: ${selectedPreset.scope.tags.join(", ")}`);
    }
    if (selectedPreset.scope.workspaceKey) {
      parts.push(`workspace: ${selectedPreset.scope.workspaceKey}`);
    }
    if (selectedPreset.scope.uploadedTodayOnly) {
      parts.push("только загруженные сегодня");
    }
    return parts.length > 0 ? parts.join(" · ") : "Preset не ограничивает корпус дополнительными фасетами.";
  }, [selectedPreset]);

  const toggleDocumentClass = (documentClass: KnowledgeDocumentClass) => {
    setForm((current) => ({
      ...current,
      documentClasses: current.documentClasses.includes(documentClass)
        ? current.documentClasses.filter((item) => item !== documentClass)
        : [...current.documentClasses, documentClass],
    }));
  };

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
    setForm({
      name: detail.name,
      description: detail.description ?? "",
      documentClasses: detail.scope.documentClasses,
      tagsText: detail.scope.tags.join(", "),
      workspaceKey: detail.scope.workspaceKey ?? "",
      uploadedTodayOnly: detail.scope.uploadedTodayOnly,
      active: detail.active,
    });
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setIsSubmitting(true);

    const payload: CreateKnowledgePresetRequest = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      active: form.active,
      scope: {
        presetIds: [],
        documentClasses: form.documentClasses,
        tags: normalizeTags(form.tagsText),
        workspaceKey: form.workspaceKey.trim() || null,
        uploadedTodayOnly: form.uploadedTodayOnly,
      },
    };

    try {
      if (editingPresetId) {
        await onUpdatePreset(editingPresetId, payload);
      } else {
        await onCreatePreset(payload);
      }
      setEditingPresetId(null);
      setForm(initialPresetForm);
    } finally {
      setIsSubmitting(false);
    }
  };

  const cancelEditing = () => {
    setEditingPresetId(null);
    setForm(initialPresetForm);
  };

  const handleDelete = async (presetId: string) => {
    const preset = presets.find((item) => item.id === presetId);
    const confirmed = window.confirm(`Удалить knowledge preset "${preset?.name ?? presetId}"?`);
    if (!confirmed) {
      return;
    }
    await onDeletePreset(presetId);
    if (editingPresetId === presetId) {
      cancelEditing();
    }
  };

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.02fr)_minmax(380px,0.98fr)]">
      <Card>
        <CardHeader>
          <SectionIntro
            badge={`${presets.length} corpus preset(s)`}
            badgeVariant="secondary"
            description="Knowledge presets ограничивают retrieval релевантным корпусом и помогают не искать сразу по всему knowledge base."
            eyebrow="Knowledge Scope"
            title="Сохранённые наборы знаний"
          />
        </CardHeader>
        <CardContent className="mt-0 space-y-4">
          {isLoading ? (
            <EmptyState description="Читаем presets корпуса." title="Загружаем knowledge presets" />
          ) : error ? (
            <EmptyState description={error} tone="danger" title="Не удалось загрузить presets" />
          ) : presets.length === 0 ? (
            <EmptyState
              description="Сохрани первый preset, чтобы retrieval можно было ограничивать релевантным корпусом."
              title="Knowledge presets пока пусты"
            />
          ) : (
            <div className="space-y-3">
              {presets.map((preset) => (
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

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <SectionIntro
              badge={formModeLabel}
              badgeVariant={editingPresetId ? "default" : "secondary"}
              description="Preset задаёт, по какому корпусу документов стоит искать ответ, и тоже имеет историю ревизий."
              eyebrow="Preset Editor"
              title={editingPresetId ? "Редактировать knowledge preset" : "Создать knowledge preset"}
            />
          </CardHeader>
          <CardContent className="mt-0 space-y-4">
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <Label htmlFor="preset-name">Название</Label>
                <Input
                  id="preset-name"
                  placeholder="Например: Договоры"
                  required
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="preset-description">Описание</Label>
                <Textarea
                  id="preset-description"
                  placeholder="Кратко опиши, когда использовать этот набор знаний."
                  rows={3}
                  value={form.description}
                  onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                />
              </div>

              <div className="space-y-3">
                <Label>Классы документов</Label>
                <div className="grid gap-3 sm:grid-cols-2">
                  {documentClasses.map((documentClass) => (
                    <label
                      className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
                      key={documentClass}
                    >
                      <Checkbox
                        aria-label={`Выбрать класс ${knowledgeDocumentClassLabels[documentClass]}`}
                        checked={form.documentClasses.includes(documentClass)}
                        onCheckedChange={() => toggleDocumentClass(documentClass)}
                      />
                      <span className="text-sm text-foreground">
                        {knowledgeDocumentClassLabels[documentClass]}
                      </span>
                    </label>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="preset-tags">Теги</Label>
                <Textarea
                  id="preset-tags"
                  placeholder="Например: procurement, premium, invoice"
                  rows={2}
                  value={form.tagsText}
                  onChange={(event) => setForm((current) => ({ ...current, tagsText: event.target.value }))}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="preset-workspace">Workspace key</Label>
                <Input
                  id="preset-workspace"
                  placeholder="Например: legal-assistant"
                  value={form.workspaceKey}
                  onChange={(event) => setForm((current) => ({ ...current, workspaceKey: event.target.value }))}
                />
              </div>

              <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
                <Checkbox
                  aria-label="Ограничить preset файлами, загруженными сегодня"
                  checked={form.uploadedTodayOnly}
                  onCheckedChange={(checked) =>
                    setForm((current) => ({ ...current, uploadedTodayOnly: Boolean(checked) }))
                  }
                />
                <span className="text-sm text-foreground">Только загруженные сегодня файлы</span>
              </label>

              <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
                <Checkbox
                  aria-label="Сделать knowledge preset активным"
                  checked={form.active}
                  onCheckedChange={(checked) =>
                    setForm((current) => ({ ...current, active: Boolean(checked) }))
                  }
                />
                <span className="text-sm text-foreground">Preset активен и доступен в выборе корпуса</span>
              </label>

              <div className="flex flex-wrap gap-3">
                <Button disabled={isSubmitting} type="submit">
                  <PlusCircle className="h-4 w-4" />
                  {isSubmitting
                    ? editingPresetId ? "Сохраняем..." : "Создаём..."
                    : editingPresetId ? "Сохранить изменения" : "Сохранить preset"}
                </Button>
                {editingPresetId ? (
                  <Button type="button" variant="secondary" onClick={cancelEditing}>
                    Отменить редактирование
                  </Button>
                ) : null}
              </div>
            </form>

            {message ? (
              <Alert variant="success">
                <AlertTitle>Knowledge preset сохранён</AlertTitle>
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

        <Card>
          <CardHeader>
            <SectionIntro
              badge={selectedPreset ? `rev ${selectedPreset.revision}` : "preview"}
              badgeVariant="secondary"
              eyebrow="Preset Inspector"
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
                          {revision.scope.documentClasses.length > 0
                            ? revision.scope.documentClasses.map((item) => knowledgeDocumentClassLabels[item]).join(", ")
                            : "Без ограничения по классам"}
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
                description="Открой любой preset слева, чтобы увидеть его scope и историю ревизий."
                icon={Database}
                title="Preset не выбран"
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
