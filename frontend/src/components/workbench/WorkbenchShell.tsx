import { useEffect, useMemo, useState, type Dispatch, type SetStateAction } from "react";
import { LogOut, Menu } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { useChatExecution } from "@/hooks/useChatExecution";
import { useChatRuns } from "@/hooks/useChatRuns";
import { useHealth } from "@/hooks/useHealth";
import { useInstructions } from "@/hooks/useInstructions";
import { useKnowledgeFacets } from "@/hooks/useKnowledgeFacets";
import { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import { useMaterials } from "@/hooks/useMaterials";
import { useModels } from "@/hooks/useModels";
import { useRagProjects } from "@/hooks/useRagProjects";
import { useReferenceData } from "@/hooks/useReferenceData";
import { cn } from "@/lib/utils";
import type { AuthSession } from "@/types";
import {
  buildDirectReadinessPresentation,
  buildRagReadinessPresentation,
  deriveRagReadiness,
} from "@/utils/readiness";
import { DEFAULT_KNOWLEDGE_SCOPE } from "@/utils/workbenchPresentation";
import { DirectStudioTab } from "./DirectStudioTab";
import { InstructionsTab } from "./InstructionsTab";
import { MaterialsTab } from "./MaterialsTab";
import { OverviewHero, OverviewTab } from "./OverviewTab";
import { RagStudioTab } from "./RagStudioTab";
import { ReferencesTab } from "./ReferencesTab";
import {
  ACTIVE_RAG_PROJECT_STORAGE_KEY,
  getFallbackRagProjectName,
  kegocLogo,
  workspaceTabs,
  type WorkspaceTab,
} from "./workbenchConfig";

type WorkbenchShellProps = {
  session: AuthSession;
  isLoggingOut: boolean;
  onLogout: () => Promise<void>;
};

type ActiveRagProjectResolution = "loading" | "ready" | "empty" | "error";

export function WorkbenchShell({ session, isLoggingOut, onLogout }: WorkbenchShellProps) {
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
  const metadataV1Enabled = health?.qualityLayer?.flags.metadataV1 === true;
  const isMaterialsActive = activeTab === "materials";
  const isInstructionsActive = activeTab === "instructions";
  const isReferencesActive = activeTab === "references";
  const isRagActive = activeTab === "rag";
  const isDirectActive = activeTab === "direct";
  const needsInstructions = isInstructionsActive || isRagActive || isDirectActive;
  const needsModels = isRagActive || isDirectActive;
  const needsKnowledgeScopeData = isReferencesActive || isRagActive;

  const { models, error: modelsError } = useModels({ enabled: needsModels });
  const instructions = useInstructions({ enabled: needsInstructions });
  const knowledgePresets = useKnowledgePresets({ enabled: needsKnowledgeScopeData });
  const knowledgeFacets = useKnowledgeFacets({ enabled: needsKnowledgeScopeData });
  const ragProjects = useRagProjects({ activeOnly: false });

  const activeRagProjects = useMemo(
    () =>
      [...ragProjects.projects]
        .filter((project) => project.active)
        .sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name, "ru")),
    [ragProjects.projects],
  );
  const fallbackActiveRagProject = useMemo(
    () => activeRagProjects.find((project) => project.isDefault) ?? activeRagProjects[0] ?? null,
    [activeRagProjects],
  );
  const activeRagProject = useMemo(
    () =>
      activeRagProjects.find((project) => project.key === activeRagProjectKey)
      ?? fallbackActiveRagProject
      ?? null,
    [activeRagProjectKey, activeRagProjects, fallbackActiveRagProject],
  );
  const activeRagProjectResolution: ActiveRagProjectResolution = ragProjects.error
    ? "error"
    : ragProjects.isLoading
      ? "loading"
      : activeRagProjects.length === 0
        ? "empty"
        : "ready";
  const resolvedActiveRagProjectKey = activeRagProjectResolution === "ready"
    ? activeRagProject?.key ?? "general"
    : "general";
  const resolvedActiveRagProjectName = activeRagProjectResolution === "ready" && activeRagProject
    ? activeRagProject.name
    : getFallbackRagProjectName(resolvedActiveRagProjectKey);
  const requestActiveRagProjectKey = activeRagProjectResolution === "ready"
    ? resolvedActiveRagProjectKey
    : null;
  const isActiveRagProjectReady = activeRagProjectResolution === "ready";
  const projectResolutionMessage = activeRagProjectResolution === "loading"
    ? "Загружаем активный RAG-проект перед запросом."
    : activeRagProjectResolution === "error"
      ? "Не удалось загрузить список RAG-проектов; запросы по материалам временно заблокированы."
      : activeRagProjectResolution === "empty"
        ? "Нет активных RAG-проектов; запросы по материалам временно заблокированы."
        : null;

  useEffect(() => {
    if (activeRagProjectResolution !== "ready" || !activeRagProject) {
      return;
    }

    if (activeRagProject.key !== activeRagProjectKey) {
      setActiveRagProjectKey(activeRagProject.key);
    }
  }, [activeRagProject, activeRagProjectKey, activeRagProjectResolution]);

  useEffect(() => {
    if (
      typeof window !== "undefined"
      && activeRagProjectResolution === "ready"
      && activeRagProject
    ) {
      window.localStorage.setItem(ACTIVE_RAG_PROJECT_STORAGE_KEY, activeRagProject.key);
    }
  }, [activeRagProject, activeRagProjectResolution]);

  const materials = useMaterials({
    enabled: isMaterialsActive && isActiveRagProjectReady,
    workspaceKey: requestActiveRagProjectKey,
  });
  const referenceData = useReferenceData({
    activeOnly: false,
    enabled: isReferencesActive || (metadataV1Enabled && isMaterialsActive),
  });
  const ragChatRuns = useChatRuns(requestActiveRagProjectKey, {
    enabled: isRagActive && isActiveRagProjectReady,
  });
  const directChatRuns = useChatRuns(null, { enabled: isDirectActive });

  const ragReadiness = deriveRagReadiness(health);
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
    workspaceKey: requestActiveRagProjectKey,
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
    if (!isRagActive) {
      return;
    }
    const auditRunId = ragChat.response?.auditRunId ?? null;
    if (!auditRunId) {
      return;
    }
    void ragChatRuns.loadRuns();
    void ragChatRuns.loadRun(auditRunId);
  }, [isRagActive, ragChat.response?.auditRunId]);

  useEffect(() => {
    if (!isDirectActive) {
      return;
    }
    const auditRunId = directChat.response?.auditRunId ?? null;
    if (!auditRunId) {
      return;
    }
    void directChatRuns.loadRuns();
    void directChatRuns.loadRun(auditRunId);
  }, [directChat.response?.auditRunId, isDirectActive]);

  useEffect(() => {
    if (!needsInstructions && instructions.instructions.length === 0) {
      return;
    }

    const knownInstructionIds = new Set(scenarioInstructions.map((instruction) => instruction.id));

    setRagInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
    setDirectInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
  }, [instructions.instructions.length, needsInstructions, scenarioInstructions]);

  const toggleInstructionSelection =
    (setSelectedInstructionIds: Dispatch<SetStateAction<string[]>>) => (instructionId: string) => {
      setSelectedInstructionIds((current) =>
        current.includes(instructionId)
          ? current.filter((currentInstructionId) => currentInstructionId !== instructionId)
          : [...current, instructionId],
      );
    };

  useEffect(() => {
    if (!needsModels || models.length === 0) {
      return;
    }

    const defaultModel = models[0].name;

    if (!models.some((model) => model.name === ragChat.model)) {
      ragChat.setModel(defaultModel);
    }

    if (!models.some((model) => model.name === directChat.model)) {
      directChat.setModel(defaultModel);
    }
  }, [directChat.model, directChat.setModel, models, needsModels, ragChat.model, ragChat.setModel]);

  const overviewRagPresentationResolved = buildRagReadinessPresentation({
    health,
    ragReadiness,
    isLoadingMaterials: false,
    materialsError: null,
    selectedModel: ragChat.model,
  });
  const materialsRagPresentation = buildRagReadinessPresentation({
    health,
    ragReadiness,
    isLoadingMaterials: materials.isLoading,
    materialsError: materials.error,
    selectedModel: ragChat.model,
  });
  const ragPresentation = buildRagReadinessPresentation({
    health,
    ragReadiness,
    isLoadingMaterials: false,
    materialsError: null,
    selectedModel: ragChat.model,
  });
  const ragHelperText = projectResolutionMessage
    ? `${ragPresentation.chatHelperText} ${projectResolutionMessage}`
    : ragPresentation.chatHelperText;
  const directPresentation = buildDirectReadinessPresentation(health, directChat.model);

  const activeWorkspace = workspaceTabs.find((tab) => tab.id === activeTab) ?? workspaceTabs[0];
  const backendStatus = health?.status ?? "Unknown";

  const renderNavigation = (className?: string) => (
    <nav aria-label="Workspace navigation" className={cn("space-y-2", className)}>
      {workspaceTabs.map((tab) => {
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
            <OverviewHero
              activeRagProject={activeRagProject}
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              activeWorkspace={activeWorkspace}
              backendStatus={backendStatus}
              health={health}
              ragPresentation={overviewRagPresentationResolved}
            />
          )}
        </header>

        <main className="space-y-6">
          <section
            aria-labelledby="nav-overview"
            hidden={activeTab !== "overview"}
            id="panel-overview"
            role="region"
          >
            <OverviewTab
              health={health}
              healthError={healthError}
              ragPresentation={overviewRagPresentationResolved}
            />
          </section>

          <section
            aria-labelledby="nav-materials"
            hidden={activeTab !== "materials"}
            id="panel-materials"
            role="region"
          >
            <MaterialsTab
              activeWorkspaceKey={resolvedActiveRagProjectKey}
              activeWorkspaceName={resolvedActiveRagProjectName}
              materials={materials}
              metadataV1Enabled={metadataV1Enabled}
              ragPresentation={materialsRagPresentation}
              referenceData={referenceData}
            />
          </section>

          <section
            aria-labelledby="nav-instructions"
            hidden={activeTab !== "instructions"}
            id="panel-instructions"
            role="region"
          >
            <InstructionsTab
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              instructions={instructions}
            />
          </section>

          <section
            aria-labelledby="nav-references"
            hidden={activeTab !== "references"}
            id="panel-references"
            role="region"
          >
            <ReferencesTab
              activeRagProjectKey={resolvedActiveRagProjectKey}
              knowledgeFacets={knowledgeFacets}
              knowledgePresets={knowledgePresets}
              ragProjects={ragProjects}
              referenceData={referenceData}
              showRagProjectSwitcher={isReferencesActive}
              onActiveRagProjectChange={setActiveRagProjectKey}
            />
          </section>

          <section
            aria-labelledby="nav-rag"
            hidden={activeTab !== "rag"}
            id="panel-rag"
            role="region"
          >
            <RagStudioTab
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              chatRuns={ragChatRuns}
              helperText={ragHelperText}
              instructions={scenarioInstructions}
              isBlocked={ragPresentation.isRagSubmitBlocked || !isActiveRagProjectReady}
              knowledgeFacets={knowledgeFacets}
              knowledgePresets={knowledgePresets}
              models={models}
              modelsError={modelsError}
              ragChat={ragChat}
              selectedInstructionIds={ragInstructionIds}
              onToggleInstruction={toggleInstructionSelection(setRagInstructionIds)}
            />
          </section>

          <section
            aria-labelledby="nav-direct"
            hidden={activeTab !== "direct"}
            id="panel-direct"
            role="region"
          >
            <DirectStudioTab
              chatRuns={directChatRuns}
              directChat={directChat}
              helperText={directPresentation.helperText}
              instructions={scenarioInstructions}
              isBlocked={directPresentation.isDirectSubmitBlocked}
              models={models}
              modelsError={modelsError}
              selectedInstructionIds={directInstructionIds}
              onToggleInstruction={toggleInstructionSelection(setDirectInstructionIds)}
            />
          </section>
        </main>
      </div>
    </div>
  );
}
