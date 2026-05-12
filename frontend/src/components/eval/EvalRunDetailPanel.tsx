import { ClipboardCheck } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { useEvalRunDetail } from "@/hooks/useEvalRunDetail";
import type { EvalCase, EvalRun } from "@/types";
import { evalInnerPanelClassName, MetricCard } from "./EvalShared";
import {
  asRecord,
  badgeForItemStatus,
  badgeForRunStatus,
  metricEntries,
  shortId,
} from "./evalPresentation";

type EvalRunDetailPanelProps = {
  casesById: Map<string, EvalCase>;
  run: EvalRun | null;
  runDetailHook: ReturnType<typeof useEvalRunDetail>;
  onOpenItem: (itemId: string) => void;
};

export function EvalRunDetailPanel({
  casesById,
  run,
  runDetailHook,
  onOpenItem,
}: EvalRunDetailPanelProps) {
  if (!run) {
    return (
      <section className={`${evalInnerPanelClassName} rounded-[26px] p-4 sm:p-5`}>
        <EmptyState description="Выбери запуск из таблицы." icon={ClipboardCheck} title="Детали запуска не выбраны" />
      </section>
    );
  }

  const items = runDetailHook.sortedItems.length > 0 ? runDetailHook.sortedItems : run.items ?? [];
  const failedCount = items.filter((item) => item.status === "FAILED" || item.status === "ERROR").length;
  const passedCount = items.filter((item) => item.status === "PASSED").length;
  const summary = asRecord(run.summary);
  const executionOptions = asRecord(run.executionConfig?.options);
  const searchSyncOptions = asRecord(executionOptions.searchSync);
  const rolloutFlags = asRecord(run.executionConfig?.rolloutFlags);
  const datasetVersion = run.executionConfig?.datasetVersion ?? String(summary.datasetVersion ?? "н/д");
  const mappingHash = String(
    summary.mappingHash
      ?? executionOptions.mappingHash
      ?? searchSyncOptions.mappingHash
      ?? rolloutFlags.mappingHash
      ?? "н/д",
  );
  const caseRevisionRefHash = String(summary.caseRevisionRefHash ?? "н/д");
  const blockerRegressions = items.filter((item) => {
    const caseDetail = item.caseId ? casesById.get(item.caseId) : null;
    return caseDetail?.severity === "BLOCKER" && (item.status === "FAILED" || item.failureCode);
  }).length;
  const topMetrics = metricEntries(summary.metrics ?? summary.metricSummary ?? run.summary);

  return (
    <section className={`${evalInnerPanelClassName} space-y-4 rounded-[26px] p-4 sm:p-5`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold uppercase text-muted-foreground">Детали запуска</p>
          <h3 className="text-xl font-semibold text-foreground">{shortId(run.id)} · {run.runKind ?? "н/д"}</h3>
        </div>
        <Badge variant={badgeForRunStatus(run.status)}>{run.status ?? "н/д"}</Badge>
      </div>

      {runDetailHook.error ? <p className="text-sm leading-6 text-destructive">{runDetailHook.error}</p> : null}

      <div className="grid gap-3 md:grid-cols-4">
        <MetricCard label="общий вердикт" value={String(summary.verdict ?? summary.overallVerdict ?? run.status ?? "н/д")} />
        <MetricCard label="прошло" value={passedCount} />
        <MetricCard label="ошибок" value={failedCount} />
        <MetricCard label="блокеры" value={blockerRegressions} />
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <MetricCard label="версия набора" value={datasetVersion} />
        <MetricCard label="хеш конфигурации" value={run.configHash ?? run.executionConfig?.configHash ?? "н/д"} />
        <MetricCard label="хеш mapping" value={mappingHash} />
        <MetricCard label="хеш ревизии кейса" value={caseRevisionRefHash} />
      </div>

      {topMetrics.length > 0 ? (
        <div className="grid gap-3 md:grid-cols-4">
          {topMetrics.map(([name, value]) => <MetricCard key={name} label={name} value={String(value)} />)}
        </div>
      ) : null}

      <div className="overflow-x-auto rounded-[22px] border border-field-border bg-field">
        <table className="w-full min-w-[920px] text-left text-sm">
          <thead className="text-xs uppercase text-muted-foreground">
            <tr>
              <th className="px-4 py-3">кейс</th>
              <th className="px-4 py-3">статус</th>
              <th className="px-4 py-3">ошибка</th>
              <th className="px-4 py-3">chat run</th>
              <th className="px-4 py-3">оценка</th>
              <th className="px-4 py-3">артефакт</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const caseDetail = item.caseId ? casesById.get(item.caseId) : null;
              const artifact = asRecord(item.artifact);
              return (
                <tr className="border-t border-border" key={item.id}>
                  <td className="max-w-[320px] px-4 py-3">
                    <strong className="block text-foreground">{caseDetail?.caseKey ?? shortId(item.caseId)}</strong>
                    <span className="line-clamp-2 text-muted-foreground">{caseDetail?.question ?? "Детали кейса не загружены"}</span>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      рев. {item.caseRevision ?? "н/д"} · хеш {shortId(String(artifact.caseContentHash ?? ""))}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={badgeForItemStatus(item.status)}>{item.status ?? "н/д"}</Badge>
                  </td>
                  <td className="px-4 py-3">{item.failureCode ?? item.failureMessage ?? "н/д"}</td>
                  <td className="px-4 py-3">{shortId(item.chatRunId)}</td>
                  <td className="px-4 py-3">{String(asRecord(item.scoreSummary).passed ?? asRecord(item.scoreSummary).verdict ?? "н/д")}</td>
                  <td className="px-4 py-3">
                    <Button
                      disabled={!item.id}
                      size="sm"
                      type="button"
                      variant="outline"
                      onClick={() => item.id ? onOpenItem(item.id) : undefined}
                    >
                      Открыть
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
