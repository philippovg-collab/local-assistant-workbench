import { AppliedInstructionList } from "./AppliedInstructionList";
import { ChatForm } from "./ChatForm";
import { ChatWorkspace } from "./ChatWorkspace";
import type { ChatExecutionResponse, InstructionSummary, ModelInfo } from "../types";

type RagChatPanelProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  prompt: string;
  onPromptChange: (value: string) => void;
  systemPrompt: string;
  onSystemPromptChange: (value: string) => void;
  instructions: InstructionSummary[];
  selectedInstructionIds: string[];
  onToggleInstruction: (instructionId: string) => void;
  helperText: string;
  isBlocked: boolean;
  isSubmitting: boolean;
  error: string | null;
  response: ChatExecutionResponse | null;
  onSubmit: () => Promise<unknown>;
};

export function RagChatPanel({
  models,
  modelsError,
  selectedModel,
  onModelChange,
  prompt,
  onPromptChange,
  systemPrompt,
  onSystemPromptChange,
  instructions,
  selectedInstructionIds,
  onToggleInstruction,
  helperText,
  isBlocked,
  isSubmitting,
  error,
  response,
  onSubmit,
}: RagChatPanelProps) {
  return (
    <ChatWorkspace
      badge="POST /api/chat mode=rag"
      description="В этом режиме модель отвечает только по найденному локальному контексту из материалов и не выходит за его пределы."
      eyebrow="RAG Chat"
      title="Ответ по материалам"
    >
      <ChatForm
        error={error}
        helperText={helperText}
        instructionEmptyStateMessage="Сначала создай инструкцию во вкладке библиотеки, чтобы затем применять её в RAG-режиме."
        instructions={instructions}
        isSubmitDisabled={isSubmitting || !prompt.trim() || isBlocked}
        isSubmitting={isSubmitting}
        models={models}
        modelsError={modelsError}
        onModelChange={onModelChange}
        onPromptChange={onPromptChange}
        onSubmit={onSubmit}
        onSystemPromptChange={onSystemPromptChange}
        onToggleInstruction={onToggleInstruction}
        prompt={prompt}
        promptLabel="Вопрос"
        promptPlaceholder="Например: Какие условия тарифа Премиум?"
        promptRows={6}
        selectedInstructionIds={selectedInstructionIds}
        selectedModel={selectedModel}
        submitBusyLabel="Ищем контекст..."
        submitIdleLabel="Спросить по материалам"
        systemPrompt={systemPrompt}
        systemPromptLabel="System override"
        systemPromptPlaceholder="Опционально переопредели базовый system prompt для этого RAG-запроса."
        systemPromptRows={4}
      />

      {response ? (
        <div className="response-stack">
          <article className="answer-card">
            <div className="item-row">
              <h3>Ответ модели</h3>
              <span className="badge subtle">{response.model}</span>
            </div>
            <p>{response.answer}</p>
          </article>

          <article className="answer-card">
            <div className="item-row">
              <h3>Источники</h3>
              <span className="badge subtle">{response.sources.length} source(s)</span>
            </div>

            {response.sources.length === 0 ? (
              <div className="empty-state compact-empty">
                <strong>Контекст не найден</strong>
                <span>Уточни вопрос или обнови материалы на вкладке с локальной базой.</span>
              </div>
            ) : (
              <div className="stack-list">
                {response.sources.map((source) => (
                  <article
                    className="source-card"
                    key={`${source.materialId}-${source.page ?? "na"}-${source.extractor ?? "default"}-${source.score}`}
                  >
                    <div className="item-row">
                      <strong>{source.title}</strong>
                      <span className="score-pill">score {source.score}</span>
                    </div>
                    <p className="item-meta">
                      {source.page ? `page ${source.page}` : "page n/a"}
                      {source.extractor ? ` · ${source.extractor}` : ""}
                      {source.ocrUsed ? " · OCR" : ""}
                    </p>
                    <p>{source.excerpt}</p>
                  </article>
                ))}
              </div>
            )}
          </article>

          <AppliedInstructionList appliedInstructions={response.appliedInstructions} />
        </div>
      ) : (
        <div className="empty-state">
          <strong>Ответ по материалам пока пустой</strong>
          <span>После первого запроса здесь появятся ответ, применённые инструкции и использованные источники.</span>
        </div>
      )}
    </ChatWorkspace>
  );
}
