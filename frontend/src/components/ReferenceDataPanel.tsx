import { useMemo, useState, type FormEvent } from "react";
import { CheckCircle2, Database, Pencil, PlusCircle, RefreshCw, RotateCcw, XCircle } from "lucide-react";
import { EmptyState } from "@/components/app/EmptyState";
import { SectionIntro } from "@/components/app/SectionIntro";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type {
  ReferenceProject,
  ReferenceProjectInput,
  ReferenceWorkspace,
  ReferenceWorkspaceInput,
} from "@/types";

type ReferenceDataController = {
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

type ReferenceDataPanelProps = {
  referenceData: ReferenceDataController;
};

type ReferenceTab = "workspaces" | "projects";

type WorkspaceFormState = {
  key: string;
  nameRu: string;
  active: boolean;
  sortOrder: string;
  isDefault: boolean;
};

type ProjectFormState = {
  key: string;
  workspaceKey: string;
  nameRu: string;
  active: boolean;
  sortOrder: string;
};

const initialWorkspaceForm: WorkspaceFormState = {
  key: "",
  nameRu: "",
  active: true,
  sortOrder: "0",
  isDefault: false,
};

const initialProjectForm: ProjectFormState = {
  key: "",
  workspaceKey: "",
  nameRu: "",
  active: true,
  sortOrder: "0",
};

const ALL_WORKSPACES_VALUE = "__all__";

const parseSortOrder = (value: string) => {
  const parsed = Number.parseInt(value.trim() || "0", 10);
  return Number.isFinite(parsed) ? parsed : 0;
};

const sortWorkspaces = (workspaces: ReferenceWorkspace[]) =>
  [...workspaces].sort((left, right) =>
    left.sortOrder - right.sortOrder || left.nameRu.localeCompare(right.nameRu, "ru"));

const sortProjects = (projects: ReferenceProject[]) =>
  [...projects].sort((left, right) =>
    left.sortOrder - right.sortOrder || left.nameRu.localeCompare(right.nameRu, "ru"));

const workspaceToForm = (workspace: ReferenceWorkspace): WorkspaceFormState => ({
  key: workspace.key,
  nameRu: workspace.nameRu,
  active: workspace.active,
  sortOrder: String(workspace.sortOrder),
  isDefault: workspace.isDefault,
});

const projectToForm = (project: ReferenceProject): ProjectFormState => ({
  key: project.key,
  workspaceKey: project.workspaceKey,
  nameRu: project.nameRu,
  active: project.active,
  sortOrder: String(project.sortOrder),
});

const buildWorkspaceInput = (form: WorkspaceFormState): ReferenceWorkspaceInput => ({
  key: form.key.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

const buildWorkspaceUpdateInput = (form: WorkspaceFormState): ReferenceWorkspaceInput => ({
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
  isDefault: form.isDefault,
});

const buildProjectInput = (form: ProjectFormState): ReferenceProjectInput => ({
  key: form.key.trim(),
  workspaceKey: form.workspaceKey.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
});

const buildProjectUpdateInput = (form: ProjectFormState): ReferenceProjectInput => ({
  workspaceKey: form.workspaceKey.trim(),
  nameRu: form.nameRu.trim(),
  active: form.active,
  sortOrder: parseSortOrder(form.sortOrder),
});

export function ReferenceDataPanel({ referenceData }: ReferenceDataPanelProps) {
  const [activeTab, setActiveTab] = useState<ReferenceTab>("workspaces");
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

  const validateWorkspaceForm = (form: WorkspaceFormState, mode: "create" | "edit") => {
    if (mode === "create" && !form.key.trim()) {
      return "Укажите ключ рабочей области.";
    }
    if (!form.nameRu.trim()) {
      return "Укажите название рабочей области.";
    }
    return null;
  };

  const validateProjectForm = (form: ProjectFormState, mode: "create" | "edit") => {
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

  return (
    <div className="space-y-6">
      <SectionIntro
        actions={(
          <Button disabled={referenceData.isLoading} type="button" variant="secondary" onClick={() => void referenceData.reload()}>
            <RefreshCw className="h-4 w-4" />
            Обновить
          </Button>
        )}
        badge="/api/reference"
        badgeVariant="default"
        description="Управляйте рабочими областями и проектами, которые затем используются в Materials и других разделах."
        eyebrow="Справочники"
        title="Рабочие области и проекты"
      />

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant={activeTab === "workspaces" ? "default" : "secondary"}
          onClick={() => setActiveTab("workspaces")}
        >
          <Database className="h-4 w-4" />
          Рабочие области
        </Button>
        <Button
          type="button"
          variant={activeTab === "projects" ? "default" : "secondary"}
          onClick={() => setActiveTab("projects")}
        >
          <Database className="h-4 w-4" />
          Проекты
        </Button>
      </div>

      {referenceData.message ? (
        <Alert variant="success">
          <AlertTitle>Справочник обновлён</AlertTitle>
          <AlertDescription>{referenceData.message}</AlertDescription>
        </Alert>
      ) : null}

      {referenceData.actionError ? (
        <Alert variant="destructive">
          <AlertTitle>Операция не выполнена</AlertTitle>
          <AlertDescription>{referenceData.actionError}</AlertDescription>
        </Alert>
      ) : null}

      {referenceData.error ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить справочники</AlertTitle>
          <AlertDescription>{referenceData.error}</AlertDescription>
        </Alert>
      ) : null}

      {activeTab === "workspaces" ? (
        <section className="space-y-5" aria-label="Рабочие области">
          <form className="surface-panel rounded-[28px] border p-5" onSubmit={handleCreateWorkspace}>
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-foreground">
              <PlusCircle className="h-4 w-4 text-primary" />
              Создать рабочую область
            </div>
            <div className="grid gap-4 lg:grid-cols-[minmax(180px,0.8fr)_minmax(220px,1fr)_120px_auto_auto] lg:items-end">
              <div className="space-y-2">
                <Label htmlFor="reference-workspace-key">Ключ рабочей области</Label>
                <Input
                  id="reference-workspace-key"
                  placeholder="north-upgrade"
                  value={workspaceCreateForm.key}
                  onChange={(event) => setWorkspaceCreateForm((current) => ({ ...current, key: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reference-workspace-name">Название рабочей области</Label>
                <Input
                  id="reference-workspace-name"
                  placeholder="Северная модернизация"
                  value={workspaceCreateForm.nameRu}
                  onChange={(event) => setWorkspaceCreateForm((current) => ({ ...current, nameRu: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reference-workspace-sort">Порядок</Label>
                <Input
                  id="reference-workspace-sort"
                  inputMode="numeric"
                  value={workspaceCreateForm.sortOrder}
                  onChange={(event) => setWorkspaceCreateForm((current) => ({ ...current, sortOrder: event.target.value }))}
                />
              </div>
              <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Checkbox
                  checked={workspaceCreateForm.active}
                  onCheckedChange={(checked) => setWorkspaceCreateForm((current) => ({ ...current, active: checked === true }))}
                />
                Активна
              </label>
              <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Checkbox
                  checked={workspaceCreateForm.isDefault}
                  onCheckedChange={(checked) => setWorkspaceCreateForm((current) => ({ ...current, isDefault: checked === true }))}
                />
                Основная
              </label>
            </div>
            {workspaceFormError ? <p className="mt-3 text-sm text-destructive">{workspaceFormError}</p> : null}
            <Button className="mt-4" disabled={submittingAction === "create-workspace"} type="submit">
              <PlusCircle className="h-4 w-4" />
              Создать рабочую область
            </Button>
          </form>

          {referenceData.isLoading && workspaces.length === 0 ? (
            <EmptyState title="Загружаем рабочие области" description="Список появится после ответа backend." />
          ) : workspaces.length === 0 ? (
            <EmptyState title="Рабочих областей пока нет" description="Создайте первую рабочую область для материалов." />
          ) : (
            <div className="grid gap-4">
              {workspaces.map((workspace) => {
                const isEditing = editingWorkspaceKey === workspace.key;
                return (
                  <article className="rounded-[24px] border bg-card p-5 shadow-soft" key={workspace.key}>
                    {isEditing ? (
                      <div className="space-y-4">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant="secondary">key: {workspace.key}</Badge>
                          <Badge variant={workspaceEditForm.active ? "success" : "warning"}>
                            {workspaceEditForm.active ? "активна" : "неактивна"}
                          </Badge>
                        </div>
                        <div className="grid gap-4 md:grid-cols-[minmax(220px,1fr)_120px_auto_auto] md:items-end">
                          <div className="space-y-2">
                            <Label htmlFor={`workspace-name-${workspace.key}`}>Название рабочей области</Label>
                            <Input
                              id={`workspace-name-${workspace.key}`}
                              value={workspaceEditForm.nameRu}
                              onChange={(event) => setWorkspaceEditForm((current) => ({ ...current, nameRu: event.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor={`workspace-sort-${workspace.key}`}>Порядок</Label>
                            <Input
                              id={`workspace-sort-${workspace.key}`}
                              inputMode="numeric"
                              value={workspaceEditForm.sortOrder}
                              onChange={(event) => setWorkspaceEditForm((current) => ({ ...current, sortOrder: event.target.value }))}
                            />
                          </div>
                          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                            <Checkbox
                              checked={workspaceEditForm.active}
                              onCheckedChange={(checked) => setWorkspaceEditForm((current) => ({ ...current, active: checked === true }))}
                            />
                            Активна
                          </label>
                          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                            <Checkbox
                              checked={workspaceEditForm.isDefault}
                              onCheckedChange={(checked) => setWorkspaceEditForm((current) => ({ ...current, isDefault: checked === true }))}
                            />
                            Основная
                          </label>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Button disabled={submittingAction === `update-workspace-${workspace.key}`} type="button" onClick={() => void handleSaveWorkspace()}>
                            <CheckCircle2 className="h-4 w-4" />
                            Сохранить
                          </Button>
                          <Button type="button" variant="secondary" onClick={() => setEditingWorkspaceKey(null)}>
                            Отмена
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                        <div className="space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">{workspace.nameRu}</h3>
                            <Badge variant={workspace.active ? "success" : "warning"}>
                              {workspace.active ? "активна" : "неактивна"}
                            </Badge>
                            {workspace.isDefault ? <Badge variant="default">основная</Badge> : null}
                          </div>
                          <p className="text-sm text-muted-foreground">key: {workspace.key}</p>
                          <p className="text-sm text-muted-foreground">Порядок: {workspace.sortOrder}</p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                          <Button type="button" variant="secondary" onClick={() => startWorkspaceEdit(workspace)}>
                            <Pencil className="h-4 w-4" />
                            Редактировать
                          </Button>
                          {!workspace.isDefault ? (
                            <Button
                              disabled={submittingAction === `default-workspace-${workspace.key}`}
                              type="button"
                              variant="secondary"
                              onClick={() => void makeWorkspaceDefault(workspace)}
                            >
                              <CheckCircle2 className="h-4 w-4" />
                              Сделать основной
                            </Button>
                          ) : null}
                          <Button
                            disabled={submittingAction === `toggle-workspace-${workspace.key}`}
                            type="button"
                            variant="secondary"
                            onClick={() => void toggleWorkspaceActive(workspace)}
                          >
                            {workspace.active ? <XCircle className="h-4 w-4" /> : <RotateCcw className="h-4 w-4" />}
                            {workspace.active ? "Деактивировать" : "Активировать"}
                          </Button>
                        </div>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      ) : (
        <section className="space-y-5" aria-label="Проекты">
          <form className="surface-panel rounded-[28px] border p-5" onSubmit={handleCreateProject}>
            <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-foreground">
              <PlusCircle className="h-4 w-4 text-primary" />
              Создать проект
            </div>
            <div className="grid gap-4 lg:grid-cols-[minmax(180px,0.8fr)_minmax(200px,1fr)_minmax(220px,1fr)_120px_auto] lg:items-end">
              <div className="space-y-2">
                <Label htmlFor="reference-project-key">Ключ проекта</Label>
                <Input
                  id="reference-project-key"
                  placeholder="north-line"
                  value={projectCreateForm.key}
                  onChange={(event) => setProjectCreateForm((current) => ({ ...current, key: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reference-project-workspace">Рабочая область проекта</Label>
                <Select
                  value={projectCreateForm.workspaceKey}
                  onValueChange={(value) => setProjectCreateForm((current) => ({ ...current, workspaceKey: value }))}
                >
                  <SelectTrigger aria-label="Рабочая область проекта" id="reference-project-workspace">
                    <SelectValue placeholder="Выберите область" />
                  </SelectTrigger>
                  <SelectContent>
                    {workspaces.map((workspace) => (
                      <SelectItem key={workspace.key} value={workspace.key}>
                        {workspace.nameRu}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="reference-project-name">Название проекта</Label>
                <Input
                  id="reference-project-name"
                  placeholder="Северная линия"
                  value={projectCreateForm.nameRu}
                  onChange={(event) => setProjectCreateForm((current) => ({ ...current, nameRu: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="reference-project-sort">Порядок</Label>
                <Input
                  id="reference-project-sort"
                  inputMode="numeric"
                  value={projectCreateForm.sortOrder}
                  onChange={(event) => setProjectCreateForm((current) => ({ ...current, sortOrder: event.target.value }))}
                />
              </div>
              <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                <Checkbox
                  checked={projectCreateForm.active}
                  onCheckedChange={(checked) => setProjectCreateForm((current) => ({ ...current, active: checked === true }))}
                />
                Активен
              </label>
            </div>
            {projectFormError ? <p className="mt-3 text-sm text-destructive">{projectFormError}</p> : null}
            <Button className="mt-4" disabled={submittingAction === "create-project"} type="submit">
              <PlusCircle className="h-4 w-4" />
              Создать проект
            </Button>
          </form>

          <div className="max-w-sm space-y-2">
            <Label htmlFor="project-workspace-filter">Фильтр по рабочей области</Label>
            <Select value={projectWorkspaceFilter} onValueChange={setProjectWorkspaceFilter}>
              <SelectTrigger aria-label="Фильтр по рабочей области" id="project-workspace-filter">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL_WORKSPACES_VALUE}>Все рабочие области</SelectItem>
                {workspaces.map((workspace) => (
                  <SelectItem key={workspace.key} value={workspace.key}>
                    {workspace.nameRu}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {referenceData.isLoading && projects.length === 0 ? (
            <EmptyState title="Загружаем проекты" description="Список появится после ответа backend." />
          ) : workspaces.length === 0 ? (
            <EmptyState title="Нет рабочих областей" description="Создайте рабочую область перед добавлением проектов." tone="warning" />
          ) : (
            <div className="space-y-5">
              {visibleWorkspaceKeys.map((workspaceKey) => {
                const workspace = workspacesByKey.get(workspaceKey);
                const workspaceProjects = projects.filter((project) => project.workspaceKey === workspaceKey);
                if (!workspace || workspaceProjects.length === 0) {
                  return null;
                }
                return (
                  <div className="space-y-3" key={workspaceKey}>
                    <div>
                      <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">{workspace.nameRu}</h3>
                      <p className="text-sm text-muted-foreground">workspaceKey: {workspace.key}</p>
                    </div>
                    <div className="grid gap-4">
                      {workspaceProjects.map((project) => {
                        const isEditing = editingProjectKey === project.key;
                        return (
                          <article className="rounded-[24px] border bg-card p-5 shadow-soft" key={project.key}>
                            {isEditing ? (
                              <div className="space-y-4">
                                <div className="flex flex-wrap items-center gap-2">
                                  <Badge variant="secondary">key: {project.key}</Badge>
                                  <Badge variant={projectEditForm.active ? "success" : "warning"}>
                                    {projectEditForm.active ? "активен" : "неактивен"}
                                  </Badge>
                                </div>
                                <div className="grid gap-4 md:grid-cols-[minmax(200px,1fr)_minmax(220px,1fr)_120px_auto] md:items-end">
                                  <div className="space-y-2">
                                    <Label htmlFor={`project-workspace-${project.key}`}>Рабочая область проекта</Label>
                                    <Select
                                      value={projectEditForm.workspaceKey}
                                      onValueChange={(value) => setProjectEditForm((current) => ({ ...current, workspaceKey: value }))}
                                    >
                                      <SelectTrigger aria-label={`Рабочая область проекта ${project.key}`} id={`project-workspace-${project.key}`}>
                                        <SelectValue />
                                      </SelectTrigger>
                                      <SelectContent>
                                        {workspaces.map((workspaceOption) => (
                                          <SelectItem key={workspaceOption.key} value={workspaceOption.key}>
                                            {workspaceOption.nameRu}
                                          </SelectItem>
                                        ))}
                                      </SelectContent>
                                    </Select>
                                  </div>
                                  <div className="space-y-2">
                                    <Label htmlFor={`project-name-${project.key}`}>Название проекта</Label>
                                    <Input
                                      id={`project-name-${project.key}`}
                                      value={projectEditForm.nameRu}
                                      onChange={(event) => setProjectEditForm((current) => ({ ...current, nameRu: event.target.value }))}
                                    />
                                  </div>
                                  <div className="space-y-2">
                                    <Label htmlFor={`project-sort-${project.key}`}>Порядок</Label>
                                    <Input
                                      id={`project-sort-${project.key}`}
                                      inputMode="numeric"
                                      value={projectEditForm.sortOrder}
                                      onChange={(event) => setProjectEditForm((current) => ({ ...current, sortOrder: event.target.value }))}
                                    />
                                  </div>
                                  <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                                    <Checkbox
                                      checked={projectEditForm.active}
                                      onCheckedChange={(checked) => setProjectEditForm((current) => ({ ...current, active: checked === true }))}
                                    />
                                    Активен
                                  </label>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                  <Button disabled={submittingAction === `update-project-${project.key}`} type="button" onClick={() => void handleSaveProject()}>
                                    <CheckCircle2 className="h-4 w-4" />
                                    Сохранить
                                  </Button>
                                  <Button type="button" variant="secondary" onClick={() => setEditingProjectKey(null)}>
                                    Отмена
                                  </Button>
                                </div>
                              </div>
                            ) : (
                              <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                                <div className="space-y-2">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <h4 className="text-lg font-semibold tracking-[-0.03em] text-foreground">{project.nameRu}</h4>
                                    <Badge variant={project.active ? "success" : "warning"}>
                                      {project.active ? "активен" : "неактивен"}
                                    </Badge>
                                  </div>
                                  <p className="text-sm text-muted-foreground">key: {project.key}</p>
                                  <p className="text-sm text-muted-foreground">Порядок: {project.sortOrder}</p>
                                </div>
                                <div className="flex flex-wrap gap-2">
                                  <Button type="button" variant="secondary" onClick={() => startProjectEdit(project)}>
                                    <Pencil className="h-4 w-4" />
                                    Редактировать
                                  </Button>
                                  <Button
                                    disabled={submittingAction === `toggle-project-${project.key}`}
                                    type="button"
                                    variant="secondary"
                                    onClick={() => void toggleProjectActive(project)}
                                  >
                                    {project.active ? <XCircle className="h-4 w-4" /> : <RotateCcw className="h-4 w-4" />}
                                    {project.active ? "Деактивировать" : "Активировать"}
                                  </Button>
                                </div>
                              </div>
                            )}
                          </article>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
              {visibleProjectCount === 0 ? (
                <EmptyState title="Проектов пока нет" description="Создайте первый проект и привяжите его к рабочей области." />
              ) : null}
            </div>
          )}
        </section>
      )}
    </div>
  );
}
