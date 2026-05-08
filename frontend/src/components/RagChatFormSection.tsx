import { X } from "lucide-react";
import { ChatForm, type ChatFormProps } from "@/components/ChatForm";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type {
  AnswerMode,
  InstructionSummary,
  ModelInfo,
  RetrievalFilters,
} from "@/types";
import {
  buildRetrievalFilterChips,
} from "@/components/ragChatPresentation";
import type { RetrievalFilterKey } from "@/utils/retrievalHints";

type RagChatFormSectionProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  answerMode: AnswerMode;
  onAnswerModeChange: (value: AnswerMode) => void;
  prompt: string;
  onPromptChange: (value: string) => void;
  temporaryInstruction: string;
  onTemporaryInstructionChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  retrievalFilters: RetrievalFilters;
  effectiveRetrievalFilters: RetrievalFilters;
  metadataFiltersEnabled: boolean;
  queryHintsEnabled: boolean;
  hintOwnedFields: RetrievalFilterKey[];
  manualOwnedFields: RetrievalFilterKey[];
  dismissedHintKeys: RetrievalFilterKey[];
  onClearRetrievalFilter: (key: RetrievalFilterKey) => void;
  onDismissHint: (key: RetrievalFilterKey) => void;
  onResetDismissedHints: () => void;
  helperText: string;
  isBlocked: boolean;
  isSubmitting: boolean;
  currentRunStatus?: string | null;
  error: string | null;
  knowledgeControls: NonNullable<ChatFormProps["knowledgeControls"]>;
  activeProjectLabel: string;
  onSubmit: () => Promise<unknown>;
};

export function RagChatFormSection({
  models,
  modelsError,
  selectedModel,
  onModelChange,
  answerMode,
  onAnswerModeChange,
  prompt,
  onPromptChange,
  temporaryInstruction,
  onTemporaryInstructionChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  retrievalFilters,
  effectiveRetrievalFilters,
  metadataFiltersEnabled,
  queryHintsEnabled,
  hintOwnedFields,
  manualOwnedFields,
  dismissedHintKeys,
  onClearRetrievalFilter,
  onDismissHint,
  onResetDismissedHints,
  helperText,
  isBlocked,
  isSubmitting,
  currentRunStatus,
  error,
  knowledgeControls,
  activeProjectLabel,
  onSubmit,
}: RagChatFormSectionProps) {
  const activeHintChips = buildRetrievalFilterChips(hintOwnedFields, effectiveRetrievalFilters);
  const activeManualChips = buildRetrievalFilterChips(manualOwnedFields, retrievalFilters);

  return (
    <>
      <div className="mb-4 space-y-3 rounded-[24px] border border-border bg-surface-subtle/80 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-foreground">Фильтры и подсказки поиска</p>
            <p className="text-xs leading-5 text-muted-foreground">
              Активный RAG-проект уже зафиксирован: {activeProjectLabel}.
              Подсказки из вопроса и выбранные фасеты работают внутри него.
            </p>
          </div>
        </div>

        {activeHintChips.length > 0 || activeManualChips.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {activeHintChips.map((chip) => (
              <div
                className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-xs text-foreground"
                key={`hint-${chip.key}`}
              >
                <Badge variant="default">hint</Badge>
                <span>{chip.label}: {chip.value}</span>
                <button
                  aria-label={`Remove hint ${chip.label}`}
                  className="rounded-full p-1 text-muted-foreground transition hover:bg-black/5 hover:text-foreground"
                  type="button"
                  onClick={() => onDismissHint(chip.key)}
                >
                  <X className="h-3 w-3" />
                </button>
              </div>
            ))}
            {activeManualChips.map((chip) => (
              <div
                className="inline-flex items-center gap-2 rounded-full border border-border bg-field px-3 py-1 text-xs text-foreground"
                key={`manual-${chip.key}`}
              >
                <Badge variant="secondary">manual</Badge>
                <span>{chip.label}: {chip.value}</span>
                <button
                  aria-label={`Clear filter ${chip.label}`}
                  className="rounded-full p-1 text-muted-foreground transition hover:bg-black/5 hover:text-foreground"
                  type="button"
                  onClick={() => onClearRetrievalFilter(chip.key)}
                >
                  <X className="h-3 w-3" />
                </button>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm leading-6 text-muted-foreground">
            {metadataFiltersEnabled
              ? queryHintsEnabled
                ? "Пока фильтры не заданы. После ввода запроса здесь появятся auto hints и ручные фасеты."
                : "Пока фильтры не заданы. Auto hints отключены rollout-флагом, но ручные фасеты доступны."
              : "Metadata filters отключены rollout-флагом; запрос уйдёт без retrievalFilters."}
          </p>
        )}

        {queryHintsEnabled && dismissedHintKeys.length > 0 ? (
          <div className="flex items-center justify-between gap-3 rounded-[18px] border border-border bg-field px-4 py-3">
            <p className="text-xs text-muted-foreground">
              Скрытые подсказки не вернутся, пока ты не восстановишь их вручную.
            </p>
            <Button size="sm" type="button" variant="outline" onClick={onResetDismissedHints}>
              Вернуть подсказки
            </Button>
          </div>
        ) : null}
      </div>

      <ChatForm
        answerMode={answerMode}
        error={error}
        helperText={isSubmitting && currentRunStatus ? `${helperText} Статус запуска: ${currentRunStatus}.` : helperText}
        instructionEmptyStateMessage="Сначала создай chat/scenario инструкцию во вкладке библиотеки."
        instructions={instructions}
        isSubmitDisabled={isSubmitting || !prompt.trim() || isBlocked}
        isSubmitting={isSubmitting}
        knowledgeControls={knowledgeControls}
        models={models}
        modelsError={modelsError}
        onAnswerModeChange={onAnswerModeChange}
        onModelChange={onModelChange}
        onPromptChange={onPromptChange}
        onSubmit={onSubmit}
        onTemporaryInstructionChange={onTemporaryInstructionChange}
        onToggleInstruction={onToggleInstruction}
        prompt={prompt}
        promptLabel="Вопрос"
        promptPlaceholder="Например: Какие условия тарифа Премиум?"
        promptRows={6}
        selectedInstructionIds={selectedInstructionIds}
        selectedModel={selectedModel}
        submitBusyLabel="Ищем контекст..."
        submitIdleLabel="Спросить по материалам"
        temporaryInstruction={temporaryInstruction}
        temporaryInstructionLabel="Временная инструкция на этот запрос"
        temporaryInstructionPlaceholder="Например: если в источниках нет подтверждения, скажи об этом прямо."
        temporaryInstructionRows={4}
      />
    </>
  );
}
