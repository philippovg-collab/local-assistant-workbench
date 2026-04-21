import { useEffect, useMemo, useState } from "react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import type {
  ReferenceProject,
  ReferenceProjectInput,
  ReferenceWorkspace,
  ReferenceWorkspaceInput,
} from "@/types";

const translateReferenceError = (error: unknown, fallback: string) =>
  translateCommonApiError(error, fallback);

type UseReferenceDataOptions = {
  enabled?: boolean;
  activeOnly?: boolean;
};

export const useReferenceData = (options: UseReferenceDataOptions = {}) => {
  const { enabled = true, activeOnly = false } = options;
  const [workspaces, setWorkspaces] = useState<ReferenceWorkspace[]>([]);
  const [projects, setProjects] = useState<ReferenceProject[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(enabled);

  const loadReferenceData = async (signal?: AbortSignal) => {
    if (!enabled) {
      setWorkspaces([]);
      setProjects([]);
      setError(null);
      setIsLoading(false);
      return {
        workspaces: [],
        projects: [],
      };
    }

    setIsLoading(true);
    try {
      const [nextWorkspaces, nextProjects] = await Promise.all([
        apiClient.fetchReferenceWorkspaces({ activeOnly }, signal),
        apiClient.fetchReferenceProjects({ activeOnly }, signal),
      ]);
      setWorkspaces(nextWorkspaces);
      setProjects(nextProjects);
      setError(null);
      return {
        workspaces: nextWorkspaces,
        projects: nextProjects,
      };
    } catch (loadError) {
      if (signal?.aborted) {
        return null;
      }
      setError(translateReferenceError(loadError, "Не удалось загрузить справочники"));
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!enabled) {
      setWorkspaces([]);
      setProjects([]);
      setError(null);
      setIsLoading(false);
      return;
    }

    const controller = new AbortController();
    void loadReferenceData(controller.signal);
    return () => controller.abort();
  }, [activeOnly, enabled]);

  const projectsByWorkspace = useMemo(() => {
    const grouped = new Map<string, ReferenceProject[]>();
    projects.forEach((project) => {
      const current = grouped.get(project.workspaceKey) ?? [];
      current.push(project);
      grouped.set(project.workspaceKey, current);
    });
    return grouped;
  }, [projects]);

  const projectsForWorkspace = (workspaceKey: string | null | undefined) => {
    const normalizedWorkspaceKey = workspaceKey?.trim();
    if (!normalizedWorkspaceKey) {
      return [];
    }
    return projectsByWorkspace.get(normalizedWorkspaceKey) ?? [];
  };

  const createWorkspace = async (input: ReferenceWorkspaceInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createReferenceWorkspace(input);
      await loadReferenceData();
      setMessage("Рабочая область сохранена.");
      return created;
    } catch (submissionError) {
      setActionError(translateReferenceError(submissionError, "Не удалось сохранить рабочую область"));
      throw submissionError;
    }
  };

  const updateWorkspace = async (workspaceKey: string, input: ReferenceWorkspaceInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const updated = await apiClient.updateReferenceWorkspace(workspaceKey, input);
      await loadReferenceData();
      setMessage("Рабочая область обновлена.");
      return updated;
    } catch (submissionError) {
      setActionError(translateReferenceError(submissionError, "Не удалось обновить рабочую область"));
      throw submissionError;
    }
  };

  const createProject = async (input: ReferenceProjectInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const created = await apiClient.createReferenceProject(input);
      await loadReferenceData();
      setMessage("Проект сохранён.");
      return created;
    } catch (submissionError) {
      setActionError(translateReferenceError(submissionError, "Не удалось сохранить проект"));
      throw submissionError;
    }
  };

  const updateProject = async (projectKey: string, input: ReferenceProjectInput) => {
    setActionError(null);
    setMessage(null);
    try {
      const updated = await apiClient.updateReferenceProject(projectKey, input);
      await loadReferenceData();
      setMessage("Проект обновлён.");
      return updated;
    } catch (submissionError) {
      setActionError(translateReferenceError(submissionError, "Не удалось обновить проект"));
      throw submissionError;
    }
  };

  return {
    workspaces,
    projects,
    error,
    actionError,
    message,
    isLoading,
    reload: loadReferenceData,
    loadReferenceData,
    projectsForWorkspace,
    createWorkspace,
    updateWorkspace,
    createProject,
    updateProject,
  };
};
