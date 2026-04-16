import type { HealthResponse, ModelInfo } from "../types";

type StatusSummaryProps = {
  health: HealthResponse | null;
  healthError: string | null;
  models: ModelInfo[];
  modelsError: string | null;
  materialsCount: number;
  isLoadingMaterials: boolean;
  instructionsCount: number;
};

export function StatusSummary({
  health,
  healthError,
  models,
  modelsError,
  materialsCount,
  isLoadingMaterials,
  instructionsCount,
}: StatusSummaryProps) {
  const isBackendHealthy = health?.status === "UP";
  const backendMessage = health
    ? isBackendHealthy
      ? health.application
      : health.ocrReasonMessage ?? health.application
    : healthError ?? "Проверяем сервис";

  return (
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
          <span className={`status-dot ${modelsError ? "warn" : models.length > 0 ? "online" : "idle"}`} />
          Ollama
        </div>
        <strong>{models.length > 0 ? `${models.length} моделей` : "Нет списка моделей"}</strong>
        <p>{modelsError ?? "Единый LLM client обслуживает direct и RAG режимы."}</p>
      </article>

      <article className="stat-card">
        <div className="status-chip">
          <span className={`status-dot ${materialsCount > 0 ? "online" : "idle"}`} />
          Материалы
        </div>
        <strong>{materialsCount}</strong>
        <p>{isLoadingMaterials ? "Сканируем локальное хранилище" : "Материалы индексируются при загрузке, а не на каждом запросе."}</p>
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
  );
}
