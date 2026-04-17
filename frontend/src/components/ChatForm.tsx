import type { FormEvent } from "react";
import { InstructionSelector } from "./InstructionSelector";
import type { InstructionSummary, ModelInfo } from "../types";

type ChatFormProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  systemPromptLabel: string;
  systemPrompt: string;
  systemPromptRows: number;
  systemPromptPlaceholder?: string;
  onSystemPromptChange: (value: string) => void;
  promptLabel: string;
  prompt: string;
  promptRows: number;
  promptPlaceholder?: string;
  onPromptChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  instructionEmptyStateMessage: string;
  isSubmitting: boolean;
  isSubmitDisabled: boolean;
  submitIdleLabel: string;
  submitBusyLabel: string;
  helperText: string;
  error: string | null;
  onSubmit: () => Promise<unknown>;
};

export function ChatForm({
  models,
  modelsError,
  selectedModel,
  onModelChange,
  systemPromptLabel,
  systemPrompt,
  systemPromptRows,
  systemPromptPlaceholder,
  onSystemPromptChange,
  promptLabel,
  prompt,
  promptRows,
  promptPlaceholder,
  onPromptChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  instructionEmptyStateMessage,
  isSubmitting,
  isSubmitDisabled,
  submitIdleLabel,
  submitBusyLabel,
  helperText,
  error,
  onSubmit,
}: ChatFormProps) {
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit();
  };

  return (
    <form className="form-card" onSubmit={handleSubmit}>
      <label className="field">
        <span>Модель</span>
        <select value={selectedModel} onChange={(event) => onModelChange(event.target.value)}>
          {models.length > 0 ? (
            models.map((model) => (
              <option key={model.name} value={model.name}>
                {model.name}
              </option>
            ))
          ) : (
            <option value={selectedModel}>{selectedModel}</option>
          )}
        </select>
      </label>

      <label className="field">
        <span>{systemPromptLabel}</span>
        <textarea
          placeholder={systemPromptPlaceholder}
          rows={systemPromptRows}
          value={systemPrompt}
          onChange={(event) => onSystemPromptChange(event.target.value)}
        />
      </label>

      <label className="field">
        <span>{promptLabel}</span>
        <textarea
          placeholder={promptPlaceholder}
          required
          rows={promptRows}
          value={prompt}
          onChange={(event) => onPromptChange(event.target.value)}
        />
      </label>

      <InstructionSelector
        emptyStateMessage={instructionEmptyStateMessage}
        instructions={instructions}
        label="Инструкции для prompt policy"
        selectedInstructionIds={selectedInstructionIds}
        onToggleInstruction={onToggleInstruction}
      />

      <div className="form-actions">
        <button className="primary-button" disabled={isSubmitDisabled} type="submit">
          {isSubmitting ? submitBusyLabel : submitIdleLabel}
        </button>
        <p className="helper">
          {modelsError ? `Список моделей сейчас недоступен: ${modelsError}` : helperText}
        </p>
      </div>

      {error ? <p className="inline-error">{error}</p> : null}
    </form>
  );
}
