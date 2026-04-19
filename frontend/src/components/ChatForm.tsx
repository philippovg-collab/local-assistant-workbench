import { useId, type FormEvent } from "react";
import { Info, LibraryBig, ShieldCheck } from "lucide-react";
import { InstructionSelector } from "@/components/InstructionSelector";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
  AnswerMode,
  InstructionSummary,
  KnowledgeDocumentClass,
  KnowledgePresetSummary,
  ModelInfo,
} from "@/types";
import {
  answerModeDescriptions,
  answerModeLabels,
  knowledgeDocumentClassLabels,
} from "@/utils/workbenchPresentation";

type KnowledgeControls = {
  presets: KnowledgePresetSummary[];
  selectedPresetIds: string[];
  onTogglePreset: (presetId: string) => void;
  selectedDocumentClasses: KnowledgeDocumentClass[];
  onToggleDocumentClass: (documentClass: KnowledgeDocumentClass) => void;
  knowledgeTagsText: string;
  onKnowledgeTagsTextChange: (value: string) => void;
  workspaceKey: string;
  onWorkspaceKeyChange: (value: string) => void;
  uploadedTodayOnly: boolean;
  onUploadedTodayOnlyChange: (value: boolean) => void;
  activeScopeSummary: string;
};

type ChatFormProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  answerMode: AnswerMode;
  onAnswerModeChange: (value: AnswerMode) => void;
  temporaryInstructionLabel: string;
  temporaryInstruction: string;
  temporaryInstructionRows: number;
  temporaryInstructionPlaceholder?: string;
  onTemporaryInstructionChange: (value: string) => void;
  promptLabel: string;
  prompt: string;
  promptRows: number;
  promptPlaceholder?: string;
  onPromptChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  instructionEmptyStateMessage: string;
  knowledgeControls?: KnowledgeControls;
  isSubmitting: boolean;
  isSubmitDisabled: boolean;
  submitIdleLabel: string;
  submitBusyLabel: string;
  helperText: string;
  error: string | null;
  onSubmit: () => Promise<unknown>;
};

const documentClasses: KnowledgeDocumentClass[] = [
  "contracts",
  "regulations",
  "correspondence",
  "techdocs",
  "other",
];

export function ChatForm({
  models,
  modelsError,
  selectedModel,
  onModelChange,
  answerMode,
  onAnswerModeChange,
  temporaryInstructionLabel,
  temporaryInstruction,
  temporaryInstructionRows,
  temporaryInstructionPlaceholder,
  onTemporaryInstructionChange,
  promptLabel,
  prompt,
  promptRows,
  promptPlaceholder,
  onPromptChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  instructionEmptyStateMessage,
  knowledgeControls,
  isSubmitting,
  isSubmitDisabled,
  submitIdleLabel,
  submitBusyLabel,
  helperText,
  error,
  onSubmit,
}: ChatFormProps) {
  const modelLabelId = useId();
  const answerModeLabelId = useId();
  const temporaryInstructionId = useId();
  const promptId = useId();
  const knowledgeTagsId = useId();
  const workspaceId = useId();

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit();
  };

  return (
    <form className="space-y-6" onSubmit={handleSubmit}>
      <div className="grid gap-5 xl:grid-cols-[220px_220px_minmax(0,1fr)]">
        <div className="space-y-2">
          <Label id={modelLabelId}>Модель</Label>
          <Select value={selectedModel} onValueChange={onModelChange}>
            <SelectTrigger aria-labelledby={modelLabelId}>
              <SelectValue placeholder="Выбери модель" />
            </SelectTrigger>
            <SelectContent>
              {models.length > 0 ? (
                models.map((model) => (
                  <SelectItem key={model.name} value={model.name}>
                    {model.name}
                  </SelectItem>
                ))
              ) : (
                <SelectItem value={selectedModel}>{selectedModel}</SelectItem>
              )}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label id={answerModeLabelId}>Режим ответа</Label>
          <Select value={answerMode} onValueChange={(value) => onAnswerModeChange(value as AnswerMode)}>
            <SelectTrigger aria-labelledby={answerModeLabelId}>
              <SelectValue placeholder="Выбери режим ответа" />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(answerModeLabels).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  {label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs leading-5 text-muted-foreground">{answerModeDescriptions[answerMode]}</p>
        </div>

        <div className="space-y-2">
          <Label htmlFor={temporaryInstructionId}>{temporaryInstructionLabel}</Label>
          <Textarea
            id={temporaryInstructionId}
            placeholder={temporaryInstructionPlaceholder}
            rows={temporaryInstructionRows}
            value={temporaryInstruction}
            onChange={(event) => onTemporaryInstructionChange(event.target.value)}
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor={promptId}>{promptLabel}</Label>
        <Textarea
          id={promptId}
          placeholder={promptPlaceholder}
          required
          rows={promptRows}
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
        />
      </div>

      {knowledgeControls ? (
        <>
          <Separator />

          <section className="space-y-4 rounded-[26px] border border-border bg-surface-subtle/80 p-4">
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <LibraryBig className="h-4 w-4 text-primary" />
              Активный корпус знаний
            </div>

            <div className="grid gap-4 xl:grid-cols-2">
              <div className="space-y-3">
                <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                  Готовые пресеты
                </p>
                {knowledgeControls.presets.length === 0 ? (
                  <p className="text-sm leading-6 text-muted-foreground">
                    Пресеты корпуса пока не настроены. Можно использовать ручные фасеты ниже.
                  </p>
                ) : (
                  <div className="grid gap-3">
                    {knowledgeControls.presets.map((preset) => {
                      const checked = knowledgeControls.selectedPresetIds.includes(preset.id);
                      return (
                        <label
                          className="flex items-start gap-3 rounded-[22px] border border-field-border bg-field px-4 py-3"
                          key={preset.id}
                        >
                          <Checkbox
                            aria-label={`Выбрать knowledge preset ${preset.name}`}
                            checked={checked}
                            onCheckedChange={() => knowledgeControls.onTogglePreset(preset.id)}
                          />
                          <div className="space-y-1">
                            <strong className="block text-sm font-semibold text-foreground">
                              {preset.name}
                            </strong>
                            <p className="text-sm leading-6 text-muted-foreground">
                              {preset.description?.trim() || `Ревизия ${preset.revision}`}
                            </p>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>

              <div className="space-y-4">
                <div className="space-y-3">
                  <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                    Фасеты поиска
                  </p>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {documentClasses.map((documentClass) => (
                      <label
                        className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
                        key={documentClass}
                      >
                        <Checkbox
                          aria-label={`Ограничить корпус ${knowledgeDocumentClassLabels[documentClass]}`}
                          checked={knowledgeControls.selectedDocumentClasses.includes(documentClass)}
                          onCheckedChange={() => knowledgeControls.onToggleDocumentClass(documentClass)}
                        />
                        <span className="text-sm text-foreground">
                          {knowledgeDocumentClassLabels[documentClass]}
                        </span>
                      </label>
                    ))}
                  </div>
                </div>

                <div className="grid gap-4">
                  <div className="space-y-2">
                    <Label htmlFor={knowledgeTagsId}>Теги корпуса</Label>
                    <Textarea
                      id={knowledgeTagsId}
                      placeholder="Например: финансы, premium, SLA"
                      rows={2}
                      value={knowledgeControls.knowledgeTagsText}
                      onChange={(event) => knowledgeControls.onKnowledgeTagsTextChange(event.target.value)}
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor={workspaceId}>Workspace / project key</Label>
                    <Textarea
                      id={workspaceId}
                      placeholder="Например: sales-bot"
                      rows={2}
                      value={knowledgeControls.workspaceKey}
                      onChange={(event) => knowledgeControls.onWorkspaceKeyChange(event.target.value)}
                    />
                  </div>

                  <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
                    <Checkbox
                      aria-label="Ограничить поиск загруженными сегодня файлами"
                      checked={knowledgeControls.uploadedTodayOnly}
                      onCheckedChange={(checked) =>
                        knowledgeControls.onUploadedTodayOnlyChange(Boolean(checked))
                      }
                    />
                    <span className="text-sm text-foreground">Только загруженные сегодня файлы</span>
                  </label>
                </div>
              </div>
            </div>

            <div className="rounded-[22px] border border-primary/15 bg-primary/5 px-4 py-3 text-sm text-foreground">
              {knowledgeControls.activeScopeSummary}
            </div>
          </section>
        </>
      ) : null}

      <Separator />

      <InstructionSelector
        emptyStateMessage={instructionEmptyStateMessage}
        instructions={instructions}
        label="Инструкции уровня chat / scenario"
        selectedInstructionIds={selectedInstructionIds}
        onToggleInstruction={onToggleInstruction}
      />

      <div className="surface-subtle flex flex-col gap-4 rounded-[24px] p-4">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <Button disabled={isSubmitDisabled} type="submit">
            {isSubmitting ? submitBusyLabel : submitIdleLabel}
          </Button>

          <div className="flex items-start gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-muted-foreground md:max-w-xl">
            <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
            <p className="leading-6">
              {modelsError ? `Список моделей сейчас недоступен: ${modelsError}` : helperText}
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-muted-foreground">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <p className="leading-6">
            Системная и workspace-инструкции подставляются автоматически. Здесь ты управляешь режимом ответа,
            временной инструкцией и сценарными инструкциями для текущего запроса.
          </p>
        </div>

        {error ? (
          <Alert variant="destructive">
            <AlertTitle>Не удалось выполнить запрос</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}
      </div>
    </form>
  );
}
