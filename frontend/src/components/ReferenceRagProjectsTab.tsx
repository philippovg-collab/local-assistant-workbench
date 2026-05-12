import { CheckCircle2, Pencil, PlusCircle, RotateCcw, XCircle } from "lucide-react";
import { RagProjectSwitcher } from "@/components/RagProjectSwitcher";
import { ReferenceDataEmptyState } from "@/components/ReferenceDataEmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ReferenceDataPanelState } from "@/hooks/useReferenceDataPanelState";

type ReferenceRagProjectsTabProps = {
  state: ReferenceDataPanelState;
  activeRagProjectKey: string | null;
  showRagProjectSwitcher: boolean;
  onActiveRagProjectChange: (projectKey: string) => void;
};

export function ReferenceRagProjectsTab({
  state,
  activeRagProjectKey,
  showRagProjectSwitcher,
  onActiveRagProjectChange,
}: ReferenceRagProjectsTabProps) {
  return (
    <section className="space-y-5" aria-label="RAG-проекты">
      {showRagProjectSwitcher ? (
        <RagProjectSwitcher
          actionError={state.ragProjects.actionError}
          activeProject={state.activeRagProject}
          activeProjectKey={state.resolvedActiveRagProjectKey}
          error={state.ragProjects.error}
          isLoading={state.ragProjects.isLoading}
          projects={state.ragProjects.projects}
          onCreateProject={state.createAndSelectRagProject}
          onReload={() => state.ragProjects.reload()}
          onSelectProject={onActiveRagProjectChange}
        />
      ) : null}

      <form className="surface-panel rounded-[28px] border p-5" onSubmit={state.handleCreateRagProject}>
        <div className="mb-4 flex items-center gap-2 text-sm font-semibold text-foreground">
          <PlusCircle className="h-4 w-4 text-primary" />
          Создать RAG-проект
        </div>
        <div className="grid gap-4 md:grid-cols-2 md:items-end 2xl:grid-cols-[minmax(180px,0.8fr)_minmax(220px,1fr)_minmax(240px,1.2fr)_120px_auto_auto]">
          <div className="space-y-2">
            <Label htmlFor="rag-project-create-key">Ключ</Label>
            <Input
              id="rag-project-create-key"
              placeholder="legal-rag"
              value={state.ragProjectCreateForm.key}
              onChange={(event) => state.setRagProjectCreateForm((current) => ({ ...current, key: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="rag-project-create-name">Название</Label>
            <Input
              id="rag-project-create-name"
              placeholder="Юридический RAG"
              value={state.ragProjectCreateForm.name}
              onChange={(event) => state.setRagProjectCreateForm((current) => ({ ...current, name: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="rag-project-create-description">Описание</Label>
            <Input
              id="rag-project-create-description"
              placeholder="Корпус, инструкции и пресеты проекта"
              value={state.ragProjectCreateForm.description}
              onChange={(event) => state.setRagProjectCreateForm((current) => ({ ...current, description: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="rag-project-create-sort">Порядок</Label>
            <Input
              id="rag-project-create-sort"
              inputMode="numeric"
              value={state.ragProjectCreateForm.sortOrder}
              onChange={(event) => state.setRagProjectCreateForm((current) => ({ ...current, sortOrder: event.target.value }))}
            />
          </div>
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Checkbox
              checked={state.ragProjectCreateForm.active}
              onCheckedChange={(checked) => state.setRagProjectCreateForm((current) => ({ ...current, active: checked === true }))}
            />
            Активен
          </label>
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Checkbox
              checked={state.ragProjectCreateForm.isDefault}
              onCheckedChange={(checked) => state.setRagProjectCreateForm((current) => ({ ...current, isDefault: checked === true }))}
            />
            Основной
          </label>
        </div>
        {state.ragProjectFormError ? <p className="mt-3 text-sm text-destructive">{state.ragProjectFormError}</p> : null}
        <Button className="mt-4" disabled={state.submittingAction === "create-rag-project"} type="submit">
          <PlusCircle className="h-4 w-4" />
          Создать RAG-проект
        </Button>
      </form>

      {state.ragProjects.isLoading && state.sortedRagProjects.length === 0 ? (
        <ReferenceDataEmptyState title="Загружаем RAG-проекты" description="Список появится после ответа backend." />
      ) : state.sortedRagProjects.length === 0 ? (
        <ReferenceDataEmptyState title="RAG-проектов пока нет" description="Дефолтный проект general появится после миграции или первого создания." />
      ) : (
        <div className="grid gap-4">
          {state.sortedRagProjects.map((project) => {
            const isEditing = state.editingRagProjectKey === project.key;
            const isActiveSelection = activeRagProjectKey === project.key;
            return (
              <article className="surface-subtle space-y-4 rounded-[24px] p-5" key={project.key}>
                {isEditing ? (
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="secondary">key: {project.key}</Badge>
                      <Badge variant={state.ragProjectEditForm.active ? "success" : "warning"}>
                        {state.ragProjectEditForm.active ? "активен" : "неактивен"}
                      </Badge>
                    </div>
                    <div className="grid gap-4 md:grid-cols-2 md:items-end 2xl:grid-cols-[minmax(220px,1fr)_minmax(260px,1.2fr)_120px_auto_auto]">
                      <div className="space-y-2">
                        <Label htmlFor={`rag-project-name-${project.key}`}>Название</Label>
                        <Input
                          id={`rag-project-name-${project.key}`}
                          value={state.ragProjectEditForm.name}
                          onChange={(event) => state.setRagProjectEditForm((current) => ({ ...current, name: event.target.value }))}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor={`rag-project-description-${project.key}`}>Описание</Label>
                        <Input
                          id={`rag-project-description-${project.key}`}
                          value={state.ragProjectEditForm.description}
                          onChange={(event) => state.setRagProjectEditForm((current) => ({ ...current, description: event.target.value }))}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor={`rag-project-sort-${project.key}`}>Порядок</Label>
                        <Input
                          id={`rag-project-sort-${project.key}`}
                          inputMode="numeric"
                          value={state.ragProjectEditForm.sortOrder}
                          onChange={(event) => state.setRagProjectEditForm((current) => ({ ...current, sortOrder: event.target.value }))}
                        />
                      </div>
                      <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                        <Checkbox
                          checked={state.ragProjectEditForm.active}
                          onCheckedChange={(checked) => state.setRagProjectEditForm((current) => ({ ...current, active: checked === true }))}
                        />
                        Активен
                      </label>
                      <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                        <Checkbox
                          checked={state.ragProjectEditForm.isDefault}
                          onCheckedChange={(checked) => state.setRagProjectEditForm((current) => ({ ...current, isDefault: checked === true }))}
                        />
                        Основной
                      </label>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button disabled={state.submittingAction === `update-rag-project-${project.key}`} type="button" onClick={() => void state.handleSaveRagProject()}>
                        <CheckCircle2 className="h-4 w-4" />
                        Сохранить
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => state.setEditingRagProjectKey(null)}>
                        Отмена
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                    <div className="space-y-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-lg font-semibold tracking-[-0.03em] text-foreground">{project.name}</h3>
                        <Badge variant={project.active ? "success" : "warning"}>
                          {project.active ? "активен" : "неактивен"}
                        </Badge>
                        {project.isDefault ? <Badge variant="default">основной</Badge> : null}
                        {isActiveSelection ? <Badge variant="secondary">выбран</Badge> : null}
                      </div>
                      <p className="text-sm text-muted-foreground">key: {project.key}</p>
                      <p className="text-sm text-muted-foreground">
                        Материалы: {project.materialCount}; ready: {project.readyMaterialCount}; порядок: {project.sortOrder}
                      </p>
                      {project.description ? <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{project.description}</p> : null}
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button disabled={!project.active} type="button" variant="secondary" onClick={() => onActiveRagProjectChange(project.key)}>
                        <CheckCircle2 className="h-4 w-4" />
                        Выбрать
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => state.startRagProjectEdit(project)}>
                        <Pencil className="h-4 w-4" />
                        Редактировать
                      </Button>
                      {!project.isDefault ? (
                        <Button
                          disabled={state.submittingAction === `default-rag-project-${project.key}`}
                          type="button"
                          variant="secondary"
                          onClick={() => void state.makeRagProjectDefault(project)}
                        >
                          <CheckCircle2 className="h-4 w-4" />
                          Сделать основным
                        </Button>
                      ) : null}
                      <Button
                        disabled={state.submittingAction === `toggle-rag-project-${project.key}`}
                        type="button"
                        variant="secondary"
                        onClick={() => void state.toggleRagProjectActive(project)}
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
      )}
    </section>
  );
}
