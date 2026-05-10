import { type ComponentProps } from "react";
import { Database, RefreshCw } from "lucide-react";
import { SectionIntro } from "@/components/app/SectionIntro";
import { KnowledgePresetLibraryPanel } from "@/components/KnowledgePresetLibraryPanel";
import { ReferenceProjectsTab } from "@/components/ReferenceProjectsTab";
import { ReferenceRagProjectsTab } from "@/components/ReferenceRagProjectsTab";
import { ReferenceWorkspaceTab } from "@/components/ReferenceWorkspaceTab";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useReferenceDataPanelState } from "@/hooks/useReferenceDataPanelState";
import type {
  RagProjectController,
  ReferenceDataController,
} from "@/components/referenceDataPresentation";

type ReferenceDataPanelProps = {
  knowledgePresets: ComponentProps<typeof KnowledgePresetLibraryPanel>;
  knowledgeFacets?: ComponentProps<typeof KnowledgePresetLibraryPanel>;
  referenceData: ReferenceDataController;
  ragProjects: RagProjectController;
  activeRagProjectKey?: string | null;
  onActiveRagProjectChange?: (projectKey: string) => void;
  showRagProjectSwitcher?: boolean;
};

export function ReferenceDataPanel({
  knowledgePresets,
  knowledgeFacets,
  referenceData,
  ragProjects,
  activeRagProjectKey = "general",
  onActiveRagProjectChange = () => undefined,
  showRagProjectSwitcher = true,
}: ReferenceDataPanelProps) {
  const state = useReferenceDataPanelState({
    referenceData,
    ragProjectsController: ragProjects,
    activeRagProjectKey,
    onActiveRagProjectChange,
  });

  const isReferenceDirectoryTab = state.activeTab !== "presets" && state.activeTab !== "facets";
  const activeProjectName = state.sortedRagProjects.find((project) => project.key === activeRagProjectKey)?.name
    ?? activeRagProjectKey;

  return (
    <div className="space-y-6">
      <SectionIntro
        actions={isReferenceDirectoryTab ? (
          <Button
            disabled={state.activeTab === "ragProjects" ? state.ragProjects.isLoading : referenceData.isLoading}
            type="button"
            variant="secondary"
            onClick={() => {
              if (state.activeTab === "ragProjects") {
                void state.ragProjects.reload();
              } else {
                void referenceData.reload();
              }
            }}
          >
            <RefreshCw className="h-4 w-4" />
            Обновить
          </Button>
        ) : undefined}
        badge={state.activeTab === "presets" ? "/api/knowledge-presets" : state.activeTab === "facets" ? "/api/knowledge-facets" : state.activeTab === "ragProjects" ? "/api/rag-projects" : "/api/reference"}
        badgeVariant="default"
        description="RAG-проект выбирает отдельный корпус материалов, пресетов, проектных инструкций и историю запусков. Legacy business projects скрыты из основного v1 UI."
        eyebrow="Справочники"
        title="Пресеты и справочники"
      />

      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant={state.activeTab === "ragProjects" ? "default" : "secondary"}
          onClick={() => state.setActiveTab("ragProjects")}
        >
          <Database className="h-4 w-4" />
          RAG-проекты
        </Button>
        <Button
          type="button"
          variant={state.activeTab === "presets" ? "default" : "secondary"}
          onClick={() => state.setActiveTab("presets")}
        >
          <Database className="h-4 w-4" />
          Пресеты проекта
        </Button>
        <Button
          type="button"
          variant={state.activeTab === "facets" ? "default" : "secondary"}
          onClick={() => state.setActiveTab("facets")}
        >
          <Database className="h-4 w-4" />
          Фасеты проекта
        </Button>
      </div>

      <ReferenceDataAlerts
        activeTab={state.activeTab}
        ragActionError={state.ragProjects.actionError}
        ragError={state.ragProjects.error}
        ragMessage={state.ragProjects.message}
        referenceActionError={referenceData.actionError}
        referenceError={referenceData.error}
        referenceMessage={referenceData.message}
      />

      {state.activeTab === "ragProjects" ? (
        <ReferenceRagProjectsTab
          activeRagProjectKey={activeRagProjectKey}
          showRagProjectSwitcher={showRagProjectSwitcher}
          state={state}
          onActiveRagProjectChange={onActiveRagProjectChange}
        />
      ) : state.activeTab === "presets" ? (
        <section className="space-y-5" aria-label="Пресеты">
          <KnowledgePresetLibraryPanel
            {...knowledgePresets}
            activeRagProjectKey={activeRagProjectKey}
            activeRagProjectName={activeProjectName}
            filterKind="preset"
            projects={state.projects}
          />
        </section>
      ) : state.activeTab === "facets" ? (
        <section className="space-y-5" aria-label="Фасеты">
          <KnowledgePresetLibraryPanel
            {...(knowledgeFacets ?? knowledgePresets)}
            activeRagProjectKey={activeRagProjectKey}
            activeRagProjectName={activeProjectName}
            filterKind="facet"
            projects={state.projects}
          />
        </section>
      ) : state.activeTab === "workspaces" ? (
        <ReferenceWorkspaceTab isLoading={referenceData.isLoading} state={state} />
      ) : (
        <ReferenceProjectsTab isLoading={referenceData.isLoading} state={state} />
      )}
    </div>
  );
}

type ReferenceDataAlertsProps = {
  activeTab: string;
  ragMessage: string | null;
  ragActionError: string | null;
  ragError: string | null;
  referenceMessage: string | null;
  referenceActionError: string | null;
  referenceError: string | null;
};

function ReferenceDataAlerts({
  activeTab,
  ragMessage,
  ragActionError,
  ragError,
  referenceMessage,
  referenceActionError,
  referenceError,
}: ReferenceDataAlertsProps) {
  const isReferenceDirectoryTab = activeTab !== "presets" && activeTab !== "facets";

  return (
    <>
      {activeTab === "ragProjects" && ragMessage ? (
        <Alert variant="success">
          <AlertTitle>RAG-проект обновлён</AlertTitle>
          <AlertDescription>{ragMessage}</AlertDescription>
        </Alert>
      ) : null}
      {activeTab === "ragProjects" && ragActionError ? (
        <Alert variant="destructive">
          <AlertTitle>Операция не выполнена</AlertTitle>
          <AlertDescription>{ragActionError}</AlertDescription>
        </Alert>
      ) : null}
      {activeTab === "ragProjects" && ragError ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить RAG-проекты</AlertTitle>
          <AlertDescription>{ragError}</AlertDescription>
        </Alert>
      ) : null}
      {isReferenceDirectoryTab && activeTab !== "ragProjects" && referenceMessage ? (
        <Alert variant="success">
          <AlertTitle>Справочник обновлён</AlertTitle>
          <AlertDescription>{referenceMessage}</AlertDescription>
        </Alert>
      ) : null}
      {isReferenceDirectoryTab && activeTab !== "ragProjects" && referenceActionError ? (
        <Alert variant="destructive">
          <AlertTitle>Операция не выполнена</AlertTitle>
          <AlertDescription>{referenceActionError}</AlertDescription>
        </Alert>
      ) : null}
      {isReferenceDirectoryTab && activeTab !== "ragProjects" && referenceError ? (
        <Alert variant="destructive">
          <AlertTitle>Не удалось загрузить справочники</AlertTitle>
          <AlertDescription>{referenceError}</AlertDescription>
        </Alert>
      ) : null}
    </>
  );
}
