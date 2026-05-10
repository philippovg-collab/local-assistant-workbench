import { useState, type FormEvent } from "react";
import { FolderKanban, PlusCircle, RefreshCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { RagProjectInput, RagProjectSummary } from "@/types";

type RagProjectSwitcherProps = {
  projects: RagProjectSummary[];
  activeProject: RagProjectSummary | null;
  activeProjectKey: string;
  isLoading: boolean;
  error: string | null;
  actionError: string | null;
  onSelectProject: (projectKey: string) => void;
  onCreateProject: (input: RagProjectInput) => Promise<RagProjectSummary>;
  onReload: () => Promise<unknown>;
};

type CreateProjectFormState = {
  key: string;
  name: string;
  description: string;
  isDefault: boolean;
};

const initialCreateForm: CreateProjectFormState = {
  key: "",
  name: "",
  description: "",
  isDefault: false,
};

export function RagProjectSwitcher({
  projects,
  activeProject,
  activeProjectKey,
  isLoading,
  error,
  actionError,
  onSelectProject,
  onCreateProject,
  onReload,
}: RagProjectSwitcherProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [form, setForm] = useState<CreateProjectFormState>(initialCreateForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const activeProjects = projects.filter((project) => project.active);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.key.trim()) {
      setFormError("Укажите ключ RAG-проекта.");
      return;
    }
    if (!form.name.trim()) {
      setFormError("Укажите название RAG-проекта.");
      return;
    }

    setIsSubmitting(true);
    setFormError(null);
    try {
      const created = await onCreateProject({
        key: form.key.trim(),
        name: form.name.trim(),
        description: form.description.trim() || null,
        active: true,
        isDefault: form.isDefault,
      });
      onSelectProject(created.key);
      setForm(initialCreateForm);
      setIsCreateOpen(false);
    } catch {
      // Hook exposes actionError; keep the dialog open for correction/retry.
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section className="surface-panel rounded-[28px] border border-border/80 px-5 py-4 shadow-soft">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-foreground">
            <FolderKanban className="h-4 w-4 text-primary" />
            Активный RAG-проект
            {activeProject?.isDefault ? <Badge variant="default">основной</Badge> : null}
            {isLoading ? <Badge variant="secondary">обновляем</Badge> : null}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <strong className="text-xl font-semibold tracking-[-0.04em] text-foreground">
              {activeProject?.name ?? activeProjectKey}
            </strong>
            <Badge variant="outline">key: {activeProject?.key ?? activeProjectKey}</Badge>
            {activeProject ? (
              <Badge variant="secondary">{activeProject.readyMaterialCount}/{activeProject.materialCount} ready</Badge>
            ) : null}
          </div>
          {activeProject?.description ? (
            <p className="max-w-3xl text-sm leading-6 text-muted-foreground">{activeProject.description}</p>
          ) : error ? (
            <p className="text-sm leading-6 text-destructive">{error}</p>
          ) : null}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <Select
            disabled={activeProjects.length === 0}
            value={activeProject?.key ?? activeProjectKey}
            onValueChange={onSelectProject}
          >
            <SelectTrigger className="min-w-[240px]" aria-label="Выбрать RAG-проект">
              <SelectValue placeholder="Выберите RAG-проект" />
            </SelectTrigger>
            <SelectContent>
              {activeProjects.map((project) => (
                <SelectItem key={project.key} value={project.key}>
                  {project.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="button" variant="secondary" onClick={() => void onReload()}>
            <RefreshCw className="h-4 w-4" />
            Обновить
          </Button>
          <Button type="button" onClick={() => setIsCreateOpen(true)}>
            <PlusCircle className="h-4 w-4" />
            Создать RAG-проект
          </Button>
        </div>
      </div>

      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Создать RAG-проект</DialogTitle>
            <DialogDescription>
              Новый проект будет использовать workspaceKey для группировки материалов, пресетов, проектных инструкций и истории RAG-запусков.
            </DialogDescription>
          </DialogHeader>

          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="rag-project-key">Ключ</Label>
                <Input
                  id="rag-project-key"
                  placeholder="legal-rag"
                  value={form.key}
                  onChange={(event) => setForm((current) => ({ ...current, key: event.target.value }))}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="rag-project-name">Название</Label>
                <Input
                  id="rag-project-name"
                  placeholder="Юридический RAG"
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="rag-project-description">Описание</Label>
              <Textarea
                id="rag-project-description"
                placeholder="Какие материалы и правила должны жить в этом корпусе."
                rows={3}
                value={form.description}
                onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
              />
            </div>

            <label className="flex items-center gap-3 rounded-[20px] border border-field-border bg-field px-4 py-3">
              <Checkbox
                checked={form.isDefault}
                onCheckedChange={(checked) => setForm((current) => ({ ...current, isDefault: checked === true }))}
              />
              <span className="text-sm text-foreground">Сделать проектом по умолчанию</span>
            </label>

            {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
            {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}

            <DialogFooter>
              <Button type="button" variant="secondary" onClick={() => setIsCreateOpen(false)}>
                Отмена
              </Button>
              <Button disabled={isSubmitting} type="submit">
                <PlusCircle className="h-4 w-4" />
                {isSubmitting ? "Создаём..." : "Создать"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </section>
  );
}
