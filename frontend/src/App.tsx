import { useEffect, useState, type Dispatch, type SetStateAction } from "react";
import "./App.css";
import kegocLogo from "./assets/logo-kegoc.png";
import { DirectChatPanel } from "./components/DirectChatPanel";
import { InstructionLibraryPanel } from "./components/InstructionLibraryPanel";
import { MaterialsPanel } from "./components/MaterialsPanel";
import { RagChatPanel } from "./components/RagChatPanel";
import { StatusSummary } from "./components/StatusSummary";
import { useChatExecution } from "./hooks/useChatExecution";
import { useHealth } from "./hooks/useHealth";
import { useInstructions } from "./hooks/useInstructions";
import { useMaterials } from "./hooks/useMaterials";
import { useModels } from "./hooks/useModels";
import { buildRagReadinessPresentation, deriveRagReadiness } from "./utils/readiness";

type WorkspaceTab = "overview" | "materials" | "instructions" | "rag" | "direct";

const tabs: Array<{ id: WorkspaceTab; label: string }> = [
  { id: "overview", label: "Обзор" },
  { id: "materials", label: "Материалы" },
  { id: "instructions", label: "Инструкции" },
  { id: "rag", label: "RAG чат" },
  { id: "direct", label: "Direct чат" },
];

function App() {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>("overview");
  const [ragInstructionIds, setRagInstructionIds] = useState<string[]>([]);
  const [directInstructionIds, setDirectInstructionIds] = useState<string[]>([]);
  const { health, error: healthError } = useHealth();
  const { models, error: modelsError } = useModels();
  const materials = useMaterials();
  const instructions = useInstructions();
  const ragReadiness = deriveRagReadiness(health, materials.materials);

  const ragChat = useChatExecution({
    mode: "rag",
    initialModel: "qwen2.5:7b",
    initialPrompt: "Что написано про тариф Премиум?",
    selectedInstructionIds: ragInstructionIds,
  });

  const directChat = useChatExecution({
    mode: "direct",
    initialModel: "qwen2.5:7b",
    initialPrompt: "Покажи пример request-response для локальной LLM через единый API.",
    initialSystemPrompt: "Отвечай кратко, по делу и на русском языке.",
    selectedInstructionIds: directInstructionIds,
  });

  useEffect(() => {
    const knownInstructionIds = new Set(instructions.instructions.map((instruction) => instruction.id));

    setRagInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
    setDirectInstructionIds((current) => {
      const next = current.filter((instructionId) => knownInstructionIds.has(instructionId));
      return next.length === current.length ? current : next;
    });
  }, [instructions.instructions]);

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
  const isOverviewTabActive = activeTab === "overview";
  const isMaterialsTabActive = activeTab === "materials";
  const isInstructionsTabActive = activeTab === "instructions";

  return (
    <main className="shell">
      <header className="shell-header">
        <nav aria-label="Рабочие вкладки" className="tab-strip" role="tablist">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;

            return (
              <button
                aria-controls={`tabpanel-${tab.id}`}
                aria-selected={isActive}
                className={`tab-button ${isActive ? "active" : ""}`}
                id={`tab-${tab.id}`}
                key={tab.id}
                role="tab"
                type="button"
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            );
          })}
        </nav>

        {isOverviewTabActive ? (
          <div className="hero-grid">
            <div className="shell-header-copy">
              <div className="shell-brand-lockup">
                <div className="shell-brand-heading">
                  <img alt="Логотип KEGOC" className="shell-logo" src={kegocLogo} />
                  <p className="eyebrow">KEGOC AI RAG Workspace</p>
                </div>

                <div className="shell-brand-text">
                  <h1 className="shell-title">AI-контур для создания direct и RAG сценариев</h1>
                </div>
              </div>

              <p className="shell-subtitle">
                Рабочее пространство позволяет загружать материалы, извлекать текст из PDF и сканов через OCR,
                подключать инструкции и выбирать локальные модели. После этого можно запускать direct-запросы
                без контекста или собирать RAG-сценарии, где ответ формируется по найденным фрагментам из базы
                знаний.
              </p>

              <div aria-label="Ключевые свойства темы" className="header-pills">
                <span className="header-pill">Обучение</span>
                <span className="header-pill">Инструкции</span>
                <span className="header-pill">Тестирование</span>
              </div>
            </div>

            <aside aria-label="Профиль темы" className="brand-panel">
              <div className="brand-panel-header">
                <span className="badge subtle">Функциональный профиль</span>
                <strong>KEGOC AI RAG Workspace</strong>
              </div>

              <p className="brand-panel-copy">
                Рабочее пространство объединяет материалы, локальные модели и instruction snippets, чтобы direct-
                и RAG-сценарии собирались в одном управляемом контуре без переключения между разными экранами.
              </p>

              <div className="brand-metrics">
                <article>
                  <span>Сценарии</span>
                  <strong>Direct и RAG</strong>
                </article>
                <article>
                  <span>Контекст</span>
                  <strong>Материалы и инструкции</strong>
                </article>
                <article>
                  <span>Контроль</span>
                  <strong>Модели и prompt policy</strong>
                </article>
              </div>
            </aside>
          </div>
        ) : null}
      </header>

      <section className="tab-panel-shell">
        <section
          aria-labelledby="tab-overview"
          className="tab-panel"
          hidden={activeTab !== "overview"}
          id="tabpanel-overview"
          role="tabpanel"
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
          aria-labelledby="tab-materials"
          className="tab-panel"
          hidden={!isMaterialsTabActive}
          id="tabpanel-materials"
          role="tabpanel"
        >
          {isMaterialsTabActive ? (
            <MaterialsPanel
              actionError={materials.actionError}
              deletingMaterialId={materials.deletingMaterialId}
              error={materials.error}
              isLoading={materials.isLoading}
              lineageError={materials.lineageError}
              loadingLineageMaterialId={materials.loadingLineageMaterialId}
              materials={materials.materials}
              message={materials.message}
              onClearLineage={materials.clearLineage}
              onCreateText={materials.createTextMaterial}
              onDelete={materials.deleteMaterial}
              onLoadLineage={(materialId) => materials.loadLineage(materialId)}
              onReindex={materials.reindexMaterial}
              onUpload={materials.uploadMaterial}
              policyWarning={materials.policyWarning}
              ragPresentation={ragPresentation}
              reindexingMaterialId={materials.reindexingMaterialId}
              selectedLineage={materials.selectedLineage}
              uploadPolicy={materials.uploadPolicy}
            />
          ) : null}
        </section>

        <section
          aria-labelledby="tab-instructions"
          className="tab-panel"
          hidden={!isInstructionsTabActive}
          id="tabpanel-instructions"
          role="tabpanel"
        >
          {isInstructionsTabActive ? (
            <InstructionLibraryPanel
              actionError={instructions.actionError}
              detailError={instructions.detailError}
              deletingInstructionId={instructions.deletingInstructionId}
              error={instructions.error}
              isLoadingDetail={instructions.isLoadingDetail}
              instructions={instructions.instructions}
              isLoading={instructions.isLoading}
              message={instructions.message}
              onCreateInstruction={instructions.createInstruction}
              onDeleteInstruction={instructions.deleteInstruction}
              onLoadInstruction={(instructionId) => instructions.loadInstruction(instructionId)}
              onUpdateInstruction={instructions.updateInstruction}
              selectedInstruction={instructions.selectedInstruction}
            />
          ) : null}
        </section>

        <section
          aria-labelledby="tab-rag"
          className="tab-panel"
          hidden={activeTab !== "rag"}
          id="tabpanel-rag"
          role="tabpanel"
        >
          <RagChatPanel
            error={ragChat.error}
            helperText={ragPresentation.chatHelperText}
            isBlocked={ragPresentation.isRagSubmitBlocked}
            isSubmitting={ragChat.isSubmitting}
            instructions={instructions.instructions}
            models={models}
            modelsError={modelsError}
            prompt={ragChat.prompt}
            response={ragChat.response}
            selectedModel={ragChat.model}
            selectedInstructionIds={ragInstructionIds}
            systemPrompt={ragChat.systemPrompt}
            onModelChange={ragChat.setModel}
            onPromptChange={ragChat.setPrompt}
            onSubmit={ragChat.submit}
            onSystemPromptChange={ragChat.setSystemPrompt}
            onToggleInstruction={toggleInstructionSelection(setRagInstructionIds)}
          />
        </section>

        <section
          aria-labelledby="tab-direct"
          className="tab-panel"
          hidden={activeTab !== "direct"}
          id="tabpanel-direct"
          role="tabpanel"
        >
          <DirectChatPanel
            error={directChat.error}
            isSubmitting={directChat.isSubmitting}
            instructions={instructions.instructions}
            models={models}
            modelsError={modelsError}
            prompt={directChat.prompt}
            requestPreview={directChat.lastSubmittedRequest && (directChat.isSubmitting || directChat.response)
              ? directChat.lastSubmittedRequest
              : {
                  mode: "direct",
                  model: directChat.model,
                  prompt: directChat.prompt,
                  instructionIds: directInstructionIds,
                  ...(directChat.systemPrompt.trim() ? { systemPrompt: directChat.systemPrompt.trim() } : {}),
                }}
            response={directChat.response}
            selectedModel={directChat.model}
            selectedInstructionIds={directInstructionIds}
            systemPrompt={directChat.systemPrompt}
            onModelChange={directChat.setModel}
            onPromptChange={directChat.setPrompt}
            onSubmit={directChat.submit}
            onSystemPromptChange={directChat.setSystemPrompt}
            onToggleInstruction={toggleInstructionSelection(setDirectInstructionIds)}
          />
        </section>
      </section>
    </main>
  );
}

export default App;
