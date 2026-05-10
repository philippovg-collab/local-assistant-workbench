import { useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import type {
  MemoryEntryRequest,
  MemoryEntryResponse,
  MemoryEntryStatus,
  MemoryEntryType,
  MemoryEntryUpdateRequest,
  MemoryReviewActionRequest,
} from "@/types";

type UseMemoryEntriesOptions = {
  enabled?: boolean;
  workspaceKey?: string | null;
};

export const useMemoryEntries = ({ enabled = true, workspaceKey = null }: UseMemoryEntriesOptions = {}) => {
  const [entries, setEntries] = useState<MemoryEntryResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<MemoryEntryStatus | "all">("pending_review");
  const [typeFilter, setTypeFilter] = useState<MemoryEntryType | "all">("all");
  const [projectKeyFilter, setProjectKeyFilter] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const requestFilters = useMemo(() => ({
    status: statusFilter === "all" ? null : statusFilter,
    type: typeFilter === "all" ? null : typeFilter,
    workspaceKey: workspaceKey?.trim() || null,
    projectKey: projectKeyFilter.trim() || null,
  }), [projectKeyFilter, statusFilter, typeFilter, workspaceKey]);

  const loadEntries = useCallback(async () => {
    if (!enabled) {
      return;
    }
    const controller = new AbortController();
    setIsLoading(true);
    setError(null);
    try {
      const result = await apiClient.fetchMemoryEntries(requestFilters, controller.signal);
      setEntries(result);
    } catch (loadError) {
      setError(translateCommonApiError(loadError, "Не удалось загрузить память"));
    } finally {
      setIsLoading(false);
    }
  }, [enabled, requestFilters]);

  useEffect(() => {
    void loadEntries();
  }, [loadEntries]);

  useEffect(() => {
    if (enabled) {
      return;
    }
    setEntries([]);
    setIsLoading(false);
    setIsMutating(false);
    setError(null);
  }, [enabled]);

  const mutate = useCallback(async <T,>(operation: () => Promise<T>) => {
    if (!enabled) {
      return null;
    }
    setIsMutating(true);
    setError(null);
    try {
      const result = await operation();
      await loadEntries();
      return result;
    } catch (mutationError) {
      setError(translateCommonApiError(mutationError, "Не удалось обновить память"));
      return null;
    } finally {
      setIsMutating(false);
    }
  }, [enabled, loadEntries]);

  const createEntry = useCallback((input: MemoryEntryRequest) =>
    mutate(() => apiClient.createMemoryEntry(input)), [mutate]);

  const updateEntry = useCallback((entryId: string, input: MemoryEntryUpdateRequest) =>
    mutate(() => apiClient.updateMemoryEntry(entryId, input)), [mutate]);

  const approveEntry = useCallback((entryId: string, input: MemoryReviewActionRequest = {}) =>
    mutate(() => apiClient.approveMemoryEntry(entryId, input)), [mutate]);

  const rejectEntry = useCallback((entryId: string, input: MemoryReviewActionRequest = {}) =>
    mutate(() => apiClient.rejectMemoryEntry(entryId, input)), [mutate]);

  const pinEntry = useCallback((entryId: string, input: MemoryReviewActionRequest = {}) =>
    mutate(() => apiClient.pinMemoryEntry(entryId, input)), [mutate]);

  const unpinEntry = useCallback((entryId: string, input: MemoryReviewActionRequest = {}) =>
    mutate(() => apiClient.unpinMemoryEntry(entryId, input)), [mutate]);

  const deleteEntry = useCallback((entryId: string, input: MemoryReviewActionRequest = {}) =>
    mutate(() => apiClient.deleteMemoryEntry(entryId, input)), [mutate]);

  return {
    entries,
    statusFilter,
    setStatusFilter,
    typeFilter,
    setTypeFilter,
    projectKeyFilter,
    setProjectKeyFilter,
    isLoading,
    isMutating,
    error,
    loadEntries,
    createEntry,
    updateEntry,
    approveEntry,
    rejectEntry,
    pinEntry,
    unpinEntry,
    deleteEntry,
  };
};
