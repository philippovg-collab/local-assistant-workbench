import { useEffect, useMemo, useState } from "react";
import { evalClient } from "@/api/evalClient";
import { translateCommonApiError } from "@/api/errorMessages";
import type { EvalRun, EvalRunItem, EvalRunItemArtifact } from "@/types";

type UseEvalRunDetailOptions = {
  enabled?: boolean;
};

const itemPriority = (item: EvalRunItem) => {
  if (item.status === "FAILED" || item.status === "ERROR") {
    return 0;
  }
  if (item.failureCode || item.failureMessage) {
    return 1;
  }
  if (item.status === "SKIPPED") {
    return 2;
  }
  return 3;
};

const sortRunItems = (items: EvalRunItem[]) =>
  [...items].sort((left, right) => {
    const priorityDelta = itemPriority(left) - itemPriority(right);
    if (priorityDelta !== 0) {
      return priorityDelta;
    }
    return (left.createdAt ?? "").localeCompare(right.createdAt ?? "");
  });

export const useEvalRunDetail = (
  runId: string | null,
  { enabled = true }: UseEvalRunDetailOptions = {},
) => {
  const [run, setRun] = useState<EvalRun | null>(null);
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<EvalRunItemArtifact[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingArtifacts, setIsLoadingArtifacts] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [artifactError, setArtifactError] = useState<string | null>(null);
  const error = artifactError ?? loadError;

  const sortedItems = useMemo(() => {
    return sortRunItems(run?.items ?? []);
  }, [run?.items]);

  const selectedItem = useMemo(
    () => sortedItems.find((item) => item.id === selectedItemId) ?? null,
    [selectedItemId, sortedItems],
  );

  const loadRun = async (signal?: AbortSignal) => {
    if (!enabled || !runId) {
      return null;
    }
    setIsLoading(true);
    try {
      const payload = await evalClient.fetchEvalRun(runId, signal);
      setRun(payload);
      setSelectedItemId((current) => {
        if (current && payload.items?.some((item) => item.id === current)) {
          return current;
        }
        return sortRunItems(payload.items ?? [])[0]?.id ?? null;
      });
      setLoadError(null);
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setLoadError(translateCommonApiError(loadError, "Не удалось загрузить eval run detail"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  };

  const loadArtifacts = async (itemId: string, signal?: AbortSignal) => {
    if (!enabled || !runId) {
      return null;
    }
    setIsLoadingArtifacts(true);
    try {
      const payload = await evalClient.fetchEvalRunItemArtifacts(runId, itemId, signal);
      setArtifacts(payload);
      setArtifactError(null);
      return payload;
    } catch (loadError) {
      if (!signal?.aborted) {
        setArtifactError(translateCommonApiError(loadError, "Не удалось загрузить artifacts eval item"));
      }
      return null;
    } finally {
      if (!signal?.aborted) {
        setIsLoadingArtifacts(false);
      }
    }
  };

  useEffect(() => {
    setRun(null);
    setSelectedItemId(null);
    setArtifacts([]);
    setArtifactError(null);
    if (!enabled || !runId) {
      return;
    }
    const controller = new AbortController();
    void loadRun(controller.signal);
    return () => controller.abort();
  }, [enabled, runId]);

  useEffect(() => {
    setArtifacts([]);
    setArtifactError(null);
    if (!enabled || !runId || !selectedItemId) {
      return;
    }
    const controller = new AbortController();
    void loadArtifacts(selectedItemId, controller.signal);
    return () => controller.abort();
  }, [enabled, runId, selectedItemId]);

  return {
    artifacts,
    artifactError,
    error,
    isLoading,
    isLoadingArtifacts,
    loadError,
    run,
    selectedItem,
    selectedItemId,
    sortedItems,
    loadArtifacts,
    loadRun,
    setSelectedItemId,
  };
};
