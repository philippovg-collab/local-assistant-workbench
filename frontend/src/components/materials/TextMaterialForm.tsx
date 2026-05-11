import { useState, type FormEvent } from "react";
import { FileText } from "lucide-react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MaterialMetadataFormSection } from "@/components/MaterialMetadataFormSection";
import { Checkbox } from "@/components/ui/checkbox";
import type { MaterialLineageOverrideInput, MaterialMetadataInput, ReferenceProject, ReferenceWorkspace } from "@/types";
import {
  emptyMaterialMetadataFormState,
  type MaterialMetadataValidation,
  toMaterialMetadataInput,
  validateMaterialMetadata,
} from "@/utils/materialMetadata";

type TextMaterialFormProps = {
  metadataV1Enabled: boolean;
  metadataDisabledReason: string;
  referenceWorkspaces: ReferenceWorkspace[];
  referenceProjects: ReferenceProject[];
  isReferenceDataLoading: boolean;
  referenceDataError: string | null;
  activeWorkspaceKey?: string | null;
  activeWorkspaceName?: string | null;
  onCreateText: (input: {
    title: string;
    content: string;
    metadata?: MaterialMetadataInput;
    lineageOverride?: MaterialLineageOverrideInput;
  }) => Promise<unknown>;
};

export function TextMaterialForm({
  metadataV1Enabled,
  metadataDisabledReason,
  referenceWorkspaces,
  referenceProjects,
  isReferenceDataLoading,
  referenceDataError,
  activeWorkspaceKey,
  activeWorkspaceName,
  onCreateText,
}: TextMaterialFormProps) {
  const [textTitle, setTextTitle] = useState("");
  const [textContent, setTextContent] = useState("");
  const [textMetadata, setTextMetadata] = useState(emptyMaterialMetadataFormState);
  const [lineageOverrideEnabled, setLineageOverrideEnabled] = useState(false);
  const [lineageOverrideKey, setLineageOverrideKey] = useState("");
  const [lineageOverrideReason, setLineageOverrideReason] = useState("");
  const [confirmLineageReuse, setConfirmLineageReuse] = useState(false);
  const [textMetadataValidation, setTextMetadataValidation] = useState<MaterialMetadataValidation | null>(null);
  const [isSavingText, setIsSavingText] = useState(false);
  const normalizedActiveWorkspaceKey = (activeWorkspaceKey ?? "").trim();
  const normalizedActiveWorkspaceName = activeWorkspaceName ?? "";
  const isMetadataUnavailable = metadataV1Enabled
    && (isReferenceDataLoading || Boolean(referenceDataError) || !normalizedActiveWorkspaceKey);

  const withActiveWorkspace = () => ({
    ...textMetadata,
    workspaceKey: normalizedActiveWorkspaceKey,
    projectKey: "",
  });

  const handleTextSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isMetadataUnavailable) {
      return;
    }
    if (metadataV1Enabled) {
      const validation = validateMaterialMetadata(withActiveWorkspace());
      if (!validation.isValid) {
        setTextMetadataValidation(validation);
        return;
      }
    }

    setIsSavingText(true);

    try {
      setTextMetadataValidation(null);
      const lineageOverride = lineageOverrideEnabled
        ? {
            lineageKey: lineageOverrideKey.trim(),
            reason: lineageOverrideReason.trim(),
            confirmSupersedeExistingLineage: confirmLineageReuse,
          }
        : undefined;
      await onCreateText({
        title: textTitle,
        content: textContent,
        ...(metadataV1Enabled ? { metadata: toMaterialMetadataInput(withActiveWorkspace()) } : {}),
        ...(lineageOverride ? { lineageOverride } : {}),
      });
      setTextTitle("");
      setTextContent("");
      setTextMetadata(emptyMaterialMetadataFormState());
      setLineageOverrideEnabled(false);
      setLineageOverrideKey("");
      setLineageOverrideReason("");
      setConfirmLineageReuse(false);
    } finally {
      setIsSavingText(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <SectionIntro
          eyebrow="Create"
          title="Добавить текст"
          description={metadataV1Enabled
            ? "Быстрый путь для FAQ, заметок и кратких фрагментов. Metadata уходит в тот же material contract, а пустые рекомендованные поля backend попробует auto-fill по title и первым строкам текста."
            : "Быстрый путь для FAQ, заметок и кратких фрагментов. Metadata capture сейчас отключён rollout-флагом, поэтому текст сохраняется без metadata payload."}
        />
      </CardHeader>
      <CardContent className="mt-0">
        <form className="space-y-4" onSubmit={handleTextSubmit}>
          <div className="space-y-2">
            <Label htmlFor="text-material-title">Название</Label>
            <Input
              id="text-material-title"
              placeholder="Например: Pricing note"
              value={textTitle}
              onChange={(event) => setTextTitle(event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="text-material-content">Содержимое</Label>
            <Textarea
              id="text-material-content"
              placeholder="Вставь сюда фрагмент документа, FAQ или инструкции."
              required
              rows={9}
              value={textContent}
              onChange={(event) => setTextContent(event.target.value)}
            />
          </div>

          {!metadataV1Enabled ? (
            <Alert>
              <AlertTitle>Metadata capture отключён</AlertTitle>
              <AlertDescription>
                Rollout flag metadata-v1 выключен, поэтому поля ниже показаны как контракт, но не будут отправлены.
              </AlertDescription>
            </Alert>
          ) : null}

          <MaterialMetadataFormSection
            disabled={!metadataV1Enabled}
            disabledReason={metadataDisabledReason}
            errors={textMetadataValidation?.fieldErrors}
            idPrefix="text-material"
            isReferenceDataLoading={isReferenceDataLoading}
            lockedWorkspaceKey={normalizedActiveWorkspaceKey}
            lockedWorkspaceName={normalizedActiveWorkspaceName}
            hideProjectField
            projects={referenceProjects}
            referenceDataError={referenceDataError}
            state={textMetadata}
            workspaces={referenceWorkspaces}
            onChange={(next) => {
              setTextMetadata(next);
              if (textMetadataValidation) {
                setTextMetadataValidation(null);
              }
            }}
          />

          <div className="rounded-2xl border border-field-border bg-field px-4 py-4">
            <label className="flex items-center gap-2 text-sm font-medium text-foreground">
              <Checkbox
                checked={lineageOverrideEnabled}
                onCheckedChange={(checked) => setLineageOverrideEnabled(checked === true)}
              />
              Lineage override
            </label>
            {lineageOverrideEnabled ? (
              <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                <div className="space-y-2">
                  <Label htmlFor="text-lineage-override-key">Override key</Label>
                  <Input
                    id="text-lineage-override-key"
                    placeholder="golden-policy-2026"
                    value={lineageOverrideKey}
                    onChange={(event) => setLineageOverrideKey(event.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="text-lineage-override-reason">Reason</Label>
                  <Input
                    id="text-lineage-override-reason"
                    placeholder="Operator-controlled versioned document"
                    value={lineageOverrideReason}
                    onChange={(event) => setLineageOverrideReason(event.target.value)}
                  />
                </div>
                <label className="flex items-center gap-2 text-sm font-medium text-foreground lg:col-span-2">
                  <Checkbox
                    checked={confirmLineageReuse}
                    onCheckedChange={(checked) => setConfirmLineageReuse(checked === true)}
                  />
                  Confirm reuse
                </label>
              </div>
            ) : null}
          </div>

          {metadataV1Enabled && textMetadataValidation?.messages.length ? (
            <Alert variant="destructive">
              <AlertTitle>Metadata заполнены не полностью</AlertTitle>
              <AlertDescription>{textMetadataValidation.messages.join(" ")}</AlertDescription>
            </Alert>
          ) : null}

          <Button
            disabled={
              isSavingText
              || !textContent.trim()
              || isMetadataUnavailable
              || (lineageOverrideEnabled && (!lineageOverrideKey.trim() || !lineageOverrideReason.trim()))
            }
            type="submit"
          >
            <FileText className="h-4 w-4" />
            {isSavingText ? "Сохраняем..." : "Сохранить текст"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
