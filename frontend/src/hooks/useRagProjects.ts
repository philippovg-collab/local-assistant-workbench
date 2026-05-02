import { useEffect, useRef, useState } from "react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import type { RagProjectInput, RagProjectSummary } from "@/types";

type UseRagProjectsOptions = {
  activeOnly?: boolean;
  enabled?: boolean;
};

export const useRagProjects = (options: UseRagProjectsOptions = {}) => {
  const { activeOnly = false, enabled = true } = options;
  const [projects, setProjects] = useState<RagProjectSummary[]>([]);
  const [isLoading, setIsLoading] = useState(enabled);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const loadedParamsRef = useRef<string | null>(null);
  const paramsKey = JSON.stringify({ activeOnly });

  const loadProjects = async (signal?: AbortSignal, options: { force?: boolean } = {}) => {
    if (!enabled && !options.force) {
      setIsLoading(false);
      return null;
    }

    setIsLoading(true);
    try {
      const payload = await apiClient.fetchRagProjects({ activeOnly }, signal);
      setProjects(payload);
      setError(null);
      loadedParamsRef.current = paramsKey;
      return payload;
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setError(translateCommonApiError(loadError, "Не удалось загрузить RAG-проекты"));
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }
    if (loadedParamsRef.current === paramsKey) {
      return;
    }

    const controller = new AbortController();
    void loadProjects(controller.signal);
    return () => controller.abort();
  }, [activeOnly, enabled, paramsKey]);

  const createProject = async (input: RagProjectInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createRagProject(input);
      await loadProjects(undefined, { force: true });
      setMessage("RAG-проект создан.");
      return created;
    } catch (submissionError) {
      setActionError(translateCommonApiError(submissionError, "Не удалось создать RAG-проект"));
      throw submissionError;
    }
  };

  const updateProject = async (projectKey: string, input: RagProjectInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const updated = await apiClient.updateRagProject(projectKey, input);
      await loadProjects(undefined, { force: true });
      setMessage("RAG-проект обновлён.");
      return updated;
    } catch (submissionError) {
      setActionError(translateCommonApiError(submissionError, "Не удалось обновить RAG-проект"));
      throw submissionError;
    }
  };

  return {
    projects,
    isLoading,
    error,
    actionError,
    message,
    reload: (signal?: AbortSignal) => loadProjects(signal, { force: true }),
    createProject,
    updateProject,
  };
};
