import { ClipboardCheck, Plus } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import type { useEvalDatasets } from "@/hooks/useEvalDatasets";
import { evalCaseSeverityValues } from "@/types";
import type { EvalCase, EvalDatasetSummary, EvalDatasetVersion } from "@/types";
import { MetricCard } from "./EvalShared";
import {
  ALL_VALUE,
  expectedModeLabels,
  NONE_VALUE,
  reviewStatusLabels,
  severityLabels,
} from "./evalPresentation";

type EvalDatasetDetailPanelProps = {
  activeCaseCount: number;
  datasetsHook: ReturnType<typeof useEvalDatasets>;
  filteredCases: EvalCase[];
  focusCaseId?: string | null;
  latestVersion: EvalDatasetVersion | null;
  promotionTargetDatasetId: string | null;
  promotionTargets: EvalDatasetSummary[];
  reviewQueue: EvalCase[];
  severityFilter: string;
  onCreateCase: () => void;
  onEditCase: (evalCase: EvalCase) => void;
  onPromotionTargetChange: (datasetId: string | null) => void;
  onSeverityFilterChange: (value: string) => void;
};

export function EvalDatasetDetailPanel({
  activeCaseCount,
  datasetsHook,
  filteredCases,
  focusCaseId,
  latestVersion,
  onCreateCase,
  onEditCase,
  onPromotionTargetChange,
  onSeverityFilterChange,
  promotionTargetDatasetId,
  promotionTargets,
  reviewQueue,
  severityFilter,
}: EvalDatasetDetailPanelProps) {
  return (
    <section className="surface-subtle space-y-4 rounded-[24px] p-5">
      {datasetsHook.selectedDataset ? (
        <>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-[11px] font-semibold uppercase text-muted-foreground">Детали набора</p>
              <h3 className="text-xl font-semibold text-foreground">
                {datasetsHook.selectedDataset.name ?? datasetsHook.selectedDataset.datasetKey}
              </h3>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" type="button" variant="outline" onClick={onCreateCase}>
                <Plus className="h-4 w-4" />
                Кейс
              </Button>
              <Button
                disabled={!datasetsHook.selectedDatasetId || datasetsHook.isMutating}
                size="sm"
                type="button"
                variant="secondary"
                onClick={() => datasetsHook.selectedDatasetId
                  ? void datasetsHook.createDatasetVersion(datasetsHook.selectedDatasetId, { note: "Created from Eval UI" })
                  : undefined}
              >
                Версия
              </Button>
            </div>
          </div>

          <div className="grid gap-3 md:grid-cols-4">
            <MetricCard label="версия" value={latestVersion?.version ?? datasetsHook.selectedDataset.version ?? "н/д"} />
            <MetricCard label="активные кейсы" value={activeCaseCount} />
            <MetricCard label="очередь ревью" value={reviewQueue.length} />
            <MetricCard label="версии" value={datasetsHook.versions.length} />
          </div>

          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_260px]">
            <div className="rounded-[22px] border border-field-border bg-field px-4 py-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <strong className="text-sm font-semibold text-foreground">Кейсы</strong>
                <Select value={severityFilter} onValueChange={onSeverityFilterChange}>
                  <SelectTrigger aria-label="Фильтр кейсов по критичности" className="max-w-[220px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={ALL_VALUE}>Любая критичность</SelectItem>
                    {evalCaseSeverityValues.map((severity) => (
                      <SelectItem key={severity} value={severity}>{severityLabels[severity]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="mt-4 overflow-x-auto">
                <table className="w-full min-w-[760px] text-left text-sm">
                  <thead className="text-xs uppercase text-muted-foreground">
                    <tr>
                      <th className="px-3 py-2">кейс</th>
                      <th className="px-3 py-2">режим</th>
                      <th className="px-3 py-2">критичность</th>
                      <th className="px-3 py-2">ревью</th>
                      <th className="px-3 py-2">действия</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredCases.map((evalCase) => (
                      <tr className={focusCaseId === evalCase.id ? "bg-primary/8" : "border-t border-border"} key={evalCase.id}>
                        <td className="max-w-[300px] px-3 py-3">
                          <strong className="block text-foreground">{evalCase.caseKey}</strong>
                          <span className="line-clamp-2 text-muted-foreground">{evalCase.question}</span>
                        </td>
                        <td className="px-3 py-3">{evalCase.expectedMode ? expectedModeLabels[evalCase.expectedMode] : "н/д"}</td>
                        <td className="px-3 py-3">
                          <Badge variant={evalCase.severity === "BLOCKER" ? "destructive" : "secondary"}>
                            {evalCase.severity ? severityLabels[evalCase.severity] : "н/д"}
                          </Badge>
                        </td>
                        <td className="px-3 py-3">{evalCase.reviewStatus ? reviewStatusLabels[evalCase.reviewStatus] : "н/д"}</td>
                        <td className="px-3 py-3">
                          <div className="flex flex-wrap gap-2">
                            <Button size="sm" type="button" variant="outline" onClick={() => onEditCase(evalCase)}>Изменить</Button>
                            <Button
                              disabled={datasetsHook.isMutating}
                              size="sm"
                              type="button"
                              variant="secondary"
                              onClick={() => evalCase.id ? void datasetsHook.submitReview(evalCase.id, { note: "Submitted from Eval UI" }) : undefined}
                            >
                              На ревью
                            </Button>
                            <Button
                              disabled={datasetsHook.isMutating}
                              size="sm"
                              type="button"
                              variant="secondary"
                              onClick={() => evalCase.id
                                ? void datasetsHook.reviewCase(evalCase.id, { status: "APPROVED", note: "Approved from Eval UI" })
                                : undefined}
                            >
                              Одобрить
                            </Button>
                            <Button
                              disabled={!promotionTargetDatasetId || datasetsHook.isMutating}
                              size="sm"
                              type="button"
                              variant="outline"
                              onClick={() => evalCase.id && promotionTargetDatasetId
                                ? void datasetsHook.promoteCase(evalCase.id, {
                                    targetDatasetId: promotionTargetDatasetId,
                                    note: "Promoted from Eval UI",
                                    version: undefined,
                                  })
                                : undefined}
                            >
                              Продвинуть
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <aside className="space-y-3 rounded-[22px] border border-field-border bg-field px-4 py-4">
              <strong className="text-sm font-semibold text-foreground">Целевой набор</strong>
              <Select
                value={promotionTargetDatasetId ?? NONE_VALUE}
                onValueChange={(value) => onPromotionTargetChange(value === NONE_VALUE ? null : value)}
              >
                <SelectTrigger aria-label="Целевой набор для продвижения">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_VALUE}>Цель не выбрана</SelectItem>
                  {promotionTargets.map((dataset) => (
                    <SelectItem key={dataset.id} value={dataset.id ?? NONE_VALUE}>{dataset.name ?? dataset.datasetKey}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Separator />
              <strong className="block text-sm font-semibold text-foreground">Очередь ревью</strong>
              <div className="space-y-2">
                {reviewQueue.slice(0, 6).map((evalCase) => (
                  <div className="rounded-[18px] border border-border bg-background px-3 py-2" key={evalCase.id}>
                    <p className="text-sm font-medium text-foreground">{evalCase.caseKey}</p>
                    <p className="text-xs text-muted-foreground">{evalCase.reviewStatus}</p>
                  </div>
                ))}
                {reviewQueue.length === 0 ? <p className="text-sm leading-6 text-muted-foreground">Очередь пуста.</p> : null}
              </div>
            </aside>
          </div>
        </>
      ) : (
        <EmptyState description="Наборы оценки появятся здесь после backend seed или продвижения из аудита." icon={ClipboardCheck} title="Набор не выбран" />
      )}
    </section>
  );
}
