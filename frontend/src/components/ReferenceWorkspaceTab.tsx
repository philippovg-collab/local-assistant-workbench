import { CheckCircle2, Pencil, PlusCircle, RotateCcw, XCircle } from "lucide-react";
import { ReferenceDataEmptyState } from "@/components/ReferenceDataEmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ReferenceDataPanelState } from "@/hooks/useReferenceDataPanelState";

type ReferenceWorkspaceTabProps = {
  state: ReferenceDataPanelState;
  isLoading: boolean;
};

export function ReferenceWorkspaceTab({ state, isLoading }: ReferenceWorkspaceTabProps) {
  return (
    <section className="space-y-5" aria-label="Рабочие области">
      <form className="surface-panel rounded-[28px] border p-5" onSubmit={state.handleCreateWorkspace}>
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
              value={state.workspaceCreateForm.key}
              onChange={(event) => state.setWorkspaceCreateForm((current) => ({ ...current, key: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reference-workspace-name">Название рабочей области</Label>
            <Input
              id="reference-workspace-name"
              placeholder="Северная модернизация"
              value={state.workspaceCreateForm.nameRu}
              onChange={(event) => state.setWorkspaceCreateForm((current) => ({ ...current, nameRu: event.target.value }))}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="reference-workspace-sort">Порядок</Label>
            <Input
              id="reference-workspace-sort"
              inputMode="numeric"
              value={state.workspaceCreateForm.sortOrder}
              onChange={(event) => state.setWorkspaceCreateForm((current) => ({ ...current, sortOrder: event.target.value }))}
            />
          </div>
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Checkbox
              checked={state.workspaceCreateForm.active}
              onCheckedChange={(checked) => state.setWorkspaceCreateForm((current) => ({ ...current, active: checked === true }))}
            />
            Активна
          </label>
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Checkbox
              checked={state.workspaceCreateForm.isDefault}
              onCheckedChange={(checked) => state.setWorkspaceCreateForm((current) => ({ ...current, isDefault: checked === true }))}
            />
            Основная
          </label>
        </div>
        {state.workspaceFormError ? <p className="mt-3 text-sm text-destructive">{state.workspaceFormError}</p> : null}
        <Button className="mt-4" disabled={state.submittingAction === "create-workspace"} type="submit">
          <PlusCircle className="h-4 w-4" />
          Создать рабочую область
        </Button>
      </form>

      {isLoading && state.workspaces.length === 0 ? (
        <ReferenceDataEmptyState title="Загружаем рабочие области" description="Список появится после ответа backend." />
      ) : state.workspaces.length === 0 ? (
        <ReferenceDataEmptyState title="Рабочих областей пока нет" description="Создайте первую рабочую область для материалов." />
      ) : (
        <div className="grid gap-4">
          {state.workspaces.map((workspace) => {
            const isEditing = state.editingWorkspaceKey === workspace.key;
            return (
              <article className="rounded-[24px] border bg-card p-5 shadow-soft" key={workspace.key}>
                {isEditing ? (
                  <div className="space-y-4">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant="secondary">key: {workspace.key}</Badge>
                      <Badge variant={state.workspaceEditForm.active ? "success" : "warning"}>
                        {state.workspaceEditForm.active ? "активна" : "неактивна"}
                      </Badge>
                    </div>
                    <div className="grid gap-4 md:grid-cols-[minmax(220px,1fr)_120px_auto_auto] md:items-end">
                      <div className="space-y-2">
                        <Label htmlFor={`workspace-name-${workspace.key}`}>Название рабочей области</Label>
                        <Input
                          id={`workspace-name-${workspace.key}`}
                          value={state.workspaceEditForm.nameRu}
                          onChange={(event) => state.setWorkspaceEditForm((current) => ({ ...current, nameRu: event.target.value }))}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor={`workspace-sort-${workspace.key}`}>Порядок</Label>
                        <Input
                          id={`workspace-sort-${workspace.key}`}
                          inputMode="numeric"
                          value={state.workspaceEditForm.sortOrder}
                          onChange={(event) => state.setWorkspaceEditForm((current) => ({ ...current, sortOrder: event.target.value }))}
                        />
                      </div>
                      <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                        <Checkbox
                          checked={state.workspaceEditForm.active}
                          onCheckedChange={(checked) => state.setWorkspaceEditForm((current) => ({ ...current, active: checked === true }))}
                        />
                        Активна
                      </label>
                      <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                        <Checkbox
                          checked={state.workspaceEditForm.isDefault}
                          onCheckedChange={(checked) => state.setWorkspaceEditForm((current) => ({ ...current, isDefault: checked === true }))}
                        />
                        Основная
                      </label>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button disabled={state.submittingAction === `update-workspace-${workspace.key}`} type="button" onClick={() => void state.handleSaveWorkspace()}>
                        <CheckCircle2 className="h-4 w-4" />
                        Сохранить
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => state.setEditingWorkspaceKey(null)}>
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
                      <Button type="button" variant="secondary" onClick={() => state.startWorkspaceEdit(workspace)}>
                        <Pencil className="h-4 w-4" />
                        Редактировать
                      </Button>
                      {!workspace.isDefault ? (
                        <Button
                          disabled={state.submittingAction === `default-workspace-${workspace.key}`}
                          type="button"
                          variant="secondary"
                          onClick={() => void state.makeWorkspaceDefault(workspace)}
                        >
                          <CheckCircle2 className="h-4 w-4" />
                          Сделать основной
                        </Button>
                      ) : null}
                      <Button
                        disabled={state.submittingAction === `toggle-workspace-${workspace.key}`}
                        type="button"
                        variant="secondary"
                        onClick={() => void state.toggleWorkspaceActive(workspace)}
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
  );
}
