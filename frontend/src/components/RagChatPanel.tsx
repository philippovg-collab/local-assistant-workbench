import type { FormEvent } from "react";
import type { ChatExecutionResponse, ModelInfo } from "../types";

type RagChatPanelProps = {
  models: ModelInfo[];
  modelsError: string | null;
  selectedModel: string;
  onModelChange: (value: string) => void;
  prompt: string;
  onPromptChange: (value: string) => void;
  systemPrompt: string;
  onSystemPromptChange: (value: string) => void;
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
  helperText,
  isBlocked,
  isSubmitting,
  error,
  response,
  onSubmit,
}: RagChatPanelProps) {
  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit();
  };

  return (
    <article className="panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">RAG Chat</p>
          <h2>Ответ по материалам</h2>
        </div>
        <span className="badge">POST /api/chat mode=rag</span>
      </div>

      <p className="section-copy">
        В этом режиме модель отвечает только по найденному локальному контексту из материалов и не выходит за его пределы.
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
          <span>System override</span>
          <textarea
            placeholder="Опционально переопредели базовый system prompt для этого RAG-запроса."
            rows={4}
            value={systemPrompt}
            onChange={(event) => onSystemPromptChange(event.target.value)}
          />
        </label>

        <label className="field">
          <span>Вопрос</span>
          <textarea
            placeholder="Например: Какие условия тарифа Премиум?"
            required
            rows={6}
            value={prompt}
            onChange={(event) => onPromptChange(event.target.value)}
          />
        </label>

        <div className="form-actions">
          <button
            className="primary-button"
            disabled={isSubmitting || !prompt.trim() || isBlocked}
            type="submit"
          >
            {isSubmitting ? "Ищем контекст..." : "Спросить по материалам"}
          </button>
          <p className="helper">
            {modelsError ? `Список моделей сейчас недоступен: ${modelsError}` : helperText}
          </p>
        </div>

        {error ? <p className="inline-error">{error}</p> : null}
      </form>

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
        </div>
      ) : (
        <div className="empty-state">
          <strong>Ответ по материалам пока пустой</strong>
          <span>После первого запроса здесь появятся ответ, применённые инструкции и использованные источники.</span>
        </div>
      )}
    </article>
  );
}
