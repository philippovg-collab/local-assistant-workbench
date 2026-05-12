import { Database, KeyRound, ListChecks, Tags, type LucideIcon } from "lucide-react";
import { SegmentedControl } from "@/components/app/SegmentedControl";
import { LlmProviderSettingsPanel } from "@/components/LlmProviderSettingsPanel";
import { ReferenceDataPanel } from "@/components/ReferenceDataPanel";
import type { useKnowledgeFacets } from "@/hooks/useKnowledgeFacets";
import type { useKnowledgePresets } from "@/hooks/useKnowledgePresets";
import type { useRagProjects } from "@/hooks/useRagProjects";
import type { useReferenceData } from "@/hooks/useReferenceData";
import type { SettingsTabId } from "./workbenchConfig";

type SettingsTabProps = {
  activeRagProjectKey: string;
  activeSettingsTab: SettingsTabId;
  knowledgeFacets: ReturnType<typeof useKnowledgeFacets>;
  knowledgePresets: ReturnType<typeof useKnowledgePresets>;
  ragProjects: ReturnType<typeof useRagProjects>;
  referenceData: ReturnType<typeof useReferenceData>;
  onActiveRagProjectChange: (projectKey: string) => void;
  onSettingsTabChange: (tab: SettingsTabId) => void;
};

const settingsTabItems: Array<{
  value: SettingsTabId;
  label: string;
  icon: LucideIcon;
}> = [
  { value: "llm", label: "LLM-подключения", icon: KeyRound },
  { value: "ragProjects", label: "RAG-проекты", icon: Database },
  { value: "presets", label: "Пресеты проекта", icon: ListChecks },
  { value: "facets", label: "Фасеты проекта", icon: Tags },
];

export function SettingsTab({
  activeRagProjectKey,
  activeSettingsTab,
  knowledgeFacets,
  knowledgePresets,
  ragProjects,
  referenceData,
  onActiveRagProjectChange,
  onSettingsTabChange,
}: SettingsTabProps) {
  return (
    <div className="space-y-6">
      <SegmentedControl
        ariaLabel="Вкладки настроек"
        items={settingsTabItems}
        value={activeSettingsTab}
        onValueChange={onSettingsTabChange}
      />

      {activeSettingsTab === "llm" ? (
        <LlmProviderSettingsPanel />
      ) : (
        <ReferenceDataPanel
          activeRagProjectKey={activeRagProjectKey}
          activeTab={activeSettingsTab}
          knowledgeFacets={{
            actionError: knowledgeFacets.actionError,
            error: knowledgeFacets.error,
            isLoading: knowledgeFacets.isLoading,
            message: knowledgeFacets.message,
            onCreatePreset: knowledgeFacets.createPreset,
            onDeletePreset: knowledgeFacets.deletePreset,
            onLoadPreset: (facetId) => knowledgeFacets.loadPreset(facetId),
            onLoadRevisionDiff: (facetId, fromRevision, toRevision) =>
              knowledgeFacets.loadRevisionDiff(facetId, fromRevision, toRevision),
            onLoadRevisions: (facetId) => knowledgeFacets.loadRevisions(facetId),
            onRestoreRevision: (facetId, revision) => knowledgeFacets.restoreRevision(facetId, revision),
            onUpdatePreset: knowledgeFacets.updatePreset,
            presets: knowledgeFacets.presets,
            revisionDiff: knowledgeFacets.revisionDiff,
            revisions: knowledgeFacets.revisions,
            selectedPreset: knowledgeFacets.selectedPreset,
          }}
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
          ragProjects={ragProjects}
          referenceData={referenceData}
          showReferenceTabs={false}
          showRagProjectSwitcher={activeSettingsTab === "ragProjects"}
          onActiveRagProjectChange={onActiveRagProjectChange}
        />
      )}
    </div>
  );
}
