import { useEffect, useState } from "react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import type { RagProjectInput, RagProjectSummary } from "@/types";

export const useRagProjects = (options: { activeOnly?: boolean } = {}) => {
  const { activeOnly = false } = options;
  const [projects, setProjects] = useState<RagProjectSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loadProjects = async (signal?: AbortSignal) => {
    setIsLoading(true);
    try {
      const payload = await apiClient.fetchRagProjects({ activeOnly }, signal);
      setProjects(payload);
      setError(null);
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
    const controller = new AbortController();
    void loadProjects(controller.signal);
    return () => controller.abort();
  }, [activeOnly]);

  const createProject = async (input: RagProjectInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createRagProject(input);
      await loadProjects();
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
      await loadProjects();
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
    reload: loadProjects,
    createProject,
    updateProject,
  };
};
