import type { FormEvent } from "react";
import { apiClient } from "../api/client";
import type { ChatExecutionRequest, ChatExecutionResponse, ModelInfo } from "../types";
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
  isSubmitting: boolean;
  error: string | null;
  response: ChatExecutionResponse | null;
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
  isSubmitting,
  error,
  response,
  onSubmit,
}: DirectChatPanelProps) {
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit();
  };

  const curlRequest: ChatExecutionRequest = {
    mode: "direct",
    model: selectedModel,
    prompt,
    instructionIds: [],
    ...(systemPrompt.trim() ? { systemPrompt: systemPrompt.trim() } : {}),
  };

  return (
    <section className="workspace-grid">
      <article className="panel">
        <div className="panel-header">
          <div>
            <p className="eyebrow">Direct Playground</p>
            <h2>Прямой запрос к модели</h2>
          </div>
          <span className="badge">POST /api/chat mode=direct</span>
        </div>

        <p className="section-copy">
          Direct-режим использует тот же execution contract, но без retrieval. Здесь остаются только модель, system prompt и прямой пользовательский запрос.
        </p>

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
            <span>System prompt</span>
            <textarea rows={4} value={systemPrompt} onChange={(event) => onSystemPromptChange(event.target.value)} />
          </label>

          <label className="field">
            <span>User prompt</span>
            <textarea required rows={7} value={prompt} onChange={(event) => onPromptChange(event.target.value)} />
          </label>

          <button className="primary-button" disabled={isSubmitting || !prompt.trim()} type="submit">
            {isSubmitting ? "Отправляем..." : "Отправить напрямую"}
          </button>

          {modelsError ? <p className="helper">Список моделей сейчас недоступен: {modelsError}</p> : null}
          {error ? <p className="inline-error">{error}</p> : null}
        </form>
      </article>

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
            <pre className="code-block">{apiClient.buildCurlExample(curlRequest)}</pre>
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
