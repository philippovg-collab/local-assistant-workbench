import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { DocumentType, SourceTrustLevel } from "@/types";
import type {
  MaterialMetadataFormErrors,
  MaterialMetadataFormState,
} from "@/utils/materialMetadata";
import {
  documentTypeLabels,
  sourceTrustLabels,
} from "@/utils/materialMetadata";

type MaterialMetadataFormSectionProps = {
  idPrefix: string;
  state: MaterialMetadataFormState;
  errors?: MaterialMetadataFormErrors;
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

export function MaterialMetadataFormSection({
  idPrefix,
  state,
  errors,
  onChange,
}: MaterialMetadataFormSectionProps) {
  const setField = <Field extends keyof MaterialMetadataFormState>(
    field: Field,
    value: MaterialMetadataFormState[Field],
  ) => {
    onChange({
      ...state,
      [field]: value,
    });
  };

  return (
    <div className="space-y-6">
      <div className="space-y-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Обязательные атрибуты</p>
          <p className="text-sm text-muted-foreground">
            Заполни тип документа, уровень доверия и хотя бы автора или подразделение.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-document-type`}>Тип документа</Label>
            <Select
              value={state.documentType}
              onValueChange={(value) => setField("documentType", value as MaterialMetadataFormState["documentType"])}
            >
              <SelectTrigger aria-label="Тип документа" id={`${idPrefix}-document-type`}>
                <SelectValue placeholder="Выбери тип" />
              </SelectTrigger>
              <SelectContent>
                {documentTypes.map((documentType) => (
                  <SelectItem key={documentType} value={documentType}>
                    {documentTypeLabels[documentType]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors?.documentType ? (
              <p className="text-sm text-destructive">{errors.documentType}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-source-trust`}>Уровень доверия</Label>
            <Select
              value={state.sourceTrust}
              onValueChange={(value) => setField("sourceTrust", value as MaterialMetadataFormState["sourceTrust"])}
            >
              <SelectTrigger aria-label="Уровень доверия" id={`${idPrefix}-source-trust`}>
                <SelectValue placeholder="Выбери доверие" />
              </SelectTrigger>
              <SelectContent>
                {sourceTrustLevels.map((sourceTrust) => (
                  <SelectItem key={sourceTrust} value={sourceTrust}>
                    {sourceTrustLabels[sourceTrust]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors?.sourceTrust ? (
              <p className="text-sm text-destructive">{errors.sourceTrust}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-author`}>Автор</Label>
            <Input
              id={`${idPrefix}-author`}
              placeholder="Например: Ops lead"
              value={state.author}
              onChange={(event) => setField("author", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-department`}>Подразделение</Label>
            <Input
              id={`${idPrefix}-department`}
              placeholder="Например: Grid operations"
              value={state.department}
              onChange={(event) => setField("department", event.target.value)}
            />
          </div>
        </div>

        {errors?.author || errors?.department ? (
          <p className="text-sm text-destructive">{errors.author ?? errors.department}</p>
        ) : null}
      </div>

      <div className="space-y-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Рекомендуемые атрибуты</p>
          <p className="text-sm text-muted-foreground">
            Если оставить эти поля пустыми, backend попробует осторожно auto-fill их по filename, media type,
            title и первым строкам контента.
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-document-date`}>Дата документа</Label>
            <Input
              id={`${idPrefix}-document-date`}
              type="date"
              value={state.documentDate}
              onChange={(event) => setField("documentDate", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-version-label`}>Версия</Label>
            <Input
              id={`${idPrefix}-version-label`}
              placeholder="Например: v3.2"
              value={state.versionLabel}
              onChange={(event) => setField("versionLabel", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-language`}>Язык</Label>
            <Input
              id={`${idPrefix}-language`}
              placeholder="Например: ru"
              value={state.language}
              onChange={(event) => setField("language", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-document-number`}>Номер документа</Label>
            <Input
              id={`${idPrefix}-document-number`}
              placeholder="Например: POL-2026-17"
              value={state.documentNumber}
              onChange={(event) => setField("documentNumber", event.target.value)}
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <Label htmlFor={`${idPrefix}-tags`}>Теги</Label>
            <Input
              id={`${idPrefix}-tags`}
              placeholder="Например: grid, policy, reserve"
              value={state.tags}
              onChange={(event) => setField("tags", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-project`}>Проект</Label>
            <Input
              id={`${idPrefix}-project`}
              placeholder="Например: North Upgrade"
              value={state.project}
              onChange={(event) => setField("project", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-counterparty`}>Контрагент</Label>
            <Input
              id={`${idPrefix}-counterparty`}
              placeholder="Например: GridBuild LLP"
              value={state.counterparty}
              onChange={(event) => setField("counterparty", event.target.value)}
            />
          </div>

          <div className="space-y-2 md:col-span-2">
            <Label htmlFor={`${idPrefix}-business-status`}>Статус</Label>
            <Input
              id={`${idPrefix}-business-status`}
              placeholder="Например: APPROVED"
              value={state.businessStatus}
              onChange={(event) => setField("businessStatus", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-period-start`}>Период с</Label>
            <Input
              id={`${idPrefix}-period-start`}
              type="date"
              value={state.periodStart}
              onChange={(event) => setField("periodStart", event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor={`${idPrefix}-period-end`}>Период по</Label>
            <Input
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
