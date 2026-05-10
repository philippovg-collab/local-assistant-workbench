import { useMemo, useState } from "react";
import { ExternalLink } from "lucide-react";
import { Badge, type BadgeProps } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  formatRetrievalResolutionStatus,
  normalizeStickyResolvedState,
  normalizeStickySources,
  onOff,
} from "@/components/contextInspectorPresentation";
import { cn } from "@/lib/utils";
import type {
  ChatRunContextDetail,
  ChatRunContextHistoryItem,
  ChatRunContextMemoryItem,
  ConversationRunDetail,
} from "@/types";

type ContextInspectorPanelProps = {
  context: ChatRunContextDetail | null;
  status: "idle" | "loading" | "not_found" | "error";
  run: ConversationRunDetail | null;
};

type InspectorTab = "overview" | "history" | "memory" | "state" | "retrieval" | "budget";

const tabs: Array<{ id: InspectorTab; label: string }> = [
  { id: "overview", label: "Overview" },
  { id: "history", label: "History" },
  { id: "memory", label: "Memory" },
  { id: "state", label: "State" },
  { id: "retrieval", label: "Retrieval" },
  { id: "budget", label: "Budget" },
];

export function ContextInspectorPanel({ context, status, run }: ContextInspectorPanelProps) {
  const [activeTab, setActiveTab] = useState<InspectorTab>("overview");
  const contextStatus = context?.status ?? { idle: "IDLE", loading: "LOADING", not_found: "NOT_FOUND", error: "ERROR" }[status];
  const statusVariant: BadgeProps["variant"] = contextStatus === "AVAILABLE"
    ? "success"
    : contextStatus === "EXPIRED"
      ? "secondary"
      : contextStatus === "DEGRADED" || status === "error"
        ? "warning"
        : "outline";
  const stickySources = useMemo(() => normalizeStickySources(context?.stickyStateResolution), [context?.stickyStateResolution]);
  const stickyResolvedState = useMemo(
    () => normalizeStickyResolvedState(context?.stickyStateResolution),
    [context?.stickyStateResolution],
  );

  return (
    <div className="rounded-[16px] border border-border bg-card/75 px-3 py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-foreground">Контекст #{context?.turnNo ?? run?.turnNo ?? "—"}</p>
          {context?.promptPreview && (
            <p className="mt-1 truncate text-xs text-muted-foreground">{context.promptPreview}</p>
          )}
        </div>
        <div className="flex flex-wrap items-center justify-end gap-2">
          {status === "loading" && <Badge variant="secondary">загрузка</Badge>}
          <Badge variant={statusVariant}>{contextStatus}</Badge>
          {context?.summaryState?.used && <Badge variant="outline">summary</Badge>}
          {context?.retrievalQueryResolution?.decision && (
            <Badge variant="outline">{formatRetrievalResolutionStatus(context.retrievalQueryResolution)}</Badge>
          )}
        </div>
      </div>

      <div className="mt-3 flex gap-1 overflow-x-auto rounded-full border border-border bg-background/60 p-1">
        {tabs.map((tab) => (
          <button
            aria-pressed={activeTab === tab.id}
            className={cn(
              "rounded-full px-3 py-1.5 text-xs font-semibold text-muted-foreground transition",
              activeTab === tab.id && "bg-primary text-primary-foreground",
            )}
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="mt-3 min-h-[112px] text-xs text-muted-foreground">
        {!context && status === "loading" && <EmptyLine text="Загружаем context snapshot." />}
        {!context && status === "error" && <EmptyLine text="Inspector сейчас недоступен." />}
        {!context && status === "not_found" && <EmptyLine text="Запуск не найден; context snapshot недоступен." />}
        {context && activeTab === "overview" && <OverviewTab context={context} />}
        {context && activeTab === "history" && <HistoryTab items={context.selectedHistory ?? []} />}
        {context && activeTab === "memory" && <MemoryTab context={context} items={context.selectedMemory ?? []} />}
        {context && activeTab === "state" && (
          <StateTab context={context} stickyResolvedState={stickyResolvedState} stickySources={stickySources} />
        )}
        {context && activeTab === "retrieval" && <RetrievalTab context={context} />}
        {context && activeTab === "budget" && <BudgetTab context={context} />}
      </div>
    </div>
  );
}

function OverviewTab({ context }: { context: ChatRunContextDetail }) {
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <Metric label="История" value={`${context.selectedHistory?.length ?? 0}`} />
      <Metric label="Memory" value={`${context.selectedMemory?.length ?? 0}`} />
      <Metric label="Сброшено" value={`${context.droppedItems?.length ?? 0}`} />
      <Metric label="Токены" value={`${context.tokenBudget?.used ?? 0}/${context.tokenBudget?.max ?? 0}`} />
      <Metric label="Summary" value={context.summaryState?.status ?? "none"} />
      {context.reasonMessage && (
        <div className="sm:col-span-2">
          <Metric label="Reason" value={context.reasonMessage} />
        </div>
      )}
      <LinkRow links={context.links} />
    </div>
  );
}

function MemoryTab({ context, items }: { context: ChatRunContextDetail; items: ChatRunContextMemoryItem[] }) {
  const state = context.memoryState;
  const dropped = context.droppedMemory ?? [];
  return (
    <div className="space-y-2">
      <div className="grid gap-2 sm:grid-cols-3">
        <Metric label="Enabled" value={onOff(state?.enabled)} />
        <Metric label="Requested" value={onOff(state?.requested)} />
        <Metric label="Status" value={state?.status ?? "disabled"} />
      </div>
      {state?.degradedReason && <Metric label="Reason" value={state.degradedReason} />}
      {items.length === 0 ? (
        <EmptyLine text="Approved memory не попала в prompt." />
      ) : (
        <div className="space-y-2">
          {items.map((item) => (
            <div className="rounded-[12px] border border-border bg-background/60 p-2" key={item.id ?? item.contentPreview}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <strong className="text-foreground">{item.entryType ?? "memory"}</strong>
                <span>{item.tokenEstimate ?? 0} токенов</span>
              </div>
              <p className="mt-2 text-foreground/80">{item.contentPreview ?? "—"}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {item.pinned && <Badge variant="default">pinned</Badge>}
                {item.workspaceKey && <Badge variant="outline">{item.workspaceKey}</Badge>}
                {item.projectKey && <Badge variant="outline">{item.projectKey}</Badge>}
              </div>
            </div>
          ))}
        </div>
      )}
      {dropped.length > 0 && (
        <div className="space-y-1">
          {dropped.map((item, index) => (
            <div className="rounded-[10px] border border-border px-2 py-1" key={`${item.id}-${item.reason}-${index}`}>
              <div className="flex items-center justify-between gap-3">
                <span>{item.entryType ?? "memory"} · {item.reason ?? "dropped"}</span>
                <span>{item.tokenEstimate ?? 0}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function HistoryTab({ items }: { items: ChatRunContextHistoryItem[] }) {
  if (items.length === 0) {
    return <EmptyLine text="История не попала в prompt." />;
  }
  return (
    <div className="space-y-2">
      {items.map((item) => (
        <div className="rounded-[12px] border border-border bg-background/60 p-2" key={`${item.runId}-${item.turnNo}`}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <strong className="text-foreground">#{item.turnNo ?? "—"} {item.status ?? "unknown"}</strong>
            <span>{item.tokenEstimate ?? 0} токенов</span>
          </div>
          <p className="mt-2 text-foreground/80">{item.promptPreview ?? "—"}</p>
          <p className="mt-1">{item.answerPreview ?? "—"}</p>
          <LinkRow links={item.links} compact />
        </div>
      ))}
    </div>
  );
}

function StateTab({
  context,
  stickyResolvedState,
  stickySources,
}: {
  context: ChatRunContextDetail;
  stickyResolvedState: Array<[string, string]>;
  stickySources: Array<[string, string]>;
}) {
  const featureState = context.featureState;
  const stickyResolution = context.stickyStateResolution ?? {};
  const stickyStatus = stickyResolution.active === true
    ? "active"
    : stickyResolution.disabledForRun === true
      ? "disabled"
      : "inactive";
  return (
    <div className="space-y-3">
      <div className="grid gap-2 sm:grid-cols-3">
        <Metric label="Context" value={onOff(featureState?.context)} />
        <Metric label="History" value={onOff(featureState?.history)} />
        <Metric label="Sticky" value={onOff(featureState?.sticky)} />
        <Metric label="Rewrite" value={onOff(featureState?.rewrite)} />
        <Metric label="Summary" value={onOff(featureState?.summary)} />
        <Metric label="Memory" value={onOff(featureState?.longTermMemory)} />
        <Metric label="Conversation" value={onOff(featureState?.conversations)} />
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        <Metric label="Sticky run" value={stickyStatus} />
        {typeof stickyResolution.stickyVersion === "number" && (
          <Metric label="Sticky version" value={`${stickyResolution.stickyVersion}`} />
        )}
        {typeof stickyResolution.stickyUpdatedThroughTurnNo === "number" && (
          <Metric label="Sticky through" value={`#${stickyResolution.stickyUpdatedThroughTurnNo}`} />
        )}
        {typeof stickyResolution.disabledReason === "string" && (
          <Metric label="Sticky reason" value={stickyResolution.disabledReason} />
        )}
      </div>
      {stickySources.length === 0 ? (
        <EmptyLine text="Sticky state не применялся." />
      ) : (
        <div className="grid gap-2 sm:grid-cols-2">
          {stickySources.map(([field, source]) => (
            <Metric key={field} label={field} value={source} />
          ))}
        </div>
      )}
      {stickyResolvedState.length > 0 && (
        <div className="grid gap-2 sm:grid-cols-2">
          {stickyResolvedState.map(([field, value]) => (
            <Metric key={field} label={`value · ${field}`} value={value} />
          ))}
        </div>
      )}
    </div>
  );
}

function RetrievalTab({ context }: { context: ChatRunContextDetail }) {
  const resolution = context.retrievalQueryResolution;
  if (!resolution) {
    return <EmptyLine text="Rewrite не запускался для этого turn." />;
  }
  return (
    <div className="space-y-2">
      <Metric label="Decision" value={resolution.decision ?? "original"} />
      <Metric label="Confidence" value={resolution.confidence == null ? "—" : `${Math.round(resolution.confidence * 100)}%`} />
      <Metric label="Query" value={resolution.queryForRetrieval ?? resolution.resolvedQuery ?? "—"} />
      {resolution.degradedReason && <Metric label="Degraded reason" value={resolution.degradedReason} />}
    </div>
  );
}

function BudgetTab({ context }: { context: ChatRunContextDetail }) {
  const budget = context.tokenBudget;
  const droppedItems = context.droppedItems ?? [];
  return (
    <div className="space-y-3">
      <div className="grid gap-2 sm:grid-cols-3">
        <Metric label="Max" value={`${budget?.max ?? 0}`} />
        <Metric label="Used" value={`${budget?.used ?? 0}`} />
        <Metric label="Remaining" value={`${budget?.remaining ?? 0}`} />
        <Metric label="History" value={`${budget?.history ?? 0}`} />
        <Metric label="Summary" value={`${budget?.summary ?? 0}`} />
        <Metric label="Memory" value={`${budget?.memory ?? 0}`} />
        <Metric label="Dropped" value={`${budget?.dropped ?? 0}`} />
      </div>
      {droppedItems.length > 0 && (
        <div className="space-y-1">
          {droppedItems.map((item, index) => (
            <div className="rounded-[10px] border border-border px-2 py-1" key={`${item.runId}-${item.turnNo}-${item.reason}-${index}`}>
              <div className="flex items-center justify-between gap-3">
                <span>#{item.turnNo ?? "—"} {item.reason ?? "dropped"}</span>
                <span>{item.tokenEstimate ?? 0}</span>
              </div>
              <LinkRow links={item.links} compact />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[10px] border border-border bg-background/60 px-2 py-1.5">
      <span className="block text-[11px] font-semibold uppercase text-muted-foreground">{label}</span>
      <span className="mt-1 block break-words text-foreground">{value}</span>
    </div>
  );
}

function LinkRow({ links, compact = false }: { links?: ChatRunContextDetail["links"]; compact?: boolean }) {
  const linkItems = [
    ["trace", links?.trace],
    ["result", links?.result],
    ["runs", links?.conversationRuns],
  ].filter((item): item is [string, string] => Boolean(item[1]));
  if (linkItems.length === 0) {
    return null;
  }
  return (
    <div className={cn("flex flex-wrap gap-2", compact ? "mt-2" : "sm:col-span-2")}>
      {linkItems.map(([label, href]) => (
        <Button asChild key={label} size="sm" variant="outline">
          <a href={href} rel="noreferrer" target="_blank">
            <ExternalLink className="h-3.5 w-3.5" />
            {label}
          </a>
        </Button>
      ))}
    </div>
  );
}

function EmptyLine({ text }: { text: string }) {
  return (
    <div className="rounded-[12px] border border-dashed border-border px-3 py-6 text-center font-medium">
      {text}
    </div>
  );
}
