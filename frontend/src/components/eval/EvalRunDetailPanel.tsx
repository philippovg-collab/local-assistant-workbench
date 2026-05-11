import { ClipboardCheck } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { useEvalRunDetail } from "@/hooks/useEvalRunDetail";
import type { EvalCase, EvalRun } from "@/types";
import { MetricCard } from "./EvalShared";
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
      <section className="surface-subtle rounded-[24px] p-5">
        <EmptyState description="Выбери run из таблицы." icon={ClipboardCheck} title="Run detail не выбран" />
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
  const datasetVersion = run.executionConfig?.datasetVersion ?? String(summary.datasetVersion ?? "n/a");
  const mappingHash = String(
    summary.mappingHash
      ?? executionOptions.mappingHash
      ?? searchSyncOptions.mappingHash
      ?? rolloutFlags.mappingHash
      ?? "n/a",
  );
  const caseRevisionRefHash = String(summary.caseRevisionRefHash ?? "n/a");
  const blockerRegressions = items.filter((item) => {
    const caseDetail = item.caseId ? casesById.get(item.caseId) : null;
    return caseDetail?.severity === "BLOCKER" && (item.status === "FAILED" || item.failureCode);
  }).length;
  const topMetrics = metricEntries(summary.metrics ?? summary.metricSummary ?? run.summary);

  return (
    <section className="surface-subtle space-y-4 rounded-[24px] p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-semibold uppercase text-muted-foreground">Run Detail</p>
          <h3 className="text-xl font-semibold text-foreground">{shortId(run.id)} · {run.runKind ?? "n/a"}</h3>
        </div>
        <Badge variant={badgeForRunStatus(run.status)}>{run.status ?? "n/a"}</Badge>
      </div>

      {runDetailHook.error ? <p className="text-sm leading-6 text-destructive">{runDetailHook.error}</p> : null}

      <div className="grid gap-3 md:grid-cols-4">
        <MetricCard label="overall verdict" value={String(summary.verdict ?? summary.overallVerdict ?? run.status ?? "n/a")} />
        <MetricCard label="passed" value={passedCount} />
        <MetricCard label="failed" value={failedCount} />
        <MetricCard label="blockers" value={blockerRegressions} />
      </div>

      <div className="grid gap-3 md:grid-cols-4">
        <MetricCard label="dataset version" value={datasetVersion} />
        <MetricCard label="config hash" value={run.configHash ?? run.executionConfig?.configHash ?? "n/a"} />
        <MetricCard label="mapping hash" value={mappingHash} />
        <MetricCard label="case ref hash" value={caseRevisionRefHash} />
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
              <th className="px-4 py-3">case</th>
              <th className="px-4 py-3">status</th>
              <th className="px-4 py-3">failure</th>
              <th className="px-4 py-3">chat run</th>
              <th className="px-4 py-3">score</th>
              <th className="px-4 py-3">artifact</th>
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
                    <span className="line-clamp-2 text-muted-foreground">{caseDetail?.question ?? "Case detail not loaded"}</span>
                    <span className="mt-1 block text-xs text-muted-foreground">
                      rev {item.caseRevision ?? "n/a"} · hash {shortId(String(artifact.caseContentHash ?? ""))}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={badgeForItemStatus(item.status)}>{item.status ?? "n/a"}</Badge>
                  </td>
                  <td className="px-4 py-3">{item.failureCode ?? item.failureMessage ?? "n/a"}</td>
                  <td className="px-4 py-3">{shortId(item.chatRunId)}</td>
                  <td className="px-4 py-3">{String(asRecord(item.scoreSummary).passed ?? asRecord(item.scoreSummary).verdict ?? "n/a")}</td>
                  <td className="px-4 py-3">
                    <Button
                      disabled={!item.id}
                      size="sm"
                      type="button"
                      variant="outline"
                      onClick={() => item.id ? onOpenItem(item.id) : undefined}
                    >
                      Open
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
