import type {
  RagProjectInput,
  RagProjectSummary,
  ReferenceProject,
  ReferenceProjectInput,
  ReferenceWorkspace,
  ReferenceWorkspaceInput,
} from "@/types";

export type ReferenceDataController = {
  workspaces: ReferenceWorkspace[];
  projects: ReferenceProject[];
  isLoading: boolean;
  error: string | null;
  actionError: string | null;
  message: string | null;
  reload: () => Promise<unknown>;
  createWorkspace: (input: ReferenceWorkspaceInput) => Promise<ReferenceWorkspace>;
  updateWorkspace: (workspaceKey: string, input: ReferenceWorkspaceInput) => Promise<ReferenceWorkspace>;
  createProject: (input: ReferenceProjectInput) => Promise<ReferenceProject>;
  updateProject: (projectKey: string, input: ReferenceProjectInput) => Promise<ReferenceProject>;
};

export type RagProjectController = {
  projects: RagProjectSummary[];
  isLoading: boolean;
  error: string | null;
  actionError: string | null;
  message: string | null;
  reload: () => Promise<unknown>;
  createProject: (input: RagProjectInput) => Promise<RagProjectSummary>;
  updateProject: (projectKey: string, input: RagProjectInput) => Promise<RagProjectSummary>;
};

export type ReferenceTab = "ragProjects" | "presets" | "facets" | "workspaces" | "projects";

export type WorkspaceFormState = {
  key: string;
  nameRu: string;
  active: boolean;
  sortOrder: string;
  isDefault: boolean;
};

export type RagProjectFormState = {
  key: string;
  name: string;
  description: string;
  active: boolean;
  sortOrder: string;
  isDefault: boolean;
};

export type ProjectFormState = {
  key: string;
  workspaceKey: string;
  nameRu: string;
  active: boolean;
  sortOrder: string;
};

export const initialWorkspaceForm: WorkspaceFormState = {
  key: "",
  nameRu: "",
  active: true,
  sortOrder: "0",
  isDefault: false,
};

export const initialRagProjectForm: RagProjectFormState = {
  key: "",
  name: "",
  description: "",
  active: true,
  sortOrder: "0",
  isDefault: false,
};

export const initialProjectForm: ProjectFormState = {
  key: "",
  workspaceKey: "",
  nameRu: "",
  active: true,
  sortOrder: "0",
};

export const ALL_WORKSPACES_VALUE = "__all__";

export const parseSortOrder = (value: string) => {
  const parsed = Number.parseInt(value.trim() || "0", 10);
  return Number.isFinite(parsed) ? parsed : 0;
};

export const sortWorkspaces = (workspaces: ReferenceWorkspace[]) =>
  [...workspaces].sort((left, right) =>
    left.sortOrder - right.sortOrder || left.nameRu.localeCompare(right.nameRu, "ru"));

export const sortProjects = (projects: ReferenceProject[]) =>
  [...projects].sort((left, right) =>
    left.sortOrder - right.sortOrder || left.nameRu.localeCompare(right.nameRu, "ru"));

export const sortRagProjects = (projects: RagProjectSummary[]) =>
  [...projects].sort((left, right) =>
    left.sortOrder - right.sortOrder || left.name.localeCompare(right.name, "ru"));

export const workspaceToForm = (workspace: ReferenceWorkspace): WorkspaceFormState => ({
  key: workspace.key,
  nameRu: workspace.nameRu,
  active: workspace.active,
  sortOrder: String(workspace.sortOrder),
  isDefault: workspace.isDefault,
});

export const ragProjectToForm = (project: RagProjectSummary): RagProjectFormState => ({
  key: project.key,
  name: project.name,
  description: project.description ?? "",
  active: project.active,
  sortOrder: String(project.sortOrder),
  isDefault: project.isDefault,
});

export const projectToForm = (project: ReferenceProject): ProjectFormState => ({
  key: project.key,
  workspaceKey: project.workspaceKey,
  nameRu: project.nameRu,
  active: project.active,
  sortOrder: String(project.sortOrder),
});

export const buildWorkspaceInput = (form: WorkspaceFormState): ReferenceWorkspaceInput => ({
  key: form.key.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

export const buildRagProjectInput = (form: RagProjectFormState): RagProjectInput => ({
  key: form.key.trim(),
  name: form.name.trim(),
  description: form.description.trim() || null,
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

export const buildRagProjectUpdateInput = (form: RagProjectFormState): RagProjectInput => ({
  name: form.name.trim(),
  description: form.description.trim() || null,
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

export const buildWorkspaceUpdateInput = (form: WorkspaceFormState): ReferenceWorkspaceInput => ({
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

export const buildProjectInput = (form: ProjectFormState): ReferenceProjectInput => ({
  key: form.key.trim(),
  workspaceKey: form.workspaceKey.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
});

export const buildProjectUpdateInput = (form: ProjectFormState): ReferenceProjectInput => ({
  workspaceKey: form.workspaceKey.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
});

export const validateRagProjectForm = (form: RagProjectFormState, mode: "create" | "edit") => {
  if (mode === "create" && !form.key.trim()) {
    return "Укажите ключ RAG-проекта.";
  }
  if (!form.name.trim()) {
    return "Укажите название RAG-проекта.";
  }
  return null;
};

export const validateWorkspaceForm = (form: WorkspaceFormState, mode: "create" | "edit") => {
  if (mode === "create" && !form.key.trim()) {
    return "Укажите ключ рабочей области.";
  }
  if (!form.nameRu.trim()) {
    return "Укажите название рабочей области.";
  }
  return null;
};

export const validateProjectForm = (form: ProjectFormState, mode: "create" | "edit") => {
  if (mode === "create" && !form.key.trim()) {
    return "Укажите ключ проекта.";
  }
  if (!form.workspaceKey.trim()) {
    return "Выберите рабочую область проекта.";
  }
  if (!form.nameRu.trim()) {
    return "Укажите название проекта.";
  }
  return null;
};
