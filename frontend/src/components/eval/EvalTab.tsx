import { useEffect, useState } from "react";
import { PageHeaderPanel } from "@/components/app/PageHeaderPanel";
import { SegmentedControl } from "@/components/app/SegmentedControl";
import { Badge } from "@/components/ui/badge";
import { useEvalCompare } from "@/hooks/useEvalCompare";
import { useEvalDatasets } from "@/hooks/useEvalDatasets";
import { useEvalRunDetail } from "@/hooks/useEvalRunDetail";
import { useEvalRuns } from "@/hooks/useEvalRuns";
import { EvalCompareView } from "./EvalCompareView";
import { EvalDatasetsView } from "./EvalDatasetsView";
import { EvalRunsView } from "./EvalRunsView";

type EvalView = "datasets" | "runs" | "compare";

const evalViewItems: Array<{ value: EvalView; label: string }> = [
  { value: "datasets", label: "Наборы" },
  { value: "runs", label: "Запуски" },
  { value: "compare", label: "Сравнение" },
];

export type EvalFocus = {
  view?: EvalView;
  datasetId?: string | null;
  caseId?: string | null;
  runId?: string | null;
  nonce?: number;
};

type EvalTabProps = {
  focus?: EvalFocus | null;
};

export function EvalTab({ focus }: EvalTabProps) {
  const [view, setView] = useState<EvalView>(focus?.view ?? "datasets");
  const datasets = useEvalDatasets({ focusDatasetId: focus?.datasetId ?? null });
  const runs = useEvalRuns({ pollingEnabled: view === "runs" || view === "compare" });
  const runDetail = useEvalRunDetail(view === "runs" ? runs.selectedRunId : null, { enabled: view === "runs" });
  const compare = useEvalCompare({ enabled: view === "compare", runs: runs.runs });

  useEffect(() => {
    if (!focus) {
      return;
    }
    setView(focus.view ?? "datasets");
    if (focus.datasetId) {
      datasets.setSelectedDatasetId(focus.datasetId);
    }
    if (focus.runId) {
      runs.setSelectedRunId(focus.runId);
    }
  }, [focus?.nonce]);

  useEffect(() => {
    if (view === "runs" && runs.selectedRun?.datasetId) {
      datasets.setSelectedDatasetId(runs.selectedRun.datasetId);
    }
  }, [runs.selectedRun?.datasetId, view]);

  return (
    <div className="space-y-5">
      <PageHeaderPanel
        badge="Оценка"
        badgeVariant="default"
        description="Здесь собраны регрессионные наборы, эталонные кейсы, запуски оценки и вердикты сравнения от backend."
        eyebrow="Контроль регрессий"
        title="Оценка"
      />

      <SegmentedControl
        ariaLabel="Разделы оценки"
        items={evalViewItems}
        value={view}
        trailing={
          <>
            <Badge variant="secondary">{datasets.datasets.length} наборов</Badge>
            <Badge variant={runs.hasActiveRuns ? "warning" : "secondary"}>
              {runs.hasActiveRuns ? "активный запуск" : `${runs.runs.length} запусков`}
            </Badge>
          </>
        }
        onValueChange={setView}
      />

      {view === "datasets" ? (
        <EvalDatasetsView datasetsHook={datasets} focusCaseId={focus?.caseId ?? null} />
      ) : null}

      {view === "runs" ? (
        <EvalRunsView datasetsHook={datasets} runDetailHook={runDetail} runsHook={runs} />
      ) : null}

      {view === "compare" ? (
        <EvalCompareView compareHook={compare} runs={runs.runs} />
      ) : null}
    </div>
  );
}
