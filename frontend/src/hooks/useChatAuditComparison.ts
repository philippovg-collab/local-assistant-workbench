import { useEffect, useMemo, useState } from "react";
import { apiClient } from "@/api/client";
import type {
  ChatAuditRunDetail,
  ChatAuditRunSummary,
  ChatRunTraceDetail,
} from "@/types";

type UseChatAuditComparisonOptions = {
  runs: ChatAuditRunSummary[];
  selectedRun: ChatAuditRunDetail | null;
  currentAuditRunId?: string | null;
  onLoadRun: (runId: string) => Promise<ChatAuditRunDetail | null>;
};

export const useChatAuditComparison = ({
  runs,
  selectedRun,
  currentAuditRunId,
  onLoadRun,
}: UseChatAuditComparisonOptions) => {
  const [cachedRuns, setCachedRuns] = useState<Record<string, ChatAuditRunDetail>>({});
  const [cachedTraces, setCachedTraces] = useState<Record<string, ChatRunTraceDetail>>({});
  const [traceError, setTraceError] = useState<string | null>(null);
  const [baseRunId, setBaseRunId] = useState<string | null>(null);
  const [compareRunId, setCompareRunId] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedRun) {
      return;
    }
    setCachedRuns((current) => ({
      ...current,
      [selectedRun.id]: selectedRun,
    }));
  }, [selectedRun]);

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

  return {
    traceError,
    baseRunId,
    compareRunId,
    baseRun,
    compareRun,
    baseTrace,
    compareTrace,
    assignRun,
  };
};
