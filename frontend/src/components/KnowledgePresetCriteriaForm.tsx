import { type Dispatch, type FormEvent, type SetStateAction } from "react";
import { PlusCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import type { ReferenceProject } from "@/types";
import {
  documentStatuses,
  documentTypes,
  languageCodes,
  type KnowledgePresetFormState,
} from "@/components/knowledgePresetPresentation";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

type KnowledgePresetCriteriaFormProps = {
  form: KnowledgePresetFormState;
  setForm: Dispatch<SetStateAction<KnowledgePresetFormState>>;
  activeProjects: ReferenceProject[];
  activeRagProjectKey: string;
  activeRagProjectName?: string | null;
  isFacet: boolean;
  entityLabel: string;
  isBusy: boolean;
  busyLabel: string;
  idleLabel: string;
  idPrefix: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel?: () => void;
};

export function KnowledgePresetCriteriaForm({
  form,
  setForm,
  activeProjects,
  activeRagProjectKey,
  activeRagProjectName,
  isFacet,
  entityLabel,
  isBusy,
  busyLabel,
  idleLabel,
  idPrefix,
  onSubmit,
  onCancel,
}: KnowledgePresetCriteriaFormProps) {
  const toggleListValue = <Value extends string,>(
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

  return (
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
            <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3" key={documentType}>
              <Checkbox
                aria-label={`Выбрать тип ${documentTypeLabels[documentType]}`}
                checked={form.documentTypes.includes(documentType)}
                onCheckedChange={() => toggleListValue("documentTypes", documentType)}
              />
              <span className="text-sm text-foreground">{documentTypeLabels[documentType]}</span>
            </label>
          ))}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="space-y-3">
          <Label>Статусы документов</Label>
          <div className="grid gap-3">
            {documentStatuses.map((documentStatus) => (
              <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3" key={documentStatus}>
                <Checkbox
                  aria-label={`Выбрать статус ${documentStatusLabels[documentStatus]}`}
                  checked={form.documentStatuses.includes(documentStatus)}
                  onCheckedChange={() => toggleListValue("documentStatuses", documentStatus)}
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
              <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3" key={languageCode}>
                <Checkbox
                  aria-label={`Выбрать язык ${materialLanguageCodeLabels[languageCode]}`}
                  checked={form.languageCodes.includes(languageCode)}
                  onCheckedChange={() => toggleListValue("languageCodes", languageCode)}
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
          <Select value="" onValueChange={(value) => toggleListValue("projectKeys", value)}>
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
                <Button key={projectKey} size="sm" type="button" variant="secondary" onClick={() => toggleListValue("projectKeys", projectKey)}>
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
        {([
          ["periodStartFrom", "Действует с: от"],
          ["periodStartTo", "Действует с: до"],
          ["periodEndFrom", "Действует по: от"],
          ["periodEndTo", "Действует по: до"],
        ] as const).map(([field, label]) => (
          <div className="space-y-2" key={field}>
            <Label htmlFor={`${idPrefix}-${field}`}>{label}</Label>
            <Input
              id={`${idPrefix}-${field}`}
              type="date"
              value={form[field]}
              onChange={(event) => setForm((current) => ({ ...current, [field]: event.target.value }))}
            />
          </div>
        ))}
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
        {activeRagProjectKey ? (
          <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground" id={`${idPrefix}-preset-workspace`}>
            <strong className="block font-semibold">{activeRagProjectName || activeRagProjectKey}</strong>
            <span className="text-xs text-muted-foreground">key: {activeRagProjectKey}</span>
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
          onCheckedChange={(checked) => setForm((current) => ({ ...current, uploadedTodayOnly: Boolean(checked) }))}
        />
        <span className="text-sm text-foreground">Только загруженные сегодня файлы</span>
      </label>

      <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
        <Checkbox
          aria-label={`Сделать ${entityLabel} активным`}
          checked={form.active}
          onCheckedChange={(checked) => setForm((current) => ({ ...current, active: Boolean(checked) }))}
        />
        <span className="text-sm text-foreground">{isFacet ? "Фасет" : "Preset"} активен и доступен в выборе корпуса</span>
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
