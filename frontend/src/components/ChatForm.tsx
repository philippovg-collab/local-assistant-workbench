import { useId, type FormEvent } from "react";
import { LibraryBig } from "lucide-react";
import { ChatFormActions } from "@/components/ChatFormActions";
import { InstructionSelector } from "@/components/InstructionSelector";
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
  KnowledgePresetSummary,
  ModelInfo,
} from "@/types";
import {
  answerModeDescriptions,
  answerModeLabels,
} from "@/utils/workbenchPresentation";

type KnowledgeControls = {
  presets: KnowledgePresetSummary[];
  facets: KnowledgePresetSummary[];
  selectedPresetIds: string[];
  onTogglePreset: (presetId: string) => void;
  selectedFacetIds: string[];
  onToggleFacet: (facetId: string) => void;
  workspaceKey: string;
  workspaceName: string;
  activeScopeSummary: string;
};

export type ChatFormProps = {
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
  isCancelling?: boolean;
  currentRunId?: string | null;
  useLongTermMemory?: boolean;
  onUseLongTermMemoryChange?: (value: boolean) => void;
  isSubmitDisabled: boolean;
  submitIdleLabel: string;
  submitBusyLabel: string;
  cancelLabel?: string;
  helperText: string;
  error: string | null;
  onSubmit: () => Promise<unknown>;
  onCancelCurrentRun?: () => Promise<unknown>;
};

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
  isCancelling = false,
  currentRunId = null,
  useLongTermMemory,
  onUseLongTermMemoryChange,
  isSubmitDisabled,
  submitIdleLabel,
  submitBusyLabel,
  cancelLabel = "Отменить запуск",
  helperText,
  error,
  onSubmit,
  onCancelCurrentRun,
}: ChatFormProps) {
  const modelLabelId = useId();
  const answerModeLabelId = useId();
  const temporaryInstructionId = useId();
  const promptId = useId();
  const isModelCatalogUnavailable = models.length === 0;
  const modelCatalogMessage = modelsError
    ? `Каталог моделей недоступен: ${modelsError}`
    : isModelCatalogUnavailable
      ? "Каталог моделей пуст. Установленные chat-модели должны появиться из подключенной Ollama."
      : null;

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isSubmitDisabled || isModelCatalogUnavailable) {
      return;
    }
    await onSubmit();
  };

  return (
    <form className="space-y-6" onSubmit={handleSubmit}>
      <div className="grid gap-5 xl:grid-cols-[220px_220px_minmax(0,1fr)]">
        <div className="space-y-2">
          <Label id={modelLabelId}>Модель</Label>
          <Select
            disabled={isModelCatalogUnavailable}
            value={isModelCatalogUnavailable ? "" : selectedModel}
            onValueChange={onModelChange}
          >
            <SelectTrigger aria-labelledby={modelLabelId} aria-invalid={modelsError ? true : undefined}>
              <SelectValue placeholder={isModelCatalogUnavailable ? "Список моделей недоступен" : "Выбери модель"} />
            </SelectTrigger>
            <SelectContent>
              {models.map((model) => (
                <SelectItem key={model.name} value={model.name}>
                  {model.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {modelCatalogMessage ? (
            <p className="text-xs leading-5 text-destructive" role={modelsError ? "alert" : undefined}>
              {modelCatalogMessage}
            </p>
          ) : null}
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
                    Пресеты корпуса пока не настроены.
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

              <div className="space-y-3">
                <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                  Фасеты поиска
                </p>
                {knowledgeControls.facets.length === 0 ? (
                  <p className="text-sm leading-6 text-muted-foreground">
                    Фасеты проекта пока не настроены.
                  </p>
                ) : (
                  <div className="grid gap-3">
                    {knowledgeControls.facets.map((facet) => {
                      const checked = knowledgeControls.selectedFacetIds.includes(facet.id);
                      return (
                        <label
                          className="flex items-start gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3"
                          key={facet.id}
                        >
                          <Checkbox
                            aria-label={`Выбрать фасет ${facet.name}`}
                            checked={checked}
                            onCheckedChange={() => knowledgeControls.onToggleFacet(facet.id)}
                          />
                          <div className="space-y-1">
                            <strong className="block text-sm font-semibold text-foreground">
                              {facet.name}
                            </strong>
                            <p className="text-sm leading-6 text-muted-foreground">
                              {facet.description?.trim() || `Ревизия ${facet.revision}`}
                            </p>
                          </div>
                        </label>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <Label>RAG-проект</Label>
              <div className="rounded-[20px] border border-field-border bg-field px-4 py-3 text-sm text-foreground">
                <strong className="block font-semibold">
                  {knowledgeControls.workspaceName || knowledgeControls.workspaceKey || "general"}
                </strong>
                <span className="text-xs text-muted-foreground">
                  key: {knowledgeControls.workspaceKey || "general"}
                </span>
              </div>
            </div>

            <div className="rounded-[22px] border border-primary/15 bg-primary/5 px-4 py-3 text-sm text-foreground">
              {knowledgeControls.activeScopeSummary}
            </div>
          </section>
        </>
      ) : null}

      <Separator />

      {onUseLongTermMemoryChange ? (
        <>
          <label className="flex items-start gap-3 rounded-[18px] border border-border bg-surface-subtle/80 px-4 py-3">
            <Checkbox
              aria-label="Использовать approved long-term memory"
              checked={useLongTermMemory === true}
              onCheckedChange={(checked) => onUseLongTermMemoryChange(checked === true)}
            />
            <span className="min-w-0">
              <span className="block text-sm font-semibold text-foreground">Long-term memory</span>
              <span className="block text-xs leading-5 text-muted-foreground">
                Approved записи могут попасть в context snapshot для этой беседы.
              </span>
            </span>
          </label>
          <Separator />
        </>
      ) : null}

      <InstructionSelector
        emptyStateMessage={instructionEmptyStateMessage}
        instructions={instructions}
        label="Инструкции уровня chat / scenario"
        selectedInstructionIds={selectedInstructionIds}
        onToggleInstruction={onToggleInstruction}
      />

      <ChatFormActions
        cancelLabel={cancelLabel}
        currentRunId={currentRunId}
        error={error}
        helperText={helperText}
        isCancelling={isCancelling}
        isSubmitDisabled={isSubmitDisabled || isModelCatalogUnavailable}
        isSubmitting={isSubmitting}
        modelsError={modelsError}
        submitBusyLabel={submitBusyLabel}
        submitIdleLabel={submitIdleLabel}
        onCancelCurrentRun={onCancelCurrentRun}
      />
    </form>
  );
}
