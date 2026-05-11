import { ListFilter, RefreshCw, Search } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { useEvalDatasets } from "@/hooks/useEvalDatasets";
import { evalDatasetKindValues, evalLifecycleStatusValues } from "@/types";
import { ALL_VALUE, datasetKindLabels } from "./evalPresentation";

type EvalDatasetListProps = {
  datasetsHook: ReturnType<typeof useEvalDatasets>;
  filteredDatasets: ReturnType<typeof useEvalDatasets>["datasets"];
  kindFilter: string;
  statusFilter: string;
  tagFilter: string;
  onKindFilterChange: (value: string) => void;
  onStatusFilterChange: (value: string) => void;
  onTagFilterChange: (value: string) => void;
};

export function EvalDatasetList({
  datasetsHook,
  filteredDatasets,
  kindFilter,
  onKindFilterChange,
  onStatusFilterChange,
  onTagFilterChange,
  statusFilter,
  tagFilter,
}: EvalDatasetListProps) {
  return (
    <section className="surface-subtle space-y-4 rounded-[24px] p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <ListFilter className="h-4 w-4 text-primary" />
          Наборы
        </div>
        <Button size="sm" type="button" variant="outline" onClick={() => void datasetsHook.loadDatasets()}>
          <RefreshCw className="h-4 w-4" />
          Обновить
        </Button>
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-1">
        <Select value={kindFilter} onValueChange={onKindFilterChange}>
          <SelectTrigger aria-label="Фильтр наборов по типу">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>Все типы</SelectItem>
            {evalDatasetKindValues.map((kind) => (
              <SelectItem key={kind} value={kind}>{datasetKindLabels[kind]}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={statusFilter} onValueChange={onStatusFilterChange}>
          <SelectTrigger aria-label="Фильтр наборов по статусу">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_VALUE}>Все статусы</SelectItem>
            {evalLifecycleStatusValues.map((status) => (
              <SelectItem key={status} value={status}>{status}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Input
          aria-label="Фильтр наборов по тегу"
          placeholder="тег"
          value={tagFilter}
          onChange={(event) => onTagFilterChange(event.target.value)}
        />
      </div>

      {datasetsHook.error ? <p className="text-sm leading-6 text-destructive">{datasetsHook.error}</p> : null}

      <div className="space-y-3">
        {filteredDatasets.map((dataset) => (
          <button
            className={`w-full rounded-[22px] border px-4 py-4 text-left transition ${
              datasetsHook.selectedDatasetId === dataset.id
                ? "border-primary bg-primary/8"
                : "border-field-border bg-field hover:border-primary/40"
            }`}
            key={dataset.id}
            type="button"
            onClick={() => datasetsHook.setSelectedDatasetId(dataset.id ?? null)}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <strong className="text-sm font-semibold text-foreground">{dataset.name ?? dataset.datasetKey}</strong>
              <Badge variant={dataset.status === "ACTIVE" ? "success" : "secondary"}>{dataset.status ?? "н/д"}</Badge>
            </div>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{dataset.description ?? "Нет описания"}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Badge variant="outline">{dataset.kind ? datasetKindLabels[dataset.kind] : "тип н/д"}</Badge>
              <Badge variant="secondary">{dataset.caseCount} кейсов</Badge>
              {dataset.version ? <Badge variant="secondary">v {dataset.version}</Badge> : null}
            </div>
          </button>
        ))}

        {filteredDatasets.length === 0 ? (
          <EmptyState description="Фильтры не нашли подходящие наборы." icon={Search} title="Наборы не найдены" />
        ) : null}
      </div>
    </section>
  );
}
