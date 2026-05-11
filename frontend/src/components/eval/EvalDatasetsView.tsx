import { useEffect, useMemo, useState } from "react";
import type { useEvalDatasets } from "@/hooks/useEvalDatasets";
import type { EvalCase } from "@/types";
import { EvalCaseDialog } from "./EvalCaseDialog";
import { EvalDatasetDetailPanel } from "./EvalDatasetDetailPanel";
import { EvalDatasetList } from "./EvalDatasetList";
import { ALL_VALUE } from "./evalPresentation";

type EvalDatasetsViewProps = {
  datasetsHook: ReturnType<typeof useEvalDatasets>;
  focusCaseId?: string | null;
};

export function EvalDatasetsView({ datasetsHook, focusCaseId }: EvalDatasetsViewProps) {
  const [kindFilter, setKindFilter] = useState<string>(ALL_VALUE);
  const [statusFilter, setStatusFilter] = useState<string>(ALL_VALUE);
  const [tagFilter, setTagFilter] = useState("");
  const [severityFilter, setSeverityFilter] = useState<string>(ALL_VALUE);
  const [editingCase, setEditingCase] = useState<EvalCase | null>(null);
  const [creatingCase, setCreatingCase] = useState(false);
  const [promotionTargetDatasetId, setPromotionTargetDatasetId] = useState<string | null>(null);

  const filteredDatasets = useMemo(() => {
    const tag = tagFilter.trim().toLowerCase();
    return datasetsHook.datasets.filter((dataset) => {
      if (kindFilter !== ALL_VALUE && dataset.kind !== kindFilter) {
        return false;
      }
      if (statusFilter !== ALL_VALUE && dataset.status !== statusFilter) {
        return false;
      }
      return !tag || (dataset.tags ?? []).some((item) => item.toLowerCase().includes(tag));
    });
  }, [datasetsHook.datasets, kindFilter, statusFilter, tagFilter]);

  const filteredCases = useMemo(() => {
    if (severityFilter === ALL_VALUE) {
      return datasetsHook.cases;
    }
    return datasetsHook.cases.filter((evalCase) => evalCase.severity === severityFilter);
  }, [datasetsHook.cases, severityFilter]);

  const reviewQueue = datasetsHook.cases.filter((evalCase) =>
    evalCase.reviewStatus === "READY_FOR_REVIEW" || evalCase.reviewStatus === "DRAFT",
  );
  const activeCaseCount = datasetsHook.cases.filter((evalCase) => evalCase.active).length;
  const latestVersion = datasetsHook.versions[0] ?? null;
  const promotionTargets = datasetsHook.datasets.filter((dataset) => dataset.kind === "GOLDEN" || dataset.kind === "SMOKE");

  useEffect(() => {
    if (!promotionTargetDatasetId || promotionTargets.some((dataset) => dataset.id === promotionTargetDatasetId)) {
      return;
    }
    setPromotionTargetDatasetId(null);
  }, [promotionTargetDatasetId, promotionTargets]);

  useEffect(() => {
    if (!focusCaseId) {
      return;
    }
    const focused = datasetsHook.cases.find((evalCase) => evalCase.id === focusCaseId);
    if (focused?.datasetId) {
      datasetsHook.setSelectedDatasetId(focused.datasetId);
    }
  }, [focusCaseId, datasetsHook.cases]);

  const closeCaseDialog = () => {
    setCreatingCase(false);
    setEditingCase(null);
  };

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(280px,0.82fr)_minmax(0,1.18fr)]">
      <EvalDatasetList
        datasetsHook={datasetsHook}
        filteredDatasets={filteredDatasets}
        kindFilter={kindFilter}
        statusFilter={statusFilter}
        tagFilter={tagFilter}
        onKindFilterChange={setKindFilter}
        onStatusFilterChange={setStatusFilter}
        onTagFilterChange={setTagFilter}
      />

      <EvalDatasetDetailPanel
        activeCaseCount={activeCaseCount}
        datasetsHook={datasetsHook}
        filteredCases={filteredCases}
        focusCaseId={focusCaseId}
        latestVersion={latestVersion}
        promotionTargetDatasetId={promotionTargetDatasetId}
        promotionTargets={promotionTargets}
        reviewQueue={reviewQueue}
        severityFilter={severityFilter}
        onCreateCase={() => setCreatingCase(true)}
        onEditCase={setEditingCase}
        onPromotionTargetChange={setPromotionTargetDatasetId}
        onSeverityFilterChange={setSeverityFilter}
      />

      <EvalCaseDialog
        caseToEdit={editingCase}
        datasetId={datasetsHook.selectedDatasetId}
        isOpen={creatingCase || Boolean(editingCase)}
        isSaving={datasetsHook.isMutating}
        onClose={closeCaseDialog}
        onCreateCase={datasetsHook.createCase}
        onUpdateCase={datasetsHook.updateCase}
      />
    </div>
  );
}
