import { useEffect, useMemo, useRef, useState } from "react";
import { evalClient } from "@/api/evalClient";
import { translateCommonApiError } from "@/api/errorMessages";
import type {
  CorpusSnapshot,
  CreateE2EEvalRunRequest,
  CreateRetrievalEvalRunRequest,
  EvalRun,
  ResolveEvalExecutionConfigRequest,
} from "@/types";

const EVAL_RUN_POLL_INTERVAL_MS = 2000;

type UseEvalRunsOptions = {
  enabled?: boolean;
  pollingEnabled?: boolean;
};

const isActiveRun = (run: EvalRun) => run.status === "QUEUED" || run.status === "RUNNING";

export const useEvalRuns = ({ enabled = true, pollingEnabled = true }: UseEvalRunsOptions = {}) => {
  const [runs, setRuns] = useState<EvalRun[]>([]);
  const [snapshots, setSnapshots] = useState<CorpusSnapshot[]>([]);
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [resolvedConfigHash, setResolvedConfigHash] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingSnapshots, setIsLoadingSnapshots] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const loadedRef = useRef(false);
  const pollingRequestActiveRef = useRef(false);
  const error = mutationError ?? loadError;

  const selectedRun = useMemo(
    () => runs.find((run) => run.id === selectedRunId) ?? null,
    [runs, selectedRunId],
  );
  const hasActiveRuns = runs.some(isActiveRun);

  const loadRuns = async (signal?: AbortSignal) => {
    if (!enabled) {
      return null;
    }
    setIsLoading(true);
    try {
      const payload = await evalClient.fetchEvalRuns(signal);
      setRuns(payload);
      setSelectedRunId((current) => {
        if (current && payload.some((run) => run.id === current)) {
          return current;
        }
        return payload[0]?.id ?? null;
      });
      setLoadError(null);
      loadedRef.current = true;
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setLoadError(translateCommonApiError(loadError, "Не удалось загрузить запуски оценки"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  };

  const loadSnapshots = async (signal?: AbortSignal) => {
    if (!enabled) {
      return null;
    }
    setIsLoadingSnapshots(true);
    try {
      const payload = await evalClient.fetchEvalSnapshots(signal);
      setSnapshots(payload);
      setLoadError(null);
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setLoadError(translateCommonApiError(loadError, "Не удалось загрузить снимки корпуса"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoadingSnapshots(false);
      }
    }
  };

  useEffect(() => {
    if (!enabled || loadedRef.current) {
      return;
    }
    const controller = new AbortController();
    void loadRuns(controller.signal);
    void loadSnapshots(controller.signal);
    return () => controller.abort();
  }, [enabled]);

  useEffect(() => {
    if (!enabled || !pollingEnabled || !hasActiveRuns) {
      return;
    }
    const controller = new AbortController();
    const intervalId = window.setInterval(() => {
      if (pollingRequestActiveRef.current) {
        return;
      }
      pollingRequestActiveRef.current = true;
      void loadRuns(controller.signal).finally(() => {
        pollingRequestActiveRef.current = false;
      });
    }, EVAL_RUN_POLL_INTERVAL_MS);
    return () => {
      controller.abort();
      pollingRequestActiveRef.current = false;
      window.clearInterval(intervalId);
    };
  }, [enabled, hasActiveRuns, pollingEnabled]);

  const mutateRun = async <T extends EvalRun | null>(
    operation: () => Promise<T>,
    fallbackMessage: string,
  ) => {
    if (!enabled) {
      return null;
    }
    setIsMutating(true);
    setMutationError(null);
    try {
      const result = await operation();
      await loadRuns();
      if (result?.id) {
        setSelectedRunId(result.id);
      }
      return result;
    } catch (mutationError) {
      setMutationError(translateCommonApiError(mutationError, fallbackMessage));
      return null;
    } finally {
      setIsMutating(false);
    }
  };

  const startRetrievalRun = (input: CreateRetrievalEvalRunRequest) =>
    mutateRun(() => evalClient.createRetrievalEvalRun(input), "Не удалось запустить retrieval-оценку");

  const startE2ERun = (input: CreateE2EEvalRunRequest) =>
    mutateRun(() => evalClient.createE2EEvalRun(input), "Не удалось запустить E2E-оценку");

  const reconcileRun = (runId: string) =>
    mutateRun(() => evalClient.reconcileEvalRun(runId), "Не удалось reconcile запуск оценки");

  const resolveExecutionConfig = async (input: ResolveEvalExecutionConfigRequest = {}) => {
    if (!enabled) {
      return null;
    }
    setIsMutating(true);
    setMutationError(null);
    try {
      const result = await evalClient.resolveEvalExecutionConfig(input);
      setResolvedConfigHash(result.configHash ?? result.executionConfig?.configHash ?? null);
      return result;
    } catch (resolveError) {
      setMutationError(translateCommonApiError(resolveError, "Не удалось рассчитать execution config"));
      return null;
    } finally {
      setIsMutating(false);
    }
  };

  return {
    error,
    hasActiveRuns,
    isLoading,
    isLoadingSnapshots,
    isMutating,
    loadError,
    mutationError,
    resolvedConfigHash,
    runs,
    selectedRun,
    selectedRunId,
    snapshots,
    loadRuns,
    loadSnapshots,
    reconcileRun,
    resolveExecutionConfig,
    setResolvedConfigHash,
    setSelectedRunId,
    startE2ERun,
    startRetrievalRun,
  };
};
