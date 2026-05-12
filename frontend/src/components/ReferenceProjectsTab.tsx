import { Pencil, PlusCircle, RotateCcw, XCircle, CheckCircle2 } from "lucide-react";
import { ReferenceDataEmptyState } from "@/components/ReferenceDataEmptyState";
import { ALL_WORKSPACES_VALUE } from "@/components/referenceDataPresentation";
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
import type { ReferenceDataPanelState } from "@/hooks/useReferenceDataPanelState";

type ReferenceProjectsTabProps = {
  state: ReferenceDataPanelState;
  isLoading: boolean;
};

export function ReferenceProjectsTab({ state, isLoading }: ReferenceProjectsTabProps) {
  return (
    <section className="space-y-5" aria-label="Проекты">
      <form className="surface-panel rounded-[28px] border p-5" onSubmit={state.handleCreateProject}>
        <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-foreground">
          <PlusCircle className="h-4 w-4 text-primary" />
          Создать проект
        </div>
        <div className="grid gap-4 md:grid-cols-2 md:items-end 2xl:grid-cols-[minmax(180px,0.8fr)_minmax(200px,1fr)_minmax(220px,1fr)_120px_auto]">
          <div className="space-y-2">
            <Label htmlFor="reference-project-key">Ключ проекта</Label>
            <Input
              id="reference-project-key"
              placeholder="north-line"
              value={state.projectCreateForm.key}
              onChange={(event) => state.setProjectCreateForm((current) => ({ ...current, key: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reference-project-workspace">Рабочая область проекта</Label>
            <Select
              value={state.projectCreateForm.workspaceKey}
              onValueChange={(value) => state.setProjectCreateForm((current) => ({ ...current, workspaceKey: value }))}
            >
              <SelectTrigger aria-label="Рабочая область проекта" id="reference-project-workspace">
                <SelectValue placeholder="Выберите область" />
              </SelectTrigger>
              <SelectContent>
                {state.workspaces.map((workspace) => (
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
              value={state.projectCreateForm.nameRu}
              onChange={(event) => state.setProjectCreateForm((current) => ({ ...current, nameRu: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reference-project-sort">Порядок</Label>
            <Input
              id="reference-project-sort"
              inputMode="numeric"
              value={state.projectCreateForm.sortOrder}
              onChange={(event) => state.setProjectCreateForm((current) => ({ ...current, sortOrder: event.target.value }))}
            />
          </div>
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Checkbox
              checked={state.projectCreateForm.active}
              onCheckedChange={(checked) => state.setProjectCreateForm((current) => ({ ...current, active: checked === true }))}
            />
            Активен
          </label>
        </div>
        {state.projectFormError ? <p className="mt-3 text-sm text-destructive">{state.projectFormError}</p> : null}
        <Button className="mt-4" disabled={state.submittingAction === "create-project"} type="submit">
          <PlusCircle className="h-4 w-4" />
          Создать проект
        </Button>
      </form>

      <div className="surface-subtle max-w-sm space-y-2 rounded-[24px] p-4">
        <Label htmlFor="project-workspace-filter">Фильтр по рабочей области</Label>
        <Select value={state.projectWorkspaceFilter} onValueChange={state.setProjectWorkspaceFilter}>
          <SelectTrigger aria-label="Фильтр по рабочей области" id="project-workspace-filter">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_WORKSPACES_VALUE}>Все рабочие области</SelectItem>
            {state.workspaces.map((workspace) => (
              <SelectItem key={workspace.key} value={workspace.key}>
                {workspace.nameRu}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading && state.projects.length === 0 ? (
        <ReferenceDataEmptyState title="Загружаем проекты" description="Список появится после ответа backend." />
      ) : state.workspaces.length === 0 ? (
        <ReferenceDataEmptyState title="Нет рабочих областей" description="Создайте рабочую область перед добавлением проектов." tone="warning" />
      ) : (
        <div className="space-y-5">
          {state.visibleWorkspaceKeys.map((workspaceKey) => {
            const workspace = state.workspacesByKey.get(workspaceKey);
            const workspaceProjects = state.projects.filter((project) => project.workspaceKey === workspaceKey);
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
                    const isEditing = state.editingProjectKey === project.key;
                    return (
                      <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={project.key}>
                        {isEditing ? (
                          <div className="space-y-4">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant="secondary">key: {project.key}</Badge>
                              <Badge variant={state.projectEditForm.active ? "success" : "warning"}>
                                {state.projectEditForm.active ? "активен" : "неактивен"}
                              </Badge>
                            </div>
                            <div className="grid gap-4 md:grid-cols-2 md:items-end 2xl:grid-cols-[minmax(200px,1fr)_minmax(220px,1fr)_120px_auto]">
                              <div className="space-y-2">
                                <Label htmlFor={`project-workspace-${project.key}`}>Рабочая область проекта</Label>
                                <Select
                                  value={state.projectEditForm.workspaceKey}
                                  onValueChange={(value) => state.setProjectEditForm((current) => ({ ...current, workspaceKey: value }))}
                                >
                                  <SelectTrigger aria-label={`Рабочая область проекта ${project.key}`} id={`project-workspace-${project.key}`}>
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {state.workspaces.map((workspaceOption) => (
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
                                  value={state.projectEditForm.nameRu}
                                  onChange={(event) => state.setProjectEditForm((current) => ({ ...current, nameRu: event.target.value }))}
                                />
                              </div>
                              <div className="space-y-2">
                                <Label htmlFor={`project-sort-${project.key}`}>Порядок</Label>
                                <Input
                                  id={`project-sort-${project.key}`}
                                  inputMode="numeric"
                                  value={state.projectEditForm.sortOrder}
                                  onChange={(event) => state.setProjectEditForm((current) => ({ ...current, sortOrder: event.target.value }))}
                                />
                              </div>
                              <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                                <Checkbox
                                  checked={state.projectEditForm.active}
                                  onCheckedChange={(checked) => state.setProjectEditForm((current) => ({ ...current, active: checked === true }))}
                                />
                                Активен
                              </label>
                            </div>
                            <div className="flex flex-wrap gap-2">
                              <Button disabled={state.submittingAction === `update-project-${project.key}`} type="button" onClick={() => void state.handleSaveProject()}>
                                <CheckCircle2 className="h-4 w-4" />
                                Сохранить
                              </Button>
                              <Button type="button" variant="secondary" onClick={() => state.setEditingProjectKey(null)}>
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
                              <Button type="button" variant="secondary" onClick={() => state.startProjectEdit(project)}>
                                <Pencil className="h-4 w-4" />
                                Редактировать
                              </Button>
                              <Button
                                disabled={state.submittingAction === `toggle-project-${project.key}`}
                                type="button"
                                variant="secondary"
                                onClick={() => void state.toggleProjectActive(project)}
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
          {state.visibleProjectCount === 0 ? (
            <ReferenceDataEmptyState title="Проектов пока нет" description="Создайте первый проект и привяжите его к рабочей области." />
          ) : null}
        </div>
      )}
    </section>
  );
}
