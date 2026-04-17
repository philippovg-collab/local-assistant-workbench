import { apiClient } from "../api/client";
import { AppliedInstructionList } from "./AppliedInstructionList";
import { ChatForm } from "./ChatForm";
import { ChatWorkspace } from "./ChatWorkspace";
import type { ChatExecutionRequest, ChatExecutionResponse, InstructionSummary, ModelInfo } from "../types";
import { formatDate } from "../utils/format";

type DirectChatPanelProps = {
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
  isSubmitting: boolean;
  error: string | null;
  response: ChatExecutionResponse | null;
  requestPreview: ChatExecutionRequest;
  onSubmit: () => Promise<unknown>;
};

export function DirectChatPanel({
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
  isSubmitting,
  error,
  response,
  requestPreview,
  onSubmit,
}: DirectChatPanelProps) {
  return (
    <section className="workspace-grid">
      <ChatWorkspace
        badge="POST /api/chat mode=direct"
        description="Direct-режим использует тот же execution contract, но без retrieval. Здесь остаются только модель, system prompt и прямой пользовательский запрос."
        eyebrow="Direct Playground"
        title="Прямой запрос к модели"
      >
        <ChatForm
          error={error}
          helperText="Direct-режим уходит в unified `/api/chat` без retrieval-контекста и использует только модель, prompt policy и пользовательский запрос."
          instructionEmptyStateMessage="Сначала создай инструкцию во вкладке библиотеки, чтобы затем применять её в direct-режиме."
          instructions={instructions}
          isSubmitDisabled={isSubmitting || !prompt.trim()}
          isSubmitting={isSubmitting}
          models={models}
          modelsError={modelsError}
          onModelChange={onModelChange}
          onPromptChange={onPromptChange}
          onSubmit={onSubmit}
          onSystemPromptChange={onSystemPromptChange}
          onToggleInstruction={onToggleInstruction}
          prompt={prompt}
          promptLabel="User prompt"
          promptRows={7}
          selectedInstructionIds={selectedInstructionIds}
          selectedModel={selectedModel}
          submitBusyLabel="Отправляем..."
          submitIdleLabel="Отправить напрямую"
          systemPrompt={systemPrompt}
          systemPromptLabel="System prompt"
          systemPromptRows={4}
        />
      </ChatWorkspace>

      <aside className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Direct Response</p>
            <h2>Контракт запроса и ответа</h2>
          </div>
        </div>

        <div className="response-stack">
          <article className="answer-card">
            <div className="item-row">
              <h3>curl</h3>
              <span className="badge subtle">unified chat contract</span>
            </div>
            <pre className="code-block">{apiClient.buildCurlExample(requestPreview)}</pre>
          </article>

          {response ? (
            <>
              <article className="answer-card">
                <div className="item-row">
                  <h3>JSON</h3>
                  <span className="badge subtle">{response.model}</span>
                </div>
                <pre className="code-block">{JSON.stringify(response, null, 2)}</pre>
              </article>

              <article className="answer-card">
                <div className="item-row">
                  <h3>Ответ модели</h3>
                  <span className="badge subtle">{formatDate(response.createdAt)}</span>
                </div>
                <p>{response.answer}</p>
              </article>

              <AppliedInstructionList appliedInstructions={response.appliedInstructions} />
            </>
          ) : (
            <div className="empty-state">
              <strong>Прямой ответ пока пустой</strong>
              <span>После запроса здесь появятся JSON-ответ, токены и итоговый ответ модели.</span>
            </div>
          )}
        </div>
      </aside>
    </section>
  );
}
