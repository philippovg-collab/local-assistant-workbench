import { useState } from "react";
import { Play, RefreshCw } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { useEvalDatasets } from "@/hooks/useEvalDatasets";
import type { useEvalRunDetail } from "@/hooks/useEvalRunDetail";
import type { useEvalRuns } from "@/hooks/useEvalRuns";
import { formatDate } from "@/utils/format";
import { EvalArtifactsSheet } from "./EvalArtifactsSheet";
import { evalInnerPanelClassName } from "./EvalShared";
import { EvalRunDetailPanel } from "./EvalRunDetailPanel";
import { StartEvalRunDialog } from "./StartEvalRunDialog";
import { asRecord, badgeForRunStatus, formatDuration, shortId } from "./evalPresentation";

type EvalRunsViewProps = {
  datasetsHook: ReturnType<typeof useEvalDatasets>;
  runDetailHook: ReturnType<typeof useEvalRunDetail>;
  runsHook: ReturnType<typeof useEvalRuns>;
};

export function EvalRunsView({ datasetsHook, runDetailHook, runsHook }: EvalRunsViewProps) {
  const [startOpen, setStartOpen] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const selectedRunForTable = runsHook.selectedRun;

  return (
    <div className="space-y-4">
      <section className={`${evalInnerPanelClassName} space-y-4 rounded-[26px] p-4 sm:p-5`}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <Play className="h-4 w-4 text-primary" />
            Запуски
          </div>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" type="button" variant="outline" onClick={() => void runsHook.loadRuns()}>
              <RefreshCw className="h-4 w-4" />
              Обновить
            </Button>
            <Button size="sm" type="button" onClick={() => setStartOpen(true)}>
              <Play className="h-4 w-4" />
              Запустить
            </Button>
          </div>
        </div>

        {runsHook.error ? <p className="text-sm leading-6 text-destructive">{runsHook.error}</p> : null}

        <div className="overflow-x-auto rounded-[22px] border border-field-border bg-field">
          <table className="w-full min-w-[940px] text-left text-sm">
            <thead className="text-xs uppercase text-muted-foreground">
              <tr>
                <th className="px-4 py-3">запуск</th>
                <th className="px-4 py-3">тип</th>
                <th className="px-4 py-3">версия набора</th>
                <th className="px-4 py-3">snapshot</th>
                <th className="px-4 py-3">статус</th>
                <th className="px-4 py-3">старт</th>
                <th className="px-4 py-3">длительность</th>
                <th className="px-4 py-3">вердикт</th>
              </tr>
            </thead>
            <tbody>
              {runsHook.runs.map((run) => (
                <tr
                  className={`cursor-pointer border-t border-border ${runsHook.selectedRunId === run.id ? "bg-primary/8" : ""}`}
                  key={run.id}
                  onClick={() => runsHook.setSelectedRunId(run.id ?? null)}
                >
                  <td className="px-4 py-3 font-medium text-foreground">{shortId(run.id)}</td>
                  <td className="px-4 py-3">{run.runKind ?? "н/д"}</td>
                  <td className="px-4 py-3">{run.executionConfig?.datasetVersion ?? "н/д"}</td>
                  <td className="px-4 py-3">{shortId(run.snapshotId ?? run.executionConfig?.corpusSnapshotId)}</td>
                  <td className="px-4 py-3">
                    <Badge variant={badgeForRunStatus(run.status)}>{run.status ?? "н/д"}</Badge>
                  </td>
                  <td className="px-4 py-3">{run.startedAt || run.createdAt ? formatDate(run.startedAt ?? run.createdAt ?? "") : "н/д"}</td>
                  <td className="px-4 py-3">{formatDuration(run.startedAt, run.completedAt)}</td>
                  <td className="px-4 py-3">{String(asRecord(run.summary).verdict ?? asRecord(run.summary).overallVerdict ?? "н/д")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {runsHook.runs.length === 0 ? (
          <EmptyState description="Запуски оценки появятся после запуска retrieval или E2E workflow." icon={Play} title="Запусков пока нет" />
        ) : null}
      </section>

      <EvalRunDetailPanel
        casesById={datasetsHook.casesById}
        run={runDetailHook.run ?? selectedRunForTable}
        runDetailHook={runDetailHook}
        onOpenItem={(itemId) => {
          runDetailHook.setSelectedItemId(itemId);
          setDrawerOpen(true);
        }}
      />

      <EvalArtifactsSheet
        artifacts={runDetailHook.artifacts}
        caseDetail={runDetailHook.selectedItem?.caseId ? datasetsHook.casesById.get(runDetailHook.selectedItem.caseId) ?? null : null}
        isLoading={runDetailHook.isLoadingArtifacts}
        item={runDetailHook.selectedItem}
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
      />

      <StartEvalRunDialog
        datasets={datasetsHook.datasets}
        isOpen={startOpen}
        isSaving={runsHook.isMutating}
        resolvedConfigHash={runsHook.resolvedConfigHash}
        selectedDatasetId={datasetsHook.selectedDatasetId}
        snapshots={runsHook.snapshots}
        onClose={() => setStartOpen(false)}
        onResolveConfig={runsHook.resolveExecutionConfig}
        onStartE2E={runsHook.startE2ERun}
        onStartRetrieval={runsHook.startRetrievalRun}
      />
    </div>
  );
}
