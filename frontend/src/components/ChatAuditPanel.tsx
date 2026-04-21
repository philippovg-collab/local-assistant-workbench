import { useEffect, useMemo, useState } from "react";
import { History, Radar } from "lucide-react";
import { apiClient } from "@/api/client";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import type {
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatRunTraceDetail,
  InstructionTraceEntry,
  KnowledgeScopeResolved,
  RetrievalTrace,
} from "@/types";
import { formatDate } from "@/utils/format";
import {
  knowledgeScopeResolvedWithDefaults,
  retrievalTraceWithDefaults,
  supportVerdictLabels,
} from "@/utils/workbenchPresentation";
import {
  documentStatusLabels,
  documentTypeLabels,
  materialLanguageCodeLabels,
} from "@/utils/materialMetadata";

type ChatAuditPanelProps = {
  runs: ChatAuditRunSummary[];
  selectedRun: ChatAuditRunDetail | null;
  error: string | null;
  onLoadRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
  currentAuditRunId?: string | null;
  currentInstructionTrace?: InstructionTraceEntry[];
  currentKnowledgeScopeResolved?: KnowledgeScopeResolved | null;
  currentRetrievalTrace?: RetrievalTrace | null;
};

const formatScopeSummary = (scope: KnowledgeScopeResolved) => {
  const parts = [];
  if (scope.presets.length > 0) {
    parts.push(`presets: ${scope.presets.map((preset) => `${preset.name} (rev ${preset.revision})`).join(", ")}`);
  }
  if (scope.facets.length > 0) {
    parts.push(`facets: ${scope.facets.map((facet) => `${facet.name} (rev ${facet.revision})`).join(", ")}`);
  }
  if (scope.documentTypes.length > 0) {
    parts.push(`types: ${scope.documentTypes.map((item) => documentTypeLabels[item] ?? item).join(", ")}`);
  }
  if (scope.documentStatuses.length > 0) {
    parts.push(`statuses: ${scope.documentStatuses.map((item) => documentStatusLabels[item] ?? item).join(", ")}`);
  }
  if (scope.projectKeys.length > 0) {
    parts.push(`projects: ${scope.projectKeys.join(", ")}`);
  }
  if (scope.documentNumber) {
    parts.push(`number: ${scope.documentNumber}`);
  }
  if (scope.languageCodes.length > 0) {
    parts.push(`languages: ${scope.languageCodes.map((item) => materialLanguageCodeLabels[item] ?? item).join(", ")}`);
  }
  if (scope.periodStartFrom || scope.periodStartTo) {
    parts.push(`valid from: ${scope.periodStartFrom ?? "*"}..${scope.periodStartTo ?? "*"}`);
  }
  if (scope.periodEndFrom || scope.periodEndTo) {
    parts.push(`valid to: ${scope.periodEndFrom ?? "*"}..${scope.periodEndTo ?? "*"}`);
  }
  if (scope.tags.length > 0) {
    parts.push(`tags: ${scope.tags.join(", ")}`);
  }
  if (scope.workspaceKey) {
    parts.push(`workspace: ${scope.workspaceKey}`);
  }
  if (scope.uploadedTodayOnly) {
    parts.push("today uploads only");
  }
  return parts.length > 0 ? parts.join(" · ") : "Без дополнительных ограничений.";
};

const runInstructionKeys = (run: ChatAuditRunDetail) =>
  new Set(
    run.instructionTrace.map((entry) =>
      [
        entry.instructionId ?? "temporary",
        entry.scopeLevel,
        entry.scopeTargetId ?? "-",
        entry.revision,
        entry.title,
      ].join(":"),
    ),
  );

const runPresetKeys = (run: ChatAuditRunDetail) =>
  new Set(knowledgeScopeResolvedWithDefaults(run.knowledgeScopeResolved).presets.map((preset) => `${preset.id}:${preset.revision}`));

const runChunkKeys = (run: ChatAuditRunDetail) =>
  new Set(run.sources.map((source) => `${source.materialId}:${source.chunkId}`));

const countSetDifference = (left: Set<string>, right: Set<string>) =>
  [...left].filter((value) => !right.has(value)).length;

const compareRuns = (baseRun: ChatAuditRunDetail, compareRun: ChatAuditRunDetail) => {
  const lines = [];
  const baseTrace = retrievalTraceWithDefaults(baseRun.retrievalTrace);
  const compareTrace = retrievalTraceWithDefaults(compareRun.retrievalTrace);

  if (baseRun.model !== compareRun.model) {
    lines.push(`Model: ${baseRun.model} vs ${compareRun.model}`);
  }
  if ((baseRun.answerMode ?? "brief") !== (compareRun.answerMode ?? "brief")) {
    lines.push(`Answer mode: ${baseRun.answerMode ?? "brief"} vs ${compareRun.answerMode ?? "brief"}`);
  }
  if ((baseRun.contextStatus ?? "ready") !== (compareRun.contextStatus ?? "ready")) {
    lines.push(`Context status: ${baseRun.contextStatus ?? "ready"} vs ${compareRun.contextStatus ?? "ready"}`);
  }

  const baseInstructions = runInstructionKeys(baseRun);
  const compareInstructions = runInstructionKeys(compareRun);
  const addedInstructions = countSetDifference(compareInstructions, baseInstructions);
  const removedInstructions = countSetDifference(baseInstructions, compareInstructions);
  if (addedInstructions > 0 || removedInstructions > 0) {
    lines.push(`Instruction stack: +${addedInstructions} / -${removedInstructions}`);
  }

  const basePresets = runPresetKeys(baseRun);
  const comparePresets = runPresetKeys(compareRun);
  const addedPresets = countSetDifference(comparePresets, basePresets);
  const removedPresets = countSetDifference(basePresets, comparePresets);
  if (addedPresets > 0 || removedPresets > 0) {
    lines.push(`Preset revisions: +${addedPresets} / -${removedPresets}`);
  }

  if (
    formatScopeSummary(knowledgeScopeResolvedWithDefaults(baseRun.knowledgeScopeResolved))
    !== formatScopeSummary(knowledgeScopeResolvedWithDefaults(compareRun.knowledgeScopeResolved))
  ) {
    lines.push("Scope resolved differs");
  }

  if (baseTrace.supportVerdict !== compareTrace.supportVerdict) {
    lines.push(
      `Support verdict: ${supportVerdictLabels[baseTrace.supportVerdict]} vs ${supportVerdictLabels[compareTrace.supportVerdict]}`,
    );
  }

  if (baseTrace.finalChunks !== compareTrace.finalChunks) {
    lines.push(`Final chunks: ${baseTrace.finalChunks} vs ${compareTrace.finalChunks}`);
  }
  if (baseTrace.scopedReadyMaterials !== compareTrace.scopedReadyMaterials) {
    lines.push(
      `Scoped ready materials: ${baseTrace.scopedReadyMaterials} vs ${compareTrace.scopedReadyMaterials}`,
    );
  }
  if (baseTrace.semanticCandidates !== compareTrace.semanticCandidates) {
    lines.push(
      `Semantic candidates: ${baseTrace.semanticCandidates} vs ${compareTrace.semanticCandidates}`,
    );
  }
  if (baseTrace.lexicalCandidates !== compareTrace.lexicalCandidates) {
    lines.push(
      `Lexical candidates: ${baseTrace.lexicalCandidates} vs ${compareTrace.lexicalCandidates}`,
    );
  }

  const baseChunks = runChunkKeys(baseRun);
  const compareChunks = runChunkKeys(compareRun);
  const addedChunks = countSetDifference(compareChunks, baseChunks);
  const removedChunks = countSetDifference(baseChunks, compareChunks);
  if (addedChunks > 0 || removedChunks > 0) {
    lines.push(`Chunk ids: +${addedChunks} / -${removedChunks}`);
  }

  return lines.length > 0 ? lines : ["Существенных отличий между выбранными запусками не найдено."];
};

const runSummary = (run: ChatAuditRunDetail) =>
  `${run.mode} · ${run.model} · ${formatDate(run.createdAt)}`;

type AuditInspectorProps = {
  run: ChatAuditRunDetail;
  label: string;
};

const AuditInspector = ({ run, label }: AuditInspectorProps) => {
  const retrievalTrace = retrievalTraceWithDefaults(run.retrievalTrace);
  const resolvedScope = knowledgeScopeResolvedWithDefaults(run.knowledgeScopeResolved);

  return (
    <article className="space-y-4 rounded-[22px] border border-field-border bg-field px-4 py-4">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Radar className="h-4 w-4 text-primary" />
        {label}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant="secondary">{runSummary(run)}</Badge>
        <Badge variant={retrievalTrace.supportVerdict === "sufficient" ? "success" : retrievalTrace.supportVerdict === "weak" ? "warning" : "outline"}>
          {supportVerdictLabels[retrievalTrace.supportVerdict]}
        </Badge>
      </div>
    </div>

    <Separator />

    <div className="space-y-2 text-sm leading-6 text-foreground">
      <p><strong>Prompt:</strong> {run.prompt}</p>
      <p><strong>Answer:</strong> {run.answer}</p>
      <p><strong>Scope:</strong> {formatScopeSummary(resolvedScope)}</p>
      <p>
        <strong>Retrieval:</strong> ready {retrievalTrace.scopedReadyMaterials}, semantic {retrievalTrace.semanticCandidates},
        lexical {retrievalTrace.lexicalCandidates}, final {retrievalTrace.finalChunks}
      </p>
      <p><strong>Support:</strong> {supportVerdictLabels[retrievalTrace.supportVerdict]}</p>
      <p>
        <strong>Instruction stack:</strong>{" "}
        {run.instructionTrace.length > 0
          ? run.instructionTrace
              .map((entry) =>
                `${entry.title} (${entry.scopeLevel}${entry.scopeTargetId ? `:${entry.scopeTargetId}` : ""}, rev ${entry.revision})`,
              )
              .join(", ")
          : "пустой"}
      </p>
    </div>

    <Separator />

    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <strong className="text-sm font-semibold text-foreground">Sources</strong>
        <Badge variant="outline">{run.sources.length} chunk(s)</Badge>
      </div>

      {run.sources.length === 0 ? (
        <p className="text-sm leading-6 text-muted-foreground">Источники для этого запуска не сохранились или retrieval ничего не вернул.</p>
      ) : (
        run.sources.map((source) => (
          <div className="rounded-[18px] border border-border bg-background px-4 py-3" key={`${run.id}-${source.chunkId}`}>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <strong className="text-sm font-semibold text-foreground">{source.title}</strong>
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="default">score {source.score}</Badge>
                {source.page ? <Badge variant="secondary">page {source.page}</Badge> : null}
                {source.chunkIndex !== undefined && source.chunkIndex !== null
                  ? <Badge variant="outline">chunk {source.chunkIndex}</Badge>
                  : null}
              </div>
            </div>
            <p className="mt-2 text-sm leading-6 text-foreground">{source.excerpt}</p>
          </div>
        ))
      )}
    </div>
  </article>
  );
};

const clipText = (value?: string | null, limit = 900) => {
  if (!value) {
    return "n/a";
  }
  const normalized = value.replace(/\s+/g, " ").trim();
  return normalized.length > limit ? `${normalized.slice(0, limit)}...` : normalized;
};

const TraceFoundationInspector = ({ trace }: { trace: ChatRunTraceDetail }) => {
  const latestLlmCall = trace.llmCalls.length > 0 ? trace.llmCalls[trace.llmCalls.length - 1] : null;
  const promptSnapshot = trace.promptSnapshot;
  const retrievalSummary = trace.retrievalSummary;
  const output = trace.output;

  return (
    <article className="space-y-4 rounded-[22px] border border-field-border bg-field px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Radar className="h-4 w-4 text-primary" />
          Trace foundation
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={trace.status === "FAILED" ? "destructive" : trace.status === "COMPLETED" ? "success" : "secondary"}>
            {trace.status}
          </Badge>
          {trace.latencyMsTotal !== undefined && trace.latencyMsTotal !== null ? (
            <Badge variant="outline">{trace.latencyMsTotal} ms</Badge>
          ) : null}
        </div>
      </div>

      {trace.status === "FAILED" ? (
        <div className="rounded-[18px] border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm leading-6 text-destructive">
          {trace.failureStage ?? "unknown stage"} · {trace.failureCode ?? "unknown_code"} · {trace.failureMessage ?? "No failure message"}
        </div>
      ) : null}

      <div className="grid gap-3 lg:grid-cols-3">
        <div className="rounded-[18px] border border-border bg-background px-4 py-3 text-sm leading-6 text-foreground">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Prompt</p>
          <p>hash: {promptSnapshot?.promptHash ?? "n/a"}</p>
          <p>messages: {promptSnapshot?.messages.length ?? 0}</p>
          <p>grounding: {promptSnapshot?.groundingRulesApplied ? "on" : "off"}</p>
        </div>
        <div className="rounded-[18px] border border-border bg-background px-4 py-3 text-sm leading-6 text-foreground">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Retrieval</p>
          <p>status: {retrievalSummary?.retrievalStatus ?? "n/a"}</p>
          <p>profile: {retrievalSummary?.relevanceProfile ?? retrievalSummary?.debug?.relevanceProfile ?? "n/a"}</p>
          <p>final: {retrievalSummary?.trace?.finalChunks ?? 0}</p>
        </div>
        <div className="rounded-[18px] border border-border bg-background px-4 py-3 text-sm leading-6 text-foreground">
          <p className="text-xs uppercase tracking-[0.14em] text-muted-foreground">LLM</p>
          <p>calls: {trace.llmCalls.length}</p>
          <p>model: {latestLlmCall?.model ?? trace.resolvedModel ?? trace.requestedModel ?? "n/a"}</p>
          <p>finish: {latestLlmCall?.finishReason ?? latestLlmCall?.errorCode ?? "n/a"}</p>
        </div>
      </div>

      <details className="rounded-[18px] border border-border bg-background px-4 py-3">
        <summary className="cursor-pointer text-sm font-medium text-foreground">Resolved system prompt</summary>
        <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-foreground">
          {clipText(promptSnapshot?.resolvedSystemPrompt)}
        </p>
      </details>

      <details className="rounded-[18px] border border-border bg-background px-4 py-3">
        <summary className="cursor-pointer text-sm font-medium text-foreground">LLM request and output</summary>
        <div className="mt-3 space-y-3 text-sm leading-6 text-foreground">
          <p><strong>Request messages:</strong> {latestLlmCall?.requestMessages.length ?? 0}</p>
          <p><strong>Raw:</strong> {clipText(output?.rawModelAnswer ?? latestLlmCall?.parsedAnswerText, 600)}</p>
          <p><strong>Final:</strong> {clipText(output?.finalUserAnswer, 600)}</p>
          <p>
            <strong>Flags:</strong>{" "}
            abstained={String(output?.abstained ?? false)} · strictBlocked={String(output?.strictSourcesBlockedAnswer ?? false)}
          </p>
        </div>
      </details>
    </article>
  );
};

export function ChatAuditPanel({
  runs,
  selectedRun,
  error,
  onLoadRun,
  currentAuditRunId,
  currentInstructionTrace = [],
  currentKnowledgeScopeResolved,
  currentRetrievalTrace,
}: ChatAuditPanelProps) {
  const [cachedRuns, setCachedRuns] = useState<Record<string, ChatAuditRunDetail>>({});
  const [cachedTraces, setCachedTraces] = useState<Record<string, ChatRunTraceDetail>>({});
  const [traceError, setTraceError] = useState<string | null>(null);
  const [baseRunId, setBaseRunId] = useState<string | null>(null);
  const [compareRunId, setCompareRunId] = useState<string | null>(null);

  const currentScope = knowledgeScopeResolvedWithDefaults(currentKnowledgeScopeResolved);
  const currentTrace = retrievalTraceWithDefaults(currentRetrievalTrace);

  useEffect(() => {
    if (!selectedRun) {
      return;
    }
    setCachedRuns((current) => ({
      ...current,
      [selectedRun.id]: selectedRun,
    }));
  }, [selectedRun]);

  const loadTraceDetail = async (runId: string) => {
    if (cachedTraces[runId]) {
      return cachedTraces[runId];
    }
    try {
      const trace = await apiClient.fetchChatRunTrace(runId);
      setCachedTraces((current) => ({
        ...current,
        [trace.id]: trace,
      }));
      setTraceError(null);
      return trace;
    } catch {
      setTraceError("Trace detail для этого запуска недоступен. Возможно, это legacy audit run до P0.");
      return null;
    }
  };

  useEffect(() => {
    if (currentAuditRunId && !baseRunId) {
      setBaseRunId(currentAuditRunId);
    }
    if (!baseRunId && runs.length > 0) {
      setBaseRunId(currentAuditRunId ?? selectedRun?.id ?? runs[0]?.id ?? null);
    }
    if (!compareRunId && runs.length > 1) {
      const fallbackCompareId = runs.find((run) => run.id !== (currentAuditRunId ?? selectedRun?.id ?? runs[0]?.id))?.id ?? null;
      setCompareRunId(fallbackCompareId);
    }
  }, [baseRunId, compareRunId, currentAuditRunId, runs, selectedRun]);

  useEffect(() => {
    if (!currentAuditRunId || cachedRuns[currentAuditRunId]) {
      return;
    }
    void loadRunDetail(currentAuditRunId);
  }, [currentAuditRunId, cachedRuns]);

  useEffect(() => {
    const ids = [baseRunId, compareRunId].filter((id): id is string => Boolean(id));
    ids.forEach((runId) => {
      if (!cachedTraces[runId]) {
        void loadTraceDetail(runId);
      }
    });
  }, [baseRunId, compareRunId]);

  const loadRunDetail = async (runId: string) => {
    if (cachedRuns[runId]) {
      return cachedRuns[runId];
    }

    const detail = await onLoadRun(runId);
    if (detail) {
      setCachedRuns((current) => ({
        ...current,
        [detail.id]: detail,
      }));
    }
    return detail;
  };

  const assignRun = async (slot: "base" | "compare", runId: string) => {
    const detail = await loadRunDetail(runId);
    if (!detail) {
      return;
    }

    if (slot === "base") {
      setBaseRunId(runId);
    } else {
      setCompareRunId(runId);
    }
    void loadTraceDetail(runId);
  };

  const baseRun = useMemo(() => {
    if (!baseRunId) {
      return null;
    }
    return cachedRuns[baseRunId] ?? (selectedRun?.id === baseRunId ? selectedRun : null);
  }, [baseRunId, cachedRuns, selectedRun]);

  const compareRun = useMemo(() => {
    if (!compareRunId) {
      return null;
    }
    return cachedRuns[compareRunId] ?? (selectedRun?.id === compareRunId ? selectedRun : null);
  }, [compareRunId, cachedRuns, selectedRun]);

  const baseTrace = baseRunId ? cachedTraces[baseRunId] ?? null : null;
  const compareTrace = compareRunId ? cachedTraces[compareRunId] ?? null : null;

  return (
    <article className="surface-subtle space-y-4 rounded-[24px] p-5">
      <SectionIntro
        badge={`${runs.length} run(s)`}
        badgeVariant="secondary"
        eyebrow="Audit Trail"
        title="История и сравнение запусков"
      />

      {error ? <p className="text-sm leading-6 text-destructive">{error}</p> : null}
      {traceError ? <p className="text-sm leading-6 text-warning">{traceError}</p> : null}

      <div className="rounded-[22px] border border-field-border bg-field px-4 py-4 text-sm leading-6 text-foreground">
        <p>
          Текущий запуск: stack {currentInstructionTrace.length}, scope {formatScopeSummary(currentScope)}, final chunks {currentTrace.finalChunks}, support {supportVerdictLabels[currentTrace.supportVerdict]}.
        </p>
      </div>

      {runs.length === 0 && !currentAuditRunId ? (
        <EmptyState
          description="После первых запросов здесь появится история запусков и сравнение деградаций."
          icon={History}
          title="История запусков пока пуста"
        />
      ) : (
        <>
          <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
            <div className="space-y-3">
              {runs.slice(0, 12).map((run) => {
                const isCurrent = currentAuditRunId === run.id;
                const isBase = baseRunId === run.id;
                const isCompare = compareRunId === run.id;

                return (
                  <article
                    className="w-full rounded-[22px] border border-field-border bg-field px-4 py-4 text-left"
                    key={run.id}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <strong className="text-sm font-semibold text-foreground">{run.model}</strong>
                      <div className="flex flex-wrap items-center gap-2">
                        {isCurrent ? <Badge variant="default">current</Badge> : null}
                        {isBase ? <Badge variant="secondary">base</Badge> : null}
                        {isCompare ? <Badge variant="warning">compare</Badge> : null}
                      </div>
                    </div>
                    <p className="mt-2 text-sm leading-6 text-foreground">{run.promptPreview}</p>
                    <p className="mt-2 text-xs uppercase tracking-[0.14em] text-muted-foreground">
                      {run.mode} · {formatDate(run.createdAt)}
                    </p>
                    <div className="mt-3 flex flex-wrap gap-2">
                      <Button size="sm" type="button" variant={isBase ? "default" : "outline"} onClick={() => void assignRun("base", run.id)}>
                        База
                      </Button>
                      <Button size="sm" type="button" variant={isCompare ? "secondary" : "outline"} onClick={() => void assignRun("compare", run.id)}>
                        Сравнить
                      </Button>
                    </div>
                  </article>
                );
              })}
            </div>

            <div className="space-y-4 rounded-[22px] border border-field-border bg-field px-4 py-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Radar className="h-4 w-4 text-primary" />
                  Compare any two runs
                </div>
                <Badge variant="secondary">
                  {baseRun && compareRun ? `${formatDate(baseRun.createdAt)} vs ${formatDate(compareRun.createdAt)}` : "Выбери 2 запуска"}
                </Badge>
              </div>

              <Separator />

              {baseRun && compareRun ? (
                <div className="space-y-3 text-sm leading-6 text-foreground">
                  {compareRuns(baseRun, compareRun).map((line) => (
                    <p key={line}>{line}</p>
                  ))}
                </div>
              ) : (
                <p className="text-sm leading-6 text-muted-foreground">
                  Назначь слева базовый и сравниваемый запуск, чтобы увидеть diff по модели, answer mode, ревизиям инструкций и chunk ids.
                </p>
              )}
            </div>
          </div>

          <div className="grid gap-4 xl:grid-cols-2">
            {baseRun ? <AuditInspector label="Base run inspector" run={baseRun} /> : null}
            {compareRun ? <AuditInspector label="Compare run inspector" run={compareRun} /> : null}
          </div>

          <div className="grid gap-4 xl:grid-cols-2">
            {baseTrace ? <TraceFoundationInspector trace={baseTrace} /> : null}
            {compareTrace ? <TraceFoundationInspector trace={compareTrace} /> : null}
          </div>
        </>
      )}
    </article>
  );
}
