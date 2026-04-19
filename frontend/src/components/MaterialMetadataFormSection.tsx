import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { DocumentType, KnowledgeDocumentClass, SourceTrustLevel } from "@/types";
import type {
  MaterialMetadataFormErrors,
  MaterialMetadataFormState,
} from "@/utils/materialMetadata";
import {
  documentTypeLabels,
  materialKnowledgeDocumentClassLabels,
  sourceTrustLabels,
} from "@/utils/materialMetadata";

type MaterialMetadataFormSectionProps = {
  idPrefix: string;
  state: MaterialMetadataFormState;
  errors?: MaterialMetadataFormErrors;
  disabled?: boolean;
  disabledReason?: string;
  title?: string;
  description?: string;
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

const sourceTrustLevels: SourceTrustLevel[] = ["HIGH", "MEDIUM", "LOW", "UNKNOWN"];
const knowledgeDocumentClasses: KnowledgeDocumentClass[] = [
  "contracts",
  "regulations",
  "correspondence",
  "techdocs",
  "other",
];

export function MaterialMetadataFormSection({
  idPrefix,
  state,
  errors,
  disabled = false,
  disabledReason,
  title = "Атрибуты материала и фильтры поиска",
  description = "Заполни известные поля вручную или оставь пустыми: backend попробует определить их по названию, файлу и первым строкам документа.",
  onChange,
}: MaterialMetadataFormSectionProps) {
  const setField = <Field extends keyof MaterialMetadataFormState>(
    field: Field,
    value: MaterialMetadataFormState[Field],
  ) => {
    if (disabled) {
      return;
    }
    onChange({
      ...state,
      [field]: value,
    });
  };

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <p className="text-sm font-semibold text-foreground">{title}</p>
        <p className="text-sm leading-6 text-muted-foreground">
          {disabled && disabledReason ? disabledReason : description}
        </p>
      </div>

      <div className="space-y-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Фильтры поиска RAG</p>
          <p className="text-sm text-muted-foreground">
            Эти поля совпадают с фильтрами запроса по материалам, чтобы загруженные документы сразу можно было точно искать.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
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
            <Label htmlFor={`${idPrefix}-document-date`}>Дата документа</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-document-date`}
              type="date"
              value={state.documentDate}
              onChange={(event) => setField("documentDate", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-department`}>Подразделение</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-department`}
              placeholder="Например: Grid operations"
              value={state.department}
              onChange={(event) => setField("department", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-project`}>Проект</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-project`}
              placeholder="Например: North Upgrade"
              value={state.project}
              onChange={(event) => setField("project", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-counterparty`}>Контрагент</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-counterparty`}
              placeholder="Например: GridBuild LLP"
              value={state.counterparty}
              onChange={(event) => setField("counterparty", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-business-status`}>Статус</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-business-status`}
              placeholder="Например: APPROVED"
              value={state.businessStatus}
              onChange={(event) => setField("businessStatus", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-language`}>Язык</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-language`}
              placeholder="Например: ru"
              value={state.language}
              onChange={(event) => setField("language", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-source-trust`}>Доверие к источнику</Label>
            <Select
              disabled={disabled}
              value={state.sourceTrust}
              onValueChange={(value) => setField("sourceTrust", value as MaterialMetadataFormState["sourceTrust"])}
            >
              <SelectTrigger aria-label="Доверие к источнику" id={`${idPrefix}-source-trust`}>
                <SelectValue placeholder="Backend default" />
              </SelectTrigger>
              <SelectContent>
                {sourceTrustLevels.map((sourceTrust) => (
                  <SelectItem key={sourceTrust} value={sourceTrust}>
                    {sourceTrustLabels[sourceTrust]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2 md:col-span-2">
            <Label htmlFor={`${idPrefix}-tags`}>Теги / ключевые слова</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-tags`}
              placeholder="Например: grid, policy, reserve"
              value={state.tags}
              onChange={(event) => setField("tags", event.target.value)}
            />
          </div>
        </div>
      </div>

      <div className="space-y-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Классификация и служебные атрибуты</p>
          <p className="text-sm text-muted-foreground">
            Эти поля помогают backend выбрать корпус знаний, workspace и версию материала. Пустые значения не блокируют загрузку.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-document-type`}>Тип документа</Label>
            <Select
              disabled={disabled}
              value={state.documentType}
              onValueChange={(value) => setField("documentType", value as MaterialMetadataFormState["documentType"])}
            >
              <SelectTrigger aria-label="Тип документа" id={`${idPrefix}-document-type`}>
                <SelectValue placeholder="Backend auto-fill" />
              </SelectTrigger>
              <SelectContent>
                {documentTypes.map((documentType) => (
                  <SelectItem key={documentType} value={documentType}>
                    {documentTypeLabels[documentType]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-knowledge-class`}>Класс корпуса знаний</Label>
            <Select
              disabled={disabled}
              value={state.knowledgeDocumentClass}
              onValueChange={(value) => setField(
                "knowledgeDocumentClass",
                value as MaterialMetadataFormState["knowledgeDocumentClass"],
              )}
            >
              <SelectTrigger aria-label="Класс корпуса знаний" id={`${idPrefix}-knowledge-class`}>
                <SelectValue placeholder="Backend auto-fill" />
              </SelectTrigger>
              <SelectContent>
                {knowledgeDocumentClasses.map((documentClass) => (
                  <SelectItem key={documentClass} value={documentClass}>
                    {materialKnowledgeDocumentClassLabels[documentClass]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-workspace-key`}>Workspace</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-workspace-key`}
              placeholder="Например: north-upgrade"
              value={state.workspaceKey}
              onChange={(event) => setField("workspaceKey", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-author`}>Автор</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-author`}
              placeholder="Например: Ops lead"
              value={state.author}
              onChange={(event) => setField("author", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-version-label`}>Версия</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-version-label`}
              placeholder="Например: v3.2"
              value={state.versionLabel}
              onChange={(event) => setField("versionLabel", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-period-start`}>Период с</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-period-start`}
              type="date"
              value={state.periodStart}
              onChange={(event) => setField("periodStart", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-period-end`}>Период по</Label>
            <Input
              disabled={disabled}
              id={`${idPrefix}-period-end`}
              type="date"
              value={state.periodEnd}
              onChange={(event) => setField("periodEnd", event.target.value)}
            />
          </div>
        </div>

        {errors?.periodStart || errors?.periodEnd ? (
          <p className="text-sm text-destructive">{errors.periodStart ?? errors.periodEnd}</p>
        ) : null}
      </div>
    </div>
  );
}
