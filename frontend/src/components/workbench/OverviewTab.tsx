import { BookOpenText, BrainCircuit, Files } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { StatusSummary } from "@/components/StatusSummary";
import type { HealthResponse, RagProjectSummary } from "@/types";
import type { RagReadinessPresentation } from "@/utils/readiness";
import { buildSearchPresentation } from "@/utils/searchPresentation";
import type { WorkspaceTabConfig } from "./workbenchConfig";

type OverviewHeroProps = {
  activeWorkspace: WorkspaceTabConfig;
  backendStatus: string;
  health: HealthResponse | null;
  activeRagProject: RagProjectSummary | null;
  activeRagProjectKey: string;
  activeRagProjectName: string;
  ragPresentation: RagReadinessPresentation;
};

export function OverviewHero({
  activeWorkspace,
  backendStatus,
  health,
  activeRagProject,
  activeRagProjectKey,
  activeRagProjectName,
  ragPresentation,
}: OverviewHeroProps) {
  const searchPresentation = buildSearchPresentation(health);
  const projectBadgeLabel = activeRagProject
    ? activeRagProject.active
      ? "активен"
      : "не активен"
    : "выбран по ключу";

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(320px,0.85fr)]">
      <div className="space-y-5">
        <div className="flex flex-wrap items-center gap-3">
          <Badge variant="default">KEGOC AI RAG Workspace</Badge>
          <Badge variant="secondary">{activeWorkspace.label}</Badge>
          <Badge variant={backendStatus === "UP" ? "success" : "warning"}>
            Backend {backendStatus}
          </Badge>
        </div>

        <div className="space-y-3">
          <h2 className="max-w-[14ch] text-balance text-4xl font-semibold leading-none tracking-[-0.06em] text-foreground sm:text-5xl">
            Фронтенд-контур для direct и RAG сценариев
          </h2>
          <p className="max-w-3xl text-sm leading-7 text-muted-foreground sm:text-base">
            Рабочее пространство объединяет материалы, локальные модели и instruction snippets,
            чтобы direct- и RAG-сценарии собирались в одном управляемом интерфейсе без
            переключения между разными экранами.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Badge variant="secondary">Knowledge Base</Badge>
          <Badge variant="secondary">Prompt Policy</Badge>
          <Badge variant="secondary">Runtime Signals</Badge>
          <Badge variant="secondary">Search Plane</Badge>
        </div>

        <div className="surface-subtle max-w-3xl rounded-[24px] border border-border/80 p-4 shadow-soft">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0 space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                Выбранный RAG-проект
              </p>
              <strong className="block break-words text-lg font-semibold tracking-[-0.03em] text-foreground">
                {activeRagProjectName}
              </strong>
            </div>
            <div className="flex flex-wrap gap-2 sm:justify-end">
              <Badge variant={activeRagProject?.active ? "success" : "secondary"}>
                {projectBadgeLabel}
              </Badge>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <Badge className="break-all normal-case tracking-normal" variant="outline">
              key: {activeRagProjectKey}
            </Badge>
            <Badge variant="secondary">
              {activeRagProject?.readyMaterialCount ?? health?.readyMaterialCount ?? 0}/
              {activeRagProject?.materialCount ?? health?.materialCount ?? 0} ready
            </Badge>
          </div>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-1">
        <div className="rounded-[28px] border border-border/80 bg-[#0b243a] p-5 text-white shadow-panel">
          <div className="flex items-start justify-between gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-[18px] bg-white/10">
              <BrainCircuit className="h-5 w-5" />
            </div>
            <Badge variant="inverted">{ragPresentation.badgeLabel}</Badge>
          </div>
          <div className="mt-4 space-y-2">
            <p className="text-sm font-medium text-white/74">RAG readiness</p>
            <strong className="block text-2xl font-semibold tracking-[-0.04em]">
              {ragPresentation.headline}
            </strong>
            <p className="text-sm leading-6 text-white/70">{ragPresentation.overviewMessage}</p>
          </div>
        </div>

        <div className="surface-subtle rounded-[28px] p-5 shadow-soft">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <BookOpenText className="h-4 w-4 text-primary" />
            Search plane
          </div>
          <div className="mt-3 space-y-1">
            <strong className="block text-lg font-semibold tracking-[-0.03em] text-foreground">
              {searchPresentation.label}
            </strong>
            <p className="text-sm leading-6 text-muted-foreground">
              Статус: {searchPresentation.statusLabel}
            </p>
            <p className="text-sm leading-6 text-muted-foreground">
              {searchPresentation.syncLabel}
            </p>
          </div>
        </div>

        <div className="surface-subtle rounded-[28px] p-5 shadow-soft">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <Files className="h-4 w-4 text-primary" />
            Workspace context
          </div>
          <dl className="mt-3 grid gap-2 text-sm">
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-foreground">Материалы</dt>
              <dd className="font-semibold text-foreground">{health?.materialCount ?? "..."}</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-foreground">Инструкции</dt>
              <dd className="font-semibold text-foreground">По запросу</dd>
            </div>
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-foreground">Модели</dt>
              <dd className="font-semibold text-foreground">По запросу</dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  );
}

type OverviewTabProps = {
  health: HealthResponse | null;
  healthError: string | null;
  ragPresentation: RagReadinessPresentation;
};

export function OverviewTab({ health, healthError, ragPresentation }: OverviewTabProps) {
  return (
    <StatusSummary
      health={health}
      healthError={healthError}
      instructionsCount={null}
      modelsError={null}
      ragPresentation={ragPresentation}
    />
  );
}
