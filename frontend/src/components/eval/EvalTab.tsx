import { useEffect, useState } from "react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useEvalCompare } from "@/hooks/useEvalCompare";
import { useEvalDatasets } from "@/hooks/useEvalDatasets";
import { useEvalRunDetail } from "@/hooks/useEvalRunDetail";
import { useEvalRuns } from "@/hooks/useEvalRuns";
import { EvalCompareView } from "./EvalCompareView";
import { EvalDatasetsView } from "./EvalDatasetsView";
import { EvalRunsView } from "./EvalRunsView";

type EvalView = "datasets" | "runs" | "compare";

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
      <SectionIntro
        badge="Eval"
        badgeVariant="default"
        description="Regression datasets, golden cases, eval runs and backend compare verdicts live here."
        eyebrow="Regression Control"
        title="Eval"
      />

      <div className="surface-subtle flex flex-wrap items-center gap-2 rounded-[24px] p-2">
        <EvalViewButton active={view === "datasets"} label="Datasets" onClick={() => setView("datasets")} />
        <EvalViewButton active={view === "runs"} label="Runs" onClick={() => setView("runs")} />
        <EvalViewButton active={view === "compare"} label="Compare" onClick={() => setView("compare")} />
        <div className="ml-auto flex flex-wrap gap-2">
          <Badge variant="secondary">{datasets.datasets.length} datasets</Badge>
          <Badge variant={runs.hasActiveRuns ? "warning" : "secondary"}>
            {runs.hasActiveRuns ? "active run" : `${runs.runs.length} runs`}
          </Badge>
        </div>
      </div>

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

function EvalViewButton({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <Button size="sm" type="button" variant={active ? "default" : "ghost"} onClick={onClick}>
      {label}
    </Button>
  );
}
