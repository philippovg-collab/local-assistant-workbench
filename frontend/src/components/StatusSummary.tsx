import type { HealthResponse, ModelInfo } from "../types";
import type { RagReadinessPresentation } from "../utils/readiness";
import { formatDate } from "../utils/format";

type StatusSummaryProps = {
  health: HealthResponse | null;
  healthError: string | null;
  models: ModelInfo[];
  modelsError: string | null;
  ragPresentation: RagReadinessPresentation;
  instructionsCount: number;
};

export function StatusSummary({
  health,
  healthError,
  models,
  modelsError,
  ragPresentation,
  instructionsCount,
}: StatusSummaryProps) {
  const isBackendHealthy = health?.status === "UP";
  const backendMessage = health
    ? isBackendHealthy
      ? health.application
      : health.llmReasonMessage ??
        health.embeddingReasonMessage ??
        health.ocrReasonMessage ??
        health.vectorReasonMessage ??
        health.application
    : healthError ?? "Проверяем сервис";
  const llmMessage = health?.llmStatus === "DOWN"
    ? health.llmReasonMessage ?? "LLM runtime недоступен."
    : modelsError ?? "Единый LLM client обслуживает direct и RAG режимы.";
  const queueSummary = health
    ? `${health.indexingPendingCount ?? 0} pending / ${health.indexingInProgressCount ?? 0} in progress / ${health.indexingFailedCount ?? 0} failed`
    : "Ожидаем snapshot очереди индексации.";

  return (
    <>
      <section className="summary-grid">
        <article className="stat-card">
          <div className="status-chip">
            <span className={`status-dot ${health ? isBackendHealthy ? "online" : "warn" : "warn"}`} />
            Backend
          </div>
          <strong>{health ? health.status : "Недоступен"}</strong>
          <p>{backendMessage}</p>
        </article>

        <article className="stat-card">
          <div className="status-chip">
            <span className={`status-dot ${health?.llmStatus === "DOWN" ? "warn" : models.length > 0 ? "online" : "idle"}`} />
            LLM
          </div>
          <strong>{health?.directStatus === "DOWN" ? "Direct недоступен" : models.length > 0 ? `${models.length} моделей` : "Нет списка моделей"}</strong>
          <p>{llmMessage}</p>
        </article>

        <article className="stat-card">
          <div className="status-chip">
            <span className={`status-dot ${ragPresentation.statusDotClass}`} />
            RAG
          </div>
          <strong>{ragPresentation.headline}</strong>
          <p>{ragPresentation.overviewMessage}</p>
        </article>

        <article className="stat-card">
          <div className="status-chip">
            <span className={`status-dot ${instructionsCount > 0 ? "online" : "idle"}`} />
            Инструкции
          </div>
          <strong>{instructionsCount}</strong>
          <p>Instruction snippets теперь подключаются явно и реально влияют на prompt policy.</p>
        </article>
      </section>

      <section className="runtime-grid">
        <article className="panel">
          <div className="panel-header compact">
            <h3>Runtime Signals</h3>
            <span className="badge subtle">/api/health</span>
          </div>
          <div className="stack-list">
            <div className="signal-row">
              <strong>Последний успешный LLM probe</strong>
              <span>{health?.llmLastSuccessfulProbeAt ? formatDate(health.llmLastSuccessfulProbeAt) : "Пока нет"}</span>
            </div>
            <div className="signal-row">
              <strong>Последний успешный embedding probe</strong>
              <span>{health?.embeddingLastSuccessfulProbeAt ? formatDate(health.embeddingLastSuccessfulProbeAt) : "Пока нет"}</span>
            </div>
            <div className="signal-row">
              <strong>Причина RAG degraded</strong>
              <span>{health?.ragDegradedReasonMessage ?? "RAG readiness сейчас не деградирован."}</span>
            </div>
            <div className="signal-row">
              <strong>Очередь индексации</strong>
              <span>{queueSummary}</span>
            </div>
            <div className="signal-row">
              <strong>Следующий retry</strong>
              <span>{health?.indexingNextRetryAt ? formatDate(health.indexingNextRetryAt) : "Не запланирован"}</span>
            </div>
          </div>
        </article>
      </section>
    </>
  );
}
