import { useMemo, useState, type FormEvent } from "react";
import type { RagProjectInput, RagProjectSummary, ReferenceProject, ReferenceWorkspace } from "@/types";
import {
  ALL_WORKSPACES_VALUE,
  buildFallbackRagProjectController,
  buildProjectInput,
  buildProjectUpdateInput,
  buildRagProjectInput,
  buildRagProjectUpdateInput,
  buildWorkspaceInput,
  buildWorkspaceUpdateInput,
  initialProjectForm,
  initialRagProjectForm,
  initialWorkspaceForm,
  projectToForm,
  ragProjectToForm,
  sortProjects,
  sortRagProjects,
  sortWorkspaces,
  validateProjectForm,
  validateRagProjectForm,
  validateWorkspaceForm,
  workspaceToForm,
  type ProjectFormState,
  type RagProjectController,
  type RagProjectFormState,
  type ReferenceDataController,
  type ReferenceTab,
  type WorkspaceFormState,
} from "@/components/referenceDataPresentation";

type UseReferenceDataPanelStateParams = {
  referenceData: ReferenceDataController;
  ragProjectsController?: RagProjectController;
  activeRagProjectKey: string | null;
  onActiveRagProjectChange: (projectKey: string) => void;
};

export function useReferenceDataPanelState({
  referenceData,
  ragProjectsController,
  activeRagProjectKey,
  onActiveRagProjectChange,
}: UseReferenceDataPanelStateParams) {
  const ragProjects = ragProjectsController ?? buildFallbackRagProjectController(referenceData);
  const [activeTab, setActiveTab] = useState<ReferenceTab>("ragProjects");
  const [ragProjectCreateForm, setRagProjectCreateForm] = useState<RagProjectFormState>(initialRagProjectForm);
  const [editingRagProjectKey, setEditingRagProjectKey] = useState<string | null>(null);
  const [ragProjectEditForm, setRagProjectEditForm] = useState<RagProjectFormState>(initialRagProjectForm);
  const [ragProjectFormError, setRagProjectFormError] = useState<string | null>(null);
  const [workspaceCreateForm, setWorkspaceCreateForm] = useState<WorkspaceFormState>(initialWorkspaceForm);
  const [projectCreateForm, setProjectCreateForm] = useState<ProjectFormState>(initialProjectForm);
  const [editingWorkspaceKey, setEditingWorkspaceKey] = useState<string | null>(null);
  const [workspaceEditForm, setWorkspaceEditForm] = useState<WorkspaceFormState>(initialWorkspaceForm);
  const [editingProjectKey, setEditingProjectKey] = useState<string | null>(null);
  const [projectEditForm, setProjectEditForm] = useState<ProjectFormState>(initialProjectForm);
  const [projectWorkspaceFilter, setProjectWorkspaceFilter] = useState(ALL_WORKSPACES_VALUE);
  const [workspaceFormError, setWorkspaceFormError] = useState<string | null>(null);
  const [projectFormError, setProjectFormError] = useState<string | null>(null);
  const [submittingAction, setSubmittingAction] = useState<string | null>(null);

  const workspaces = useMemo(() => sortWorkspaces(referenceData.workspaces), [referenceData.workspaces]);
  const projects = useMemo(() => sortProjects(referenceData.projects), [referenceData.projects]);
  const workspacesByKey = useMemo(
    () => new Map(workspaces.map((workspace) => [workspace.key, workspace])),
    [workspaces],
  );
  const visibleWorkspaceKeys = useMemo(
    () =>
      projectWorkspaceFilter === ALL_WORKSPACES_VALUE
        ? workspaces.map((workspace) => workspace.key)
        : [projectWorkspaceFilter],
    [projectWorkspaceFilter, workspaces],
  );
  const visibleProjectCount = useMemo(
    () => projects.filter((project) => visibleWorkspaceKeys.includes(project.workspaceKey)).length,
    [projects, visibleWorkspaceKeys],
  );
  const sortedRagProjects = useMemo(
    () => sortRagProjects(ragProjects.projects),
    [ragProjects.projects],
  );
  const activeRagProjects = useMemo(
    () => sortedRagProjects.filter((project) => project.active),
    [sortedRagProjects],
  );
  const activeRagProject = useMemo(
    () =>
      activeRagProjects.find((project) => project.key === activeRagProjectKey)
      ?? activeRagProjects.find((project) => project.isDefault)
      ?? activeRagProjects[0]
      ?? null,
    [activeRagProjectKey, activeRagProjects],
  );
  const resolvedActiveRagProjectKey = activeRagProject?.key ?? activeRagProjectKey ?? "general";
  const isReferenceDirectoryTab = activeTab !== "presets" && activeTab !== "facets";

  const createAndSelectRagProject = async (input: RagProjectInput) => {
    const created = await ragProjects.createProject(input);
    onActiveRagProjectChange(created.key);
    void referenceData.reload();
    return created;
  };

  const handleCreateRagProject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateRagProjectForm(ragProjectCreateForm, "create");
    setRagProjectFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction("create-rag-project");
    try {
      const created = await ragProjects.createProject(buildRagProjectInput(ragProjectCreateForm));
      onActiveRagProjectChange(created.key);
      setRagProjectCreateForm(initialRagProjectForm);
      void referenceData.reload();
    } catch {
      // useRagProjects exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const handleSaveRagProject = async () => {
    if (!editingRagProjectKey) {
      return;
    }
    const validationError = validateRagProjectForm(ragProjectEditForm, "edit");
    setRagProjectFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction(`update-rag-project-${editingRagProjectKey}`);
    try {
      const updated = await ragProjects.updateProject(
        editingRagProjectKey,
        buildRagProjectUpdateInput(ragProjectEditForm),
      );
      if (updated.active) {
        onActiveRagProjectChange(updated.key);
      }
      setEditingRagProjectKey(null);
      void referenceData.reload();
    } catch {
      // useRagProjects exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const handleCreateWorkspace = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateWorkspaceForm(workspaceCreateForm, "create");
    setWorkspaceFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction("create-workspace");
    try {
      await referenceData.createWorkspace(buildWorkspaceInput(workspaceCreateForm));
      setWorkspaceCreateForm(initialWorkspaceForm);
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const handleSaveWorkspace = async () => {
    if (!editingWorkspaceKey) {
      return;
    }
    const validationError = validateWorkspaceForm(workspaceEditForm, "edit");
    setWorkspaceFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction(`update-workspace-${editingWorkspaceKey}`);
    try {
      await referenceData.updateWorkspace(editingWorkspaceKey, buildWorkspaceUpdateInput(workspaceEditForm));
      setEditingWorkspaceKey(null);
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const handleCreateProject = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const validationError = validateProjectForm(projectCreateForm, "create");
    setProjectFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction("create-project");
    try {
      await referenceData.createProject(buildProjectInput(projectCreateForm));
      setProjectCreateForm(initialProjectForm);
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const handleSaveProject = async () => {
    if (!editingProjectKey) {
      return;
    }
    const validationError = validateProjectForm(projectEditForm, "edit");
    setProjectFormError(validationError);
    if (validationError) {
      return;
    }

    setSubmittingAction(`update-project-${editingProjectKey}`);
    try {
      await referenceData.updateProject(editingProjectKey, buildProjectUpdateInput(projectEditForm));
      setEditingProjectKey(null);
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const startWorkspaceEdit = (workspace: ReferenceWorkspace) => {
    setWorkspaceFormError(null);
    setEditingWorkspaceKey(workspace.key);
    setWorkspaceEditForm(workspaceToForm(workspace));
  };

  const startRagProjectEdit = (project: RagProjectSummary) => {
    setRagProjectFormError(null);
    setEditingRagProjectKey(project.key);
    setRagProjectEditForm(ragProjectToForm(project));
  };

  const startProjectEdit = (project: ReferenceProject) => {
    setProjectFormError(null);
    setEditingProjectKey(project.key);
    setProjectEditForm(projectToForm(project));
  };

  const toggleWorkspaceActive = async (workspace: ReferenceWorkspace) => {
    setSubmittingAction(`toggle-workspace-${workspace.key}`);
    try {
      await referenceData.updateWorkspace(workspace.key, {
        nameRu: workspace.nameRu,
        active: !workspace.active,
        sortOrder: workspace.sortOrder,
        isDefault: workspace.isDefault,
      });
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const makeWorkspaceDefault = async (workspace: ReferenceWorkspace) => {
    setSubmittingAction(`default-workspace-${workspace.key}`);
    try {
      await referenceData.updateWorkspace(workspace.key, {
        nameRu: workspace.nameRu,
        active: workspace.active,
        sortOrder: workspace.sortOrder,
        isDefault: true,
      });
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const toggleRagProjectActive = async (project: RagProjectSummary) => {
    setSubmittingAction(`toggle-rag-project-${project.key}`);
    try {
      const updated = await ragProjects.updateProject(project.key, {
        name: project.name,
        description: project.description,
        active: !project.active,
        sortOrder: project.sortOrder,
        isDefault: project.isDefault,
      });
      if (updated.active) {
        onActiveRagProjectChange(updated.key);
      }
      void referenceData.reload();
    } catch {
      // useRagProjects exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const makeRagProjectDefault = async (project: RagProjectSummary) => {
    setSubmittingAction(`default-rag-project-${project.key}`);
    try {
      const updated = await ragProjects.updateProject(project.key, {
        name: project.name,
        description: project.description,
        active: project.active,
        sortOrder: project.sortOrder,
        isDefault: true,
      });
      onActiveRagProjectChange(updated.key);
      void referenceData.reload();
    } catch {
      // useRagProjects exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  const toggleProjectActive = async (project: ReferenceProject) => {
    setSubmittingAction(`toggle-project-${project.key}`);
    try {
      await referenceData.updateProject(project.key, {
        workspaceKey: project.workspaceKey,
        nameRu: project.nameRu,
        active: !project.active,
        sortOrder: project.sortOrder,
      });
    } catch {
      // useReferenceData exposes actionError.
    } finally {
      setSubmittingAction(null);
    }
  };

  return {
    activeTab,
    setActiveTab,
    ragProjects,
    workspaces,
    projects,
    workspacesByKey,
    visibleWorkspaceKeys,
    visibleProjectCount,
    sortedRagProjects,
    activeRagProject,
    resolvedActiveRagProjectKey,
    isReferenceDirectoryTab,
    ragProjectCreateForm,
    setRagProjectCreateForm,
    editingRagProjectKey,
    setEditingRagProjectKey,
    ragProjectEditForm,
    setRagProjectEditForm,
    ragProjectFormError,
    workspaceCreateForm,
    setWorkspaceCreateForm,
    projectCreateForm,
    setProjectCreateForm,
    editingWorkspaceKey,
    setEditingWorkspaceKey,
    workspaceEditForm,
    setWorkspaceEditForm,
    editingProjectKey,
    setEditingProjectKey,
    projectEditForm,
    setProjectEditForm,
    projectWorkspaceFilter,
    setProjectWorkspaceFilter,
    workspaceFormError,
    projectFormError,
    submittingAction,
    createAndSelectRagProject,
    handleCreateRagProject,
    handleSaveRagProject,
    handleCreateWorkspace,
    handleSaveWorkspace,
    handleCreateProject,
    handleSaveProject,
    startWorkspaceEdit,
    startRagProjectEdit,
    startProjectEdit,
    toggleWorkspaceActive,
    makeWorkspaceDefault,
    toggleRagProjectActive,
    makeRagProjectDefault,
    toggleProjectActive,
  };
}

export type ReferenceDataPanelState = ReturnType<typeof useReferenceDataPanelState>;
