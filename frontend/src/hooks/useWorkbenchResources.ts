import { useEffect, useMemo, type Dispatch, type SetStateAction } from "react";
import { useConversationChatExecution } from "@/hooks/useConversationChatExecution";
import { useConversationRuns } from "@/hooks/useConversationRuns";
import { useConversations } from "@/hooks/useConversations";
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
import {
  buildDirectReadinessPresentation,
  buildRagReadinessPresentation,
  deriveRagReadiness,
} from "@/utils/readiness";
import { DEFAULT_KNOWLEDGE_SCOPE } from "@/utils/workbenchPresentation";
import {
  ACTIVE_RAG_PROJECT_STORAGE_KEY,
  getFallbackRagProjectName,
  workspaceTabs,
  type SettingsTabId,
  type WorkspaceTab,
} from "@/components/workbench/workbenchConfig";

type ActiveRagProjectResolution = "loading" | "ready" | "empty" | "error";

type UseWorkbenchResourcesOptions = {
  activeTab: WorkspaceTab;
  activeSettingsTab: SettingsTabId;
  activeRagProjectKey: string;
  setActiveRagProjectKey: Dispatch<SetStateAction<string>>;
  ragInstructionIds: string[];
  directInstructionIds: string[];
  setRagInstructionIds: Dispatch<SetStateAction<string[]>>;
  setDirectInstructionIds: Dispatch<SetStateAction<string[]>>;
};

export const useWorkbenchResources = ({
  activeTab,
  activeSettingsTab,
  activeRagProjectKey,
  setActiveRagProjectKey,
  ragInstructionIds,
  directInstructionIds,
  setRagInstructionIds,
  setDirectInstructionIds,
}: UseWorkbenchResourcesOptions) => {
  const { health, error: healthError } = useHealth();
  const contextFeatures = health?.contextFeatures ?? null;
  const conversationsEnabled = contextFeatures?.conversations === true;
  const isLongTermMemoryEnabled = contextFeatures?.longTermMemory === true;
  const metadataV1Enabled = health?.qualityLayer?.flags.metadataV1 === true;
  const isMaterialsActive = activeTab === "materials";
  const isInstructionsActive = activeTab === "instructions";
  const isSettingsReferenceActive = activeTab === "settings" && activeSettingsTab !== "llm";
  const isMemoryActive = activeTab === "memory" && isLongTermMemoryEnabled;
  const isRagActive = activeTab === "rag";
  const isDirectActive = activeTab === "direct";
  const needsInstructions = isInstructionsActive || isRagActive || isDirectActive;
  const needsModels = isRagActive || isDirectActive;
  const needsKnowledgeScopeData = isSettingsReferenceActive || isRagActive || isMemoryActive;

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
  }, [activeRagProject, activeRagProjectKey, activeRagProjectResolution, setActiveRagProjectKey]);

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
    enabled: isSettingsReferenceActive || (metadataV1Enabled && isMaterialsActive),
  });
  const ragChatRuns = useChatRuns(requestActiveRagProjectKey, {
    enabled: isRagActive && isActiveRagProjectReady,
  });
  const directChatRuns = useChatRuns(null, { enabled: isDirectActive });
  const ragConversations = useConversations({
    enabled: conversationsEnabled && isRagActive && isActiveRagProjectReady,
    mode: "rag",
    workspaceKey: requestActiveRagProjectKey,
  });
  const directConversations = useConversations({
    enabled: conversationsEnabled && isDirectActive,
    mode: "direct",
    workspaceKey: null,
  });
  const ragConversationRuns = useConversationRuns(ragConversations.selectedConversationId, {
    enabled: conversationsEnabled && isRagActive && isActiveRagProjectReady,
  });
  const directConversationRuns = useConversationRuns(directConversations.selectedConversationId, {
    enabled: conversationsEnabled && isDirectActive,
  });

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

  const ragConversationChat = useConversationChatExecution({
    chat: ragChat,
    conversationsEnabled,
    conversationId: ragConversations.selectedConversationId,
    conversationDetail: ragConversations.selectedConversationDetail,
    setSelectedInstructionIds: setRagInstructionIds,
    onConversationSelected: ragConversations.setSelectedConversationId,
    onSubmitted: async (submission) => {
      await ragConversations.loadConversations();
      if (submission.conversationId) {
        await ragConversations.loadConversationDetail(submission.conversationId);
        await ragConversationRuns.loadRunsFor(submission.conversationId);
      }
    },
    onCompleted: async (response) => {
      await ragConversations.loadConversations();
      if (response.conversationId) {
        await ragConversations.loadConversationDetail(response.conversationId);
        await ragConversationRuns.loadRunsFor(response.conversationId);
      }
    },
  });

  const directConversationChat = useConversationChatExecution({
    chat: directChat,
    conversationsEnabled,
    conversationId: directConversations.selectedConversationId,
    conversationDetail: directConversations.selectedConversationDetail,
    setSelectedInstructionIds: setDirectInstructionIds,
    onConversationSelected: directConversations.setSelectedConversationId,
    onSubmitted: async (submission) => {
      await directConversations.loadConversations();
      if (submission.conversationId) {
        await directConversations.loadConversationDetail(submission.conversationId);
        await directConversationRuns.loadRunsFor(submission.conversationId);
      }
    },
    onCompleted: async (response) => {
      await directConversations.loadConversations();
      if (response.conversationId) {
        await directConversations.loadConversationDetail(response.conversationId);
        await directConversationRuns.loadRunsFor(response.conversationId);
      }
    },
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
  }, [
    instructions.instructions.length,
    needsInstructions,
    scenarioInstructions,
    setDirectInstructionIds,
    setRagInstructionIds,
  ]);

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

  return {
    health,
    healthError,
    contextFeatures,
    conversationsEnabled,
    isLongTermMemoryEnabled,
    metadataV1Enabled,
    models,
    modelsError,
    instructions,
    knowledgePresets,
    knowledgeFacets,
    ragProjects,
    activeRagProject,
    resolvedActiveRagProjectKey,
    resolvedActiveRagProjectName,
    isActiveRagProjectReady,
    materials,
    referenceData,
    ragChatRuns,
    directChatRuns,
    ragChat,
    directChat,
    ragConversations,
    directConversations,
    ragConversationRuns,
    directConversationRuns,
    ragConversationChat,
    directConversationChat,
    scenarioInstructions,
    overviewRagPresentationResolved,
    materialsRagPresentation,
    ragPresentation,
    ragHelperText,
    directPresentation,
    activeWorkspace: workspaceTabs.find((tab) => tab.id === activeTab) ?? workspaceTabs[0],
    backendStatus: health?.status ?? "Unknown",
    toggleInstructionSelection,
  };
};
