import { useEffect, useMemo } from "react";
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
import type {
  DocumentStatus,
  DocumentType,
  MaterialLanguageCode,
  ReferenceProject,
  ReferenceWorkspace,
} from "@/types";
import type {
  MaterialLanguageFormValue,
  MaterialMetadataFormErrors,
  MaterialMetadataFormState,
} from "@/utils/materialMetadata";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

type MaterialMetadataFormSectionProps = {
  idPrefix: string;
  state: MaterialMetadataFormState;
  errors?: MaterialMetadataFormErrors;
  disabled?: boolean;
  disabledReason?: string;
  title?: string;
  description?: string;
  workspaces: ReferenceWorkspace[];
  projects: ReferenceProject[];
  isReferenceDataLoading?: boolean;
  referenceDataError?: string | null;
  onChange: (next: MaterialMetadataFormState) => void;
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
const languageOptions: MaterialLanguageFormValue[] = ["AUTO", "RU", "KK", "EN"];
const NO_PROJECT_VALUE = "__none__";

const languageLabel = (languageCode: MaterialLanguageFormValue) =>
  languageCode === "AUTO" ? "Определить автоматически" : materialLanguageCodeLabels[languageCode];

export function MaterialMetadataFormSection({
  idPrefix,
  state,
  errors,
  disabled = false,
  disabledReason,
  title = "Метаданные документа",
  description = "Выберите рабочую область, тип и статус документа. Остальные поля можно заполнить в блоке «Дополнительно».",
  workspaces,
  projects,
  isReferenceDataLoading = false,
  referenceDataError = null,
  onChange,
}: MaterialMetadataFormSectionProps) {
  const activeWorkspaces = useMemo(
    () => workspaces.filter((workspace) => workspace.active),
    [workspaces],
  );
  const activeProjects = useMemo(
    () => projects.filter((project) => project.active),
    [projects],
  );
  const defaultWorkspaceKey = useMemo(
    () => activeWorkspaces.find((workspace) => workspace.isDefault)?.key ?? activeWorkspaces[0]?.key ?? "",
    [activeWorkspaces],
  );
  const availableProjects = useMemo(
    () => activeProjects.filter((project) => project.workspaceKey === state.workspaceKey),
    [activeProjects, state.workspaceKey],
  );

  useEffect(() => {
    if (disabled || activeWorkspaces.length === 0) {
      return;
    }

    const workspaceExists = activeWorkspaces.some((workspace) => workspace.key === state.workspaceKey);
    const nextWorkspaceKey = workspaceExists ? state.workspaceKey : defaultWorkspaceKey;
    const projectExists = activeProjects.some(
      (project) => project.key === state.projectKey && project.workspaceKey === nextWorkspaceKey,
    );
    const nextProjectKey = projectExists ? state.projectKey : "";

    if (nextWorkspaceKey !== state.workspaceKey || nextProjectKey !== state.projectKey) {
      onChange({
        ...state,
        workspaceKey: nextWorkspaceKey,
        projectKey: nextProjectKey,
      });
    }
  }, [activeProjects, activeWorkspaces, defaultWorkspaceKey, disabled, onChange, state]);

  const setField = <Field extends keyof MaterialMetadataFormState>(
    field: Field,
    value: MaterialMetadataFormState[Field],
  ) => {
    if (disabled) {
      return;
    }

    if (field === "workspaceKey") {
      onChange({
        ...state,
        workspaceKey: value as string,
        projectKey: "",
      });
      return;
    }

    if (field === "openEnded") {
      onChange({
        ...state,
        openEnded: value as boolean,
        periodEnd: value ? "" : state.periodEnd,
      });
      return;
    }

    onChange({
      ...state,
      [field]: value,
    });
  };

  const isProjectDisabled = disabled || !state.workspaceKey || availableProjects.length === 0;
  const referenceHint = referenceDataError
    ? referenceDataError
    : isReferenceDataLoading
      ? "Загружаем справочники..."
      : activeWorkspaces.length === 0
        ? "Нет активных рабочих областей."
        : null;

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <p className="text-sm leading-6 text-muted-foreground">
          {disabled && disabledReason ? disabledReason : description}
        </p>
        {referenceHint ? (
          <p className="text-sm text-muted-foreground">{referenceHint}</p>
        ) : null}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-workspace-key`}>Рабочая область</Label>
          <Select
            disabled={disabled || isReferenceDataLoading || activeWorkspaces.length === 0}
            value={state.workspaceKey}
            onValueChange={(value) => setField("workspaceKey", value)}
          >
            <SelectTrigger aria-label="Рабочая область" id={`${idPrefix}-workspace-key`}>
              <SelectValue placeholder="Выберите область" />
            </SelectTrigger>
            <SelectContent>
              {activeWorkspaces.map((workspace) => (
                <SelectItem key={workspace.key} value={workspace.key}>
                  {workspace.nameRu}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors?.workspaceKey ? <p className="text-sm text-destructive">{errors.workspaceKey}</p> : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-document-type`}>Тип документа</Label>
          <Select
            disabled={disabled}
            value={state.documentType}
            onValueChange={(value) => setField("documentType", value as DocumentType)}
          >
            <SelectTrigger aria-label="Тип документа" id={`${idPrefix}-document-type`}>
              <SelectValue placeholder="Выберите тип" />
            </SelectTrigger>
            <SelectContent>
              {documentTypes.map((documentType) => (
                <SelectItem key={documentType} value={documentType}>
                  {documentTypeLabels[documentType]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors?.documentType ? <p className="text-sm text-destructive">{errors.documentType}</p> : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor={`${idPrefix}-document-status`}>Статус документа</Label>
          <Select
            disabled={disabled}
            value={state.documentStatus}
            onValueChange={(value) => setField("documentStatus", value as DocumentStatus)}
          >
            <SelectTrigger aria-label="Статус документа" id={`${idPrefix}-document-status`}>
              <SelectValue placeholder="Выберите статус" />
            </SelectTrigger>
            <SelectContent>
              {documentStatuses.map((documentStatus) => (
                <SelectItem key={documentStatus} value={documentStatus}>
                  {documentStatusLabels[documentStatus]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {errors?.documentStatus ? <p className="text-sm text-destructive">{errors.documentStatus}</p> : null}
        </div>
      </div>

      <details className="rounded-2xl border border-border bg-background px-4 py-3">
        <summary className="cursor-pointer text-sm font-semibold text-foreground">
          Дополнительно
        </summary>

        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-project-key`}>Проект</Label>
            <Select
              disabled={isProjectDisabled}
              value={state.projectKey || NO_PROJECT_VALUE}
              onValueChange={(value) => setField("projectKey", value === NO_PROJECT_VALUE ? "" : value)}
            >
              <SelectTrigger aria-label="Проект" id={`${idPrefix}-project-key`}>
                <SelectValue placeholder="Выберите проект" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_PROJECT_VALUE}>Без проекта</SelectItem>
                {availableProjects.map((project) => (
                  <SelectItem key={project.key} value={project.key}>
                    {project.nameRu}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-document-number`}>Номер документа</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-document-number`}
              placeholder="Например: POL-2026-17"
              value={state.documentNumber}
              onChange={(event) => setField("documentNumber", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-language-code`}>Язык документа</Label>
            <Select
              disabled={disabled}
              value={state.languageCode}
              onValueChange={(value) => setField("languageCode", value as MaterialLanguageFormValue)}
            >
              <SelectTrigger aria-label="Язык документа" id={`${idPrefix}-language-code`}>
                <SelectValue placeholder="Определить автоматически" />
              </SelectTrigger>
              <SelectContent>
                {languageOptions.map((languageCode) => (
                  <SelectItem key={languageCode} value={languageCode}>
                    {languageLabel(languageCode)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-period-start`}>Действует с</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-period-start`}
              type="date"
              value={state.periodStart}
              onChange={(event) => setField("periodStart", event.target.value)}
            />
          </div>

          <div className="flex items-center gap-3 md:col-span-2">
            <Checkbox
              checked={state.openEnded}
              disabled={disabled}
              id={`${idPrefix}-open-ended`}
              onCheckedChange={(checked) => setField("openEnded", checked === true)}
            />
            <Label className="text-sm font-medium" htmlFor={`${idPrefix}-open-ended`}>
              Без срока действия
            </Label>
          </div>

          {!state.openEnded ? (
            <div className="space-y-2">
              <Label htmlFor={`${idPrefix}-period-end`}>Действует по</Label>
              <Input
                disabled={disabled}
                id={`${idPrefix}-period-end`}
                type="date"
                value={state.periodEnd}
                onChange={(event) => setField("periodEnd", event.target.value)}
              />
            </div>
          ) : null}

          <div className="space-y-2 md:col-span-2">
            <Label htmlFor={`${idPrefix}-manual-tags`}>Теги</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-manual-tags`}
              placeholder="Например: grid, policy, reserve"
              value={state.manualTags}
              onChange={(event) => setField("manualTags", event.target.value)}
            />
          </div>
        </div>

        {errors?.periodStart || errors?.periodEnd ? (
          <p className="mt-3 text-sm text-destructive">{errors.periodStart ?? errors.periodEnd}</p>
        ) : null}
      </details>
    </div>
  );
}
