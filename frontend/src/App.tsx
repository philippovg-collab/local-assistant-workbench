import { useCallback, useEffect, useMemo, useState, type Dispatch, type FormEvent, type SetStateAction } from "react";
import {
  BookOpenText,
  BrainCircuit,
  Database,
  Files,
  LayoutDashboard,
  Loader2,
  LockKeyhole,
  LogOut,
  Menu,
  MessageCircleCode,
  NotebookPen,
  Radar,
} from "lucide-react";
import { apiClient } from "@/api/client";
import { translateCommonApiError } from "@/api/errorMessages";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { DirectChatPanel } from "@/components/DirectChatPanel";
import { InstructionLibraryPanel } from "@/components/InstructionLibraryPanel";
import { MaterialsPanel } from "@/components/MaterialsPanel";
import { RagChatPanel } from "@/components/RagChatPanel";
import { RagProjectSwitcher } from "@/components/RagProjectSwitcher";
import { ReferenceDataPanel } from "@/components/ReferenceDataPanel";
import { StatusSummary } from "@/components/StatusSummary";
import { cn } from "@/lib/utils";
import { useChatExecution } from "@/hooks/useChatExecution";
import { useChatRuns } from "@/hooks/useChatRuns";
import { useHealth } from "@/hooks/useHealth";
import { useInstructions } from "@/hooks/useInstructions";
import { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import { useMaterials } from "@/hooks/useMaterials";
import { useModels } from "@/hooks/useModels";
import { useRagProjects } from "@/hooks/useRagProjects";
import { useReferenceData } from "@/hooks/useReferenceData";
import type { AuthSession } from "@/types";
import {
  buildDirectReadinessPresentation,
  buildRagReadinessPresentation,
  deriveRagReadiness,
} from "@/utils/readiness";
import { buildSearchPresentation } from "@/utils/searchPresentation";
import { DEFAULT_KNOWLEDGE_SCOPE } from "@/utils/workbenchPresentation";

const kegocLogo = "https://ai.kegoc.kz/assets/kegoc-logo-new-nY5PHfMg.svg";
const ACTIVE_RAG_PROJECT_STORAGE_KEY = "kegoc.activeRagProjectKey";

type WorkspaceTab = "overview" | "materials" | "instructions" | "references" | "rag" | "direct";
type AuthStatus = "loading" | "authenticated" | "unauthenticated";

type WorkbenchAppProps = {
  session: AuthSession;
  isLoggingOut: boolean;
  onLogout: () => Promise<void>;
};

const tabs: Array<{
  id: WorkspaceTab;
  label: string;
  description: string;
  icon: typeof LayoutDashboard;
}> = [
  {
    id: "overview",
    label: "Дашборд",
    description: "Общий health, readiness и production signals",
    icon: LayoutDashboard,
  },
  {
    id: "materials",
    label: "Материалы",
    description: "Управление knowledge base и lineage",
    icon: Files,
  },
  {
    id: "instructions",
    label: "Инструкции",
    description: "Instruction stack по уровням",
    icon: NotebookPen,
  },
  {
    id: "rag",
    label: "RAG Studio",
    description: "Контекстные ответы по локальным материалам",
    icon: Radar,
  },
  {
    id: "direct",
    label: "Direct Studio",
    description: "Прямые запросы к модели без retrieval",
    icon: MessageCircleCode,
  },
  {
    id: "references",
    label: "Справочники",
    description: "Пресеты корпуса, рабочие области и проекты",
    icon: Database,
  },
];

function WorkbenchApp({ session, isLoggingOut, onLogout }: WorkbenchAppProps) {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("overview");
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [ragInstructionIds, setRagInstructionIds] = useState<string[]>([]);
  const [directInstructionIds, setDirectInstructionIds] = useState<string[]>([]);
  const [activeRagProjectKey, setActiveRagProjectKey] = useState(() => {
    if (typeof window === "undefined") {
      return "general";
    }
    return window.localStorage.getItem(ACTIVE_RAG_PROJECT_STORAGE_KEY) || "general";
  });
  const { health, error: healthError } = useHealth();
  const { models, error: modelsError } = useModels();
  const instructions = useInstructions();
  const knowledgePresets = useKnowledgePresets();
  const ragProjects = useRagProjects({ activeOnly: false });
  const activeRagProjects = useMemo(
    () =>
      [...ragProjects.projects]
        .filter((project) => project.active)
        .sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name, "ru")),
    [ragProjects.projects],
  );
  const activeRagProject = useMemo(
    () =>
      activeRagProjects.find((project) => project.key === activeRagProjectKey)
      ?? activeRagProjects.find((project) => project.isDefault)
      ?? activeRagProjects[0]
      ?? null,
    [activeRagProjectKey, activeRagProjects],
  );
  const resolvedActiveRagProjectKey = activeRagProject?.key ?? activeRagProjectKey;
  const resolvedActiveRagProjectName = activeRagProject?.name ?? resolvedActiveRagProjectKey;
  const materials = useMaterials({ workspaceKey: resolvedActiveRagProjectKey });
  const ragChatRuns = useChatRuns(resolvedActiveRagProjectKey);
  const directChatRuns = useChatRuns();
  const ragReadiness = deriveRagReadiness(health);
  const metadataV1Enabled = health?.qualityLayer?.flags.metadataV1 === true;
  const referenceData = useReferenceData({
    activeOnly: false,
    enabled: metadataV1Enabled || activeTab === "references",
  });
  const scenarioInstructions = instructions.instructions.filter(
    (instruction) => (instruction.scopeLevel ?? "chat_scenario") === "chat_scenario" && (instruction.active ?? true),
  );

  const ragChat = useChatExecution({
    mode: "rag",
    initialModel: "qwen2.5:7b",
    initialPrompt: "Что написано про тариф Премиум?",
    initialAnswerMode: "strict_sources_only",
    initialKnowledgeScope: DEFAULT_KNOWLEDGE_SCOPE,
    rolloutFlags: health?.qualityLayer?.flags ?? null,
    selectedInstructionIds: ragInstructionIds,
    workspaceKey: resolvedActiveRagProjectKey,
  });

  const directChat = useChatExecution({
    mode: "direct",
    initialModel: "qwen2.5:7b",
    initialPrompt: "Покажи пример request-response для локальной LLM через единый API.",
    initialTemporaryInstruction: "Отвечай кратко, по делу и на русском языке.",
    initialAnswerMode: "brief",
    rolloutFlags: health?.qualityLayer?.flags ?? null,
    selectedInstructionIds: directInstructionIds,
  });

  useEffect(() => {
    if (!ragProjects.isLoading && activeRagProjects.length > 0) {
      const currentIsActive = activeRagProjects.some((project) => project.key === activeRagProjectKey);
      const fallbackKey = activeRagProjects.find((project) => project.isDefault)?.key ?? activeRagProjects[0].key;
      if (!currentIsActive && fallbackKey !== activeRagProjectKey) {
        setActiveRagProjectKey(fallbackKey);
      }
    }
  }, [activeRagProjectKey, activeRagProjects, ragProjects.isLoading]);

  useEffect(() => {
    if (typeof window !== "undefined" && resolvedActiveRagProjectKey) {
      window.localStorage.setItem(ACTIVE_RAG_PROJECT_STORAGE_KEY, resolvedActiveRagProjectKey);
    }
  }, [resolvedActiveRagProjectKey]);

  useEffect(() => {
    const auditRunId = ragChat.response?.auditRunId ?? null;
    if (!auditRunId) {
      return;
    }
    void ragChatRuns.loadRuns();
    void ragChatRuns.loadRun(auditRunId);
  }, [ragChat.response?.auditRunId]);

  useEffect(() => {
    const auditRunId = directChat.response?.auditRunId ?? null;
    if (!auditRunId) {
      return;
    }
    void directChatRuns.loadRuns();
    void directChatRuns.loadRun(auditRunId);
  }, [directChat.response?.auditRunId]);

  useEffect(() => {
    const knownInstructionIds = new Set(scenarioInstructions.map((instruction) => instruction.id));

    setRagInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
    setDirectInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
  }, [scenarioInstructions]);

  const toggleInstructionSelection =
    (setSelectedInstructionIds: Dispatch<SetStateAction<string[]>>) => (instructionId: string) => {
      setSelectedInstructionIds((current) =>
        current.includes(instructionId)
          ? current.filter((currentInstructionId) => currentInstructionId !== instructionId)
          : [...current, instructionId],
      );
    };

  useEffect(() => {
    if (models.length === 0) {
      return;
    }

    const defaultModel = models[0].name;

    if (!models.some((model) => model.name === ragChat.model)) {
      ragChat.setModel(defaultModel);
    }

    if (!models.some((model) => model.name === directChat.model)) {
      directChat.setModel(defaultModel);
    }
  }, [models, ragChat.model, ragChat.setModel, directChat.model, directChat.setModel]);

  const ragPresentation = buildRagReadinessPresentation({
    health,
    ragReadiness,
    isLoadingMaterials: materials.isLoading,
    materialsError: materials.error,
    selectedModel: ragChat.model,
  });
  const directPresentation = buildDirectReadinessPresentation(health, directChat.model);
  const searchPresentation = buildSearchPresentation(health);

  const activeWorkspace = tabs.find((tab) => tab.id === activeTab) ?? tabs[0];
  const backendStatus = health?.status ?? "Unknown";

  const renderNavigation = (className?: string) => (
    <nav aria-label="Workspace navigation" className={cn("space-y-2", className)}>
      {tabs.map((tab) => {
        const isActive = activeTab === tab.id;
        const Icon = tab.icon;

        return (
          <button
            aria-controls={`panel-${tab.id}`}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "flex w-full items-center gap-3 rounded-[24px] px-4 py-4 text-left transition",
              isActive
                ? "bg-sidebar-accent text-sidebar-accent-foreground shadow-soft"
                : "text-sidebar-foreground/78 hover:bg-white/8 hover:text-sidebar-foreground",
            )}
            id={`nav-${tab.id}`}
            key={tab.id}
            type="button"
            onClick={() => {
              setActiveTab(tab.id);
              setMobileNavOpen(false);
            }}
          >
            <span
              className={cn(
                "flex h-11 w-11 items-center justify-center rounded-2xl border",
                isActive
                  ? "border-white/12 bg-white/12"
                  : "border-white/8 bg-transparent",
              )}
            >
              <Icon className="h-5 w-5" />
            </span>
            <span className="min-w-0">
              <span className="block text-sm font-semibold">{tab.label}</span>
              <span className="block text-xs leading-5 text-sidebar-foreground/60">
                {tab.description}
              </span>
            </span>
          </button>
        );
      })}
    </nav>
  );

  return (
    <div className="relative mx-auto flex min-h-screen w-full max-w-[1600px] gap-4 px-4 py-4 sm:px-6 lg:px-8">
      <aside className="surface-sidebar sticky top-4 hidden h-[calc(100vh-2rem)] w-[300px] shrink-0 rounded-[34px] px-5 py-5 lg:flex lg:flex-col">
        <div className="space-y-5">
          <div className="flex items-center gap-3">
            <div className="flex h-14 w-14 items-center justify-center rounded-[20px] bg-white/10">
              <img alt="Логотип KEGOC" className="h-10 w-10 object-contain" src={kegocLogo} />
            </div>
            <div className="space-y-1">
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-sidebar-foreground/60">
                ПАНЕЛЬ УПРАВЛЕНИЯ
              </p>
              <h1 className="text-xl font-semibold tracking-[-0.04em]">KEGOC RAG</h1>
            </div>
          </div>
        </div>

        <Separator className="my-5 bg-white/10" />
        {renderNavigation("flex-1")}
        <Separator className="my-5 bg-white/10" />

        <div className="mb-4 rounded-[22px] border border-white/8 bg-white/6 px-4 py-3">
          <p className="truncate text-sm font-semibold text-sidebar-foreground">
            {session.username ?? "admin"}
          </p>
          <Button
            className="mt-3 w-full justify-center"
            disabled={isLoggingOut}
            size="sm"
            type="button"
            variant="secondary"
            onClick={() => {
              void onLogout();
            }}
          >
            <LogOut className="h-4 w-4" />
            Выйти
          </Button>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header
          className={cn(
            "surface-panel mb-6 rounded-[34px] px-5 py-5 sm:px-6 lg:px-7",
            activeTab !== "overview" && "lg:hidden",
          )}
        >
          <div
            className={cn(
              "flex items-center justify-between gap-3 lg:hidden",
              activeTab === "overview" && "mb-5",
            )}
          >
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-primary">
                Workspace
              </p>
              <strong className="text-lg font-semibold tracking-[-0.03em] text-foreground">
                {activeWorkspace.label}
              </strong>
            </div>

            <div className="flex items-center gap-2">
              <Button
                disabled={isLoggingOut}
                size="icon"
                type="button"
                variant="secondary"
                onClick={() => {
                  void onLogout();
                }}
              >
                <LogOut className="h-5 w-5" />
                <span className="sr-only">Выйти</span>
              </Button>
              <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
                <SheetTrigger asChild>
                  <Button size="icon" variant="secondary">
                    <Menu className="h-5 w-5" />
                    <span className="sr-only">Открыть навигацию</span>
                  </Button>
                </SheetTrigger>
              <SheetContent side="left">
                <SheetHeader className="mb-6">
                  <SheetTitle>KEGOC RAG</SheetTitle>
                  <SheetDescription>
                    Навигация по основным разделам фронтенда.
                  </SheetDescription>
                </SheetHeader>
                {renderNavigation()}
              </SheetContent>
              </Sheet>
            </div>
          </div>

          {activeTab === "overview" && (
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
                      <dd className="font-semibold text-foreground">{materials.materialTotal}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-muted-foreground">Инструкции</dt>
                      <dd className="font-semibold text-foreground">{instructions.instructions.length}</dd>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-muted-foreground">Модели</dt>
                      <dd className="font-semibold text-foreground">{models.length}</dd>
                    </div>
                  </dl>
                </div>
              </div>
            </div>
          )}
        </header>

        <main className="space-y-6">
          <section
            aria-labelledby="nav-overview"
            hidden={activeTab !== "overview"}
            id="panel-overview"
            role="region"
          >
            <StatusSummary
              health={health}
              healthError={healthError}
              instructionsCount={instructions.instructions.length}
              models={models}
              modelsError={modelsError}
              ragPresentation={ragPresentation}
            />
          </section>

          <section
            aria-labelledby="nav-materials"
            hidden={activeTab !== "materials"}
            id="panel-materials"
            role="region"
          >
            <MaterialsPanel
              actionError={materials.actionError}
              deletingMaterialId={materials.deletingMaterialId}
              editingMaterialId={materials.editingMaterialId}
              error={materials.error}
              isLoading={materials.isLoading}
              isLoadingMore={materials.isLoadingMore}
              lineageError={materials.lineageError}
              loadingLineageMaterialId={materials.loadingLineageMaterialId}
              materials={materials.materials}
              materialTotal={materials.materialTotal}
              hasMoreMaterials={materials.hasMoreMaterials}
              metadataV1Enabled={metadataV1Enabled}
              message={materials.message}
              onClearLineage={materials.clearLineage}
              onCreateText={materials.createTextMaterial}
              onDelete={materials.deleteMaterial}
              onEditMaterial={materials.editMaterial}
              onLoadMore={materials.loadMoreMaterials}
              onLoadLineage={(materialId) => materials.loadLineage(materialId)}
              onReindex={materials.reindexMaterial}
              onUpload={materials.uploadMaterial}
              onUploadVersion={materials.uploadMaterialVersion}
              policyWarning={materials.policyWarning}
              ragPresentation={ragPresentation}
              referenceDataError={referenceData.error}
              referenceProjects={referenceData.projects}
              referenceWorkspaces={referenceData.workspaces}
              activeWorkspaceKey={resolvedActiveRagProjectKey}
              activeWorkspaceName={resolvedActiveRagProjectName}
              reindexingMaterialId={materials.reindexingMaterialId}
              selectedLineage={materials.selectedLineage}
              uploadPolicy={materials.uploadPolicy}
              isReferenceDataLoading={referenceData.isLoading}
              versionUploadingMaterialId={materials.versionUploadingMaterialId}
            />
          </section>

          <section
            aria-labelledby="nav-instructions"
            hidden={activeTab !== "instructions"}
            id="panel-instructions"
            role="region"
          >
            <InstructionLibraryPanel
              actionError={instructions.actionError}
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              detailError={instructions.detailError}
              deletingInstructionId={instructions.deletingInstructionId}
              error={instructions.error}
              isLoadingDetail={instructions.isLoadingDetail}
              instructions={instructions.instructions}
              isLoading={instructions.isLoading}
              message={instructions.message}
              onCreateInstruction={instructions.createInstruction}
              onDeleteInstruction={instructions.deleteInstruction}
              onLoadInstructionDiff={(instructionId, fromRevision, toRevision) =>
                instructions.loadInstructionDiff(instructionId, fromRevision, toRevision)}
              onLoadInstruction={(instructionId) => instructions.loadInstruction(instructionId)}
              onLoadInstructionRevisions={(instructionId) => instructions.loadInstructionRevisions(instructionId)}
              onRestoreInstructionRevision={(instructionId, revision) =>
                instructions.restoreInstructionRevision(instructionId, revision)}
              onUpdateInstruction={instructions.updateInstruction}
              revisionDiff={instructions.revisionDiff}
              revisions={instructions.revisions}
              selectedInstruction={instructions.selectedInstruction}
            />
          </section>

          <section
            aria-labelledby="nav-references"
            hidden={activeTab !== "references"}
            id="panel-references"
            role="region"
          >
            {activeTab === "references" ? (
              <RagProjectSwitcher
                actionError={ragProjects.actionError}
                activeProject={activeRagProject}
                activeProjectKey={resolvedActiveRagProjectKey}
                error={ragProjects.error}
                isLoading={ragProjects.isLoading}
                projects={ragProjects.projects}
                onCreateProject={async (input) => {
                  const created = await ragProjects.createProject(input);
                  setActiveRagProjectKey(created.key);
                  void referenceData.reload();
                  return created;
                }}
                onReload={() => ragProjects.reload()}
                onSelectProject={setActiveRagProjectKey}
              />
            ) : null}

            <ReferenceDataPanel
              knowledgePresets={{
                actionError: knowledgePresets.actionError,
                error: knowledgePresets.error,
                isLoading: knowledgePresets.isLoading,
                message: knowledgePresets.message,
                onCreatePreset: knowledgePresets.createPreset,
                onDeletePreset: knowledgePresets.deletePreset,
                onLoadPreset: (presetId) => knowledgePresets.loadPreset(presetId),
                onLoadRevisionDiff: (presetId, fromRevision, toRevision) =>
                  knowledgePresets.loadRevisionDiff(presetId, fromRevision, toRevision),
                onLoadRevisions: (presetId) => knowledgePresets.loadRevisions(presetId),
                onRestoreRevision: (presetId, revision) => knowledgePresets.restoreRevision(presetId, revision),
                onUpdatePreset: knowledgePresets.updatePreset,
                presets: knowledgePresets.presets,
                revisionDiff: knowledgePresets.revisionDiff,
                revisions: knowledgePresets.revisions,
                selectedPreset: knowledgePresets.selectedPreset,
              }}
              referenceData={referenceData}
              ragProjects={ragProjects}
              activeRagProjectKey={resolvedActiveRagProjectKey}
              onActiveRagProjectChange={setActiveRagProjectKey}
            />
          </section>

          <section
            aria-labelledby="nav-rag"
            hidden={activeTab !== "rag"}
            id="panel-rag"
            role="region"
          >
            <RagChatPanel
              answerMode={ragChat.answerMode}
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              chatRuns={ragChatRuns.runs}
              chatRunsError={ragChatRuns.error}
              error={ragChat.error}
              helperText={ragPresentation.chatHelperText}
              metadataFiltersEnabled={ragChat.metadataFiltersEnabled}
              queryHintsEnabled={ragChat.queryHintsEnabled}
              isBlocked={ragPresentation.isRagSubmitBlocked}
              isSubmitting={ragChat.isSubmitting}
              currentRunStatus={ragChat.currentRunStatus}
              instructions={scenarioInstructions}
              knowledgePresets={knowledgePresets.presets}
              knowledgeScope={ragChat.knowledgeScope}
              retrievalFilters={ragChat.retrievalFilters}
              effectiveRetrievalFilters={ragChat.effectiveRetrievalFilters}
              queryHints={ragChat.queryHints}
              hintOwnedFields={ragChat.hintOwnedFields}
              manualOwnedFields={ragChat.manualOwnedFields}
              dismissedHintKeys={ragChat.dismissedHintKeys}
              models={models}
              modelsError={modelsError}
              onAnswerModeChange={ragChat.setAnswerMode}
              onKnowledgeScopeChange={ragChat.setKnowledgeScope}
              onRetrievalFilterChange={ragChat.updateRetrievalFilter}
              onClearRetrievalFilter={ragChat.clearRetrievalFilter}
              onDismissHint={ragChat.dismissHint}
              onResetDismissedHints={ragChat.resetDismissedHints}
              onLoadChatRun={(runId) => ragChatRuns.loadRun(runId)}
              prompt={ragChat.prompt}
              response={ragChat.response}
              selectedChatRun={ragChatRuns.selectedRun}
              selectedModel={ragChat.model}
              selectedInstructionIds={ragInstructionIds}
              temporaryInstruction={ragChat.temporaryInstruction}
              onModelChange={ragChat.setModel}
              onPromptChange={ragChat.setPrompt}
              onSubmit={ragChat.submit}
              onTemporaryInstructionChange={ragChat.setTemporaryInstruction}
              onToggleInstruction={toggleInstructionSelection(setRagInstructionIds)}
            />
          </section>

          <section
            aria-labelledby="nav-direct"
            hidden={activeTab !== "direct"}
            id="panel-direct"
            role="region"
          >
            <DirectChatPanel
              answerMode={directChat.answerMode}
              chatRuns={directChatRuns.runs}
              chatRunsError={directChatRuns.error}
              error={directChat.error}
              helperText={directPresentation.helperText}
              isBlocked={directPresentation.isDirectSubmitBlocked}
              isSubmitting={directChat.isSubmitting}
              currentRunStatus={directChat.currentRunStatus}
              instructions={scenarioInstructions}
              models={models}
              modelsError={modelsError}
              onAnswerModeChange={directChat.setAnswerMode}
              onLoadChatRun={(runId) => directChatRuns.loadRun(runId)}
              prompt={directChat.prompt}
              requestPreview={directChat.lastSubmittedRequest && (directChat.isSubmitting || directChat.response)
                ? directChat.lastSubmittedRequest
                : {
                    mode: "direct",
                    model: directChat.model,
                    prompt: directChat.prompt,
                    instructionIds: directInstructionIds,
                    answerMode: directChat.answerMode,
                    ...(directChat.temporaryInstruction.trim()
                      ? { temporaryInstruction: directChat.temporaryInstruction.trim() }
                      : {}),
                  }}
              response={directChat.response}
              selectedChatRun={directChatRuns.selectedRun}
              selectedModel={directChat.model}
              selectedInstructionIds={directInstructionIds}
              temporaryInstruction={directChat.temporaryInstruction}
              onModelChange={directChat.setModel}
              onPromptChange={directChat.setPrompt}
              onSubmit={directChat.submit}
              onTemporaryInstructionChange={directChat.setTemporaryInstruction}
              onToggleInstruction={toggleInstructionSelection(setDirectInstructionIds)}
            />
          </section>
        </main>
      </div>
    </div>
  );
}

function App() {
  const auth = useAuthSession();

  if (auth.status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <div className="flex items-center gap-3 rounded-[24px] border border-border bg-surface-subtle px-5 py-4 text-sm font-semibold text-foreground shadow-soft">
          <Loader2 className="h-5 w-5 animate-spin text-primary" />
          Проверяем сессию
        </div>
      </div>
    );
  }

  if (auth.status !== "authenticated" || auth.session === null) {
    return (
      <LoginScreen
        error={auth.error}
        isSubmitting={auth.isSubmitting}
        onLogin={auth.login}
      />
    );
  }

  return (
    <WorkbenchApp
      isLoggingOut={auth.isLoggingOut}
      session={auth.session}
      onLogout={auth.logout}
    />
  );
}

function LoginScreen({
  error,
  isSubmitting,
  onLogin,
}: {
  error: string | null;
  isSubmitting: boolean;
  onLogin: (username: string, password: string) => Promise<void>;
}) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    void onLogin(username, password);
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <main className="w-full max-w-[420px] rounded-[30px] border border-border bg-card p-6 shadow-panel">
        <div className="mb-7 flex flex-col items-center gap-3 text-center sm:flex-row sm:text-left">
          <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-[22px] border border-sidebar-border bg-sidebar shadow-panel">
            <img alt="Логотип KEGOC" className="h-12 w-12 object-contain" src={kegocLogo} />
          </div>
          <div className="min-w-0">
            <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-primary">
              ПАНЕЛЬ УПРАВЛЕНИЯ
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-[-0.04em] text-foreground">KEGOC RAG</h1>
            <p className="mt-1 text-sm font-medium text-muted-foreground">Вход администратора</p>
          </div>
        </div>

        {error && (
          <Alert className="mb-5" variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <Label htmlFor="auth-username">Логин</Label>
            <Input
              autoComplete="username"
              id="auth-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="auth-password">Пароль</Label>
            <Input
              autoComplete="current-password"
              id="auth-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>

          <Button className="w-full" disabled={isSubmitting} type="submit">
            {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <LockKeyhole className="h-4 w-4" />}
            Войти
          </Button>
        </form>
      </main>
    </div>
  );
}

function useAuthSession() {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [session, setSession] = useState<AuthSession | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    void apiClient.fetchSession(controller.signal)
      .then((payload) => {
        setSession(payload.authenticated ? payload : null);
        setStatus(payload.authenticated ? "authenticated" : "unauthenticated");
        setError(null);
      })
      .catch((loadError) => {
        if (controller.signal.aborted) {
          return;
        }
        setSession(null);
        setStatus("unauthenticated");
        setError(translateCommonApiError(loadError, "Не удалось проверить сессию"));
      });

    return () => controller.abort();
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    setIsSubmitting(true);
    setError(null);
    try {
      const payload = await apiClient.login({ username, password });
      if (!payload.authenticated) {
        setSession(null);
        setStatus("unauthenticated");
        setError("Не удалось открыть сессию.");
        return;
      }
      setSession(payload);
      setStatus("authenticated");
    } catch (loginError) {
      setSession(null);
      setStatus("unauthenticated");
      setError(translateCommonApiError(loginError, "Не удалось войти"));
    } finally {
      setIsSubmitting(false);
    }
  }, []);

  const logout = useCallback(async () => {
    setIsLoggingOut(true);
    try {
      await apiClient.logout();
    } finally {
      setSession(null);
      setStatus("unauthenticated");
      setIsLoggingOut(false);
    }
  }, []);

  return {
    status,
    session,
    error,
    isSubmitting,
    isLoggingOut,
    login,
    logout,
  };
}

export default App;
