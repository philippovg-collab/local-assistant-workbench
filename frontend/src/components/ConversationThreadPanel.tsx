import { useMemo } from "react";
import { Archive, MessageSquare, Plus, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ContextInspectorPanel } from "@/components/ContextInspectorPanel";
import { useChatRunContext } from "@/hooks/useChatRunContext";
import { cn } from "@/lib/utils";
import type { ConversationRunDetail, ConversationSummary } from "@/types";
import { formatDate } from "@/utils/format";

type ConversationThreadPanelProps = {
  conversations: ConversationSummary[];
  runs: ConversationRunDetail[];
  selectedConversationId?: string | null;
  currentRunId?: string | null;
  error?: string | null;
  runsError?: string | null;
  isLoading?: boolean;
  isMutating?: boolean;
  onSelectConversation: (conversationId: string | null) => void;
  onCreateConversation: () => Promise<unknown>;
  onArchiveConversation: (conversationId: string) => Promise<unknown>;
  onRefreshRuns: () => Promise<unknown>;
};

export function ConversationThreadPanel({
  conversations,
  runs,
  selectedConversationId = null,
  currentRunId = null,
  error = null,
  runsError = null,
  isLoading = false,
  isMutating = false,
  onSelectConversation,
  onCreateConversation,
  onArchiveConversation,
  onRefreshRuns,
}: ConversationThreadPanelProps) {
  const activeConversation = conversations.find((conversation) => conversation.id === selectedConversationId) ?? null;
  const latestRun = runs.length > 0 ? runs[runs.length - 1] : null;
  const inspectedRunId = currentRunId ?? latestRun?.runId ?? null;
  const inspectedRun = useMemo(
    () => runs.find((run) => run.runId === inspectedRunId) ?? null,
    [inspectedRunId, runs],
  );
  const { snapshot: contextSnapshot, status: contextStatus } = useChatRunContext(inspectedRunId, {
    enabled: Boolean(activeConversation && inspectedRunId),
  });

  return (
    <section className="surface-subtle grid gap-4 rounded-[22px] p-4 lg:grid-cols-[260px_minmax(0,1fr)]">
      <div className="space-y-3">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <MessageSquare className="h-4 w-4 text-primary" />
            Беседы
          </div>
          <Button
            aria-label="Создать беседу"
            disabled={isMutating}
            size="icon"
            type="button"
            variant="secondary"
            onClick={() => void onCreateConversation()}
          >
            <Plus className="h-4 w-4" />
          </Button>
        </div>

        <div className="space-y-2">
          <Button
            className={cn("w-full justify-start", selectedConversationId === null && "border-primary/50")}
            type="button"
            variant={selectedConversationId === null ? "default" : "outline"}
            onClick={() => onSelectConversation(null)}
          >
            Без беседы
          </Button>

          {conversations.map((conversation) => (
            <button
              className={cn(
                "w-full rounded-[16px] border px-3 py-2 text-left text-sm transition",
                conversation.id === selectedConversationId
                  ? "border-primary bg-primary/10 text-foreground"
                  : "border-border bg-card/70 text-muted-foreground hover:border-primary/40 hover:text-foreground",
              )}
              key={conversation.id}
              type="button"
              onClick={() => onSelectConversation(conversation.id)}
            >
              <span className="block truncate font-semibold">{conversation.title}</span>
              <span className="mt-1 flex items-center justify-between gap-2 text-xs">
                <span>{conversation.turnCount} запусков</span>
                <span>{conversation.lastRunAt ? formatDate(conversation.lastRunAt) : conversation.status}</span>
              </span>
            </button>
          ))}
        </div>

        {(error || runsError) && (
          <p className="rounded-[14px] border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive">
            {error ?? runsError}
          </p>
        )}
      </div>

      <div className="min-w-0 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-foreground">
              {activeConversation ? activeConversation.title : "Разовый запуск"}
            </p>
            <p className="text-xs text-muted-foreground">
              {activeConversation ? `${activeConversation.status} · ${activeConversation.turnCount} запусков` : "История не выбрана"}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {activeConversation && (
              <Button
                aria-label="Архивировать беседу"
                disabled={isMutating}
                size="icon"
                type="button"
                variant="outline"
                onClick={() => void onArchiveConversation(activeConversation.id)}
              >
                <Archive className="h-4 w-4" />
              </Button>
            )}
            <Button
              aria-label="Обновить ветку"
              disabled={!activeConversation || isLoading}
              size="icon"
              type="button"
              variant="outline"
              onClick={() => void onRefreshRuns()}
            >
              <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
            </Button>
          </div>
        </div>

        <div className="max-h-[300px] space-y-2 overflow-y-auto pr-1">
          {activeConversation && inspectedRun && (
            <ContextInspectorPanel context={contextSnapshot} run={inspectedRun} status={contextStatus} />
          )}

          {activeConversation && runs.length === 0 && (
            <div className="rounded-[16px] border border-dashed border-border px-3 py-6 text-center text-sm font-medium text-muted-foreground">
              Ветка пока пустая
            </div>
          )}

          {runs.map((run) => (
            <article
              className={cn(
                "rounded-[16px] border border-border bg-card/75 px-3 py-2",
                run.runId === currentRunId && "border-primary/70",
              )}
              key={run.runId}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-foreground">
                    #{run.turnNo} {run.userPrompt}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">{formatDate(run.createdAt)}</p>
                </div>
                <Badge variant={run.status === "FAILED" ? "destructive" : run.status === "COMPLETED" ? "default" : "secondary"}>
                  {run.status}
                </Badge>
              </div>
              {run.contextAssemblyId && (
                <div className="mt-2">
                  <Badge variant="outline">контекст</Badge>
                </div>
              )}
              {run.failureMessage && (
                <p className="mt-2 text-xs font-medium text-destructive">{run.failureMessage}</p>
              )}
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
