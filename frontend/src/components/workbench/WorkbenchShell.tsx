import { useEffect, useMemo, useState } from "react";
import { useEvalCandidatePromotion } from "@/hooks/useEvalCandidatePromotion";
import { useWorkbenchResources } from "@/hooks/useWorkbenchResources";
import { useWorkbenchState } from "@/hooks/useWorkbenchState";
import type { AuthSession, ChatAuditRunDetail } from "@/types";
import { DirectStudioTab } from "./DirectStudioTab";
import { EvalTab, type EvalFocus } from "@/components/eval/EvalTab";
import { InstructionsTab } from "./InstructionsTab";
import { LlmProviderSettingsPanel } from "@/components/LlmProviderSettingsPanel";
import { MaterialsTab } from "./MaterialsTab";
import { MemoryTab } from "./MemoryTab";
import { OverviewTab } from "./OverviewTab";
import { RagStudioTab } from "./RagStudioTab";
import { ReferencesTab } from "./ReferencesTab";
import type { WorkspaceTab } from "./workbenchConfig";
import { WorkbenchMobileHeader } from "./WorkbenchMobileHeader";
import { WorkbenchSidebar } from "./WorkbenchSidebar";

type WorkbenchShellProps = {
  session: AuthSession;
  isLoggingOut: boolean;
  onLogout: () => Promise<void>;
};

export function WorkbenchShell({ session, isLoggingOut, onLogout }: WorkbenchShellProps) {
  const state = useWorkbenchState();
  const [evalFocus, setEvalFocus] = useState<EvalFocus | null>(null);
  const evalCandidatePromotion = useEvalCandidatePromotion();
  const {
    activeTab,
    setActiveTab,
    mobileNavOpen,
    setMobileNavOpen,
    ragInstructionIds,
    setRagInstructionIds,
    directInstructionIds,
    setDirectInstructionIds,
    activeRagProjectKey,
    setActiveRagProjectKey,
  } = state;
  const resources = useWorkbenchResources({
    activeTab,
    activeRagProjectKey,
    setActiveRagProjectKey,
    ragInstructionIds,
    directInstructionIds,
    setRagInstructionIds,
    setDirectInstructionIds,
  });
  const {
    health,
    healthError,
    conversationsEnabled,
    isLongTermMemoryEnabled,
    metadataV1Enabled,
    isReferencesActive,
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
    activeWorkspace,
    backendStatus,
    toggleInstructionSelection,
  } = resources;
  const hiddenTabs = useMemo<WorkspaceTab[]>(
    () => (isLongTermMemoryEnabled ? [] : ["memory"]),
    [isLongTermMemoryEnabled],
  );
  const selectTab = (tab: WorkspaceTab) => {
    if (hiddenTabs.includes(tab)) {
      setActiveTab("overview");
      setMobileNavOpen(false);
      return;
    }
    setActiveTab(tab);
    setMobileNavOpen(false);
  };
  const createEvalCandidateFromAudit = async (run: ChatAuditRunDetail) => {
    const candidate = await evalCandidatePromotion.createCandidate(run);
    if (candidate) {
      setEvalFocus({
        caseId: candidate.id ?? null,
        datasetId: candidate.datasetId ?? null,
        nonce: Date.now(),
        view: "datasets",
      });
      setActiveTab("eval");
      setMobileNavOpen(false);
    }
    return candidate;
  };

  useEffect(() => {
    if (activeTab === "memory" && !isLongTermMemoryEnabled) {
      setActiveTab("overview");
    }
  }, [activeTab, isLongTermMemoryEnabled, setActiveTab]);

  return (
    <div className="relative mx-auto flex min-h-screen w-full max-w-[1600px] gap-4 px-4 py-4 sm:px-6 lg:px-8">
      <WorkbenchSidebar
        activeTab={activeTab}
        hiddenTabs={hiddenTabs}
        isLoggingOut={isLoggingOut}
        session={session}
        onLogout={onLogout}
        onSelectTab={selectTab}
      />

      <div className="min-w-0 flex-1">
        <WorkbenchMobileHeader
          activeRagProject={activeRagProject}
          activeRagProjectKey={resolvedActiveRagProjectKey}
          activeRagProjectName={resolvedActiveRagProjectName}
          activeTab={activeTab}
          activeWorkspace={activeWorkspace}
          backendStatus={backendStatus}
          health={health}
          hiddenTabs={hiddenTabs}
          isLoggingOut={isLoggingOut}
          mobileNavOpen={mobileNavOpen}
          ragPresentation={overviewRagPresentationResolved}
          onLogout={onLogout}
          onMobileNavOpenChange={setMobileNavOpen}
          onSelectTab={selectTab}
        />

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
            aria-labelledby="nav-memory"
            hidden={activeTab !== "memory" || !isLongTermMemoryEnabled}
            id="panel-memory"
            role="region"
          >
            <MemoryTab
              activeRagProjectKey={resolvedActiveRagProjectKey}
              activeRagProjectName={resolvedActiveRagProjectName}
              enabled={isLongTermMemoryEnabled}
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
              conversationsEnabled={conversationsEnabled}
              conversationChat={ragConversationChat}
              conversationRuns={ragConversationRuns}
              conversations={ragConversations}
              helperText={ragHelperText}
              instructions={scenarioInstructions}
              isBlocked={ragPresentation.isRagSubmitBlocked || !isActiveRagProjectReady}
              knowledgeFacets={knowledgeFacets}
              knowledgePresets={knowledgePresets}
              models={models}
              modelsError={modelsError}
              ragChat={ragChat}
              longTermMemoryEnabled={isLongTermMemoryEnabled}
              selectedInstructionIds={ragInstructionIds}
              onToggleInstruction={toggleInstructionSelection(setRagInstructionIds)}
              evalCandidateAction={{
                error: evalCandidatePromotion.error,
                isCreating: evalCandidatePromotion.isCreating,
                onCreateCandidate: createEvalCandidateFromAudit,
              }}
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
              conversationsEnabled={conversationsEnabled}
              conversationChat={directConversationChat}
              conversationRuns={directConversationRuns}
              conversations={directConversations}
              directChat={directChat}
              helperText={directPresentation.helperText}
              instructions={scenarioInstructions}
              isBlocked={directPresentation.isDirectSubmitBlocked}
              models={models}
              modelsError={modelsError}
              longTermMemoryEnabled={isLongTermMemoryEnabled}
              selectedInstructionIds={directInstructionIds}
              onToggleInstruction={toggleInstructionSelection(setDirectInstructionIds)}
              evalCandidateAction={{
                error: evalCandidatePromotion.error,
                isCreating: evalCandidatePromotion.isCreating,
                onCreateCandidate: createEvalCandidateFromAudit,
              }}
            />
          </section>

          <section
            aria-labelledby="nav-eval"
            hidden={activeTab !== "eval"}
            id="panel-eval"
            role="region"
          >
            {activeTab === "eval" ? <EvalTab focus={evalFocus} /> : null}
          </section>

          <section
            aria-labelledby="nav-settings"
            hidden={activeTab !== "settings"}
            id="panel-settings"
            role="region"
          >
            {activeTab === "settings" ? <LlmProviderSettingsPanel /> : null}
          </section>
        </main>
      </div>
    </div>
  );
}
